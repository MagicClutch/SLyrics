package dev.slyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Decides which line is being sung and draws the scene's words in the world. */
public final class LyricsWorld {
	private static final LyricScene SCENE = new LyricScene();
	private static final int OUTLINE_RGB = 0x080B16;

	/** Unit-circle offsets used for the halo and the outline. */
	private static final float[][] RING8 = new float[8][];

	static {
		for (int i = 0; i < 8; i++) RING8[i] = new float[]{(float) Math.cos(i * Math.PI / 4), (float) Math.sin(i * Math.PI / 4)};
	}

	private static String lastTrack = "";
	private static int lastLine = -1;
	private static boolean wasPlaying;

	private interface Sink {
		void draw(Component text, float x, float y, float z, int argb, Font.DisplayMode mode);

		default void draw(Component text, float x, float y, int argb, Font.DisplayMode mode) {
			draw(text, x, y, 0f, argb, mode);
		}
	}

	private LyricsWorld() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LyricsWorld::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(LyricsWorld::render);
	}

	// ------------------------------------------------------------------ which line is sung

	private static int lineAt(List<LyricLine> lines, long pos) {
		int lo = 0, hi = lines.size() - 1, found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= pos) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return found;
	}

	private static void tick(Minecraft mc) {
		Config c = Config.INSTANCE;
		long now = System.currentTimeMillis();
		Track track = Spotify.track;
		if (mc.player == null) {
			SCENE.clear();
			lastLine = -1;
			return;
		}
		if (track == null || !c.enabled || !c.hologram) {
			lastLine = -1;
			return;
		}
		if (!track.id().equals(lastTrack)) {
			lastTrack = track.id();
			lastLine = -1;
			SCENE.fadeAll(now);
		}
		if (!Spotify.playing) {
			if (wasPlaying) SCENE.fadeAll(now);
			wasPlaying = false;
			return;
		}
		wasPlaying = true;

		List<LyricLine> lines = Spotify.lyrics;
		if (lines.isEmpty()) return;
		long pos = Spotify.positionMs() + c.offsetMs;
		int idx = lineAt(lines, pos);
		if (idx == lastLine) return;
		lastLine = idx;
		if (idx < 0) return;

		LyricLine line = lines.get(idx);
		if (line.text().isEmpty()) {
			SCENE.fadeAll(now);
			return;
		}
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : line.timeMs() + 5000;
		SCENE.setLine(mc, line.text(), line.timeMs(), end, pos, now);
	}

	// ------------------------------------------------------------------ drawing

	private static void render(LevelRenderContext ctx) {
		Config c = Config.INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !c.enabled || !c.hologram) return;
		long now = System.currentTimeMillis();
		SCENE.frame(mc, now);
		if (SCENE.items.isEmpty()) return;

		PoseStack ps = ctx.poseStack();
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		var collector = ctx.submitNodeCollector();
		Sink sink = (text, x, y, z, argb, mode) -> {
			if (z != 0f) ps.pushPose();
			if (z != 0f) ps.translate(0f, 0f, z);
			collector.submitText(ps, x, y, text.getVisualOrderText(), false, mode, LightCoordsUtil.FULL_BRIGHT, argb, 0, 0);
			if (z != 0f) ps.popPose();
		};

		// far to near, so near words end up on top
		List<LyricScene.Item> order = new java.util.ArrayList<>(SCENE.items);
		order.sort((a, b) -> Double.compare(SCENE.worldPos(b, c.follow).distanceToSqr(cam), SCENE.worldPos(a, c.follow).distanceToSqr(cam)));
		for (LyricScene.Item it : order) drawItem(mc, ps, cam, it, now, sink);
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	private static float smooth(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Scale curve for the pop-in: grows past 1 and settles back. */
	private static float pop(float t) {
		t = clamp01(t);
		return 0.4f + 0.6f * smooth(t) + 0.18f * (float) Math.sin(Math.PI * t) * (1f - t);
	}

	private static void drawItem(Minecraft mc, PoseStack ps, Vec3 cam, LyricScene.Item it, long now, Sink sink) {
		Config c = Config.INSTANCE;
		if (now < it.sungAt[0]) return;

		float born = clamp01((now - it.sungAt[0]) / (float) LyricScene.POP_MS);
		float dying = now < it.fadeAt ? 0f : clamp01((now - it.fadeAt) / (float) LyricScene.FADE_MS);
		float alpha = smooth(born) * (1f - dying) * (c.opacity / 100f);
		if (it.sky) { // flashes up: three quick pulses, then a faint shimmer
			double sn = Math.sin(born * Math.PI * 2.5);
			float flash = 0.25f + 0.75f * (float) (sn * sn);
			float shimmer = 0.88f + 0.12f * (float) Math.sin(now / 900.0 + it.seed);
			alpha *= born < 1f ? flash : shimmer;
		}
		if (alpha < 0.02f) return;

		float bounce = c.bounce / 100f;
		float scaleAnim = it.sky ? 1f + 0.06f * (1f - born) : 1f + (pop(born) - 1f) * bounce - 0.15f * dying;
		double lift = it.sky ? 0 : -0.35 * bounce * (1f - smooth(born)) + Math.sin(now / 750.0 + it.seed) * 0.045 * bounce;
		float smear = c.blur / 100f * Math.max(1f - born, dying);

		Vec3 at = SCENE.worldPos(it, c.follow);
		if (it.sky) { // slow sideways drift, like a cloud
			Vec3 d = at.subtract(cam);
			double len = Math.max(1.0, Math.hypot(d.x, d.z));
			double drift = (now - it.sungAt[0]) / 1000.0 * len * 0.006;
			at = at.add(-d.z / len * drift, 0, d.x / len * drift);
		}
		ps.pushPose();
		ps.translate((float) (at.x - cam.x), (float) (at.y - cam.y + lift), (float) (at.z - cam.z));
		ps.rotateDegrees(Axis.YP, -it.yaw);
		ps.rotateDegrees(Axis.XP, it.pitch);
		ps.rotateDegrees(Axis.ZP, it.roll);
		float s = 0.05f * (c.size / 100f) * it.fit * scaleAnim;
		ps.scale(-s, -s, s);

		// Which local z direction points away from the camera: the 3D body is built behind the front face.
		double yawRad = Math.toRadians(-it.yaw);
		Vec3 toCam = cam.subtract(at);
		float away = (toCam.x * Math.sin(yawRad) + toCam.z * Math.cos(yawRad)) >= 0 ? -1f : 1f;

		Font font = mc.font;
		int n = it.words.length;
		Component[] texts = new Component[n];
		float[] widths = new float[n];
		float gap = font.width(LyricsHud.styled(" ", c));
		float total = gap * (n - 1);
		for (int i = 0; i < n; i++) {
			texts[i] = LyricsHud.styled(it.words[i], c);
			widths[i] = font.width(texts[i]);
			total += widths[i];
		}

		int current = -1;
		for (int i = 0; i < n; i++) if (now >= it.sungAt[i]) current = i;
		float x = -total / 2f, y = -font.lineHeight / 2f;
		for (int i = 0; i < n; i++) {
			float a = alpha, grow = 1f;
			if (n > 1) {
				boolean active = now >= it.sungAt[i] && (now < it.heldUntil[i] || i == current);
				if (!active) a *= now < it.sungAt[i] ? 0.4f : 0.7f;
				else grow += 0.1f * bounce * (float) Math.sin(Math.PI * clamp01((now - it.sungAt[i]) / 260f));
			}
			float sustain = sustain(it, i, now);
			grow *= 1f + 0.09f * bounce * sustain;

			ps.pushPose();
			ps.translate(x + widths[i] / 2f, 0f, 0f);
			ps.scale(grow, grow, 1f);
			float glitch = c.flicker ? glitch(now, it.seed + i * 1.9f) : 0f;
			drawWord(texts[i], -widths[i] / 2f, y, a, smear, glitch, (1f + 0.8f * sustain) * (it.sky && born < 1f ? 2f : 1f), away, sink);
			ps.popPose();
			x += widths[i] + gap;
		}
		ps.popPose();
	}

	/** 0..1 while a long note is held (eases in), then eases out for 300 ms. */
	private static float sustain(LyricScene.Item it, int i, long now) {
		long from = it.sungAt[i], to = it.heldUntil[i];
		if (to - from < 350 || now < from) return 0f;
		if (now < to) return smooth((now - from) / (float) (to - from));
		return clamp01(1f - (now - to) / 300f);
	}

	private static void drawWord(Component text, float x, float y, float a, float smear, float glitch, float boost, float away, Sink sink) {
		Config c = Config.INSTANCE;
		float glow = c.glow / 100f, outline = c.outline / 100f;
		var under = c.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		var top = c.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET;
		if (glitch > 0f) {
			x += (glitch - 0.5f) * 4f;
			a *= 0.55f + 0.3f * glitch;
		}

		if (glow > 0.02f) {
			int col = argb(a * (0.1f + 0.06f * glow) * boost, hex(c.glowColor, 0xDDE9FF));
			float r = (0.6f + 0.9f * glow) * (0.8f + 0.2f * boost);
			if (col != 0) for (float[] o : RING8) {
				sink.draw(text, x + o[0] * r, y + o[1] * r, col, under);
				sink.draw(text, x + o[0] * r * 2f, y + o[1] * r * 2f, col, under);
			}
		}
		if (smear > 0.01f) {
			int col = argb(a * 0.28f, c.rgb());
			if (col != 0) for (int k = 1; k <= 2; k++) {
				sink.draw(text, x - k * 1.6f * smear, y, col, under);
				sink.draw(text, x + k * 1.6f * smear, y, col, under);
			}
		}
		if (glitch > 0f) {
			float split = 1f + 2f * glitch;
			int cyan = argb(a * 0.6f, 0x5CE1FF), pink = argb(a * 0.6f, 0xFF51B8);
			if (cyan != 0) sink.draw(text, x - split, y, cyan, under);
			if (pink != 0) sink.draw(text, x + split, y, pink, under);
		}
		if (outline > 0.02f) {
			int col = argb(a * 0.3f * outline, OUTLINE_RGB);
			if (col != 0) for (float[] o : RING8) sink.draw(text, x + o[0] * 0.8f, y + o[1] * 0.8f, col, under);
		}
		if (c.depth3d && c.depthSize > 0.1f) {
			// stacked copies from the back to just behind the front face, darker toward the back
			int layers = Math.max(2, Math.min(12, Math.round(c.depthSize * 1.6f)));
			int base = c.rgb();
			for (int k = layers; k >= 1; k--) {
				float f = (float) k / layers;                      // 1 = back, small = near the front
				float shade = 0.72f - 0.4f * f;                    // back is darker
				int rgb = ((int) (((base >> 16) & 255) * shade) << 16) | ((int) (((base >> 8) & 255) * shade) << 8) | (int) ((base & 255) * shade);
				int col = argb(a * 0.97f, rgb);
				if (col != 0) sink.draw(text, x, y, away * c.depthSize * f, col, under);
			}
		}
		int main = argb(a * (1f - 0.55f * smear), c.rgb());
		if (main != 0) sink.draw(text, x, y, main, top);
	}

	/** Occasional short glitch: 0 most of the time, 0.15..1 in about one 70 ms slot in eight. */
	private static float glitch(long now, float seed) {
		double h = Math.sin((now / 70L) * 12.9898 + seed * 78.233) * 43758.5453;
		float u = (float) (h - Math.floor(h));
		return u < 0.12f ? 0.15f + u / 0.12f * 0.85f : 0f;
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.round(clamp01(alpha) * 255f);
		return a < 6 ? 0 : (a << 24) | (rgb & 0xFFFFFF); // tiny alpha would be drawn opaque by the font renderer
	}

	private static int hex(String s, int def) {
		try {
			return Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
