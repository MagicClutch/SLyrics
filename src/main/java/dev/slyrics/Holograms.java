package dev.slyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Jede neue Lyric-Zeile erscheint als schwebender Text an einer festen Position vor dem Spieler. */
public final class Holograms {
	private static final Random RNG = new Random();
	private static final List<Holo> ACTIVE = new ArrayList<>();
	private static String lastTrackId = "";
	private static int lastIdx = -1;

	private static final class Holo {
		final String text;
		final List<String> lines;
		final int fullWidth;
		final Vec3 pos;
		final long born;
		final long life;
		final long reveal;
		/** Drehung um die Y-Achse, einmalig beim Spawnen zum Spieler hin. */
		final float yaw;
		/** Zusatzskalierung, damit Text in der Ferne gleich gross wirkt. */
		final float sizeMul;
		final float roll;

		Holo(String text, List<String> lines, int fullWidth, Vec3 pos, float yaw, float sizeMul, long life, long reveal) {
			this(text, lines, fullWidth, pos, yaw, sizeMul, 0f, 0L, life, reveal);
		}

		Holo(String text, List<String> lines, int fullWidth, Vec3 pos, float yaw, float sizeMul, float roll, long delay, long life, long reveal) {
			this.sizeMul = sizeMul;
			this.roll = roll;
			this.lines = lines;
			this.fullWidth = fullWidth;
			this.yaw = yaw;
			this.text = text;
			this.pos = pos;
			this.born = System.currentTimeMillis() + delay;
			this.life = life;
			this.reveal = reveal;
		}
	}

	private Holograms() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Holograms::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Holograms::render);
	}

	private static void tick(Minecraft mc) {
		Config c = Config.INSTANCE;
		if (mc.player == null) PENDING.clear(); else processPending(mc);
		Track t = Spotify.track;
		List<LyricLine> lines = Spotify.lyrics;
		if (mc.player == null || t == null || lines.isEmpty() || !c.enabled || !c.hologram) {
			if (mc.player == null) ACTIVE.clear();
			lastIdx = -1;
			return;
		}
		if (!t.id().equals(lastTrackId)) {
			lastTrackId = t.id();
			lastIdx = -1;
			ACTIVE.clear();
			PENDING.clear();
		}
		long pos = Spotify.positionMs() + c.offsetMs;
		int idx = -1;
		int lo = 0, hi = lines.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= pos) {
				idx = mid;
				lo = mid + 1;
			} else hi = mid - 1;
		}
		if (!Spotify.playing || idx == lastIdx) return;
		lastIdx = idx;
		if (idx < 0) return;

		LyricLine cur = lines.get(idx);
		if (cur.text().isEmpty()) return;
		long next = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : cur.timeMs() + 5000;
		long len = Math.max(1200, Math.min(next - cur.timeMs(), 9000));
		schedule(cur.text(), len + c.fadeMs * 2L, Math.min((long) (len * 0.5), 1800));
	}

	private static List<String> wrap(String text, int maxWords) {
		String[] words = text.trim().split("\\s+");
		List<String> out = new ArrayList<>();
		int n = Math.max(1, maxWords);
		// gleichmäßig verteilen statt 6 + 1 Wort
		int lines = (words.length + n - 1) / n;
		int per = (words.length + lines - 1) / lines;
		for (int i = 0; i < words.length; i += per) {
			out.add(String.join(" ", java.util.Arrays.copyOfRange(words, i, Math.min(words.length, i + per))));
		}
		return out;
	}

	/** Teilt die Zeile in Stücke mit höchstens maxWords Wörtern; jedes Stück erscheint einzeln, nacheinander. */
	private static void schedule(String text, long life, long reveal) {
		Config c = Config.INSTANCE;
		if (c.skyMode) {
			spawnSky(text, life, reveal);
			return;
		}
		String[] words = text.trim().split("\\s+");
		int n = Math.max(1, c.maxWords);
		int chunks = (words.length + n - 1) / n;
		int per = (words.length + chunks - 1) / chunks;
		long now = System.currentTimeMillis();
		long span = Math.min(life, 4000) * 6 / 10;
		for (int i = 0; i < chunks; i++) {
			String chunk = String.join(" ", java.util.Arrays.copyOfRange(words, i * per, Math.min(words.length, (i + 1) * per)));
			long delay = chunks == 1 ? 0 : span * i / chunks;
			PENDING.add(new Pending(chunk, now + delay, Math.max(1500, life - delay), false, i, chunks));
		}
	}

	private static void spawn(Minecraft mc, String text, long life, long reveal) {
		Config c = Config.INSTANCE;
		if (!c.skyMode) text = Emojis.decorate(text, " ", c);
		var p = mc.player;
		Vec3 eye = p.getEyePosition();
		Font font = mc.font;

		List<String> lines = wrap(text, c.maxWords);
		int widthPx = 1;
		for (String l : lines) widthPx = Math.max(widthPx, font.width(LyricsHud.styled(l, c)));

		if (c.skyMode) {
			spawnSky(text, life, reveal);
			return;
		}

		// Sichtfeld (FOV-Einstellung ist vertikal)
		double vfov = Math.toRadians(mc.options.fov().get());
		double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double hfov = 2 * Math.atan(Math.tan(vfov / 2) * aspect);
		double margin = 0.06;

		double minD = Math.max(2.0, c.holoRadius * 0.7), maxD = Math.max(minD + 0.1, c.holoRadius);
		Vec3 best = null;
		float bestMul = 1f;
		double bestScore = -1;
		for (int i = 0; i < 16; i++) {
			double d = minD + RNG.nextDouble() * (maxD - minD);
			float mul = (float) Math.max(1.0, d / 5.0);
			double unit = 0.025 * c.holoScale * mul; // Blöcke pro Pixel
			double halfW = widthPx * unit / 2.0;
			double halfH = lines.size() * font.lineHeight * unit / 2.0;
			double maxYaw = hfov / 2 - Math.atan(halfW / d) - margin;
			double maxPitch = vfov / 2 - Math.atan(halfH / d) - margin;
			if (maxYaw < 0.02 || maxPitch < 0.02) continue; // Text passt in dieser Entfernung nicht ins Bild
			// bevorzugt weit seitlich (Mitte bleibt frei), aber nie aus dem Sichtfeld
			double yawOff, pitchOff;
			if (curCount > 1) { // Lesereihenfolge: von links nach rechts, leicht versetzt auf/ab
				double f = (curIdx + 0.5) / curCount * 2 - 1;
				yawOff = f * maxYaw * 0.9 + (RNG.nextDouble() * 2 - 1) * maxYaw * 0.08;
				pitchOff = (curIdx % 2 == 0 ? 1 : -1) * Math.min(maxPitch, 0.2) * 0.5 + (RNG.nextDouble() * 2 - 1) * 0.03;
			} else {
				double side = RNG.nextBoolean() ? 1 : -1;
				yawOff = side * maxYaw * (0.2 + 0.6 * RNG.nextDouble());
				pitchOff = (RNG.nextDouble() * 2 - 1) * Math.min(maxPitch, 0.2);
			}
			double yaw = Math.toRadians(p.getYRot()) + yawOff;
			double pitch = Math.toRadians(p.getXRot()) + pitchOff;
			Vec3 cand = eye.add(-Math.sin(yaw) * Math.cos(pitch) * d, -Math.sin(pitch) * d, Math.cos(yaw) * Math.cos(pitch) * d);
			double nearest = 99;
			for (Holo h : ACTIVE) nearest = Math.min(nearest, h.pos.distanceTo(cand));
			if (nearest > bestScore) {
				bestScore = nearest;
				best = cand;
				bestMul = mul;
			}
		}
		if (best == null) { // Notlösung: mittig, so weit wie nötig
			double d = maxD;
			double yaw = Math.toRadians(p.getYRot()), pitch = Math.toRadians(p.getXRot());
			best = eye.add(-Math.sin(yaw) * Math.cos(pitch) * d, -Math.sin(pitch) * d, Math.cos(yaw) * Math.cos(pitch) * d);
			bestMul = (float) Math.max(1.0, d / 5.0);
		}
		if (!c.throughWalls) best = pullInFrontOfBlocks(mc, eye, best);
		float faceYaw = (float) Math.atan2(eye.x - best.x, eye.z - best.z);
		ACTIVE.add(new Holo(text, lines, widthPx, best, faceYaw, bestMul, life, reveal));
		while (ACTIVE.size() > Math.max(1, c.holoMax)) ACTIVE.remove(0);
	}

	/** Liegt ein Block zwischen Auge und Ziel, wird das Ziel kurz vor den Block gezogen. */
	private static Vec3 pullInFrontOfBlocks(Minecraft mc, Vec3 eye, Vec3 target) {
		if (mc.level == null) return target;
		var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, target,
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return target;
		Vec3 dir = hit.getLocation().subtract(eye);
		double len = dir.length();
		return len < 1.5 ? eye.add(dir.scale(0.5)) : eye.add(dir.scale(Math.max(0.0, len - 0.6) / len));
	}

	private static long pausedSince = 0;

	private record Pending(String raw, long due, long life, boolean sky, int idx, int count) {}

	/** Position des gerade platzierten Stücks in seiner Zeile (für Lesereihenfolge links -> rechts). */
	private static int curIdx = 0, curCount = 1;

	private static final List<Pending> PENDING = new ArrayList<>();

	/** Sky-Modus: Wörter werden eingeplant und erst beim Erscheinen platziert (aktuelle Blickrichtung). */
	private static void spawnSky(String text, long life, long fullReveal) {
		String[] words = text.trim().split("\\s+");
		int totalChars = 0;
		for (String w : words) totalChars += w.length() + 1;
		long singing = Math.min(fullReveal * 2, 3500);
		long now = System.currentTimeMillis();
		int before = 0;
		for (int idx = 0; idx < words.length; idx++) {
			String raw = words[idx];
			long delay = singing * before / Math.max(1, totalChars);
			PENDING.add(new Pending(raw, now + delay, Math.max(1500, life - delay), true, idx, words.length));
			before += raw.length() + 1;
		}
	}

	private static void processPending(Minecraft mc) {
		if (PENDING.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending pe = it.next();
			if (pe.due() > now) continue;
			it.remove();
			curIdx = pe.idx();
			curCount = pe.count();
			if (pe.sky()) placeSkyWord(mc, pe.raw(), pe.life());
			else spawn(mc, pe.raw(), pe.life(), Math.min((long) (pe.life() * 0.4), 1500));
		}
	}

	private static void placeSkyWord(Minecraft mc, String raw, long life) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = p.getEyePosition();
		Font font = mc.font;
		String word = Emojis.decorate(raw, "", c);
		int wPx = font.width(LyricsHud.styled(word, c));

		double d = Math.max(15.0, c.skyDistance);
		float mul = (float) Math.max(1.0, d / 9.0);
		double unit = 0.025 * c.holoScale * mul;
		// Etwas Reserve für Schräglage und Zeilenhöhe
		double halfW = wPx * unit / 2.0 * 1.15 + font.lineHeight * unit * 0.3;
		double halfH = font.lineHeight * unit / 2.0 * 1.15 + wPx * unit * 0.2;

		double vfov = Math.toRadians(mc.options.fov().get()) * 1.1; // Reserve (z.B. Sprint-FOV)
		double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double hfov = 2 * Math.atan(Math.tan(vfov / 2) * aspect);
		double margin = 0.07;

		double maxYaw = hfov / 2 - Math.atan(halfW / d) - margin;
		double maxPitch = vfov / 2 - Math.atan(halfH / d) - margin;
		maxYaw = Math.max(0.03, Math.min(maxYaw, Math.toRadians(40)));
		maxPitch = Math.max(0.03, maxPitch);
		double viewElev = Math.toRadians(-p.getXRot());

		Vec3 best = null;
		double bestScore = -1;
		for (int i = 0; i < 24; i++) {
			double yawOff = curCount > 1
				? ((curIdx + 0.5) / curCount * 2 - 1) * maxYaw * 0.9 + (RNG.nextDouble() * 2 - 1) * maxYaw * 0.08
				: (RNG.nextDouble() * 2 - 1) * maxYaw * 0.6;
			double zig = curCount > 1 ? (curIdx % 2 == 0 ? 6 : -6) : 0;
			double yaw = Math.toRadians(p.getYRot()) + yawOff;
			double target = Math.toRadians(c.skyElevation + zig + (RNG.nextDouble() * 2 - 1) * 4);
			// immer innerhalb des sichtbaren Bereichs, auch wenn der Spieler nicht nach oben schaut
			double elev = Math.max(viewElev - maxPitch, Math.min(viewElev + maxPitch, target));
			Vec3 cand = eye.add(-Math.sin(yaw) * Math.cos(elev) * d, Math.sin(elev) * d, Math.cos(yaw) * Math.cos(elev) * d);
			double nearest = 999;
			for (Holo h : ACTIVE) nearest = Math.min(nearest, h.pos.distanceTo(cand) / h.sizeMul);
			if (nearest > bestScore) {
				bestScore = nearest;
				best = cand;
			}
		}
		float faceYaw = (float) Math.atan2(eye.x - best.x, eye.z - best.z);
		float roll = (float) Math.toRadians((RNG.nextDouble() * 2 - 1) * c.skyTilt);
		ACTIVE.add(new Holo(word, List.of(word), wPx, best, faceYaw, mul, roll, 0L, life, 350));
		while (ACTIVE.size() > Math.max(c.holoMax, 14)) ACTIVE.remove(0);
	}

	private static void render(LevelRenderContext ctx) {
		Config c = Config.INSTANCE;
		if (ACTIVE.isEmpty() || !c.enabled || !c.hologram) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		PoseStack ps = ctx.poseStack();
		long now = System.currentTimeMillis();
		float pauseMul = 1f;
		if (Spotify.playing) pausedSince = 0;
		else {
			if (pausedSince == 0) pausedSince = now;
			pauseMul = Math.max(0f, 1f - (now - pausedSince) / 500f);
			if (pauseMul == 0f) {
				ACTIVE.clear();
				PENDING.clear();
				return;
			}
		}
		int rgb = c.rgb();
		// Rand: dunklere Variante der Textfarbe -> auch weisser Text bleibt auf hellem Himmel lesbar
		int glowRgb = ((((rgb >> 16 & 255) * 35 / 100)) << 16) | ((((rgb >> 8 & 255) * 35 / 100)) << 8) | ((rgb & 255) * 35 / 100);

		for (Iterator<Holo> it = ACTIVE.iterator(); it.hasNext(); ) {
			Holo h = it.next();
			long age = now - h.born;
			if (age < 0) continue; // Wort kommt erst später
			if (age > h.life) {
				it.remove();
				continue;
			}
			float fade = Math.max(1f, c.fadeMs);
			float alpha = Math.min(1f, Math.min(age / fade, (h.life - age) / fade));
			int a = Math.round(alpha * pauseMul * 255);
			if (a < 4) continue;

			int totalChars = h.text.length();
			int visible = totalChars;
			if (c.typewriter) {
				visible = (int) Math.max(1, Math.min(totalChars, Math.round(totalChars * Math.min(1f, age / (float) h.reveal))));
			}

			double drift = Math.min(age, 6000) / 1000.0 * 0.06;
			ps.pushPose();
			ps.translate(h.pos.x - cam.pos.x, h.pos.y - cam.pos.y + drift, h.pos.z - cam.pos.z);
			ps.mulPose(new org.joml.Matrix4f().rotationY(h.yaw)); // fest, kein Billboard
			if (h.roll != 0f) ps.mulPose(new org.joml.Matrix4f().rotationZ(h.roll));
			float s = 0.025f * c.holoScale * h.sizeMul;
			ps.scale(s, -s, s);
			Font.DisplayMode mode = c.throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
			int n = h.lines.size();
			int used = 0;
			for (int i = 0; i < n; i++) {
				String full = h.lines.get(i);
				int take = Math.max(0, Math.min(full.length(), visible - used));
				used += full.length() + 1;
				if (take == 0) continue;
				int w = font.width(LyricsHud.styled(full, c));
				float y = (i - n / 2f) * font.lineHeight;
				ctx.submitNodeCollector().submitText(ps, -w / 2f, y, LyricsHud.styled(full.substring(0, take), c).getVisualOrderText(),
					c.shadow, mode, LightCoordsUtil.FULL_BRIGHT, (a << 24) | rgb, c.background ? ((a * 140 / 255) << 24) : 0, c.glow ? (a << 24) | glowRgb : 0);
			}
			ps.popPose();
		}
	}
}
