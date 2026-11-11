package dev.slyrics;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The media player window (top-left by default): cover, title, artist, progress and previous / play-pause / next.
 * Clicking works while a screen is open (chat, pause menu); keys can be bound in the controls menu.
 */
public final class MediaPlayer implements HudElement {
	private static final int W = 160, H = 46, COVER = 36;
	/** Local (unscaled) button boxes: x, y, w, h. */
	private static final int[][] BUTTONS = {{44, 24, 16, 12}, {64, 24, 16, 12}, {84, 24, 16, 12}};

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

	private static KeyMapping keyPlay, keyNext, keyPrev;

	// where the window was drawn last frame (screen coordinates), for the mouse
	private static volatile float lastX, lastY, lastScale;
	private static volatile boolean shown;

	private static volatile String notice = "";
	private static volatile long noticeUntil;

	// cover texture
	private static Identifier coverId;
	private static String coverFor = "";
	private static volatile String loading = "";
	private static volatile BufferedImage decoded;
	private static volatile String decodedFor = "";
	private static int coverCounter;

	private MediaPlayer() {}

	public static void init() {
		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("slyrics", "main"));
		keyPlay = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.slyrics.play_pause", InputConstants.UNKNOWN.getValue(), category));
		keyNext = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.slyrics.next", InputConstants.UNKNOWN.getValue(), category));
		keyPrev = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.slyrics.previous", InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (keyPlay.consumeClick()) togglePlay();
			while (keyNext.consumeClick()) next();
			while (keyPrev.consumeClick()) previous();
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) ->
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !(event.button() == 0 && click(event.x(), event.y()))));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("slyrics", "media"), new MediaPlayer());
	}

	// ------------------------------------------------------------------ actions

	static void notice(String text) {
		notice = text;
		noticeUntil = System.currentTimeMillis() + 4000;
	}

	private static boolean spotify() {
		return "Spotify".equals(Config.INSTANCE.musicSource);
	}

	public static void togglePlay() {
		if (spotify()) Spotify.control(Spotify.playing ? "pause" : "play");
		else AppleMusic.control("PlayPause");
	}

	public static void next() {
		if (spotify()) Spotify.control("next");
		else AppleMusic.control("Next");
	}

	public static void previous() {
		if (spotify()) Spotify.control("previous");
		else AppleMusic.control("Previous");
	}

	/** Returns true if the click hit one of the buttons (and was used). */
	private static boolean click(double mx, double my) {
		if (!shown) return false;
		for (int i = 0; i < BUTTONS.length; i++) {
			if (!inside(mx, my, BUTTONS[i])) continue;
			switch (i) {
				case 0 -> previous();
				case 1 -> togglePlay();
				default -> next();
			}
			return true;
		}
		return false;
	}

	private static boolean inside(double mx, double my, int[] b) {
		float s = lastScale;
		double x = lastX + b[0] * s, y = lastY + b[1] * s;
		return mx >= x && mx < x + b[2] * s && my >= y && my < y + b[3] * s;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Config c = Config.INSTANCE;
		Track t = Spotify.track;
		shown = false;
		if (!c.enabled || !c.mediaPlayer || t == null) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;

		float scale = c.mediaScale / 100f;
		float x0 = g.guiWidth() * c.mediaX / 100f, y0 = g.guiHeight() * c.mediaY / 100f;
		x0 = Math.max(0, Math.min(x0, g.guiWidth() - W * scale));
		y0 = Math.max(0, Math.min(y0, g.guiHeight() - H * scale));
		lastX = x0;
		lastY = y0;
		lastScale = scale;
		shown = true;

		int a = Math.round(c.mediaOpacity / 100f * 255);
		boolean cursor = mc.gui.screen() != null;
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());

		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale(scale, scale);

		g.fill(0, 0, W, H, (a * 170 / 255) << 24 | 0x101014);
		g.outline(0, 0, W, H, (a * 90 / 255) << 24 | 0xFFFFFF);

		// cover
		if (c.mediaCover) {
			updateCover(mc);
			if (coverId != null && coverFor.equals(Spotify.coverUrl)) g.blit(RenderPipelines.GUI_TEXTURED, coverId, 4, 4, 0f, 0f, COVER, COVER, 64, 64, 64, 64);
			else g.fill(4, 4, 4 + COVER, 4 + COVER, (a << 24) | 0x2A2A32);
		}
		int tx = c.mediaCover ? 44 : 6;

		// title / artist (or a short notice when a control failed)
		boolean note = System.currentTimeMillis() < noticeUntil;
		int textW = W - tx - 5;
		g.text(font, fit(font, t.title(), textW), tx, 5, (a << 24) | 0xFFFFFF, false);
		g.text(font, fit(font, note ? notice : t.artist(), textW), tx, 14, (a << 24) | (note ? 0xFF8A80 : 0xB0B0B8), false);

		// buttons
		for (int i = 0; i < BUTTONS.length; i++) {
			int[] b = BUTTONS[i];
			boolean hot = cursor && mx >= x0 + b[0] * scale && mx < x0 + (b[0] + b[2]) * scale && my >= y0 + b[1] * scale && my < y0 + (b[1] + b[3]) * scale;
			g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hot ? (a << 24) | 0x555566 : (a * 120 / 255) << 24 | 0x3A3A46);
			int cx = b[0] + b[2] / 2, cy = b[1] + b[3] / 2;
			int col = (a << 24) | 0xFFFFFF;
			switch (i) {
				case 0 -> { g.fill(cx - 4, cy - 3, cx - 3, cy + 4, col); triangle(g, cx - 2, cy, false, col); }
				case 1 -> {
					if (Spotify.playing) { g.fill(cx - 3, cy - 3, cx - 1, cy + 4, col); g.fill(cx + 1, cy - 3, cx + 3, cy + 4, col); }
					else triangle(g, cx - 2, cy, true, col);
				}
				default -> { triangle(g, cx - 3, cy, true, col); g.fill(cx + 2, cy - 3, cx + 3, cy + 4, col); }
			}
		}

		// time + progress
		long dur = Math.max(0, t.durationMs()), pos = Math.max(0, Math.min(dur > 0 ? dur : Long.MAX_VALUE, Spotify.positionMs()));
		String time = mmss(pos) + (dur > 0 ? " / " + mmss(dur) : "");
		g.text(font, time, W - 5 - font.width(time), 27, (a << 24) | 0xB0B0B8, false);
		g.fill(6, H - 7, W - 6, H - 5, (a * 110 / 255) << 24 | 0xFFFFFF);
		if (dur > 0) g.fill(6, H - 7, 6 + Math.round((W - 12) * (pos / (float) dur)), H - 5, (a << 24) | 0x1ED760);

		g.pose().popMatrix();
	}

	/** A small triangle (5 columns wide, 7 tall) whose tip points right or left, starting at x. */
	private static void triangle(GuiGraphicsExtractor g, int x, int cy, boolean right, int col) {
		for (int i = 0; i < 4; i++) {
			int h = right ? 7 - 2 * i : 1 + 2 * i; // height of this column
			g.fill(x + i, cy - h / 2, x + i + 1, cy - h / 2 + h, col);
		}
	}

	private static String fit(Font font, String s, int width) {
		if (font.width(s) <= width) return s;
		while (s.length() > 1 && font.width(s + "..") > width) s = s.substring(0, s.length() - 1);
		return s + "..";
	}

	private static String mmss(long ms) {
		long s = ms / 1000;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	// ------------------------------------------------------------------ cover art

	/** Downloads the cover in the background, then turns it into a 64x64 texture on the render thread. */
	private static void updateCover(Minecraft mc) {
		String url = Spotify.coverUrl;
		BufferedImage img = decoded;
		if (img != null) {
			decoded = null;
			String forUrl = decodedFor;
			if (forUrl.equals(url)) {
				NativeImage ni = new NativeImage(64, 64, false);
				for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) ni.setPixel(x, y, img.getRGB(x, y) | 0xFF000000);
				if (coverId != null) mc.getTextureManager().release(coverId);
				coverId = Identifier.fromNamespaceAndPath("slyrics", "cover/" + (coverCounter++));
				int n = coverCounter;
				mc.getTextureManager().register(coverId, new DynamicTexture(() -> "slyrics cover " + n, ni));
				coverFor = forUrl;
			}
		}
		if (url == null || url.isEmpty() || url.equals(coverFor) || url.equals(loading)) return;
		loading = url;
		Spotify.executor().execute(() -> {
			try {
				BufferedImage src;
				try (InputStream in = url.startsWith("file:")
					? java.nio.file.Files.newInputStream(java.nio.file.Path.of(URI.create(url)))
					: HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofInputStream()).body()) {
					src = ImageIO.read(in);
				}
				if (src == null) return;
				BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
				var g2 = out.createGraphics();
				g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.drawImage(src.getScaledInstance(64, 64, Image.SCALE_SMOOTH), 0, 0, null);
				g2.dispose();
				decodedFor = url;
				decoded = out;
			} catch (Exception e) {
				SLyricsClient.LOGGER.debug("Could not load cover {}", url, e);
			} finally {
				loading = "";
			}
		});
	}
}
