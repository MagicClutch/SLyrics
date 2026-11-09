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
	/** Words are placed this long before they are sung (hidden until then), so the AFK camera can swing to them in time. */
	private static final long PLACE_LEAD_MS = 1000L;
	/** The AFK camera starts turning toward a word this long before it appears. */
	private static final long PAN_LEAD_MS = 750L;
	private static final int MAX_ITEMS = 40;

	/** A word, or a whole line, floating in the world. */
	public static final class Item {
		public final String[] words;
		public final long[] sungAt;
		public final long[] heldUntil;
		public Vec3 pos;
		public final Vec3 anchor;
		public float yaw, pitch;
		public final float roll;
		/** Sky words in the AFK camera stay fixed relative to the camera view, so they cannot slip out of frame. */
		public boolean camLocked;
		public double relYaw, relPitch, dist;
		/** Shrink factor applied to the world size so it fits the view. */
		public final float fit;
		/** Rough angular size in degrees, used to keep words apart. */
		public final double spanH, spanV;
		public final float seed;
		public final boolean sky;
		public final boolean stick;
		public long fadeAt = Long.MAX_VALUE;

		Item(String[] words, long[] sungAt, long[] heldUntil, Vec3 pos, Vec3 anchor, float yaw, float pitch, float roll,
			 float fit, double spanH, double spanV, float seed, boolean sky, boolean stick) {
			this.sky = sky;
			this.stick = stick;
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

	private record Queued(String word, long sungAt, long heldUntil, long fadeAt, int order, int mode) {}

	private final Random rng = new Random();
	public final List<Item> items = new ArrayList<>();
	private final List<Queued> queue = new ArrayList<>();
	private Vec3 follow;
	private long followNs;
	private int slotCursor;
	private Vec3 lastCarry;

	public void clear() {
		items.clear();
		queue.clear();
	}

	public void fadeAll(long now) {
		for (Item it : items) it.fadeAt = Math.min(it.fadeAt, now);
		queue.clear();
	}

	/** Keeps camera-locked sky words at the same place in the AFK camera view while the camera moves. */
	private void followCamera(Minecraft mc, long now) {
		boolean afk = AfkCamera.active();
		for (Item it : items) {
			if (!it.camLocked) continue;
			// outside the AFK camera the word follows the view only until it appears, then it stays where it is
			if (!afk && now >= it.sungAt[0]) {
				it.camLocked = false;
				continue;
			}
			Vec3 eye = AfkCamera.eye(mc);
			Vec3 spot = eye.add(dirOf(AfkCamera.yaw(mc) + it.relYaw, AfkCamera.pitch(mc) + it.relPitch).scale(it.dist));
			Vec3 off = spot.subtract(eye);
			it.pos = spot;
			it.yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f;
			it.pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
		}
	}

	/** Position of the word being sung, or of the next one shortly before it appears (the latest one within PAN_LEAD_MS), or null. */
	public Vec3 currentWordPos(long now, boolean followMe) {
		Item best = null;
		for (Item it : items) {
			if (it.sungAt[0] > now + PAN_LEAD_MS || now > it.fadeAt) continue;
			if (best == null || it.sungAt[0] > best.sungAt[0]) best = it;
		}
		return best == null ? null : worldPos(best, followMe);
	}

	public Vec3 worldPos(Item it, boolean followMe) {
		return followMe && !it.camLocked && follow != null ? it.pos.add(follow.subtract(it.anchor)) : it.pos;
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

		int modes = (c.hologram ? 1 : 0) + (c.skyMode ? 1 : 0) + (c.stickMode ? 1 : 0);
		boolean worldOnly = c.hologram && modes == 1;
		if (worldOnly && (c.lineMode || c.centerLayout)) {
			fadeAll(now);
		} else {
			queue.clear();
			for (Item it : items) if (it.fadeAt == Long.MAX_VALUE) it.fadeAt = now;
		}

		List<String> list = new ArrayList<>();
		List<Boolean> emphasis = new ArrayList<>();
		for (String w : text.trim().split("\\s+")) {
			if (w.isEmpty()) continue;
			emphasis.add(w.endsWith("!") || (w.length() >= 3 && w.equals(w.toUpperCase()) && w.chars().anyMatch(Character::isLetter)));
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

		if (c.beatShake && AfkCamera.active()) { // experimental: camera shakes timed by the lyrics
			if (c.shakeOnLines) AfkCamera.scheduleKick(start[0] + 90, 0.55);
			for (int i = 0; i < words.length; i++) {
				if (c.shakeOnLongWords && end[i] - start[i] >= 700) AfkCamera.scheduleKick(start[i], 1.0);
				else if (c.shakeOnEmphasis && emphasis.get(i)) AfkCamera.scheduleKick(start[i], 0.8);
			}
		}

		if (follow == null || follow.distanceToSqr(player.position()) > 900) follow = player.position();

		if (worldOnly && c.lineMode) {
			Vec3 spot = c.centerLayout ? ahead(player, 0, 4) : pick(mc, words, start[0], 4000, true);
			if (c.blockClipping) spot = safeSpot(mc, eye0(player), spot, words);
			place(mc, words, start, end, spot, c.centerLayout ? 0 : 1, 1f);
		} else if (worldOnly && c.centerLayout) {
			placeRow(mc, words, start, end);
		} else {
			// every word goes to one of the enabled modes (World words / Sky / Stick on blocks)
			List<Integer> enabled = new ArrayList<>();
			if (c.hologram) enabled.add(0);
			if (c.skyMode) enabled.add(1);
			if (c.stickMode) enabled.add(2);
			long gap = words.length > 1 ? (start[words.length - 1] - start[0]) / (words.length - 1) : 300;
			long life = Math.max(1200, Math.min(2400, gap * 4));
			for (int i = 0; i < words.length; i++) {
				int mode = enabled.get(rng.nextInt(enabled.size()));
				long fadeAt = mode == 1 ? Math.max(start[i] + 2600, end[i] + 1400) : Math.max(Math.max(start[i] + 1200, end[i] + 500), start[i] + life);
				if (c.wordHoldMs > 0) fadeAt = start[i] + c.wordHoldMs; // user-set: fade this long after the word is sung
				if (fadeAt < now) continue;
				queue.add(new Queued(words[i], start[i], end[i], fadeAt, i, mode));
			}
		}
		trim();
	}

	private static Vec3 eye0(net.minecraft.world.entity.player.Player p) {
		return AfkCamera.eye(Minecraft.getInstance());
	}

	private void trim() {
		while (items.size() > MAX_ITEMS) items.remove(0);
	}

	// ------------------------------------------------------------------ every frame

	public void frame(Minecraft mc, long now) {
		var player = mc.player;
		if (player == null) return;
		followCamera(mc, now);
		smoothFollow(player.position());
		carryUnsung(now);

		for (int i = 0; i < queue.size(); i++) {
			Queued q = queue.get(i);
			if (now < q.sungAt() - PLACE_LEAD_MS) continue;
			queue.remove(i--);
			if (now > q.fadeAt()) continue;
			String[] w = {q.word()};
			if (q.mode() == 1) {
				placeSky(mc, w, new long[]{q.sungAt()}, new long[]{q.heldUntil()}).fadeAt = q.fadeAt();
				continue;
			}
			if (q.mode() == 2) {
				Item stuck = placeStick(mc, w, new long[]{q.sungAt()}, new long[]{q.heldUntil()});
				if (stuck != null) {
					stuck.fadeAt = q.fadeAt();
					continue;
				}
				// no block in sight: fall through and float it like a normal word
			}
			Vec3 spot = pick(mc, w, q.sungAt(), q.fadeAt() - q.sungAt(), false);
			Vec3 eye = AfkCamera.eye(mc);
			Vec3 safe = Config.INSTANCE.blockClipping ? safeSpot(mc, eye, spot, w) : spot;
			float shrink = (float) Math.min(1.0, safe.distanceTo(eye) / Math.max(0.01, spot.distanceTo(eye)) * 1.1);
			place(mc, w, new long[]{q.sungAt()}, new long[]{q.heldUntil()}, safe, 2, shrink).fadeAt = q.fadeAt();
		}
		trim();

		Vec3 me = player.position();
		boolean fm = Config.INSTANCE.follow;
		items.removeIf(it -> now - it.fadeAt > FADE_MS || (!it.sky && worldPos(it, fm).distanceToSqr(me) > 3600)); // sky words stand far away on purpose
	}

	/**
	 * Words that are placed ahead of time (and the rest of a long line) travel with the player sideways and forwards,
	 * so when they are sung they are still the set distance in front of you, however fast you run.
	 */
	private void carryUnsung(long now) {
		Vec3 prev = lastCarry;
		lastCarry = follow;
		if (prev == null || follow == null || Config.INSTANCE.follow) return;
		double dx = follow.x - prev.x, dz = follow.z - prev.z;
		if (dx == 0 && dz == 0) return;
		for (Item it : items) {
			if (it.sky || it.stick || it.camLocked || now > it.sungAt[it.sungAt.length - 1]) continue;
			it.pos = it.pos.add(dx, 0, dz);
		}
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
		Minecraft mc = Minecraft.getInstance();
		return AfkCamera.eye(mc).add(dirOf(AfkCamera.yaw(mc) + yawOff, AfkCamera.pitch(mc)).scale(dist));
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
		Vec3 eye = AfkCamera.eye(mc);
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
			Vec3 spot = eye.add(dirOf(AfkCamera.yaw(mc) + dx, AfkCamera.pitch(mc) + dy).scale(dist));
			if (c.blockClipping) spot = liftFromGround(mc, spot);
			double room = clearance(eye, spot, spanH, spanV, c.follow);
			double score = Math.min(room, 7) * 9 - Math.hypot(dx - cx, dy - cy) * 0.35;
			if (coversPlayer(mc, eye, spot, spanH, spanV)) score -= 200;
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
		Vec3 eye = AfkCamera.eye(mc);
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
				yaw = AfkCamera.yaw(mc) * -1f;
				pitch = 0;
			}
		} else {
			yaw = -AfkCamera.yaw(mc);
		}
		Item it = new Item(words, sung, held, spot, follow != null ? follow : p.position(), yaw, pitch, roll, fit, spanH, spanV, rng.nextFloat() * 100f, false, false);
		items.add(it);
		return it;
	}

	/** Puts words huge and far away in the sky, inside the current view, without overlapping other sky words. */
	private Item placeSky(Minecraft mc, String[] words, long[] sung, long[] held) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = AfkCamera.eye(mc);
		double fovH = hFov(mc), fovV = mc.options.fov().get();
		double viewElev = -AfkCamera.pitch(mc);
		double viewYaw = AfkCamera.yaw(mc);
		int pixels = pixelWidth(mc, words);

		Item best = null;
		double bestGap = -1e9;
		// If no free spot exists, retry with smaller letters.
		double[] shrink = {1.0, 0.85, 0.7, 0.55};
		for (double sh : shrink) {
			// every word gets its own size and distance, so the sky does not look stamped
			double sizeVar = (0.8 + rng.nextDouble() * 0.45) * sh;
			for (int i = 0; i < 40; i++) {
				double dist = Math.max(30, c.skyDistance) * (0.75 + rng.nextDouble() * 0.5);
				double unit = 2 * dist * Math.tan(Math.toRadians(c.skyTextDeg * sizeVar) / 2) / 9.0;
				double width = pixels * unit;
				double maxWidth = 2 * dist * Math.tan(Math.toRadians(fovH * 0.36));
				if (width > maxWidth) {
					unit *= maxWidth / width;
					width = maxWidth;
				}
				double spanH = Math.toDegrees(Math.atan(width / 2 / dist));
				double spanV = Math.toDegrees(Math.atan(9 * unit / 2 / dist));

				double dxLimit = Math.max(0, fovH / 2 - spanH - 4);
				double dx = (rng.nextDouble() * 2 - 1) * dxLimit;
				double room = fovV * 0.5 - spanV - 4;
				double elev = c.skyElevation + rng.nextGaussian() * 9;
				elev = Math.max(viewElev - room, Math.min(viewElev + room, elev));
				elev = Math.max(Math.min(6, viewElev + room), elev); // stays in view even when looking steeply down

				Vec3 spot = eye.add(dirOf(viewYaw + dx, -elev).scale(dist));
				double gap = skyGap(eye, spot, spanH, spanV);
				if (c.blockClipping && blocked(mc, eye, spot, width / 2, 9 * unit / 2)) gap -= 60;
				if (coversPlayer(mc, eye, spot, spanH, spanV)) gap -= 80;
				if (gap > bestGap) {
					bestGap = gap;
					float fit = (float) (unit / unit());
					Vec3 off = spot.subtract(eye);
					float yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f;
					float pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
					float roll = (rng.nextFloat() - 0.5f) * 6f;
					best = new Item(words, sung, held, spot, follow != null ? follow : p.position(), yaw, pitch, roll, fit, spanH, spanV, rng.nextFloat() * 100f, true, false);
					// keep it in view while the camera moves (AFK) or until it appears (normal), so it never lands on the screen edge
					best.camLocked = true;
					best.relYaw = dx;
					best.relPitch = -elev - AfkCamera.pitch(mc);
					best.dist = dist;
				}
			}
			if (bestGap >= 1.0) break; // found a clear spot at this size
		}
		items.add(best);
		return best;
	}

	/** Free angular space (degrees) between a sky word and the nearest other sky word, treating both as rectangles. */
	private double skyGap(Vec3 eye, Vec3 spot, double halfW, double halfH) {
		Vec3 d = spot.subtract(eye);
		double yawA = Math.toDegrees(Math.atan2(d.z, d.x));
		double pitchA = Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		double gap = 8;
		for (Item it : items) {
			if (!it.sky) continue;
			Vec3 o = it.pos.subtract(eye);
			double dYaw = Math.abs(Math.toDegrees(Math.atan2(o.z, o.x)) - yawA);
			if (dYaw > 180) dYaw = 360 - dYaw;
			dYaw *= Math.cos(Math.toRadians(pitchA)); // yaw degrees shrink toward the zenith
			double dPitch = Math.abs(Math.toDegrees(Math.atan2(o.y, Math.hypot(o.x, o.z))) - pitchA);
			// widths are projected sideways, so scale them with the pitch; add a margin for the tilt
			double gx = dYaw - (halfW + it.spanH) * 1.15 - 1.5;
			double gy = dPitch - (halfH + it.spanV) * 1.3 - 1.0;
			gap = Math.min(gap, Math.max(gx, gy));
		}
		return gap;
	}

	/** Sticks a word flat onto a block surface in view, away from other stuck words. Returns null if no block is in sight. */
	private Item placeStick(Minecraft mc, String[] words, long[] sung, long[] held) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		if (mc.level == null) return null;
		Vec3 eye = AfkCamera.eye(mc);
		double yawV = AfkCamera.yaw(mc), pitchV = AfkCamera.pitch(mc);
		double fovH = hFov(mc), fovV = mc.options.fov().get();

		double unitW = 0.05 * c.stickSize / 100.0;
		double width = pixelWidth(mc, words) * unitW;
		double scale = width > 3.5 ? 3.5 / width : 1.0;
		double unit = unitW * scale;
		double halfW = width * scale / 2, halfH = 9 * unit / 2;

		Item best = null;
		double bestScore = -1e9;
		for (int i = 0; i < 32; i++) {
			double dx = (rng.nextDouble() * 2 - 1) * fovH * 0.38, dy = (rng.nextDouble() * 2 - 1) * fovV * 0.38;
			Vec3 dir = dirOf(yawV + dx, pitchV + dy);
			var hit = mc.level.clip(new ClipContext(eye, eye.add(dir.scale(Math.max(3, c.stickRange))), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) continue;
			var face = bhr.getDirection();
			Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			Vec3 spot = bhr.getLocation().add(n.scale(0.03));
			double facing = -dir.dot(n); // only faces seen head-on
			if (facing < 0.5) continue;

			double gap = 6;
			for (Item it : items) {
				if (!it.stick) continue;
				gap = Math.min(gap, spot.distanceTo(it.pos) - (halfW + it.spanH) * 1.1);
			}
			double score = Math.min(gap, 6) * 10 - Math.hypot(dx, dy) * 0.05 + facing * 25;
			double sd = Math.max(1, spot.distanceTo(eye));
			if (coversPlayer(mc, eye, spot, Math.toDegrees(Math.atan(halfW / sd)), Math.toDegrees(Math.atan(halfH / sd)))) score -= 200;
			if (score > bestScore) {
				bestScore = score;
				float yaw, pitch = 0, roll = (rng.nextFloat() - 0.5f) * 6f;
				if (Math.abs(n.y) > 0.9) { // floor or ceiling: lie flat, readable from where you stand
					Vec3 fwd = dirOf(yawV, 0);
					yaw = (float) Math.toDegrees(Math.atan2(fwd.z, fwd.x)) - 90f;
					pitch = n.y > 0 ? 90f : -90f;
				} else {
					yaw = (float) Math.toDegrees(Math.atan2(-n.z, -n.x)) - 90f;
				}
				best = new Item(words, sung, held, spot, follow != null ? follow : p.position(), yaw, pitch, roll,
					(float) (unit / unit()), halfW, halfH, rng.nextFloat() * 100f, false, true);
			}
		}
		if (best != null) items.add(best);
		return best;
	}

	/** Words in a row straight ahead, each appearing as it is sung. */
	private void placeRow(Minecraft mc, String[] words, long[] start, long[] end) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = AfkCamera.eye(mc);
		double dist = (c.near + c.far) / 2;
		Vec3 centre = ahead(p, 0, dist).add(0, -0.25, 0);
		if (c.blockClipping) centre = safeSpot(mc, eye, centre, words);
		double d = centre.distanceTo(eye);
		Vec3 side = dirOf(AfkCamera.yaw(mc) + 90, 0);
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

	/**
	 * In AFK mode the camera looks at the player: true if a word at this spot (half sizes in degrees)
	 * would cover the player on screen, or sit right in front of them.
	 */
	private boolean coversPlayer(Minecraft mc, Vec3 eye, Vec3 spot, double halfWDeg, double halfHDeg) {
		if (!AfkCamera.active() || mc.player == null) return false;
		Vec3 body = mc.player.position().add(0, 0.9, 0);
		Vec3 toBody = body.subtract(eye);
		double bodyDist = Math.max(0.5, toBody.length());
		Vec3 toSpot = spot.subtract(eye);
		double yawB = Math.toDegrees(Math.atan2(toBody.z, toBody.x)), yawS = Math.toDegrees(Math.atan2(toSpot.z, toSpot.x));
		double pitchB = Math.toDegrees(Math.atan2(toBody.y, Math.hypot(toBody.x, toBody.z)));
		double pitchS = Math.toDegrees(Math.atan2(toSpot.y, Math.hypot(toSpot.x, toSpot.z)));
		double dYaw = Math.abs(yawB - yawS);
		if (dYaw > 180) dYaw = 360 - dYaw;
		dYaw *= Math.cos(Math.toRadians(pitchS));
		double dPitch = Math.abs(pitchB - pitchS);
		double bodyHalfW = Math.toDegrees(Math.atan(0.55 / bodyDist)) + 2;
		double bodyHalfH = Math.toDegrees(Math.atan(1.0 / bodyDist)) + 2;
		return dYaw < halfWDeg + bodyHalfW && dPitch < halfHDeg + bodyHalfH;
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
