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

import java.util.Comparator;
import java.util.List;

/** Drives the scene (which line is sung now) and draws its words in the world. */
public final class LyricsWorld {
	private static final LyricScene SCENE = new LyricScene();
	private static final int OUTLINE_COLOR = 0x070A14;
	private static final float KARAOKE_MS = 250f;
	private static final long HELD_NOTE_MS = 350L;
	private static final int MIN_ALPHA = 6;

	private static final float[][] GLOW_OFFSETS = new float[16][];
	private static final float[][] RING = new float[8][];

	static {
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			GLOW_OFFSETS[i * 2] = new float[]{(float) (Math.cos(a) * 0.6), (float) (Math.sin(a) * 0.6)};
			GLOW_OFFSETS[i * 2 + 1] = new float[]{(float) (Math.cos(a) * 1.3), (float) (Math.sin(a) * 1.3)};
			RING[i] = new float[]{(float) Math.cos(a), (float) Math.sin(a)};
		}
	}

	private static String lastTrackId = "";
	private static int lastIdx = -1;
	private static boolean wasPlaying = false;

	private interface Sink {
		void draw(Component text, float x, float y, int color, Font.DisplayMode mode);
	}

	private LyricsWorld() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LyricsWorld::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(LyricsWorld::render);
	}

	private static void tick(Minecraft mc) {
		Config c = Config.INSTANCE;
		long now = System.currentTimeMillis();
		Track t = Spotify.track;
		List<LyricLine> lines = Spotify.lyrics;
		if (mc.player == null || t == null || !c.enabled || !c.hologram) {
			if (mc.player == null) SCENE.clear();
			lastIdx = -1;
			return;
		}
		if (!t.id().equals(lastTrackId)) {
			lastTrackId = t.id();
			lastIdx = -1;
			SCENE.fadeAll(now);
		}
		if (!Spotify.playing) {
			if (wasPlaying) SCENE.fadeAll(now);
			wasPlaying = false;
			return;
		}
		wasPlaying = true;
		if (lines.isEmpty()) return;
		long pos = Spotify.positionMs() + c.offsetMs;
		int idx = -1, lo = 0, hi = lines.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= pos) {
				idx = mid;
				lo = mid + 1;
			} else hi = mid - 1;
		}
		if (idx == lastIdx) return;
		lastIdx = idx;
		if (idx < 0) return;
		LyricLine cur = lines.get(idx);
		if (cur.text().isEmpty()) {
			SCENE.fadeAll(now);
			return;
		}
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : cur.timeMs() + 5000;
		SCENE.setLine(mc, cur.text(), cur.timeMs(), end, pos, now);
	}

	private static void render(LevelRenderContext ctx) {
		Config c = Config.INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !c.enabled || !c.hologram) return;
		long now = System.currentTimeMillis();
		SCENE.frame(mc, now);
		if (SCENE.pieces.isEmpty()) return;

		PoseStack ps = ctx.poseStack();
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		var collector = ctx.submitNodeCollector();
		Sink sink = (text, x, y, color, mode) ->
			collector.submitText(ps, x, y, text.getVisualOrderText(), false, mode, LightCoordsUtil.FULL_BRIGHT, color, 0, 0);

		List<LyricScene.Piece> ordered = SCENE.pieces.stream()
			.sorted(Comparator.comparingDouble((LyricScene.Piece p) -> SCENE.worldPos(p, c.follow).distanceToSqr(cam)).reversed())
			.toList();
		for (LyricScene.Piece p : ordered) drawPiece(mc, ps, cam, p, now, sink);
	}

	private static float easeOutCubic(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private static float easeOutBack(float t, float s) {
		float u = t - 1f;
		return 1f + (s + 1f) * u * u * u + s * u * u;
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static void drawPiece(Minecraft mc, PoseStack ps, Vec3 cam, LyricScene.Piece piece, long now, Sink sink) {
		Config c = Config.INSTANCE;
		long start = piece.sungAt[0];
		if (now < start) return;
		float appear = clamp01((now - start) / (float) LyricScene.APPEAR_MS);
		float fade = now < piece.fadeAt ? 0f : clamp01((now - piece.fadeAt) / (float) LyricScene.FADE_MS);
		float opacity = c.opacity / 100f;
		float alpha = easeOutCubic(appear) * (1f - fade) * opacity;
		if (alpha < 0.02f) return;

		float blur = c.blur / 100f * Math.max(1f - appear, fade);
		float bounce = c.bounce / 100f;
		float pop = (1f - 0.6f * bounce * (1f - easeOutBack(appear, 2f))) * (1f - 0.15f * fade);
		double rise = -0.4 * bounce * (1f - easeOutCubic(appear));
		double drift = Math.sin(now / 700.0 + piece.driftSeed) * 0.05 * bounce;

		Vec3 pos = SCENE.worldPos(piece, c.follow);
		ps.pushPose();
		ps.translate((float) (pos.x - cam.x), (float) (pos.y - cam.y + rise + drift), (float) (pos.z - cam.z));
		ps.rotateDegrees(Axis.YP, -piece.yaw);
		ps.rotateDegrees(Axis.XP, piece.pitch);
		ps.rotateDegrees(Axis.ZP, piece.roll);
		float s = 0.05f * (c.size / 100f) * piece.fit * pop;
		ps.scale(-s, -s, s);

		boolean karaoke = piece.words.length > 1;
		int current = -1;
		if (karaoke) for (int i = 0; i < piece.words.length; i++) if (now >= piece.sungAt[i]) current = i;
		Font font = mc.font;
		Component[] texts = new Component[piece.words.length];
		for (int i = 0; i < texts.length; i++) texts[i] = LyricsHud.styled(piece.words[i], c);
		float space = font.width(LyricsHud.styled(" ", c));
		float total = -space;
		for (Component t : texts) total += font.width(t) + space;
		float x = -total / 2f;
		float y = -font.lineHeight / 2f;
		int color = c.rgb();

		for (int i = 0; i < texts.length; i++) {
			float w = font.width(texts[i]);
			float a = alpha;
			float pulse = 1f;
			boolean lit = now >= piece.sungAt[i] && (now < piece.heldUntil[i] || i == current);
			if (karaoke && !lit) {
				a *= now < piece.sungAt[i] ? 0.45f : 0.7f;
			} else if (karaoke) {
				float t = clamp01((now - piece.sungAt[i]) / KARAOKE_MS);
				if (t < 1f) pulse += 0.12f * bounce * (float) Math.sin(t * Math.PI);
			}
			float hold = held(piece, i, now);
			pulse *= 1f + 0.1f * bounce * hold;
			ps.pushPose();
			ps.translate(x + w / 2f, 0f, 0f);
			ps.scale(pulse, pulse, 1f);
			drawWord(texts[i], -w / 2f, y, a, blur, color, c.flicker ? glitch(now, piece.driftSeed + i * 1.7f) : 0f, 1f + 0.8f * hold, sink);
			ps.popPose();
			x += w + space;
		}
		ps.popPose();
	}

	/** 0 to 1: how far into a long held note word i is, easing back to 0 over 300 ms once it ends. */
	private static float held(LyricScene.Piece piece, int i, long now) {
		long start = piece.sungAt[i], end = piece.heldUntil[i];
		if (end - start < HELD_NOTE_MS || now < start) return 0f;
		if (now < end) {
			float t = (now - start) / (float) (end - start);
			return t * t * (3f - 2f * t);
		}
		return clamp01(1f - (now - end) / 300f);
	}

	private static void drawWord(Component text, float x0, float y, float a0, float blur, int color, float glitch, float glowBoost, Sink sink) {
		Config c = Config.INSTANCE;
		float x = x0, a = a0;
		float glow = c.glow / 100f, outline = c.outline / 100f;
		int glowColor = parseColor(c.glowColor, 0xDDE9FF);
		Font.DisplayMode under = c.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
		Font.DisplayMode top = c.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET;
		if (glitch > 0f) {
			x += (glitch - 0.55f) * 4f;
			a *= 0.55f + 0.3f * glitch;
		}
		if (glow > 0.02f) {
			int ga = argb(a * (0.1f + 0.05f * glow) * glowBoost, glowColor);
			float spread = (0.5f + 0.5f * glow) * (0.75f + 0.25f * glowBoost);
			if (ga != 0) for (float[] o : GLOW_OFFSETS) sink.draw(text, x + o[0] * spread, y + o[1] * spread, ga, under);
		}
		if (blur > 0.01f) {
			for (int k = 1; k <= 2; k++) {
				int ba = argb(a * 0.3f / k, color);
				if (ba == 0) continue;
				float dx = k * 1.6f * blur;
				sink.draw(text, x - dx, y, ba, under);
				sink.draw(text, x + dx, y, ba, under);
			}
		}
		if (glitch > 0f) {
			float split = 1f + 2f * glitch;
			int cyan = argb(a * 0.6f, 0x5CE1FF), pink = argb(a * 0.6f, 0xFF51B8);
			if (cyan != 0) sink.draw(text, x - split, y, cyan, under);
			if (pink != 0) sink.draw(text, x + split, y, pink, under);
		}
		if (outline > 0.02f) {
			int inner = argb(a * 0.34f * outline, OUTLINE_COLOR), outer = argb(a * 0.16f * outline, OUTLINE_COLOR);
			for (float[] o : RING) {
				if (inner != 0) sink.draw(text, x + o[0] * 0.55f, y + o[1] * 0.55f, inner, under);
				if (outer != 0) sink.draw(text, x + o[0] * 1.1f, y + o[1] * 1.1f, outer, under);
			}
		}
		int main = argb(a * (1f - 0.55f * blur), color);
		if (main != 0) sink.draw(text, x, y, main, top);
	}

	/** 0, or a strength from 0.15 to 1 in roughly one 70 ms slot in eight. */
	private static float glitch(long now, float seed) {
		long h = (now / 70L) * 0x9E3779B97F4A7C15L ^ ((long) Float.floatToIntBits(seed) * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		float u = (h & 0xFFFF) / 65536f;
		return u < 0.12f ? 0.15f + u / 0.12f * 0.85f : 0f;
	}

	private static int argb(float alpha, int rgb) {
		int a = Math.round(clamp01(alpha) * 255f);
		return a < MIN_ALPHA ? 0 : (a << 24) | (rgb & 0xFFFFFF);
	}

	private static int parseColor(String hex, int def) {
		try {
			return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
