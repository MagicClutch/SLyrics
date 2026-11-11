package dev.slyrics;

import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.base.CustomButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD editor: drag the lyric line, the karaoke panel and the media player with the mouse, scroll over one to resize it.
 * They never overlap. Esc or Done saves.
 */
public final class HudEditor extends Screen {
	private static final Track SAMPLE = new Track("sample", "Song title", "Artist", "", 215_000L);
	private static final String LINE_SAMPLE = "The lyric line";
	private static final int PLAYER = 0, KARAOKE = 1, LINE = 2, COUNT = 3;
	/** Topmost first, for clicking. */
	private static final int[] CLICK_ORDER = {LINE, KARAOKE, PLAYER};
	private int dragging = -1, selected = -1;
	private double offX, offY;
	/** The size shown for a moment after scrolling, like the action bar above the hotbar. */
	private String toast = "";
	private long toastUntil;

	private final Screen parent;

	private HudEditor(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	public static void open() {
		open(null);
	}

	/** Opens the editor; Done goes back to the given screen (or to the game). */
	public static void open(Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new HudEditor(parent)));
	}

	/**
	 * Clicking the "HUD editor" or "Profiles" category in the settings opens the editor / the profile list straight away instead of a page that
	 * only holds an Open button. Resourceful Config opens a screen for the category; this replaces it.
	 */
	public static void initRedirect() {
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!screen.getClass().getName().startsWith("com.teamresourceful.resourcefulconfig.") || !screen.getClass().getName().endsWith("ConfigScreen")) return;
			Object cfg = field(screen, "config"), parent = field(screen, "parent");
			if (!(cfg instanceof com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig category) || !(parent instanceof Screen parentScreen)) return;
			if (!(field(parentScreen, "config") instanceof com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig main) || !"slyrics".equals(main.id())) return;
			if (main.categories().get("HUD editor") == category) open(parentScreen);
			else if (main.categories().get("Profiles") == category) {
				client.execute(() -> client.gui.setScreen(new dev.slyrics.profiles.ProfilesScreen(parentScreen)));
			}
		});
	}

	private static Object field(Object o, String name) {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			try {
				var f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
				// try the superclass
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	@Override
	protected void init() {
		MediaPlayer.editing = true;
		int bw = 70, gap = 6, x = (this.width - 2 * bw - gap) / 2, y = this.height - 30;
		CustomButton reset = addRenderableWidget(new CustomButton(bw, 18, Component.literal("Reset"), this::reset));
		reset.setPosition(x, y);
		CustomButton done = addRenderableWidget(new CustomButton(bw, 18, Component.literal("Done"), this::onClose));
		done.setPosition(x + bw + gap, y);
	}

	@Override
	public void removed() {
		MediaPlayer.editing = false;
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(parent);
	}

	// ------------------------------------------------------------------ modules

	/** Box {x, y, width, height} of a module in screen pixels. */
	private float[] box(int m) {
		Config c = Config.INSTANCE;
		if (m == PLAYER) {
			float[] p = MediaPlayer.place(this.width, this.height);
			return new float[]{p[0], p[1], MediaPlayer.W * p[2], MediaPlayer.H * p[2]};
		}
		if (m == KARAOKE) {
			float[] b = LyricsHud.sampleBox(this.font, c, this.width, this.height);
			return new float[]{b[0], b[1], b[3], b[4]};
		}
		float w = (this.font.width(LyricsHud.styled(LINE_SAMPLE, c, true)) + 6) * c.scale, h = (this.font.lineHeight + 4) * c.scale;
		float cx = this.width * c.xPercent / 100f, cy = this.height * c.yPercent / 100f;
		return new float[]{Math.max(0, Math.min(cx - w / 2, this.width - w)), Math.max(0, Math.min(cy - h / 2, this.height - h)), w, h};
	}

	/** Moves a module to (x, y) pixels, away from the others and inside the screen. */
	private void moveTo(int m, float x, float y) {
		float[] mine = box(m);
		float[] at = {x, y};
		for (int pass = 0; pass < 3; pass++) {
			for (int o = 0; o < COUNT; o++) {
				if (o == m) continue;
				at = HudLayout.separate(at[0], at[1], mine[2], mine[3], box(o), this.width, this.height);
			}
		}
		Config c = Config.INSTANCE;
		switch (m) {
			case PLAYER -> {
				c.mediaX = at[0] / this.width * 100f;
				c.mediaY = at[1] / this.height * 100f;
			}
			case KARAOKE -> {
				c.hudX = at[0] / this.width * 100f;
				c.hudY = at[1] / this.height * 100f;
			}
			default -> {
				c.xPercent = (at[0] + mine[2] / 2) / this.width * 100f;
				c.yPercent = (at[1] + mine[3] / 2) / this.height * 100f;
			}
		}
	}

	private boolean overlapsAny(int m) {
		float[] b = box(m);
		for (int o = 0; o < COUNT; o++) {
			if (o != m && HudLayout.overlaps(b[0], b[1], b[2], b[3], box(o))) return true;
		}
		return false;
	}

	private void rescale(int m, int steps) {
		Config c = Config.INSTANCE;
		int oldInt = m == PLAYER ? c.mediaScale : c.hudScale;
		float oldLine = c.scale;
		switch (m) {
			case PLAYER -> c.mediaScale = Math.max(50, Math.min(300, c.mediaScale + 10 * steps));
			case KARAOKE -> c.hudScale = Math.max(40, Math.min(300, c.hudScale + 10 * steps));
			default -> c.scale = Math.max(0.5f, Math.min(5f, c.scale + 0.2f * steps));
		}
		if (overlapsAny(m)) {
			float[] b = box(m);
			moveTo(m, b[0], b[1]); // it grew into another module: step aside
		}
		if (overlapsAny(m)) { // no room: undo
			if (m == PLAYER) c.mediaScale = oldInt;
			else if (m == KARAOKE) c.hudScale = oldInt;
			else c.scale = oldLine;
		}
	}

	private void reset() {
		Config d = new Config(), c = Config.INSTANCE;
		c.mediaX = d.mediaX; c.mediaY = d.mediaY; c.mediaScale = d.mediaScale;
		c.hudX = d.hudX; c.hudY = d.hudY; c.hudScale = d.hudScale;
		c.xPercent = d.xPercent; c.yPercent = d.yPercent; c.scale = d.scale;
		for (int m : new int[]{KARAOKE, LINE}) {
			float[] b = box(m);
			moveTo(m, b[0], b[1]);
		}
	}

	private int moduleAt(double mx, double my) {
		for (int m : CLICK_ORDER) {
			float[] b = box(m);
			if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return m;
		}
		return -1;
	}

	private String label(int m) {
		Config c = Config.INSTANCE;
		return switch (m) {
			case PLAYER -> "Media player " + c.mediaScale + "%";
			case KARAOKE -> "Karaoke " + c.hudScale + "%" + (c.hudMode ? "" : " (karaoke mode is off)");
			default -> "Lyric line " + Math.round(c.scale * 100) + "%" + (c.anyWorld() ? " (hidden while a world mode is on)" : "");
		};
	}

	// ------------------------------------------------------------------ screen

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Config c = Config.INSTANCE;
		g.centeredText(this.font, Component.literal("Edit HUD").withColor(UIConstants.TEXT_TITLE), this.width / 2, 10, -1);
		g.centeredText(this.font, Component.literal("Drag a module to move it, scroll over it to resize it"), this.width / 2, 24, 0xFFAAAAAA);

		float[] pb = MediaPlayer.place(this.width, this.height);
		Track t = Spotify.track != null ? Spotify.track : SAMPLE;
		long pos = Spotify.track != null ? Spotify.positionMs() : 83_000L;
		MediaPlayer.draw(g, t, pos, pb[0], pb[1], pb[2], false);
		float[] lb = LyricsHud.sampleBox(this.font, c, this.width, this.height);
		if (!c.background) g.fill(Math.round(lb[0]), Math.round(lb[1]), Math.round(lb[0] + lb[3]), Math.round(lb[1] + lb[4]), 0x55000000);
		LyricsHud.drawSample(g, this.font, c, lb);
		float[] ln = box(LINE);
		LyricsHud.draw(g, this.font, LINE_SAMPLE, ln[0] + ln[2] / 2, ln[1] + ln[3] / 2, c.scale, c.rgb(), 1f, c, 1f);

		int hover = dragging >= 0 ? dragging : moduleAt(mouseX, mouseY);
		for (int m = 0; m < COUNT; m++) {
			float[] b = box(m);
			int col = m == hover ? 0xFFFFFFFF : 0x66FFFFFF;
			g.outline(Math.round(b[0]) - 1, Math.round(b[1]) - 1, Math.round(b[2]) + 2, Math.round(b[3]) + 2, col);
		}
		long left = toastUntil - System.currentTimeMillis();
		if (left > 0 && !toast.isEmpty()) {
			int alpha = (int) Math.max(8, Math.min(255, left * 255 / 400)); // fades out during the last 0.4 s
			int w = this.font.width(toast), x = (this.width - w) / 2, y = this.height - 52;
			g.fill(x - 4, y - 3, x + w + 4, y + this.font.lineHeight + 2, (alpha * 150 / 255) << 24);
			g.text(this.font, toast, x, y, (alpha << 24) | 0xFFFFFF, true);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		int m = moduleAt(e.x(), e.y());
		if (m < 0) return false;
		float[] b = box(m);
		dragging = selected = m;
		offX = e.x() - b[0];
		offY = e.y() - b[1];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging < 0) return super.mouseDragged(e, dx, dy);
		moveTo(dragging, (float) (e.x() - offX), (float) (e.y() - offY));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = -1;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int m = moduleAt(mx, my);
		if (m < 0) m = selected;
		if (m < 0) return false;
		selected = m;
		rescale(m, sy > 0 ? 1 : sy < 0 ? -1 : 0);
		toast = label(m);
		toastUntil = System.currentTimeMillis() + 1500;
		return true;
	}
}
