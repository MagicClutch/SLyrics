package dev.slyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Holds the lyric words that currently exist in the world: when each one is sung, where it floats,
 * how it is turned, and when it fades. Runs on the render thread.
 */
public final class LyricScene {
	public static final long POP_MS = 300L;
	public static final long FADE_MS = 500L;
	private static final int MAX_ITEMS = 40;

	/** A word, or a whole line, floating in the world. */
	public static final class Item {
		public final String[] words;
		public final long[] sungAt;
		public final long[] heldUntil;
		public final Vec3 pos;
		public final Vec3 anchor;
		public final float yaw, pitch, roll;
		/** Shrink factor applied to the world size so it fits the view. */
		public final float fit;
		/** Rough angular size in degrees, used to keep words apart. */
		public final double spanH, spanV;
		public final float seed;
		public final boolean sky;
		public long fadeAt = Long.MAX_VALUE;

		Item(String[] words, long[] sungAt, long[] heldUntil, Vec3 pos, Vec3 anchor, float yaw, float pitch, float roll,
			 float fit, double spanH, double spanV, float seed, boolean sky) {
			this.sky = sky;
			this.words = words;
			this.sungAt = sungAt;
			this.heldUntil = heldUntil;
			this.pos = pos;
			this.anchor = anchor;
			this.yaw = yaw;
			this.pitch = pitch;
			this.roll = roll;
			this.fit = fit;
			this.spanH = spanH;
			this.spanV = spanV;
			this.seed = seed;
		}
	}

	private record Queued(String word, long sungAt, long heldUntil, long fadeAt, int order) {}

	private final Random rng = new Random();
	public final List<Item> items = new ArrayList<>();
	private final List<Queued> queue = new ArrayList<>();
	private Vec3 follow;
	private long followNs;
	private int slotCursor;

	public void clear() {
		items.clear();
		queue.clear();
	}

	public void fadeAll(long now) {
		for (Item it : items) it.fadeAt = Math.min(it.fadeAt, now);
		queue.clear();
	}

	public Vec3 worldPos(Item it, boolean followMe) {
		return followMe && follow != null ? it.pos.add(follow.subtract(it.anchor)) : it.pos;
	}

	// ------------------------------------------------------------------ a new line

	/** Distributes the line's duration over its words, weighted by word length. */
	private static long[] schedule(String[] words, long start, long duration) {
		double[] w = new double[words.length];
		double sum = 0;
		for (int i = 0; i < words.length; i++) {
			w[i] = words[i].length() + 1.5;
			sum += w[i];
		}
		double usable = Math.min(duration * 0.85, 420.0 * sum / 4.0);
		long[] t = new long[words.length];
		double acc = 0;
		for (int i = 0; i < words.length; i++) {
			t[i] = start - 90 + Math.round(acc / sum * usable);
			acc += w[i];
		}
		return t;
	}

	public void setLine(Minecraft mc, String text, long lineTimeMs, long lineEndMs, long positionMs, long now) {
		Config c = Config.INSTANCE;
		var player = mc.player;
		if (player == null) return;

		if (c.lineMode || c.centerLayout) {
			fadeAll(now);
		} else {
			queue.clear();
			for (Item it : items) if (it.fadeAt == Long.MAX_VALUE) it.fadeAt = now;
		}

		List<String> list = new ArrayList<>();
		for (String w : text.trim().split("\\s+")) {
			if (w.isEmpty()) continue;
			String d = Emojis.decorate(w, "", c);
			list.add(c.uppercase ? d.toUpperCase() : d);
		}
		if (list.isEmpty()) return;
		String[] words = list.toArray(new String[0]);

		long songStart = now - positionMs;
		long duration = Math.max(400L, lineEndMs - lineTimeMs);
		long[] start = schedule(words, songStart + lineTimeMs, duration);
		long[] end = new long[words.length];
		for (int i = 0; i < words.length; i++) end[i] = i + 1 < words.length ? start[i + 1] : songStart + lineEndMs;

		if (follow == null || follow.distanceToSqr(player.position()) > 900) follow = player.position();

		if (c.skyMode) {
			if (c.lineMode) {
				placeSky(mc, words, start, end);
			} else {
				for (int i = 0; i < words.length; i++) {
					long fadeAt = Math.max(start[i] + 2600, end[i] + 1400);
					if (fadeAt < now) continue;
					queue.add(new Queued(words[i], start[i], end[i], fadeAt, i));
				}
			}
		} else if (c.lineMode) {
			Vec3 spot = c.centerLayout ? ahead(player, 0, 4) : pick(mc, words, start[0], 4000, true);
			if (c.blockClipping) spot = safeSpot(mc, eye0(player), spot, words);
			place(mc, words, start, end, spot, c.centerLayout ? 0 : 1, 1f);
		} else if (c.centerLayout) {
			placeRow(mc, words, start, end);
		} else {
			long gap = words.length > 1 ? (start[words.length - 1] - start[0]) / (words.length - 1) : 300;
			long life = Math.max(1200, Math.min(2400, gap * 4));
			for (int i = 0; i < words.length; i++) {
				long fadeAt = Math.max(start[i] + 1200, end[i] + 500);
				if (fadeAt < now) continue;
				queue.add(new Queued(words[i], start[i], end[i], Math.max(fadeAt, start[i] + life), i));
			}
		}
		trim();
	}

	private static Vec3 eye0(net.minecraft.world.entity.player.Player p) {
		return p.getEyePosition();
	}

	private void trim() {
		while (items.size() > MAX_ITEMS) items.remove(0);
	}

	// ------------------------------------------------------------------ every frame

	public void frame(Minecraft mc, long now) {
		var player = mc.player;
		if (player == null) return;
		smoothFollow(player.position());

		for (int i = 0; i < queue.size(); i++) {
			Queued q = queue.get(i);
			if (now < q.sungAt() - 50) continue;
			queue.remove(i--);
			if (now > q.fadeAt()) continue;
			String[] w = {q.word()};
			if (Config.INSTANCE.skyMode) {
				placeSky(mc, w, new long[]{q.sungAt()}, new long[]{q.heldUntil()}).fadeAt = q.fadeAt();
				continue;
			}
			Vec3 spot = pick(mc, w, q.sungAt(), q.fadeAt() - q.sungAt(), false);
			Vec3 eye = player.getEyePosition();
			Vec3 safe = Config.INSTANCE.blockClipping ? safeSpot(mc, eye, spot, w) : spot;
			float shrink = (float) Math.min(1.0, safe.distanceTo(eye) / Math.max(0.01, spot.distanceTo(eye)) * 1.1);
			place(mc, w, new long[]{q.sungAt()}, new long[]{q.heldUntil()}, safe, 2, shrink).fadeAt = q.fadeAt();
		}
		trim();

		Vec3 me = player.position();
		boolean fm = Config.INSTANCE.follow;
		items.removeIf(it -> now - it.fadeAt > FADE_MS || worldPos(it, fm).distanceToSqr(me) > 3600);
	}

	private void smoothFollow(Vec3 target) {
		long ns = System.nanoTime();
		if (follow == null || follow.distanceToSqr(target) > 900) {
			follow = target;
		} else {
			double dt = Math.min(0.1, Math.max(0, (ns - followNs) / 1e9));
			follow = follow.add(target.subtract(follow).scale(1 - Math.exp(-dt * 7)));
		}
		followNs = ns;
	}

	// ------------------------------------------------------------------ placement

	private float unit() {
		return 0.05f * Config.INSTANCE.size / 100f;
	}

	/** A point in the player's view, offset by yaw/pitch degrees, at the given distance. */
	private Vec3 ahead(net.minecraft.world.entity.player.Player p, double yawOff, double dist) {
		return p.getEyePosition().add(dirOf(p.getYRot() + yawOff, p.getXRot()).scale(dist));
	}

	private static Vec3 dirOf(double yawDeg, double pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	private static double hFov(Minecraft mc) {
		double v = Math.toRadians(mc.options.fov().get());
		double aspect = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getGuiScaledHeight());
		return Math.toDegrees(2 * Math.atan(Math.tan(v / 2) * aspect));
	}

	private int pixelWidth(Minecraft mc, String[] words) {
		Config c = Config.INSTANCE;
		int sum = mc.font.width(LyricsHud.styled(" ", c)) * (words.length - 1);
		for (String w : words) sum += mc.font.width(LyricsHud.styled(w, c));
		return sum;
	}

	/**
	 * Chooses where a word goes: the view is split into six slots (3 across, 2 up/down) that are
	 * used in turn; around the slot centre several random spots are tried and the one farthest
	 * from the words already visible wins.
	 */
	private Vec3 pick(Minecraft mc, String[] words, long from, long life, boolean wholeLine) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = p.getEyePosition();
		double fovH = hFov(mc), fovV = mc.options.fov().get();
		double reachH = Math.min(fovH * 0.22, 20), reachV = Math.min(fovV * 0.18, 11);
		int slot = wholeLine ? 0 : (slotCursor++ % 6);
		double cx = wholeLine ? 0 : (slot % 3 - 1) * reachH * 0.8;
		double cy = wholeLine ? -0.15 * reachV : (slot / 3 == 0 ? 0.55 : -0.4) * reachV;
		double near = c.near, far = Math.max(near + 0.5, c.far);
		double width = pixelWidth(mc, words) * unit();

		Vec3 best = null;
		double bestScore = -1e9;
		for (int i = 0; i < 24; i++) {
			double j = 2 + i * 0.5;
			double dx = Math.max(-reachH * 1.2, Math.min(reachH * 1.2, cx + rng.nextGaussian() * j));
			double dy = Math.max(-reachV * 1.2, Math.min(reachV * 1.2, cy + rng.nextGaussian() * j * 0.55));
			double dist = near + rng.nextDouble() * (far - near);
			double fit = Math.min(1.0, 2 * dist * Math.tan(Math.toRadians(fovH * 0.38)) / Math.max(width, 0.01));
			double spanH = Math.toDegrees(Math.atan(width * fit / (2 * dist)));
			double spanV = Math.toDegrees(Math.atan(9 * unit() * fit / (2 * dist)));
			Vec3 spot = eye.add(dirOf(p.getYRot() + dx, p.getXRot() + dy).scale(dist));
			if (c.blockClipping) spot = liftFromGround(mc, spot);
			double room = clearance(eye, spot, spanH, spanV, c.follow);
			double score = Math.min(room, 7) * 9 - Math.hypot(dx - cx, dy - cy) * 0.35;
			if (score > bestScore) {
				bestScore = score;
				best = spot;
			}
		}
		return best != null ? best : ahead(p, 0, (near + far) / 2);
	}

	/** Angular room (degrees) between a spot and the nearest visible word, capped at 7. */
	private double clearance(Vec3 eye, Vec3 spot, double spanH, double spanV, boolean followMe) {
		Vec3 a = spot.subtract(eye).normalize();
		double room = 7;
		for (Item it : items) {
			Vec3 b = worldPos(it, followMe).subtract(eye).normalize();
			double sep = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.dot(b)))));
			double need = Math.max(spanH + it.spanH, spanV + it.spanV);
			room = Math.min(room, sep - need * 0.75);
		}
		return room;
	}

	private Item place(Minecraft mc, String[] words, long[] sung, long[] held, Vec3 spot, int faceMode, float fitLimit) {
		var p = mc.player;
		Vec3 eye = p.getEyePosition();
		double dist = Math.max(0.5, spot.distanceTo(eye));
		double width = pixelWidth(mc, words) * unit();
		float fit = (float) Math.min(fitLimit, 2 * dist * Math.tan(Math.toRadians(hFov(mc) * 0.38)) / Math.max(width, 0.01));
		double spanH = Math.toDegrees(Math.atan(width * fit / (2 * dist)));
		double spanV = Math.toDegrees(Math.atan(9 * unit() * fit / (2 * dist)));

		float yaw, pitch = 0, roll = 0;
		Vec3 off = spot.subtract(eye);
		if (faceMode == 2 || faceMode == 1) {
			// turned toward the player; single words are also knocked slightly off square
			float k = words.length > 1 ? 0f : 1f;
			yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f + (rng.nextFloat() - 0.5f) * 44f * k;
			pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
			roll = (rng.nextFloat() - 0.5f) * 14f * k;
			if (words.length > 1) { // keep long lines square so their far end does not smear
				yaw = p.getYRot() * -1f;
				pitch = 0;
			}
		} else {
			yaw = -p.getYRot();
		}
		Item it = new Item(words, sung, held, spot, follow != null ? follow : p.position(), yaw, pitch, roll, fit, spanH, spanV, rng.nextFloat() * 100f, false);
		items.add(it);
		return it;
	}

	private int skyCursor;

	/** Puts words huge and far away in the sky, inside the current view, kept apart from other sky words. */
	private Item placeSky(Minecraft mc, String[] words, long[] sung, long[] held) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = p.getEyePosition();
		double fovH = hFov(mc), fovV = mc.options.fov().get();
		double viewElev = -p.getXRot();
		int slot = skyCursor++ % 5;

		Item best = null;
		double bestRoom = -1e9;
		for (int i = 0; i < 16; i++) {
			double dist = Math.max(30, c.skyDistance) * (0.85 + rng.nextDouble() * 0.3);
			double unit = 2 * dist * Math.tan(Math.toRadians(c.skyTextDeg) / 2) / 9.0;
			double width = pixelWidth(mc, words) * unit;
			double maxWidth = 2 * dist * Math.tan(Math.toRadians(fovH * 0.36));
			if (width > maxWidth) {
				unit *= maxWidth / width;
				width = maxWidth;
			}
			double spanH = Math.toDegrees(Math.atan(width / 2 / dist));
			double spanV = Math.toDegrees(Math.atan(9 * unit / 2 / dist));

			double dx = (slot - 2) * fovH * 0.14 + (rng.nextDouble() - 0.5) * fovH * 0.1;
			double dxLimit = Math.max(0, fovH / 2 - spanH - 4);
			dx = Math.max(-dxLimit, Math.min(dxLimit, dx));
			double elev = c.skyElevation + (slot % 2 == 0 ? 5 : -5) + (rng.nextDouble() - 0.5) * 8;
			double room = fovV * 0.5 - spanV - 4;
			elev = Math.max(viewElev - room, Math.min(viewElev + room, elev));
			elev = Math.max(6, elev);

			Vec3 spot = eye.add(dirOf(p.getYRot() + dx, -elev).scale(dist));
			float fit = (float) (unit / unit());
			Vec3 off = spot.subtract(eye);
			float yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f;
			float pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
			float roll = (rng.nextFloat() - 0.5f) * 6f;
			Item it = new Item(words, sung, held, spot, follow != null ? follow : p.position(), yaw, pitch, roll, fit, spanH, spanV, rng.nextFloat() * 100f, true);
			double room2 = clearance(eye, spot, spanH, spanV, false);
			if (c.blockClipping && blocked(mc, eye, spot, width / 2, 9 * unit / 2)) room2 -= 60;
			if (room2 > bestRoom) {
				bestRoom = room2;
				best = it;
			}
		}
		items.add(best);
		return best;
	}

	/** Words in a row straight ahead, each appearing as it is sung. */
	private void placeRow(Minecraft mc, String[] words, long[] start, long[] end) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = p.getEyePosition();
		double dist = (c.near + c.far) / 2;
		Vec3 centre = ahead(p, 0, dist).add(0, -0.25, 0);
		if (c.blockClipping) centre = safeSpot(mc, eye, centre, words);
		double d = centre.distanceTo(eye);
		Vec3 side = dirOf(p.getYRot() + 90, 0);
		float spaceW = mc.font.width(LyricsHud.styled(" ", c));
		float[] w = new float[words.length];
		float total = spaceW * (words.length - 1);
		for (int i = 0; i < words.length; i++) {
			w[i] = mc.font.width(LyricsHud.styled(words[i], c));
			total += w[i];
		}
		double fit = Math.min(1.0, 2 * d * Math.tan(Math.toRadians(hFov(mc) * 0.38)) / Math.max(total * unit(), 0.01));
		double u = unit() * fit;
		double x = -total / 2.0;
		for (int i = 0; i < words.length; i++) {
			Vec3 spot = centre.add(side.scale((x + w[i] / 2.0) * u));
			place(mc, new String[]{words[i]}, new long[]{start[i]}, new long[]{end[i]}, spot, 0, (float) fit);
			x += w[i] + spaceW;
		}
	}

	/** True if a block is between the eye and the word's centre or any of its four edges. */
	private boolean blocked(Minecraft mc, Vec3 eye, Vec3 spot, double halfW, double halfH) {
		if (mc.level == null) return false;
		Vec3 dir = spot.subtract(eye);
		Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
		Vec3[] pts = {spot, spot.add(side.scale(halfW)), spot.subtract(side.scale(halfW)), spot.add(0, halfH, 0), spot.add(0, -halfH, 0)};
		for (Vec3 pt : pts) {
			var hit = mc.level.clip(new ClipContext(eye, pt, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (hit.getType() != HitResult.Type.MISS) return true;
		}
		return false;
	}

	/**
	 * Moves the spot toward the eye until the whole word (centre and its four edges) has a clear
	 * line of sight, so it is never drawn inside a block.
	 */
	private Vec3 safeSpot(Minecraft mc, Vec3 eye, Vec3 spot, String[] words) {
		if (mc.level == null) return spot;
		double halfW = pixelWidth(mc, words) * unit() / 2, halfH = 9 * unit() / 2;
		Vec3 dir = spot.subtract(eye);
		double len = dir.length();
		if (len < 0.01) return spot;
		Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
		Vec3[] pts = {spot, spot.add(side.scale(halfW)), spot.subtract(side.scale(halfW)), spot.add(0, halfH, 0), spot.add(0, -halfH, 0)};
		double ratio = 1.0;
		for (Vec3 pt : pts) {
			var hit = mc.level.clip(new ClipContext(eye, pt, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (hit.getType() == HitResult.Type.MISS) continue;
			double r = hit.getLocation().subtract(eye).length() / Math.max(0.01, pt.subtract(eye).length());
			ratio = Math.min(ratio, r);
		}
		if (ratio >= 1.0) return spot;
		double newLen = Math.max(0.8, len * ratio - 0.6);
		return eye.add(dir.scale(newLen / len));
	}

	/** If a block is between the eye and the spot, the spot moves to just in front of it. */
	private Vec3 clipToWalls(Minecraft mc, Vec3 eye, Vec3 spot) {
		if (mc.level == null) return spot;
		var hit = mc.level.clip(new ClipContext(eye, spot, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() == HitResult.Type.MISS) return spot;
		Vec3 dir = hit.getLocation().subtract(eye);
		double len = dir.length();
		return len < 1.2 ? eye.add(dir.scale(0.5)) : eye.add(dir.scale((len - 0.5) / len));
	}

	/** Moves a spot up if it would sit inside the ground. */
	private Vec3 liftFromGround(Minecraft mc, Vec3 spot) {
		if (mc.level == null) return spot;
		Vec3 s = spot;
		for (int i = 0; i < 3; i++) {
			var pos = net.minecraft.core.BlockPos.containing(s.x, s.y, s.z);
			if (mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return s;
			s = s.add(0, 1, 0);
		}
		return s;
	}
}
