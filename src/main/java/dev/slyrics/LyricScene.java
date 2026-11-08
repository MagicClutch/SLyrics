package dev.slyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The words of the song placed in the world, each with the moment it is sung and how long it is held.
 * Everything here runs on the render thread.
 */
public final class LyricScene {
	public static final long APPEAR_MS = 280L;
	public static final long FADE_MS = 550L;
	private static final int MAX_PIECES = 32;
	private static final long WORD_LEAD_MS = 80L;
	private static final double MAX_SYLLABLE_MS = 450.0;
	private static final long MIN_WORD_LIFE_MS = 1200L;
	private static final long MAX_WORD_LIFE_MS = 2400L;
	private static final float GLYPH_HEIGHT = 9f;
	private static final String VOWELS = "aeiouyáéíóúýěůäöüåøæàèìòùâêîôûãõ";

	/** One word (or line) in the world. */
	public static final class Piece {
		public final String[] words;
		public final long[] sungAt, heldUntil;
		public final double x, y, z;
		public final Vec3 anchor;
		public final float yaw, pitch, roll, fit;
		public final double halfWidth, halfHeight;
		public final float driftSeed;
		public long fadeAt = Long.MAX_VALUE;

		Piece(String[] words, long[] sungAt, long[] heldUntil, Vec3 pos, Vec3 anchor, float yaw, float pitch, float roll,
			  float fit, double halfWidth, double halfHeight, float driftSeed) {
			this.words = words;
			this.sungAt = sungAt;
			this.heldUntil = heldUntil;
			this.x = pos.x;
			this.y = pos.y;
			this.z = pos.z;
			this.anchor = anchor;
			this.yaw = yaw;
			this.pitch = pitch;
			this.roll = roll;
			this.fit = fit;
			this.halfWidth = halfWidth;
			this.halfHeight = halfHeight;
			this.driftSeed = driftSeed;
		}
	}

	private record Pending(String word, long sungAt, long heldUntil, long fadeAt, int index) {}

	private final Random rng = new Random();
	public final List<Piece> pieces = new ArrayList<>();
	private final List<Pending> pending = new ArrayList<>();
	private Vec3 followPos;
	private long lastFollowNs;

	public void clear() {
		pieces.clear();
		pending.clear();
	}

	public void fadeAll(long now) {
		for (Piece p : pieces) p.fadeAt = Math.min(p.fadeAt, now);
		pending.clear();
	}

	/** Where a piece is drawn: where it was put, or carried along with the player. */
	public Vec3 worldPos(Piece piece, boolean follow) {
		Vec3 base = new Vec3(piece.x, piece.y, piece.z);
		if (follow && followPos != null) return base.add(followPos.subtract(piece.anchor));
		return base;
	}

	// ---------------------------------------------------------------- timing guess

	/** A guess at when each word of a line is sung: the line's time shared out by syllables. */
	public static long[] wordTimes(String[] words, long start, long durationMs) {
		int[] syl = new int[words.length];
		int sum = 0;
		for (int i = 0; i < words.length; i++) {
			syl[i] = syllables(words[i]);
			sum += syl[i];
		}
		double per = Math.min(durationMs * 0.85 / Math.max(1, sum), MAX_SYLLABLE_MS);
		long[] out = new long[words.length];
		int before = 0;
		for (int i = 0; i < words.length; i++) {
			out[i] = start - WORD_LEAD_MS + Math.round(before * per);
			before += syl[i];
		}
		return out;
	}

	public static int syllables(String word) {
		String lower = word.toLowerCase();
		int count = 0, letters = 0;
		boolean inVowel = false;
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if (Character.isLetter(c)) letters++;
			boolean vowel = VOWELS.indexOf(c) >= 0;
			if (vowel && !inVowel) count++;
			inVowel = vowel;
		}
		if (count > 1 && lower.endsWith("e") && !lower.endsWith("le")) count--;
		if (count == 0) count = Math.max(1, letters / 3);
		return Math.min(count, 5);
	}

	// ---------------------------------------------------------------- a new line

	public void setLine(Minecraft mc, String text, long lineTimeMs, long lineEndMs, long positionMs, long now) {
		Config c = Config.INSTANCE;
		boolean lines = c.lineMode, center = c.centerLayout;
		if (lines || center) {
			fadeAll(now);
		} else {
			pending.clear();
			for (Piece p : pieces) if (p.fadeAt == Long.MAX_VALUE) p.fadeAt = now;
		}
		var player = mc.player;
		if (player == null) return;
		long songStart = now - positionMs;

		String[] split = text.trim().split("\\s+");
		List<String> rawList = new ArrayList<>();
		for (String w : split) if (!w.isEmpty()) rawList.add(c.uppercase ? Emojis.decorate(w, "", c).toUpperCase() : Emojis.decorate(w, "", c));
		if (rawList.isEmpty()) return;
		String[] words = rawList.toArray(new String[0]);
		long[] times = wordTimes(words, songStart + lineTimeMs, Math.max(400L, lineEndMs - lineTimeMs));
		long[] ends = new long[words.length];
		for (int i = 0; i < words.length; i++) ends[i] = i + 1 < words.length ? times[i + 1] : songStart + lineEndMs;

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		float yaw = yawOf(look), pitch = pitchOf(look);
		if (followPos == null || followPos.distanceToSqr(player.position()) > 1024.0) followPos = player.position();

		if (lines) {
			Vec3 target = center ? centerPos(eye, pitch, yaw) : scatter(mc, eye, pitch, yaw, words, 0, times[0], 4000L, true);
			addPiece(words, times, ends, inFront(mc, eye, target), eye, yaw, !center, 1f);
		} else if (!center) {
			long gap = words.length > 1 ? (times[times.length - 1] - times[0]) / (words.length - 1) : 300L;
			long life = Math.max(MIN_WORD_LIFE_MS, Math.min(MAX_WORD_LIFE_MS, 4 * gap));
			for (int i = 0; i < words.length; i++) {
				long fadeAt = times[i] + life;
				if (fadeAt < now) continue;
				pending.add(new Pending(words[i], times[i], ends[i], fadeAt, i));
			}
		} else {
			// Words in a row ahead, each appearing as it is sung, the whole row fitted to the view.
			var font = mc.font;
			float space = font.width(LyricsHud.styled(" ", c));
			float[] widths = new float[words.length];
			float total = space * (words.length - 1);
			for (int i = 0; i < words.length; i++) {
				widths[i] = font.width(LyricsHud.styled(words[i], c));
				total += widths[i];
			}
			Vec3 ctr = inFront(mc, eye, centerPos(eye, pitch, yaw));
			Vec3 right = Vec3.directionFromRotation(0f, yaw + 90f);
			float unit = unit();
			float fit = (float) Math.min(1.0, 2.0 * ctr.distanceTo(eye) * Math.tan(Math.toRadians(horizontalFov(mc) * 0.38)) / Math.max(total * (double) unit, 0.001));
			unit *= fit;
			float x = -total / 2f;
			for (int i = 0; i < words.length; i++) {
				Vec3 pos = ctr.add(right.scale((x + widths[i] / 2f) * unit));
				addPiece(new String[]{words[i]}, new long[]{times[i]}, new long[]{ends[i]}, pos, eye, yaw, false, fit);
				x += widths[i] + space;
			}
		}
		while (pieces.size() > MAX_PIECES) pieces.remove(0);
	}

	// ---------------------------------------------------------------- every frame

	public void frame(Minecraft mc, long now) {
		var player = mc.player;
		if (player == null) return;
		updateFollow(player.position());
		if (!pending.isEmpty()) {
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getViewVector(1f);
			float yaw = yawOf(look), pitch = pitchOf(look);
			for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
				Pending p = it.next();
				if (now < p.sungAt() - 60) continue;
				it.remove();
				if (now > p.fadeAt()) continue;
				Vec3 target = scatter(mc, eye, pitch, yaw, new String[]{p.word()}, p.index(), p.sungAt(), p.fadeAt() - p.sungAt(), false);
				Vec3 placed = inFront(mc, eye, target);
				// Pulled in front of a wall: shrink it so it looks the size it would have been.
				float fit = (float) Math.min(1.0, placed.distanceTo(eye) / Math.max(0.01, target.distanceTo(eye)) * 1.1);
				addPiece(new String[]{p.word()}, new long[]{p.sungAt()}, new long[]{p.heldUntil()}, placed, eye, yaw, true, fit).fadeAt = p.fadeAt();
			}
			while (pieces.size() > MAX_PIECES) pieces.remove(0);
		}
		Vec3 pp = player.position();
		boolean follow = Config.INSTANCE.follow;
		pieces.removeIf(p -> now - p.fadeAt > FADE_MS || worldPos(p, follow).distanceToSqr(pp) > 4096.0);
	}

	private void updateFollow(Vec3 target) {
		long nowNs = System.nanoTime();
		if (followPos == null || followPos.distanceToSqr(target) > 1024.0) {
			followPos = target;
		} else {
			double dt = Math.max(0.0, Math.min(0.1, (nowNs - lastFollowNs) / 1e9));
			double k = 1.0 - Math.exp(-dt * 8.0);
			followPos = followPos.add(target.subtract(followPos).scale(k));
		}
		lastFollowNs = nowNs;
	}

	// ---------------------------------------------------------------- placing

	private float unit() {
		return 0.05f * Config.INSTANCE.size / 100f;
	}

	private Piece addPiece(String[] words, long[] times, long[] ends, Vec3 pos, Vec3 eye, float camYaw, boolean tilt, float fitLimit) {
		Minecraft mc = Minecraft.getInstance();
		double dist = pos.distanceTo(eye);
		double width = textWidth(mc, words) * unit();
		float fit = (float) Math.min(fitLimit, 2.0 * dist * Math.tan(Math.toRadians(horizontalFov(mc) * 0.38)) / Math.max(width, 0.001));
		double halfWidth = Math.toDegrees(Math.atan(width * fit / (2.0 * dist))) + 2.0;
		double halfHeight = Math.toDegrees(Math.atan(GLYPH_HEIGHT * unit() * fit / (2.0 * dist))) + 2.0;

		float yaw, pitch, roll;
		if (tilt) {
			// Turned to face you; a single word is also knocked a little off square.
			float k = words.length > 1 ? 0f : 1f;
			Vec3 off = pos.subtract(eye);
			yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f + (rng.nextFloat() - 0.5f) * 50f * k;
			pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
			roll = (rng.nextFloat() - 0.5f) * 16f * k;
		} else {
			yaw = camYaw;
			pitch = 0f;
			roll = 0f;
		}
		Vec3 anchor = followPos != null ? followPos : mc.player != null ? mc.player.position() : eye;
		Piece piece = new Piece(words, times, ends, pos, anchor, yaw, pitch, roll, fit, halfWidth, halfHeight, rng.nextFloat() * 100f);
		pieces.add(piece);
		return piece;
	}

	/** A spot near the crosshair for word [index]: four columns across the view, kept where it overlaps the other words least. */
	private Vec3 scatter(Minecraft mc, Vec3 eye, float pitch, float yaw, String[] words, int index, long sungAt, long life, boolean lineBase) {
		Config c = Config.INSTANCE;
		double hFov = horizontalFov(mc);
		double vFov = mc.options.fov().get();
		double spanH = Math.min(hFov * 0.21, 18.0);
		double spanV = Math.min(vFov * 0.17, 10.0);
		double baseH = lineBase ? 0.0 : -spanH + (index % 4) * (2 * spanH / 3);
		double baseV = (lineBase ? -0.2 : index % 2 == 0 ? 0.5 : -0.3) * spanV;
		float near = c.near;
		float far = Math.max(near + 0.5f, c.far);
		double width = textWidth(mc, words) * unit();

		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < 28; i++) {
			double spread = 3.0 + i * 0.6;
			double dh = clamp(baseH + rng.nextGaussian() * spread, -spanH * 1.25, spanH * 1.25);
			double dv = clamp(baseV + rng.nextGaussian() * spread * 0.6, -spanV * 1.25, spanV * 1.25);
			double dist = near + rng.nextDouble() * (far - near);
			double fit = Math.min(1.0, 2.0 * dist * Math.tan(Math.toRadians(hFov * 0.38)) / Math.max(width, 0.001));
			double halfW = Math.toDegrees(Math.atan(width * fit / (2.0 * dist))) + 2.0;
			double halfH = Math.toDegrees(Math.atan(GLYPH_HEIGHT * unit() * fit / (2.0 * dist))) + 2.0;
			Vec3 pos = aboveFloor(mc, eye.add(Vec3.directionFromRotation((float) (pitch + dv), (float) (yaw + dh)).scale(dist)));
			double gap = nearestGap(eye, pos, halfW, halfH, sungAt, sungAt + life, c.follow);
			double score = Math.min(gap, 6.0) * 10.0 - Math.hypot(dh - baseH, dv - baseV) * 0.3;
			if (score > bestScore) {
				bestScore = score;
				best = pos;
			}
		}
		return best != null ? best : eye.add(Vec3.directionFromRotation(pitch, yaw).scale((near + far) / 2));
	}

	/** Degrees of clear sky between a spot and the nearest word on screen at the same time (6 when none). */
	private double nearestGap(Vec3 eye, Vec3 pos, double halfW, double halfH, long from, long until, boolean follow) {
		Vec3 dir = pos.subtract(eye);
		double yawA = Math.toDegrees(Math.atan2(dir.z, dir.x));
		double pitchA = Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));
		double gap = 6.0;
		for (Piece piece : pieces) {
			if (piece.fadeAt + FADE_MS < from || piece.sungAt[0] > until) continue;
			Vec3 other = worldPos(piece, follow).subtract(eye);
			double dYaw = Math.abs(Math.toDegrees(Math.atan2(other.z, other.x)) - yawA);
			if (dYaw > 180) dYaw = 360 - dYaw;
			double dPitch = Math.abs(Math.toDegrees(Math.atan2(other.y, Math.hypot(other.x, other.z))) - pitchA);
			gap = Math.min(gap, Math.max(dYaw - (halfW + piece.halfWidth), dPitch - (halfH + piece.halfHeight)));
		}
		return gap;
	}

	private Vec3 centerPos(Vec3 eye, float pitch, float yaw) {
		Config c = Config.INSTANCE;
		return eye.add(Vec3.directionFromRotation(Math.max(-40f, Math.min(40f, pitch + 4f)), yaw).scale((c.near + c.far) / 2.0));
	}

	/** Pulls a spot in front of the first wall between the eye and it, so a word never sits inside a block. */
	private Vec3 inFront(Minecraft mc, Vec3 eye, Vec3 pos) {
		var level = mc.level;
		if (level == null) return pos;
		Vec3 delta = pos.subtract(eye);
		double length = delta.length();
		if (length < 0.01) return pos;
		Vec3 step = delta.scale(0.2 / length);
		Vec3 at = eye;
		double travelled = 0.0;
		while (travelled < length) {
			at = at.add(step);
			travelled += 0.2;
			BlockPos block = BlockPos.containing(at.x, at.y, at.z);
			if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
				return eye.add(delta.scale(Math.max(1.0, travelled - 0.4) / length));
			}
		}
		return pos;
	}

	/** Lifts a spot out of the ground it would be buried in. */
	private Vec3 aboveFloor(Minecraft mc, Vec3 pos) {
		var level = mc.level;
		if (level == null) return pos;
		Vec3 out = pos;
		for (int i = 0; i < 3; i++) {
			BlockPos block = BlockPos.containing(out.x, out.y, out.z);
			if (level.getBlockState(block).getCollisionShape(level, block).isEmpty()) return out;
			out = out.add(0.0, 1.0, 0.0);
		}
		return out;
	}

	private int textWidth(Minecraft mc, String[] words) {
		Config c = Config.INSTANCE;
		var font = mc.font;
		int space = font.width(LyricsHud.styled(" ", c));
		int sum = 0;
		for (String w : words) sum += font.width(LyricsHud.styled(w, c));
		return sum + space * (words.length - 1);
	}

	private static double horizontalFov(Minecraft mc) {
		double v = Math.toRadians(mc.options.fov().get());
		double aspect = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getGuiScaledHeight());
		return Math.toDegrees(2.0 * Math.atan(Math.tan(v / 2.0) * aspect));
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static float yawOf(Vec3 look) {
		return (float) Math.toDegrees(Math.atan2(-look.x, look.z));
	}

	private static float pitchOf(Vec3 look) {
		return (float) Math.toDegrees(-Math.asin(clamp(look.y, -1.0, 1.0)));
	}
}
