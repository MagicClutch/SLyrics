package dev.slyrics.compat.oneconfig;

import dev.slyrics.Config;
import dev.slyrics.HudMode;
import dev.slyrics.LyricsHud;
import dev.slyrics.MediaPlayer;
import dev.slyrics.SLyricsClient;
import dev.slyrics.Spotify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.LegacyHud;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The three HUD elements of SLyrics (lyric line, karaoke panel, media player) as OneConfig HUD elements: added, moved, scaled
 * and set up in OneConfig's own HUD editor (each element has its settings there). An element shows while it is added in the
 * editor. They draw with the same code as without OneConfig. Place, size and settings are kept in step with {@link Config}
 * (place and size as percent of the screen), so profiles see the same values. The editor works on copies of the registered
 * elements, so the copies that are really on screen are the ones kept in step.
 */
final class SLyricsHuds {
	private static final float EPS_POS = 0.05f, EPS_SCALE = 0.0005f;
	private static final long SAVE_DELAY_MS = 400;

	private static boolean registered;
	/** Per element on screen: its place-and-size link and the last values of its settings. */
	private static final Map<Base, Link> links = new WeakHashMap<>();
	private static final Map<Base, Map<String, Object>> seenSettings = new WeakHashMap<>();
	private static boolean dirty;
	private static long dirtySince;

	private SLyricsHuds() {}

	static void register() {
		LineHud line = new LineHud();
		PanelHud panel = new PanelHud();
		MediaHud media = new MediaHud();
		for (Base h : new Base[]{line, panel, media}) {
			h.setUseGuiScale(true); // scale 1.0 = the size the mod draws at without OneConfig, so the numbers match the config
			HudManager.register(h);
		}
		registered = true;
		HudMode.oneConfig = true;
	}

	/** Called every client tick. */
	static void tick() {
		if (!registered) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() == null) return;
		float gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
		if (gw <= 0 || gh <= 0) return;
		for (Hud h : HudManager.instances()) {
			if (!(h instanceof Base hud)) continue;
			try {
				boolean changed = links.computeIfAbsent(hud, Link::new).sync(gw, gh);
				changed |= syncSettings(hud);
				if (changed) {
					dirty = true;
					dirtySince = System.currentTimeMillis();
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				SLyricsClient.LOGGER.warn("OneConfig HUD sync failed for {}", hud.getClass().getSimpleName(), e);
			}
		}
		if (dirty && System.currentTimeMillis() - dirtySince > SAVE_DELAY_MS) { // not while the mouse is still dragging
			dirty = false;
			Config.save();
		}
	}

	/** Opens OneConfig's HUD editor. */
	static void openEditor() {
		HudManager.INSTANCE.openEditor();
	}

	private static boolean editing() {
		return HudManager.INSTANCE.isEditing();
	}

	/**
	 * The element's settings (its fields with a OneConfig annotation, named like the Config fields) and the config in step:
	 * whichever side changed since the last pass wins; at the start the config is the truth. Returns true if the config changed.
	 */
	private static boolean syncSettings(Base hud) throws ReflectiveOperationException {
		Map<String, Object> seen = seenSettings.computeIfAbsent(hud, k -> new HashMap<>());
		boolean changed = false;
		for (Field f : hud.getClass().getDeclaredFields()) {
			if (!f.isAnnotationPresent(Switch.class) && !f.isAnnotationPresent(Slider.class)) continue;
			Field cf = Config.class.getField(f.getName());
			Object hv = f.get(hud), cv = cf.get(Config.INSTANCE), last = seen.get(f.getName());
			if (last != null && !hv.equals(last)) { // changed in the HUD editor
				cf.set(Config.INSTANCE, hv);
				seen.put(f.getName(), hv);
				changed = true;
			} else if (!cv.equals(last)) { // first pass, or changed elsewhere (profile)
				f.set(hud, cv);
				seen.put(f.getName(), cv);
			}
		}
		return changed;
	}

	// ------------------------------------------------------------------ the elements

	private abstract static class Base extends LegacyHud {
		Base(String id, String title) {
			super(id, title, Hud.Category.getINFO());
		}

		@Override
		public boolean update() {
			return true;
		}

		@Override
		public boolean multipleInstancesAllowed() {
			return false; // one of each: they all share the same place and settings in the config
		}

		/** Size in the element's own units (before the scale). */
		abstract float contentW();

		abstract float contentH();

		@Override
		public float getWidth() {
			return contentW();
		}

		@Override
		public float getHeight() {
			return contentH();
		}
	}

	/** The lyric line. Its box is fixed; the text is centered in it, and that center is (xPercent, yPercent). */
	private static final class LineHud extends Base {
		static final float W = 160f, H = 14f;

		@Switch(title = "Scrolling line", description = "The line scrolls through like a ticker: the word being sung stays in the middle and the text keeps running past it.")
		public boolean lineScroll = Config.INSTANCE.lineScroll;

		@Slider(title = "Words at once", description = "How many words are shown at once (0 = the whole line). With scrolling: how many words are visible around the middle.", min = 0, max = 16, step = 1)
		public int lineMaxWords = Config.INSTANCE.lineMaxWords;

		@Switch(title = "Previous and next line", description = "Show the previous and the next line, dimmed")
		public boolean showNeighbors = Config.INSTANCE.showNeighbors;

		@Switch(title = "Status message", description = "A short message when nothing is playing or no lyrics were found")
		public boolean showStatus = Config.INSTANCE.showStatus;

		LineHud() {
			super("slyrics_line", "SLyrics lyric line");
		}

		@Override
		float contentW() {
			return W;
		}

		@Override
		float contentH() {
			return H;
		}

		@Override
		public boolean shouldShow() {
			Config c = Config.INSTANCE;
			return editing() || c.enabled;
		}

		@Override
		public void render(GuiGraphicsExtractor g) {
			Config c = Config.INSTANCE;
			Minecraft mc = Minecraft.getInstance();
			float room = g.guiWidth();
			float half = (room / 2f - 6f) / Math.max(0.01f, getEffectiveScale());
			if (editing() && Spotify.lyrics.isEmpty()) {
				LyricsHud.draw(g, mc.font, "The lyric line", W / 2f, H / 2f, 1f, c.rgb(), 1f, c);
				return;
			}
			LyricsHud.drawLine(g, mc.font, c, W / 2f, H / 2f, 1f, half);
		}
	}

	/** The karaoke panel; its box follows the text. */
	private static final class PanelHud extends Base {
		PanelHud() {
			super("slyrics_panel", "SLyrics karaoke panel");
		}

		private float[] size() {
			float[] s = LyricsHud.drawPanelLocal(null, Minecraft.getInstance().font, Config.INSTANCE, editing());
			return s != null ? s : new float[]{60f, 40f};
		}

		@Override
		float contentW() {
			return size()[0];
		}

		@Override
		float contentH() {
			return size()[1];
		}

		@Override
		public boolean shouldShow() {
			Config c = Config.INSTANCE;
			return editing() || c.enabled && !Spotify.lyrics.isEmpty();
		}

		@Override
		public void render(GuiGraphicsExtractor g) {
			LyricsHud.drawPanelLocal(g, Minecraft.getInstance().font, Config.INSTANCE, editing() && Spotify.lyrics.isEmpty());
		}
	}

	/** The media player window. */
	private static final class MediaHud extends Base {
		@Slider(title = "Opacity", description = "How opaque the window is", min = 20, max = 100, step = 1)
		public int mediaOpacity = Config.INSTANCE.mediaOpacity;

		@Switch(title = "Album cover", description = "Show the album cover (if the player provides one)")
		public boolean mediaCover = Config.INSTANCE.mediaCover;
		MediaHud() {
			super("slyrics_media", "SLyrics media player");
			setShowInChat(true); // the buttons only work while a screen is open
			setShowInScreens(true);
		}

		@Override
		float contentW() {
			return MediaPlayer.W;
		}

		@Override
		float contentH() {
			return MediaPlayer.H;
		}

		@Override
		public boolean shouldShow() {
			return editing() || MediaPlayer.hasSong();
		}

		@Override
		public void render(GuiGraphicsExtractor g) {
			MediaPlayer.drawInHud(g, editing());
		}
	}

	// ------------------------------------------------------------------ keeping the config and the elements in step

	private static final class Link {
		final Base hud;
		boolean started;
		/** Last values seen on the element and in the config: top-left x, y in screen pixels, scale. */
		float hx, hy, hs, cx, cy, cs;

		Link(Base hud) {
			this.hud = hud;
		}

		/** The config's top-left corner and scale for a screen of gw x gh. */
		float[] fromConfig(float gw, float gh) {
			Config c = Config.INSTANCE;
			if (hud instanceof LineHud) {
				float s = c.scale;
				return new float[]{gw * c.xPercent / 100f - LineHud.W * s / 2f, gh * c.yPercent / 100f - LineHud.H * s / 2f, s};
			}
			if (hud instanceof PanelHud) return new float[]{gw * c.hudX / 100f, gh * c.hudY / 100f, c.hudScale / 100f};
			return new float[]{gw * c.mediaX / 100f, gh * c.mediaY / 100f, c.mediaScale / 100f};
		}

		void toConfig(float x, float y, float s, float gw, float gh) {
			Config c = Config.INSTANCE;
			if (hud instanceof LineHud) {
				s = Math.max(0.5f, Math.min(5f, s));
				c.scale = s;
				c.xPercent = (x + LineHud.W * s / 2f) / gw * 100f;
				c.yPercent = (y + LineHud.H * s / 2f) / gh * 100f;
			} else if (hud instanceof PanelHud) {
				c.hudScale = Math.max(25, Math.min(400, Math.round(s * 100f)));
				c.hudX = x / gw * 100f;
				c.hudY = y / gh * 100f;
			} else {
				c.mediaScale = Math.max(25, Math.min(400, Math.round(s * 100f)));
				c.mediaX = x / gw * 100f;
				c.mediaY = y / gh * 100f;
			}
		}

		/** Puts the config's place and size on the element, then reads back what it really took (it keeps elements on screen). */
		void pushToHud(float[] cfg) {
			float guiScale = Math.max(1f, Minecraft.getInstance().getWindow().getGuiScale());
			hud.setCustomScale(hud.getUseGuiScale() ? cfg[2] : cfg[2] * guiScale);
			hud.setX(cfg[0]);
			hud.setY(cfg[1]);
			readHud();
		}

		void readHud() {
			hx = hud.getX();
			hy = hud.getY();
			hs = hud.getEffectiveScale();
		}

		/** Returns true if the config was changed (and has to be saved). */
		boolean sync(float gw, float gh) {
			float[] cfg = fromConfig(gw, gh);
			if (!started) { // the config is the truth at the start
				started = true;
				pushToHud(cfg);
				cx = cfg[0]; cy = cfg[1]; cs = cfg[2];
				return false;
			}
			boolean hudChanged = Math.abs(hud.getX() - hx) > EPS_POS || Math.abs(hud.getY() - hy) > EPS_POS || Math.abs(hud.getEffectiveScale() - hs) > EPS_SCALE;
			boolean cfgChanged = Math.abs(cfg[0] - cx) > EPS_POS || Math.abs(cfg[1] - cy) > EPS_POS || Math.abs(cfg[2] - cs) > EPS_SCALE;
			if (hudChanged) { // moved or resized in OneConfig's HUD editor
				readHud();
				toConfig(hx, hy, hs, gw, gh);
				float[] now = fromConfig(gw, gh);
				cx = now[0]; cy = now[1]; cs = now[2];
				return true;
			}
			if (cfgChanged) { // changed elsewhere (settings, old editor, profile, command)
				pushToHud(cfg);
				cx = cfg[0]; cy = cfg[1]; cs = cfg[2];
			}
			return false;
		}
	}
}
