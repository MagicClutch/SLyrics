package dev.slyrics;

import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.base.CustomButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import dev.slyrics.profiles.Profiles;
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
	private static final float MARGIN = 6f, SNAP = 5f;
	/** Screen position of the guide line to draw while a module is snapped (x for a vertical line, y for a horizontal one), -1 = none. */
	private float guideX = -1, guideY = -1;
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
		for (int m : new int[]{KARAOKE, LINE}) { // modules that already overlap are separated once, so scrolling does not jump
			if (overlapsAny(m)) {
				float[] b = box(m);
				moveTo(m, b[0], b[1]);
			}
		}
		reset = addRenderableWidget(new CustomButton(BW, 18, Component.literal("Reset"), this::reset));
		save = addRenderableWidget(new CustomButton(SAVE_W, 18, Component.literal("Save profile"), this::saveProfile));
		done = addRenderableWidget(new CustomButton(BW, 18, Component.literal("Done"), this::onClose));
		layoutButtons();
	}

	private static final int BW = 70, SAVE_W = 90, GAP = 6;
	private CustomButton reset, save, done;

	/** True if a profile is active and the settings (with the layout) differ from what is saved in it. */
	private static boolean canSave() {
		return !Profiles.active().isEmpty() && Profiles.modified();
	}

	/** Reset | Save profile | Done, centered; Save profile only while there is something to save. */
	private void layoutButtons() {
		boolean showSave = canSave();
		save.visible = showSave;
		save.active = showSave;
		int total = 2 * BW + GAP + (showSave ? SAVE_W + GAP : 0);
		int x = (this.width - total) / 2, y = this.height - 30;
		reset.setPosition(x, y);
		x += BW + GAP;
		if (showSave) {
			save.setPosition(x, y);
			x += SAVE_W + GAP;
		}
		done.setPosition(x, y);
	}

	private void saveProfile() {
		try {
			ConfigSync.saveAll();
			String name = Profiles.save(Profiles.active());
			toast = "Saved profile '" + name + "'";
		} catch (Exception e) {
			toast = "Could not save the profile";
		}
		toastUntil = System.currentTimeMillis() + 1800;
	}

	@Override
	public void removed() {
		MediaPlayer.editing = false;
	}

	@Override
	public void onClose() {
		ConfigSync.saveAll(); // also writes `scale`, which Resourceful Config stores
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
		setPos(m, at[0], at[1]);
	}

	/** Puts a module at (x, y) pixels (kept inside the screen) without looking at the other modules. */
	private void setPos(int m, float x, float y) {
		float[] mine = box(m);
		x = Math.max(0, Math.min(x, this.width - mine[2]));
		y = Math.max(0, Math.min(y, this.height - mine[3]));
		Config c = Config.INSTANCE;
		switch (m) {
			case PLAYER -> {
				c.mediaX = x / this.width * 100f;
				c.mediaY = y / this.height * 100f;
			}
			case KARAOKE -> {
				c.hudX = x / this.width * 100f;
				c.hudY = y / this.height * 100f;
			}
			default -> {
				c.xPercent = (x + mine[2] / 2) / this.width * 100f;
				c.yPercent = (y + mine[3] / 2) / this.height * 100f;
			}
		}
	}

	/** Snaps a box at (x, y) to the screen center and to the edges (with a margin) when it comes close, and remembers the guide lines to show. */
	private float[] snap(float x, float y, float w, float h) {
		guideX = guideY = -1;
		float[] xs = {MARGIN, (this.width - w) / 2f, this.width - w - MARGIN};
		float[] xl = {MARGIN, this.width / 2f, this.width - MARGIN};
		float[] ys = {MARGIN, (this.height - h) / 2f, this.height - h - MARGIN};
		float[] yl = {MARGIN, this.height / 2f, this.height - MARGIN};
		float bestX = SNAP, bestY = SNAP;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(x - xs[i]) < bestX) { bestX = Math.abs(x - xs[i]); guideX = xl[i]; }
			if (Math.abs(y - ys[i]) < bestY) { bestY = Math.abs(y - ys[i]); guideY = yl[i]; }
		}
		float nx = x, ny = y;
		for (int i = 0; i < 3; i++) {
			if (guideX == xl[i] && Math.abs(x - xs[i]) < SNAP) nx = xs[i];
			if (guideY == yl[i] && Math.abs(y - ys[i]) < SNAP) ny = ys[i];
		}
		return new float[]{nx, ny};
	}

	private boolean overlapsAny(int m) {
		float[] b = box(m);
		for (int o = 0; o < COUNT; o++) {
			if (o != m && HudLayout.overlaps(b[0], b[1], b[2], b[3], box(o))) return true;
		}
		return false;
	}

	/** The lyric line size is a Resourceful Config setting: set both copies, or ConfigSync would copy the old value back a moment later. */
	private static void setLineScale(float v) {
		Config.INSTANCE.scale = v;
		RcConfig.SizeCat.scale = v;
	}

	/**
	 * Grows or shrinks a module: the media player and the karaoke panel stay on their top-left corner, the lyric line on its
	 * own center. It only stops when it would run into another module.
	 */
	private void rescale(int m, int steps) {
		Config c = Config.INSTANCE;
		int oldInt = m == PLAYER ? c.mediaScale : c.hudScale;
		float oldLine = c.scale;
		boolean hadOverlap = overlapsAny(m);
		float[] before = box(m);
		float cx = before[0] + before[2] / 2, cy = before[1] + before[3] / 2;
		switch (m) {
			case PLAYER -> c.mediaScale = Math.max(50, Math.min(300, c.mediaScale + 10 * steps));
			case KARAOKE -> c.hudScale = Math.max(40, Math.min(300, c.hudScale + 10 * steps));
			default -> setLineScale(Math.max(0.5f, Math.min(5f, steps > 0 ? c.scale * 1.1f : c.scale / 1.1f)));
		}
		float[] after = box(m);
		if (m == LINE) setPos(m, cx - after[2] / 2, cy - after[3] / 2);
		else setPos(m, before[0], before[1]);
		boolean noEffect = steps > 0 && after[2] <= before[2] + 0.01f && after[3] <= before[3] + 0.01f; // at its limit: do not pile up a size nobody sees
		if (noEffect || !hadOverlap && overlapsAny(m)) { // no room: undo
			if (m == PLAYER) c.mediaScale = oldInt;
			else if (m == KARAOKE) c.hudScale = oldInt;
			else setLineScale(oldLine);
			setPos(m, before[0], before[1]);
		}
	}

	private void reset() {
		Config d = new Config(), c = Config.INSTANCE;
		c.mediaX = d.mediaX; c.mediaY = d.mediaY; c.mediaScale = d.mediaScale;
		c.hudX = d.hudX; c.hudY = d.hudY; c.hudScale = d.hudScale;
		c.xPercent = d.xPercent; c.yPercent = d.yPercent; setLineScale(d.scale);
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
			case KARAOKE -> "Karaoke " + c.hudScale + "%" + (c.hudMode ? "" : " (off)");
			default -> "Lyric line " + Math.round(c.scale * 100) + "%" + (c.lineMode ? "" : " (off)");
		};
	}

	// ------------------------------------------------------------------ screen

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		layoutButtons();
		super.extractRenderState(g, mouseX, mouseY, a);
		Config c = Config.INSTANCE;
		g.centeredText(this.font, Component.literal("Edit HUD").withColor(UIConstants.TEXT_TITLE), this.width / 2, 10, -1);
		g.centeredText(this.font, Component.literal("Drag a module to move it, scroll over it to resize it"), this.width / 2, 24, 0xFFAAAAAA);

		float[] pb = MediaPlayer.place(this.width, this.height);
		Track t = Spotify.track != null ? Spotify.track : SAMPLE;
		long pos = Spotify.track != null ? Spotify.positionMs() : 83_000L;
		MediaPlayer.draw(g, t, pos, pb[0], pb[1], pb[2], false, false);
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
		if (guideX >= 0) g.fill(Math.round(guideX), 0, Math.round(guideX) + 1, this.height, 0xCC55E6FF);
		if (guideY >= 0) g.fill(0, Math.round(guideY), this.width, Math.round(guideY) + 1, 0xCC55E6FF);
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
		float[] b = box(dragging);
		float[] at = snap((float) (e.x() - offX), (float) (e.y() - offY), b[2], b[3]);
		moveTo(dragging, at[0], at[1]);
		float[] after = box(dragging);
		if (Math.abs(after[0] - at[0]) > 0.6f) guideX = -1; // pushed away by another module: not really snapped
		if (Math.abs(after[1] - at[1]) > 0.6f) guideY = -1;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = -1;
		guideX = guideY = -1;
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
