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
	/** Experimental: the lyrics in the OneClient lobby, placed in the lobby camera's view. */
	private static final LyricScene LOBBY = new LyricScene();
	private static String lobbyTrack = "";
	private static int lobbyLine = -1;
	private static boolean lobbyWasPlaying;

	/** True when lyrics should show in the OneClient lobby right now (the song is then polled without a player too). */
	public static boolean lobbyWanted() {
		Config c = Config.INSTANCE;
		return c.enabled && c.lobbyLyrics && dev.slyrics.compat.polyplus.PolyPlusLobby.inLobby();
	}

	/** Where the word being sung is, or the next one just before it appears (for the AFK camera), or null. */
	public static net.minecraft.world.phys.Vec3 currentWordPos(long now) {
		return SCENE.currentWordPos(now, Config.INSTANCE.follow);
	}

	private static final int OUTLINE_RGB = 0x080B16;
	/**
	 * Depth between two text layers, in text pixels: enough for the depth buffer to tell them apart, too little to see.
	 * Copies on one plane would fight over the same pixels and flicker (worst far away, where the depth buffer is coarse).
	 */
	private static final float LAYER_GAP = 0.25f;
	/** The glow copies are spread over this much depth behind the outline, each one on its own. */
	private static final float GLOW_DEPTH = 1.2f;

	/** Glow offsets {dx, dy, depth, weight}: three rings around the glyph, the weight falls off outward. */
	private static final float[][] GLOW;
	/** Unit-circle offsets used for the outline and the sweep glow. */
	private static final float[][] RING8 = new float[8][];

	static {
		// three rings, the outer (faintest) one first: copies are drawn back to front, each a little deeper than the next
		float[] radius = {2.0f, 1.2f, 0.6f}, weight = {0.12f, 0.24f, 0.4f};
		int[] count = {10, 8, 6};
		java.util.List<float[]> g = new java.util.ArrayList<>();
		for (int k = 0; k < radius.length; k++) {
			for (int i = 0; i < count[k]; i++) {
				double ang = (i + 0.5 * k) * 2 * Math.PI / count[k];
				g.add(new float[]{(float) Math.cos(ang) * radius[k], (float) Math.sin(ang) * radius[k], 0f, weight[k]});
			}
		}
		for (int i = 0; i < g.size(); i++) g.get(i)[2] = 1f - (float) i / g.size(); // depth: 1 = farthest back
		GLOW = g.toArray(new float[0][]);
		for (int i = 0; i < 8; i++) RING8[i] = new float[]{(float) Math.cos(i * Math.PI / 4), (float) Math.sin(i * Math.PI / 4)};
	}

	/** AFK camera cut: the words of the old shot disappear now instead of jumping with the new camera. */
	public static void fadeVisibleNow() {
		SCENE.fadeVisible(System.currentTimeMillis());
	}

	private static boolean stickNow;
	/** True while a word is drawn that the camera sees from behind: its text is drawn from the back (mirrored, like a 3D object). */
	private static boolean backNow;
	/** True while the lobby scene is drawn (its words never turn around). */
	private static boolean lobbyNow;
	/** The pose stack of the frame being drawn (the sink draws with it), and where the frame's geometry goes. */
	private static PoseStack poseNow;
	private static net.minecraft.client.renderer.SubmitNodeCollector collectorNow;
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
		if (dev.slyrics.compat.polyplus.PolyPlusLobby.inLobby()) {
			SCENE.clear();
			lastLine = -1;
			lobbyTick(c, track, now);
			return;
		}
		if (!LOBBY.items.isEmpty()) LOBBY.clear();
		lobbyLine = -1;
		if (mc.player == null) {
			SCENE.clear();
			lastLine = -1;
			return;
		}
		if (track == null || !c.enabled || !c.anyWorld()) {
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
		long pos = Spotify.positionMs() - c.offsetMs;
		int idx = lineAt(lines, pos);
		if (idx == lastLine) return;
		lastLine = idx;
		if (idx < 0) return;
		AfkCamera.onLine(idx, lines);

		LyricLine line = lines.get(idx);
		if (line.text().isEmpty()) {
			SCENE.fadeAll(now);
			return;
		}
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : line.timeMs() + 5000;
		dev.slyrics.pretty.PrettyTimes.ensure(lines, c);
		var times = dev.slyrics.pretty.PrettyTimes.line(idx); // exact word times, or Pretty Lyrics' estimate (the last word may be held)
		boolean use = times != null && (c.prettyLyrics || times.real);
		SCENE.setLine(mc, line.text(), line.timeMs(), end, pos, now, use ? times.start : null, use ? times.end : null);
	}

	/** Experimental: the same line tracking as {@link #tick}, for the OneClient lobby (no player, no AFK camera). */
	private static void lobbyTick(Config c, Track track, long now) {
		if (track == null || !c.enabled || !c.lobbyLyrics) {
			if (!LOBBY.items.isEmpty()) LOBBY.fadeAll(now);
			lobbyLine = -1;
			return;
		}
		if (!track.id().equals(lobbyTrack)) {
			lobbyTrack = track.id();
			lobbyLine = -1;
			LOBBY.fadeAll(now);
		}
		if (!Spotify.playing) {
			if (lobbyWasPlaying) LOBBY.fadeAll(now);
			lobbyWasPlaying = false;
			return;
		}
		lobbyWasPlaying = true;
		List<LyricLine> lines = Spotify.lyrics;
		if (lines.isEmpty()) return;
		long pos = Spotify.positionMs() - c.offsetMs;
		int idx = lineAt(lines, pos);
		if (idx == lobbyLine) return;
		lobbyLine = idx;
		if (idx < 0) return;
		LyricLine line = lines.get(idx);
		if (line.text().isEmpty()) {
			LOBBY.fadeAll(now);
			return;
		}
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : line.timeMs() + 5000;
		dev.slyrics.pretty.PrettyTimes.ensure(lines, c);
		var times = dev.slyrics.pretty.PrettyTimes.line(idx);
		boolean use = times != null && (c.prettyLyrics || times.real);
		LOBBY.setLobbyLine(line.text(), line.timeMs(), end, pos, now, use ? times.start : null, use ? times.end : null);
	}

	// ------------------------------------------------------------------ drawing

	private static void render(LevelRenderContext ctx) {
		Config c = Config.INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		if (dev.slyrics.compat.polyplus.PolyPlusLobby.isRendering()) {
			if (c.enabled && c.lobbyLyrics) renderLobby(ctx, mc);
			return;
		}
		if (mc.level == null || mc.player == null || !c.enabled || !c.anyWorld()) return;
		long now = System.currentTimeMillis();
		SCENE.frame(mc, now);
		if (SCENE.items.isEmpty()) return;
		drawScene(ctx, mc, SCENE, c.follow, now);
	}

	/** Experimental: the lobby line, placed from the lobby camera (where you look at the menu, not where your character looks). */
	private static void renderLobby(LevelRenderContext ctx, Minecraft mc) {
		var cam = ctx.levelState().cameraRenderState;
		if (cam == null || cam.pos == null) return;
		long now = System.currentTimeMillis();
		double vFov = 70, hFov = 70 * 16 / 9.0;
		if (cam.projectionMatrix != null && cam.projectionMatrix.m11() > 0.01f && cam.projectionMatrix.m00() > 0.01f) {
			vFov = Math.toDegrees(2 * Math.atan(1.0 / cam.projectionMatrix.m11()));
			hFov = Math.toDegrees(2 * Math.atan(1.0 / cam.projectionMatrix.m00()));
		}
		LOBBY.frameLobby(mc, cam.pos, cam.yRot, cam.xRot, hFov, vFov, now);
		if (LOBBY.items.isEmpty()) return;
		drawScene(ctx, mc, LOBBY, false, now);
	}

	private static void drawScene(LevelRenderContext ctx, Minecraft mc, LyricScene scene, boolean followMe, long now) {
		PoseStack ps = ctx.poseStack();
		poseNow = ps;
		lobbyNow = scene == LOBBY;
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		var collector = ctx.submitNodeCollector();
		collectorNow = collector;
		Sink sink = (text, x, y, z, argb, mode) -> {
			if (z != 0f) ps.pushPose();
			if (z != 0f) ps.translate(0f, 0f, z);
			if (backNow) BackFace.submitText(collector, ps, text.getVisualOrderText(), x, y, argb, mode, LightCoordsUtil.FULL_BRIGHT);
			else collector.submitText(ps, x, y, text.getVisualOrderText(), false, mode, LightCoordsUtil.FULL_BRIGHT, argb, 0, 0);
			if (z != 0f) ps.popPose();
		};

		// far to near, so near words end up on top
		List<LyricScene.Item> order = new java.util.ArrayList<>(scene.items);
		order.sort((a, b) -> Double.compare(scene.worldPos(b, followMe).distanceToSqr(cam), scene.worldPos(a, followMe).distanceToSqr(cam)));
		for (LyricScene.Item it : order) drawItem(mc, ps, cam, scene.worldPos(it, followMe), it, now, sink);
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

	private static void drawItem(Minecraft mc, PoseStack ps, Vec3 cam, Vec3 at, LyricScene.Item it, long now, Sink sink) {
		Config c = Config.INSTANCE;
		if (now < it.sungAt[0]) return;

		float born = clamp01((now - it.sungAt[0]) / (float) LyricScene.POP_MS);
		float dying = now < it.fadeAt ? 0f : clamp01((now - it.fadeAt) / (float) LyricScene.FADE_MS);
		float alpha = smooth(born) * (1f - dying) * (c.opacity / 100f);
		if (it.sky) { // flashes up: three quick pulses, then a faint shimmer
			double sn = Math.sin(born * Math.PI * 2.5);
			float flash = 0.7f + 0.3f * (float) (sn * sn); // pulses, but the word is always clearly readable
			float shimmer = 0.94f + 0.06f * (float) Math.sin(now / 900.0 + it.seed);
			alpha *= born < 1f ? flash : shimmer;
		}
		if (alpha < 0.02f) return;

		float bounce = c.bounce / 100f;
		float scaleAnim = it.sky || it.stick ? 1f + 0.06f * (1f - born) : 1f + (pop(born) - 1f) * bounce - 0.15f * dying;
		double lift = it.sky || it.stick ? 0 : -0.35 * bounce * (1f - smooth(born)) + Math.sin(now / 750.0 + it.seed) * 0.045 * bounce;
		float smear = c.blur / 100f * Math.max(1f - born, dying);

		stickNow = it.stick;
		if (it.sky) { // slow sideways drift, like a cloud
			Vec3 d = at.subtract(cam);
			double len = Math.max(1.0, Math.hypot(d.x, d.z));
			double drift = (now - it.sungAt[0]) / 1000.0 * len * 0.006;
			at = at.add(-d.z / len * drift, 0, d.x / len * drift);
		}
		float s = it.base * it.fit * scaleAnim;
		ps.pushPose();
		ps.translate((float) (at.x - cam.x), (float) (at.y - cam.y + lift), (float) (at.z - cam.z));
		ps.rotateDegrees(Axis.YP, -it.yaw);
		ps.rotateDegrees(Axis.XP, it.pitch);
		ps.rotateDegrees(Axis.ZP, it.roll);
		ps.scale(-s, -s, s);

		// Which local z direction points away from the camera: the 3D body is built behind the front face.
		double yawRad = Math.toRadians(-it.yaw);
		Vec3 toCam = cam.subtract(at);
		float away = (toCam.x * Math.sin(yawRad) + toCam.z * Math.cos(yawRad)) >= 0 ? -1f : 1f;
		// Seen from behind (you walked past it): in the world the text shows its back, mirrored like a real 3D object,
		// instead of vanishing and leaving its glow behind. Words stuck on blocks and the lobby words stay one-sided.
		backNow = away < 0 && !it.stick && !lobbyNow;

		Font font = mc.font;
		int n = it.words.length;
		Component[] texts = new Component[n];
		Component[] colored = new Component[n];
		float[] widths = new float[n];
		float gap = font.width(LyricsHud.styled(" ", c));
		float total = gap * (n - 1);
		for (int i = 0; i < n; i++) {
			texts[i] = LyricsHud.styled(it.words[i], c);
			colored[i] = LyricsHud.styled(it.words[i], c, true);
			widths[i] = font.width(texts[i]);
			total += widths[i];
		}

		int current = -1;
		for (int i = 0; i < n; i++) if (now >= it.sungAt[i]) current = i;
		float x = -total / 2f, y = -font.lineHeight / 2f;
		if (c.prettyLyrics && (it.pretty == null || it.pretty.length != n)) it.pretty = new dev.slyrics.pretty.PrettyWord[n];
		for (int i = 0; i < n; i++) {
			if (c.prettyLyrics) { // Pretty Lyrics: the word's own springs, a soft sweep and glow instead of the word animations
				String raw = it.words[i];
				long dur = Math.max(1, it.heldUntil[i] - it.sungAt[i]);
				if (it.pretty[i] == null) it.pretty[i] = new dev.slyrics.pretty.PrettyWord(raw.length(), dev.slyrics.pretty.PrettyHud.emphasized(raw, dur, c));
				var pw = it.pretty[i];
				float t = clamp01((now - it.sungAt[i]) / (float) dur);
				pw.update(t);
				float glitch = c.flicker ? glitch(now, it.seed + i * 1.9f) : 0f;
				ps.pushPose();
				ps.translate(x + widths[i] / 2f, 0f, 0f);
				drawPretty(font, c, texts[i], colored[i], raw, pw, t, widths[i], y, alpha, smear, glitch, away, sink);
				ps.popPose();
				x += widths[i] + gap;
				continue;
			}
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
			boolean actNow = now >= it.sungAt[i] && (now < it.heldUntil[i] || i == current);
			float[] an = anim(c.textAnim, now - it.sungAt[i], i, now, it.seed, actNow);
			ps.translate(an[2], an[3], 0f);
			ps.rotateDegrees(Axis.ZP, an[1]);
			ps.scale(an[0], an[0], 1f);
			float glitch = c.flicker ? glitch(now, it.seed + i * 1.9f) : 0f;
			float sweepProg = 0f, sweepAlpha = 0f;
			if (c.sweepMode && now >= it.sungAt[i]) {
				long len = Math.max(150L, it.heldUntil[i] - it.sungAt[i]);
				if (now < it.heldUntil[i]) {
					sweepProg = clamp01((now - it.sungAt[i]) / (float) len);
					sweepAlpha = 1f;
				} else if (c.sweepFadeMs > 0) {
					sweepProg = 1f;
					sweepAlpha = clamp01(1f - (now - it.heldUntil[i]) / (float) c.sweepFadeMs); // the color goes off when the next word starts
				}
			}
			drawWord(texts[i], colored[i], it.words[i], -widths[i] / 2f, y, a, smear, glitch, (1f + 0.8f * sustain) * (it.sky && born < 1f ? 2f : 1f), away, sweepProg, sweepAlpha, sink);
			ps.popPose();
			x += widths[i] + gap;
		}
		ps.popPose();
	}

	/** Overshooting pop curve: 0 -> 1.3 -> 0.93 -> 1 over p = 0..1. */
	private static float popCurve(float p) {
		if (p < 0.55f) return smooth(p / 0.55f) * 1.3f;
		if (p < 0.8f) return 1.3f - 0.37f * smooth((p - 0.55f) / 0.25f);
		return 0.93f + 0.07f * smooth((p - 0.8f) / 0.2f);
	}

	private static float noise(double x) {
		double h = Math.sin(x * 12.9898) * 43758.5453;
		return (float) (h - Math.floor(h));
	}

	/**
	 * Viral-caption animation of one word: {scale, rotation degrees, dx, dy} in font pixels.
	 * tw is the time since the word was sung (negative = not sung yet).
	 */
	private static float[] anim(String style, long tw, int i, long now, float seed, boolean active) {
		float spd = Config.INSTANCE.animSpeed / 100f;
		float sc = 1f, rot = 0f, dx = 0f, dy = 0f;
		float t = tw * spd;
		switch (style == null ? "None" : style) {
			case "TikTok pop" -> {
				if (tw >= 0) {
					float p = clamp01(t / 380f);
					sc = popCurve(p);
					rot = -7f * (1f - p);
				}
			}
			case "Slam" -> {
				if (tw >= 0) {
					float p = clamp01(t / 220f);
					sc = 2.8f - 1.8f * p * p;
					if (t >= 220f && t < 460f) { // impact shake
						float k = 1f - (t - 220f) / 240f;
						dx = (noise(now / 30.0 + seed) - 0.5f) * 5f * k;
						dy = (noise(now / 30.0 + seed + 9) - 0.5f) * 5f * k;
					}
				}
			}
			case "Shake" -> {
				if (tw >= 0) sc = popCurve(clamp01(t / 380f));
				if (active) {
					dx = (noise(now / 45.0 + seed + i) - 0.5f) * 2.6f;
					dy = (noise(now / 45.0 + seed + i + 5) - 0.5f) * 2.6f;
				}
			}
			case "Wobble" -> {
				if (tw >= 0) sc = popCurve(clamp01(t / 380f));
				if (active) rot = (float) Math.sin(now * spd / 110.0 + seed) * 6f;
			}
			case "Wave" -> dy = (float) Math.sin(now * spd / 200.0 - i * 0.9) * 2.5f;
			case "Spin in" -> {
				if (tw >= 0) {
					float p = clamp01(t / 450f);
					rot = 360f * (1f - smooth(p));
					sc = 0.2f + 0.8f * smooth(p) + 0.12f * (float) Math.sin(Math.PI * p);
				}
			}
			case "Slide up" -> {
				if (tw >= 0) dy = 14f * (1f - smooth(clamp01(t / 320f)));
			}
			default -> { }
		}
		return new float[]{Math.max(0.01f, sc), rot, dx, dy};
	}

	/** 0..1 while a long note is held (eases in), then eases out for 300 ms. */
	private static float sustain(LyricScene.Item it, int i, long now) {
		long from = it.sungAt[i], to = it.heldUntil[i];
		if (to - from < 350 || now < from) return 0f;
		if (now < to) return smooth((now - from) / (float) (to - from));
		return clamp01(1f - (now - to) / 300f);
	}

	private static void drawWord(Component text, Component colored, String raw, float x, float y, float a, float smear, float glitch, float boost, float away, float sweepProg, float sweepAlpha, Sink sink) {
		Config c = Config.INSTANCE;
		float glow = c.glow / 100f, outline = c.outline / 100f;
		// depth-tested: blocks always hide the lyrics behind them. The layers sit a little behind each other (glow at the back,
		// then outline and effects, the text in front): on one plane they would fight over the same pixels and flicker,
		// most of all far away (sky words), where the depth buffer is coarse.
		var under = stickNow ? Font.DisplayMode.POLYGON_OFFSET : Font.DisplayMode.NORMAL;
		var top = Font.DisplayMode.POLYGON_OFFSET;
		float zGlow = away * LAYER_GAP * 3f, zUnder = away * LAYER_GAP;
		if (glitch > 0f) {
			x += (glitch - 0.5f) * 4f;
			a *= 0.55f + 0.3f * glitch;
		}

		if (glow > 0.02f) { // soft halo: tiny-offset copies on three rings, fading outward, so it reads as smooth light
			int rgb = hex(c.glowColor, 0xDDE9FF);
			float r = (0.6f + 0.9f * glow) * (0.8f + 0.2f * Math.min(boost, 1.6f));
			float strength = a * Math.min(1f, 0.35f * glow + 0.1f) * Math.min(boost, 1.6f);
			for (float[] g : GLOW) {
				int col = argb(strength * g[3], rgb);
				if (col != 0) sink.draw(text, x + g[0] * r, y + g[1] * r, zGlow + away * GLOW_DEPTH * g[2], col, under);
			}
		}
		if (smear > 0.01f) {
			int col = argb(a * 0.28f, c.rgb());
			if (col != 0) for (int k = 1; k <= 2; k++) {
				sink.draw(text, x - k * 1.6f * smear, y, zUnder, col, under);
				sink.draw(text, x + k * 1.6f * smear, y, zUnder, col, under);
			}
		}
		if (glitch > 0f) {
			float split = 1f + 2f * glitch;
			int cyan = argb(a * 0.6f, 0x5CE1FF), pink = argb(a * 0.6f, 0xFF51B8);
			if (cyan != 0) sink.draw(text, x - split, y, zUnder, cyan, under);
			if (pink != 0) sink.draw(text, x + split, y, zUnder, pink, under);
		}
		if (outline > 0.02f) {
			int col = argb(a * 0.3f * outline, OUTLINE_RGB);
			if (col != 0) for (int i = 0; i < RING8.length; i++) {
				float[] o = RING8[i];
				sink.draw(text, x + o[0] * 0.8f, y + o[1] * 0.8f, zUnder + away * LAYER_GAP * (RING8.length - i) / RING8.length, col, under);
			}
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
				if (col != 0) sink.draw(text, x, y, away * (LAYER_GAP + c.depthSize * f), col, under);
			}
		}
		int main = argb(a * (1f - 0.55f * smear), c.rgb());
		if (main != 0) sink.draw(colored, x, y, main, top);

		if (sweepAlpha > 0.01f && !raw.isEmpty()) {
			Font font = Minecraft.getInstance().font;
			int rgb = hex(c.sweepColor, 0xFFE14D);
			int n = raw.length();
			float exact = c.sweepWhole ? n : sweepProg * n;
			int whole = Math.min(n, (int) Math.floor(exact));
			float frac = exact - whole;
			float z = -away * LAYER_GAP; // a little in front of the base text
			Component prefix = whole > 0 ? LyricsHud.styled(raw.substring(0, whole), c, true) : null;
			int col = argb(a * sweepAlpha, rgb);
			if (col != 0) {
				if (c.sweepGlow) {
					int gcol = argb(a * sweepAlpha * 0.16f, rgb);
					if (gcol != 0 && prefix != null) for (int i = 0; i < RING8.length; i++) { // between the base text and the sweep, each copy on its own depth
						float[] o = RING8[i];
						sink.draw(prefix, x + o[0] * 1.2f, y + o[1] * 1.2f, z * (i + 1) / (RING8.length + 1), gcol, under);
					}
				}
				if (prefix != null) sink.draw(prefix, x, y, z, col, top);
				if (whole < n && frac > 0.02f) {
					float cx = x + (prefix != null ? font.width(prefix) : 0);
					int pcol = argb(a * sweepAlpha * frac, rgb);
					if (pcol != 0) sink.draw(LyricsHud.styled(raw.substring(whole, whole + 1), c, true), cx, y, z, pcol, top);
				}
			}
		}
	}

	/**
	 * One word in Pretty Lyrics style, centered at x = 0 (top at y): it grows and lifts around the middle of its baseline,
	 * the base layers (3D body, outline, the normal glow) are drawn dim, the sung color sweeps over it with a soft edge, and it
	 * glows while sung. With letter ripple on, every letter also hops and grows while the sweep passes it (held notes more,
	 * each letter with its own glow).
	 */
	private static void drawPretty(Font font, Config c, Component text, Component colored, String raw, dev.slyrics.pretty.PrettyWord pw, float t,
								   float w, float y, float a, float smear, float glitch, float away, Sink sink) {
		var ps = poseNow;
		float motion = c.prettyMotion / 100f, unsung = c.prettyUnsung / 100f, sung = c.prettySung / 100f;
		float lh = font.lineHeight, gain = dev.slyrics.pretty.PrettyHud.LIFT_GAIN;
		int hi = hex(c.sweepColor, 0xFFFFFF);
		float edge = dev.slyrics.pretty.PrettyHud.edgeWidth(c, lh);
		float litTo = -edge + (w + edge) * clamp01(t);
		int n = raw.length();
		float[] cws = new float[n], xs = new float[n];
		float ax = 0;
		for (int k = 0; k < n; k++) {
			cws[k] = font.width(LyricsHud.styled(raw.substring(k, k + 1), c));
			xs[k] = ax;
			ax += cws[k];
		}
		boolean letters = c.prettyLetters && pw.n == n && n > 1;
		float sc = pw.emph && letters ? 1f : 1f + (pw.scale() - 1f) * motion;
		float lift = pw.emph && letters ? 0f : pw.lift() * lh * gain * motion;
		ps.pushPose();
		ps.translate(0f, y + lh, 0f);
		ps.scale(sc, sc, 1f);
		ps.translate(0f, -lh + lift, 0f);
		if (!pw.emph || !letters) prettyGlow(cws, -w / 2f, a, pw.glow(), false, away, lh);
		if (!letters) {
			drawWord(text, colored, raw, -w / 2f, 0f, a * unsung, smear, glitch, 1f, away, 0f, 0f, sink);
			prettySweep(font, c, raw, -w / 2f, w, litTo, edge, a * sung, hi, away, sink);
			ps.popPose();
			return;
		}
		float[] lt = new float[n], gt = new float[n];
		dev.slyrics.pretty.PrettyWord.letterProgress(litTo, edge, xs, cws, w, t, lt, gt);
		pw.letters(lt, gt);
		for (int k = 0; k < n; k++) {
			String sub = raw.substring(k, k + 1);
			Component ch = LyricsHud.styled(sub, c), chc = LyricsHud.styled(sub, c, true);
			float cw = cws[k], ls = 1f + (pw.letterScale(k) - 1f) * motion;
			ps.pushPose();
			ps.translate(-w / 2f + xs[k] + cw / 2f, lh, 0f);
			ps.scale(ls, ls, 1f);
			ps.translate(-cw / 2f, -lh + pw.letterLift(k) * lh * gain * motion, 0f);
			if (pw.emph) prettyGlow(new float[]{cw}, 0f, a, pw.letterGlow(k), true, away, lh);
			drawWord(ch, chc, sub, 0f, 0f, a * unsung, smear, glitch, 1f, away, 0f, 0f, sink);
			float b = edge <= 0.001f ? (xs[k] + cw / 2f <= litTo ? 1f : 0f) : clamp01((litTo + edge - (xs[k] + cw / 2f)) / edge);
			int col = argb(a * sung * b, hi);
			if (col != 0) sink.draw(chc, 0f, 0f, -away * LAYER_GAP, col, Font.DisplayMode.POLYGON_OFFSET);
			ps.popPose();
		}
		ps.popPose();
	}

	/**
	 * Pretty Lyrics glow in the glow color, as on the screen: a soft blob behind every character and a faint halo around the
	 * whole word, drawn as glowing quads (not copies of the font), behind the text. widths = character widths, x = left edge.
	 */
	private static void prettyGlow(float[] widths, float x, float a, float gv, boolean emph, float away, float lh) {
		Config c = Config.INSTANCE;
		gv *= c.prettyGlow / 100f;
		if (gv < 0.02f || a < 0.02f || collectorNow == null) return;
		int rgb = hex(c.glowColor, 0xFFFFFF);
		float r = (emph ? 4f + 6f * gv : 4f + 2f * gv) / 32f * lh * 2.2f;
		float op = Math.min(1f, emph ? gv : 0.45f * gv) * a;
		float total = 0;
		for (float w : widths) total += w;
		float z = away * LAYER_GAP * 3f; // behind the text
		glowQuad(x - 2f * r, lh * 0.45f - (lh + 3f * r) / 2f, total + 4f * r, lh + 3f * r, z, op * 0.35f, rgb);
		float cx = x;
		for (float w : widths) {
			if (w > 0) glowQuad(cx - r, lh * 0.45f - (lh * 0.9f + 2f * r) / 2f, w + 2f * r, lh * 0.9f + 2f * r, z, op * 0.5f, rgb);
			cx += w;
		}
	}

	/** One soft blob (the Pretty Lyrics glow texture) filling the box, both sides visible, never cut off at low alpha. */
	private static void glowQuad(float x, float y, float w, float h, float z, float alpha, int rgb) {
		int al = Math.round(clamp01(alpha) * 255);
		if (al < 2 || w <= 0 || h <= 0) return;
		int col = (al << 24) | (rgb & 0xFFFFFF);
		var type = net.minecraft.client.renderer.rendertype.RenderTypes.eyes(dev.slyrics.pretty.PrettyGlow.texture());
		int light = LightCoordsUtil.FULL_BRIGHT, overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
		float x1 = x + w, y1 = y + h;
		collectorNow.submitCustomGeometry(poseNow, type, (pose, vc) -> {
			float[][] q = {{x, y, 0, 0}, {x, y1, 0, 1}, {x1, y1, 1, 1}, {x1, y, 1, 0}};
			for (int side = 0; side < 2; side++) // both windings, so culling never hides it
				for (int i = 0; i < 4; i++) {
					float[] v = q[side == 0 ? i : 3 - i];
					vc.addVertex(pose, v[0], v[1], z).setColor(col).setUv(v[2], v[3]).setOverlay(overlay).setLight(light).setNormal(0f, 0f, 1f);
				}
		});
	}

	/** The sung color over the word: fully on up to litTo, fading out over the soft edge (in the word's units, as on the screen). */
	private static void prettySweep(Font font, Config c, String raw, float x, float w, float litTo, float edge, float a, int rgb, float away, Sink sink) {
		if (litTo + edge <= 0f || raw.isEmpty() || w <= 0) return;
		float z = -away * LAYER_GAP; // a little in front of the base text
		var top = Font.DisplayMode.POLYGON_OFFSET;
		if (litTo >= w) {
			int col = argb(a, rgb);
			if (col != 0) sink.draw(LyricsHud.styled(raw, c, true), x, 0f, z, col, top);
			return;
		}
		int n = raw.length();
		float[] xs = new float[n], bright = new float[n];
		float ax = 0;
		for (int i = 0; i < n; i++) {
			float cw = font.width(LyricsHud.styled(raw.substring(i, i + 1), c));
			xs[i] = ax;
			float mid = ax + cw / 2f;
			bright[i] = edge <= 0.001f ? (mid <= litTo ? 1f : 0f) : clamp01((litTo + edge - mid) / edge);
			ax += cw;
		}
		int full = 0;
		while (full < n && bright[full] >= 0.99f) full++;
		if (full > 0) {
			int col = argb(a, rgb);
			if (col != 0) sink.draw(LyricsHud.styled(raw.substring(0, full), c, true), x, 0f, z, col, top);
		}
		for (int i = full; i < n && bright[i] >= 0.02f; i++) {
			int col = argb(a * bright[i], rgb);
			if (col != 0) sink.draw(LyricsHud.styled(raw.substring(i, i + 1), c, true), x + xs[i], 0f, z, col, top);
		}
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
