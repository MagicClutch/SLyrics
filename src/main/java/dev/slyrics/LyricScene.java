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
	/** Extra room in degrees kept between two words. */
	private static final double WORD_MARGIN = 1.0;
	/** How long a word stays after it has been sung (unless Word hold is set), and how much of a long sung word counts. */
	private static final long WORD_HOLD_MS = 2000, MAX_SUNG_MS = 3000;
	/** A word must fit inside the view with at least this much room (degrees) to the screen edge. */
	private static final double EDGE_DEG = 2.0;
	/**
	 * Spread (0..100) as the share of the view, measured from the middle to each side, that words may use: 0 keeps them
	 * close around the middle, 100 lets them use about 3/4 of the screen (3/8 to each side).
	 */
	private static double spreadV(int spread) {
		return 0.1 + 0.275 * Math.max(0, Math.min(100, spread)) / 100.0;
	}

	private static double spreadH(int spread) {
		return 0.15 + 0.225 * Math.max(0, Math.min(100, spread)) / 100.0;
	}
	/** Room (degrees) left at the side of a sky word for its slow drift. */
	private static final double SKY_DRIFT_DEG = 3.0;
	/** Sky words never come lower than this many degrees above the horizon (measured to the bottom of the word). */
	private static final double SKY_MIN_ELEV = 4.0;
	/** Each new word stands about this much (blocks) behind the farthest one showing; past far + DEPTH_OVERFLOW it starts in front again. */
	private static final double DEPTH_STEP = 0.6, DEPTH_OVERFLOW = 2.5;
	/** Words are placed this long before they are sung (hidden until then), so the AFK camera can swing to them in time. */
	private static final long PLACE_LEAD_MS = 1000L;
	/** Without the AFK camera words are placed only this long before they are sung, so they fit the view of that moment. */
	private static final long PLACE_LEAD_NORMAL_MS = 250L;
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
		/** Half size used to keep words apart: in degrees, except for stuck words, where it is in blocks. */
		public final double spanH, spanV;
		public final float seed;
		/** World size of one font pixel when the word was placed (the general text size); fit scales it. */
		public final float base = unitNow();
		public final boolean sky;
		public final boolean stick;
		public long fadeAt = Long.MAX_VALUE;
		/** Floating mode: travel speed in blocks per second, and how far it has travelled so far. */
		public Vec3 vel, drift;
		public boolean floatInit;
		/** Pretty Lyrics: the animation state of each word (made when the word is first drawn). */
		public dev.slyrics.pretty.PrettyWord[] pretty;

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

	private record Queued(String word, long sungAt, long heldUntil, long fadeAt, int mode) {}

	private final Random rng = new Random();
	public final List<Item> items = new ArrayList<>();
	private final List<Queued> queue = new ArrayList<>();
	private Vec3 follow;
	private long followNs;
	private int slotCursor;
	private Vec3 lastCarry;
	/** Floating mode: which way the current line flows across the view (alternates per line). */
	private int floatSign = 1;

	public void clear() {
		items.clear();
		queue.clear();
		lobbyQueue.clear();
	}

	public void fadeAll(long now) {
		for (Item it : items) it.fadeAt = Math.min(it.fadeAt, now);
		queue.clear();
		lobbyQueue.clear();
	}

	/** Fades out the words that are on screen right now (the upcoming ones stay queued). Used at AFK camera cuts. */
	public void fadeVisible(long now) {
		for (Item it : items) if (now >= it.sungAt[0]) it.fadeAt = Math.min(it.fadeAt, now);
	}

	/**
	 * Sky words are placed inside the view and keep their place in the view while the AFK camera moves, so they
	 * never drift out of the picture. Outside the AFK camera they follow the view only until they appear, then
	 * they stand still in the world.
	 */
	private void followCamera(Minecraft mc, long now) {
		boolean afk = AfkCamera.active();
		for (Item it : items) {
			if (!it.camLocked) continue;
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
		Vec3 base = followMe && !it.camLocked && follow != null ? it.pos.add(follow.subtract(it.anchor)) : it.pos;
		return it.drift == null ? base : base.add(it.drift);
	}

	/** A line split into its words, with when each word is sung and when it ends (wall-clock ms). */
	private record Prepared(String[] words, long[] start, long[] end) {}

	private static Prepared prepare(String text, long lineTimeMs, long lineEndMs, long positionMs, long now, long[] wordStart, long[] wordEnd) {
		Config c = Config.INSTANCE;
		List<String> list = new ArrayList<>();
		for (String w : text.trim().split("\\s+")) {
			if (w.isEmpty()) continue;
			String d = Emojis.decorate(w, "", c);
			list.add(c.uppercase ? d.toUpperCase() : d);
		}
		if (list.isEmpty()) return null;
		String[] words = list.toArray(new String[0]);

		long songStart = now - positionMs;
		long duration = Math.max(400L, lineEndMs - lineTimeMs);
		long[] start, end;
		if (wordStart != null && wordEnd != null && wordStart.length == words.length && wordEnd.length == words.length) {
			start = new long[words.length];
			end = new long[words.length];
			for (int i = 0; i < words.length; i++) {
				start[i] = songStart + wordStart[i];
				end[i] = songStart + wordEnd[i];
			}
		} else {
			start = schedule(words, songStart + lineTimeMs, duration);
			end = new long[words.length];
			for (int i = 0; i < words.length; i++) end[i] = i + 1 < words.length ? start[i + 1] : songStart + lineEndMs;
		}
		return new Prepared(words, start, end);
	}

	// ------------------------------------------------------------------ OneClient lobby (experimental)

	/** Lobby words waiting for their moment: they are placed in a lobby frame, where the lobby camera is known. */
	private final List<Queued> lobbyQueue = new ArrayList<>();
	/** Word height on the screen at 100 % Word size, as a share of the screen height. */
	private static final double LOBBY_WORD_H = 0.075;
	/** Screen areas kept free in the lobby ({u0, v0, u1, v1}, v up): your character in the middle, the menu panels. */
	private static final double[][] LOBBY_FREE = {
		{-0.2, -1.0, 0.2, 0.3},   // the character
		{0.5, -1.0, 1.0, -0.15},  // play panel, bottom right
		{-1.0, -1.0, -0.5, -0.7}, // social bar, bottom left
	};

	/** Lobby: a new line is being sung; its words are queued one by one, each placed shortly before it is sung. */
	public void setLobbyLine(String text, long lineTimeMs, long lineEndMs, long positionMs, long now, long[] wordStart, long[] wordEnd) {
		lobbyQueue.clear();
		Prepared pr = prepare(text, lineTimeMs, lineEndMs, positionMs, now, wordStart, wordEnd);
		if (pr == null) return;
		Config c = Config.INSTANCE;
		long hold = c.wordHoldMs > 0 ? c.wordHoldMs : WORD_HOLD_MS;
		for (int i = 0; i < pr.words().length; i++) {
			long fadeAt = pr.start()[i] + Math.min(MAX_SUNG_MS, Math.max(0, pr.end()[i] - pr.start()[i])) + hold;
			if (fadeAt < now) continue;
			lobbyQueue.add(new Queued(pr.words()[i], pr.start()[i], pr.end()[i], fadeAt, 0));
		}
	}

	/**
	 * Lobby frame: places the words whose moment has come on a free spot of what the lobby camera sees right now. Once
	 * placed, a word stays at its place in the world: when the camera turns, sways or zooms in, it moves over the screen
	 * (and may leave it) like any block would.
	 */
	public void frameLobby(Minecraft mc, Vec3 eye, float camYaw, float camPitch, double hFovDeg, double vFovDeg, long now) {
		Config c = Config.INSTANCE;
		double tanH = Math.tan(Math.toRadians(hFovDeg) / 2), tanV = Math.tan(Math.toRadians(vFovDeg) / 2);
		double halfH = LOBBY_WORD_H * c.lobbyTextSize / 100.0; // half the word height in v units (the screen is 2 high)
		double spread = Math.max(0.3, Math.min(1.0, c.lobbySpread / 100.0));
		Vec3 fwd = dirOf(camYaw, camPitch), up = dirOf(camYaw, camPitch - 90), right = fwd.cross(up).normalize();

		long latestStarted = Long.MIN_VALUE;
		for (Queued q : lobbyQueue) if (q.sungAt() <= now) latestStarted = Math.max(latestStarted, q.sungAt());
		for (int i = 0; i < lobbyQueue.size(); i++) {
			Queued q = lobbyQueue.get(i);
			if (now < q.sungAt() - PLACE_LEAD_NORMAL_MS) continue;
			lobbyQueue.remove(i--);
			if (now > q.fadeAt() || q.sungAt() < latestStarted) continue;
			String[] w = {q.word()};
			double halfW = halfH * tanV / tanH * pixelWidth(mc, w) / 9.0;
			double maxHalfW = 0.45 * spread;
			double wordH = halfH;
			if (halfW > maxHalfW) { // a very long word: smaller, so it still fits
				wordH *= maxHalfW / halfW;
				halfW = maxHalfW;
			}
			double[] spot = lobbySpot(eye, fwd, right, up, tanH, tanV, halfW, wordH, spread, now);

			Vec3 dir = fwd.add(right.scale(spot[0] * tanH)).add(up.scale(spot[1] * tanV)).normalize();
			double dist = Math.max(2.0, c.lobbyDistance) * (0.8 + 0.4 * rng.nextDouble());
			Vec3 at = eye.add(dir.scale(dist));
			if (mc.level != null) { // a hill in the way: just in front of it (it then looks just as big)
				var hit = mc.level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
				if (hit.getType() != HitResult.Type.MISS) {
					dist = Math.max(1.5, hit.getLocation().distanceTo(eye) * 0.85);
					at = eye.add(dir.scale(dist));
				}
			}
			// world height so the word covers 2*wordH of the screen height from where the camera is now
			double along = dist * dir.dot(fwd);
			float fit = (float) (2 * wordH * tanV * along / (9 * unit()));
			Vec3 off = at.subtract(eye);
			float yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f + (rng.nextFloat() - 0.5f) * 30f;
			float pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
			// span: half size in blocks across and up, used to keep the next words apart on the screen
			double worldHalfW = halfW * tanH * along, worldHalfH = wordH * tanV * along;
			Item it = new Item(w, new long[]{q.sungAt()}, new long[]{q.heldUntil()}, at, at, yaw, pitch,
				(rng.nextFloat() - 0.5f) * 14f, fit, worldHalfW, worldHalfH, rng.nextFloat() * 100f, false, false);
			it.fadeAt = q.fadeAt();
			items.add(it);
		}
		trim();
		items.removeIf(it -> now - it.fadeAt > FADE_MS);
	}

	/**
	 * A free spot on the lobby screen (u, v in -1..1, v up) for a word of this half size: the try with the most room to the
	 * words that show now (wherever the camera has moved them) wins.
	 */
	private double[] lobbySpot(Vec3 eye, Vec3 fwd, Vec3 right, Vec3 up, double tanH, double tanV, double halfW, double halfH, double spread, long now) {
		// where the other words are on the screen now
		List<double[]> others = new ArrayList<>();
		for (Item it : items) {
			if (now - it.fadeAt > FADE_MS) continue;
			Vec3 off = it.pos.subtract(eye);
			double z = off.dot(fwd);
			if (z < 0.1) continue;
			others.add(new double[]{off.dot(right) / (z * tanH), off.dot(up) / (z * tanV), it.spanH / (z * tanH), it.spanV / (z * tanV)});
		}
		double uMax = Math.max(0.05, 0.92 * spread - halfW), vMax = Math.max(0.05, 0.8 * spread - halfH);
		double[] best = {0, 0.5};
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < 60; k++) {
			double u = (rng.nextDouble() * 2 - 1) * uMax, v = (rng.nextDouble() * 2 - 1) * vMax;
			double score = 10;
			for (double[] f : LOBBY_FREE) {
				double dx = Math.max(f[0] - (u + halfW), (u - halfW) - f[2]);
				double dy = Math.max(f[1] - (v + halfH), (v - halfH) - f[3]);
				double out = Math.max(dx, dy); // > 0: outside the free area by that much
				if (out < 0) score = Math.min(score, out * 20 - 1);
			}
			for (double[] o : others) {
				double dx = Math.abs(u - o[0]) - (halfW + o[2]), dy = Math.abs(v - o[1]) - (halfH + o[3]);
				double gap = Math.max(dx, dy * 1.4);
				score = Math.min(score, gap < 0 ? gap * 10 - 1 : gap);
			}
			if (score > bestScore) {
				bestScore = score;
				best = new double[]{u, v};
			}
		}
		return best;
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

	/**
	 * A new line is being sung. wordStart / wordEnd: when each word is sung in song ms (exact word times or Pretty Lyrics'
	 * timing), or null to spread the line over its words here.
	 */
	public void setLine(Minecraft mc, String text, long lineTimeMs, long lineEndMs, long positionMs, long now, long[] wordStart, long[] wordEnd) {
		Config c = Config.INSTANCE;
		var player = mc.player;
		if (player == null) return;

		queue.clear();
		for (Item it : items) if (it.fadeAt == Long.MAX_VALUE) it.fadeAt = now;

		Prepared pr = prepare(text, lineTimeMs, lineEndMs, positionMs, now, wordStart, wordEnd);
		if (pr == null) return;
		String[] words = pr.words();
		long[] start = pr.start(), end = pr.end();

		if (follow == null || follow.distanceToSqr(player.position()) > 900) follow = player.position();
		floatSign = -floatSign;

		{
			// every word goes to one of the enabled modes (Aura / Sky / Stick on blocks)
			List<Integer> enabled = new ArrayList<>();
			if (c.hologram) enabled.add(0);
			if (c.skyMode) enabled.add(1);
			if (c.stickMode) enabled.add(2);
			// every word stays the same time after it has been sung (a word that is held longer stays that much longer); the
			// sung part counts at most MAX_SUNG_MS, so the last word before a long pause does not stand there for ages
			long hold = c.wordHoldMs > 0 ? c.wordHoldMs : WORD_HOLD_MS;
			for (int i = 0; i < words.length; i++) {
				int mode = enabled.get(rng.nextInt(enabled.size()));
				long fadeAt = start[i] + Math.min(MAX_SUNG_MS, Math.max(0, end[i] - start[i])) + hold;
				if (c.floating && mode != 2) fadeAt = Math.max(fadeAt, start[i] + 3200); // floating words need time to travel
				if (fadeAt < now) continue;
				queue.add(new Queued(words[i], start[i], end[i], fadeAt, mode));
			}
		}
		trim();
	}

	private void trim() {
		while (items.size() > MAX_ITEMS) items.remove(0);
	}

	// ------------------------------------------------------------------ every frame

	public void frame(Minecraft mc, long now) {
		var player = mc.player;
		if (player == null) return;
		MovePredictor.update(mc);
		followCamera(mc, now);
		smoothFollow(player.position());
		carryUnsung(now);

		// words whose moment has passed while a later one is already being sung (the line started midway, the song was
		// skipped ahead, the position jumped) are left out, so words never pop up in a bunch: only the one being sung shows
		long latestStarted = Long.MIN_VALUE;
		for (Queued q : queue) if (q.sungAt() <= now) latestStarted = Math.max(latestStarted, q.sungAt());
		for (int i = 0; i < queue.size(); i++) {
			Queued q = queue.get(i);
			if (now < q.sungAt() - (AfkCamera.active() ? PLACE_LEAD_MS : PLACE_LEAD_NORMAL_MS)) continue;
			queue.remove(i--);
			if (now > q.fadeAt() || q.sungAt() < latestStarted) continue;
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
			Vec3 spot = pick(mc, w);
			Vec3 eye = AfkCamera.eye(mc);
			// a spot behind the ground or a wall comes forward to just in front of it, and shrinks by as much, so the word looks
			// just as big where you look but stands in the world (small, on the ground)
			Vec3 safe = inFrontOfBlocks(mc, eye, spot, w);
			float shrink = (float) Math.min(1.0, safe.distanceTo(eye) / Math.max(0.01, spot.distanceTo(eye)) * 1.1);
			Item placed = place(mc, w, new long[]{q.sungAt()}, new long[]{q.heldUntil()}, safe, shrink);
			placed.fadeAt = q.fadeAt();
		}
		trim();
		updateFloating(mc, now);

		Vec3 me = player.position();
		boolean fm = Config.INSTANCE.follow;
		// words left far behind go; the limit grows with speed, or words placed far ahead while flying would vanish at once
		double keep = 80 + MovePredictor.reachAhead(Config.INSTANCE.predictSeconds) + Config.INSTANCE.far;
		items.removeIf(it -> now - it.fadeAt > FADE_MS || (!it.sky && worldPos(it, fm).distanceToSqr(me) > keep * keep)); // sky words stand far away on purpose
	}

	/**
	 * Words that are placed ahead of time (and the rest of a long line) travel with the player sideways and forwards,
	 * so when they are sung they are still the set distance in front of you, however fast you run.
	 */
	private void carryUnsung(long now) {
		Vec3 prev = lastCarry;
		lastCarry = follow;
		if (prev == null || follow == null || Config.INSTANCE.follow || MovePredictor.enabled()) return; // predicted words already sit on the path ahead
		double dx = follow.x - prev.x, dz = follow.z - prev.z;
		if (dx == 0 && dz == 0) return;
		for (Item it : items) {
			if (it.sky || it.stick || it.camLocked || now > it.sungAt[it.sungAt.length - 1]) continue;
			it.pos = it.pos.add(dx, 0, dz);
		}
	}

	/** Floating mode: gives new words a velocity across the view and moves all floating words along. */
	private void updateFloating(Minecraft mc, long now) {
		Config c = Config.INSTANCE;
		Vec3 eye = null;
		for (Item it : items) {
			if (!it.floatInit) {
				it.floatInit = true;
				if (c.floating && !it.stick) {
					if (eye == null) eye = AfkCamera.eye(mc);
					Vec3 off = it.pos.subtract(eye);
					double dist = Math.max(1.0, off.length());
					double flat = Math.max(0.001, Math.hypot(off.x, off.z));
					Vec3 side = new Vec3(-off.z / flat, 0, off.x / flat);
					double perSec = Math.toRadians(c.floatSpeed) * dist; // same angular speed near and far
					it.vel = side.scale(floatSign * perSec).add(0, perSec * 0.12, 0);
				}
			}
			if (it.vel != null) it.drift = it.vel.scale(Math.max(0, now - it.sungAt[0]) / 1000.0);
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
		return unitNow();
	}

	/** World size of one font pixel for new words (the general text size; sky words work out their own). */
	static float unitNow() {
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

	/** Horizontal field of view in degrees. */
	private static double hFov(Minecraft mc) {
		double aspect = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getGuiScaledHeight());
		return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(mc.options.fov().get()) / 2) * aspect));
	}

	private int pixelWidth(Minecraft mc, String[] words) {
		Config c = Config.INSTANCE;
		int sum = mc.font.width(LyricsHud.styled(" ", c)) * (words.length - 1);
		for (String w : words) sum += mc.font.width(LyricsHud.styled(w, c));
		return sum;
	}

	/**
	 * Chooses where a floating (Aura) word goes: spots are tried from the middle of the view outward, and the best one is as
	 * central as possible, clear of the words you can see, whole on screen and inside the band around the crosshair. Each new
	 * word stands a little behind the ones before it. The prediction (see MovePredictor) moves everything to where you are
	 * heading. In the AFK camera the words stand beside the player instead.
	 */
	private Vec3 pick(Minecraft mc, String[] words) {
		Config c = Config.INSTANCE;
		var p = mc.player;
		Vec3 eye = AfkCamera.eye(mc);
		long now = System.currentTimeMillis();
		double fovH = hFov(mc), fovV = mc.options.fov().get();
		int side = (slotCursor++ % 2 == 0) ? -1 : 1; // AFK camera: words alternate left and right of the player
		double near = c.near, far = Math.max(near + 0.5, c.far);
		double width = pixelWidth(mc, words) * unit();
		boolean predict = MovePredictor.enabled();
		Vec3 base = predict ? MovePredictor.eyeIn(mc, c.predictSeconds) : eye;
		double yawOff = predict ? MovePredictor.yawOffset(mc, c.predictSeconds, Math.max(0, fovH / 2 - 8)) : 0;
		// words go back in the order they are sung: each one a little behind the farthest word still showing
		double behind = farthestWord(eye, now) + DEPTH_STEP;
		if (behind > far + DEPTH_OVERFLOW) behind = 0; // the row has gone far enough back: start in front again (the old words fade out in their own time)
		double minD = Math.max(near, behind), maxD = Math.min(Math.max(far, minD + 0.5), minD + DEPTH_STEP * 2);
		boolean afk = AfkCamera.active() && p != null;
		double s = Math.max(0, Math.min(100, c.auraSpread)) / 100.0;
		double maxH = fovH * spreadH(c.auraSpread), maxV = fovV * spreadV(c.auraSpread);

		// try spots from the middle of the view outward: the first ones close to the centre, later ones spread wider
		Vec3 best = null;
		double bestScore = -1e9;
		for (int i = 0; i < 48; i++) {
			double spread = (3 + i * 0.8) * (0.4 + s);
			double dx = yawOff + rng.nextGaussian() * spread, dy = rng.nextGaussian() * spread * 0.7;
			double dist = minD + rng.nextDouble() * (maxD - minD);
			Vec3 spot = afk ? besideThePlayer(eye, p, side, c)
				: base.add(dirOf(AfkCamera.yaw(mc) + dx, AfkCamera.pitch(mc) + dy).scale(dist));
			if (!afk && spot.distanceTo(eye) < minD) spot = eye.add(spot.subtract(eye).normalize().scale(minD)); // measured from where you are now

			// the size the word will be drawn at (see place), as seen from where you are now
			double d = Math.max(0.5, spot.distanceTo(eye));
			double grow = afk ? 1 : Math.max(1, d / far);
			double fit = Math.min(grow, 2 * d * Math.tan(Math.toRadians(fovH * 0.38)) / Math.max(width, 0.01));
			double spanH = Math.toDegrees(Math.atan(width * fit / (2 * d)));
			double spanV = Math.toDegrees(Math.atan(9 * unit() * fit / (2 * d)));

			// where it lands on screen, in degrees from the crosshair
			Vec3 to = spot.subtract(eye);
			double offH = MovePredictor.wrap(Math.toDegrees(Math.atan2(-to.x, to.z)) - AfkCamera.yaw(mc));
			double offV = Math.toDegrees(Math.atan2(-to.y, Math.hypot(to.x, to.z))) - AfkCamera.pitch(mc);

			double room = clearance(eye, spot, spanH, spanV, c.follow, now);
			double score = Math.min(room, 7) * 9 - (afk ? 0 : Math.hypot(offH - yawOff, offV) * (0.9 - 0.6 * s)); // less pull to the middle when spread wide
			// in this order: whole on screen, not on top of a word you can see, inside the spread area (a word wider than the
			// area may still sit in its middle)
			boolean onScreen = Math.abs(offH) + spanH <= fovH / 2 - EDGE_DEG && Math.abs(offV) + spanV <= fovV / 2 - EDGE_DEG;
			boolean inArea = Math.abs(offH) + spanH <= Math.max(maxH, spanH + 1) && Math.abs(offV) + spanV <= Math.max(maxV, spanV + 1);
			if (!afk && !onScreen) score -= 1000;
			if (room < 0) score -= 300;
			if (!afk && !inArea) score -= 200;
			if (coversPlayer(mc, eye, spot, spanH, spanV)) score -= 200;
			if (score > bestScore) {
				bestScore = score;
				best = spot;
			}
		}
		return best != null ? best : ahead(p, 0, (near + far) / 2);
	}


	/**
	 * A spot to the left (sign -1) or right (+1) of the player as the AFK camera sees them: mostly sideways,
	 * at most 30 degrees toward or away from the camera, at afkTextNear..afkTextFar blocks from the player.
	 */
	private Vec3 besideThePlayer(Vec3 eye, net.minecraft.world.entity.player.Player p, int sign, Config c) {
		Vec3 body = p.position().add(0, 0.9, 0);
		Vec3 axis = new Vec3(body.x - eye.x, 0, body.z - eye.z);
		if (axis.lengthSqr() < 0.01) axis = dirOf(AfkCamera.yaw(Minecraft.getInstance()), 0);
		axis = axis.normalize();
		Vec3 side = new Vec3(-axis.z, 0, axis.x);
		double lo = Math.min(c.afkTextNear, c.afkTextFar), hi = Math.max(c.afkTextNear, c.afkTextFar);
		double r = lo + rng.nextDouble() * (hi - lo);
		double theta = Math.toRadians((rng.nextDouble() - 0.5) * 60);
		Vec3 off = side.scale(sign * r * Math.cos(theta)).add(axis.scale(r * Math.sin(theta)));
		return body.add(off).add(0, (rng.nextDouble() - 0.35) * 1.6, 0);
	}

	/** Angular room (degrees) between a spot and the nearest visible word, capped at 7. */
	private double clearance(Vec3 eye, Vec3 spot, double spanH, double spanV, boolean followMe, long now) {
		double room = 7;
		for (Item it : items) {
			if (now > it.fadeAt) continue; // already on its way out
			Vec3 at = worldPos(it, followMe);
			double h = it.spanH, v = it.spanV;
			if (it.stick) { // its size is in blocks: as seen from here, in degrees
				double d = Math.max(0.5, at.distanceTo(eye));
				h = Math.toDegrees(Math.atan(h / d));
				v = Math.toDegrees(Math.atan(v / d));
			}
			room = Math.min(room, gap(eye, spot, spanH, spanV, at, h, v));
		}
		return room;
	}

	/**
	 * Free space in degrees between two words as seen from the eye (half sizes spanH / spanV): the larger of the sideways and
	 * the up-down gap, so words side by side or on top of each other both count as apart. Negative when they overlap.
	 */
	private static double gap(Vec3 eye, Vec3 a, double hA, double vA, Vec3 b, double hB, double vB) {
		Vec3 da = a.subtract(eye), db = b.subtract(eye);
		double yawA = Math.toDegrees(Math.atan2(-da.x, da.z)), yawB = Math.toDegrees(Math.atan2(-db.x, db.z));
		double pitchA = Math.toDegrees(Math.atan2(da.y, Math.hypot(da.x, da.z))), pitchB = Math.toDegrees(Math.atan2(db.y, Math.hypot(db.x, db.z)));
		double dYaw = Math.abs(((yawA - yawB) % 360 + 540) % 360 - 180);
		return Math.max(dYaw - (hA + hB) - WORD_MARGIN, Math.abs(pitchA - pitchB) - (vA + vB) - WORD_MARGIN);
	}

	/**
	 * Moves the spot toward the eye until the whole word (centre and its four edges) has a clear line of sight, so it is
	 * never hidden inside the ground or behind a wall.
	 */
	private Vec3 inFrontOfBlocks(Minecraft mc, Vec3 eye, Vec3 spot, String[] words) {
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
		double newLen = Math.max(0.8, len * ratio - 0.3);
		return eye.add(dir.scale(newLen / len));
	}

	/**
	 * True if nothing (terrain, trees, buildings) is between the eye and a sky word at this direction and distance: checked
	 * at its centre and its four edges (half sizes in degrees).
	 */
	private static boolean openSky(Minecraft mc, Vec3 eye, double yaw, double elev, double dist, double spanH, double spanV) {
		if (mc.level == null) return true;
		double[][] pts = {{0, 0}, {-spanH, 0}, {spanH, 0}, {0, spanV}, {0, -spanV}};
		for (double[] o : pts) {
			Vec3 to = eye.add(dirOf(yaw + o[0], -(elev + o[1])).scale(dist));
			if (mc.level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) return false;
		}
		return true;
	}

	/** Distance from the eye to the farthest floating word that is still showing (0 if none). Sky and stuck words do not count. */
	private double farthestWord(Vec3 eye, long now) {
		boolean fm = Config.INSTANCE.follow;
		double far = 0;
		for (Item it : items) if (!it.sky && !it.stick && now <= it.fadeAt) far = Math.max(far, worldPos(it, fm).distanceTo(eye));
		return far;
	}

	/** Makes a floating word at this spot, turned toward you; fitLimit shrinks it (a spot pulled in front of a wall). */
	private Item place(Minecraft mc, String[] words, long[] sung, long[] held, Vec3 spot, float fitLimit) {
		var p = mc.player;
		Vec3 eye = AfkCamera.eye(mc);
		double dist = Math.max(0.5, spot.distanceTo(eye));
		double width = pixelWidth(mc, words) * unit();
		// placed beyond the usual range (the prediction puts words far ahead while running or flying): grow with the distance
		double grow = AfkCamera.active() ? 1 : Math.max(1, dist / Math.max(1, Config.INSTANCE.far));
		float fit = (float) Math.min(fitLimit * grow, 2 * dist * Math.tan(Math.toRadians(hFov(mc) * 0.38)) / Math.max(width, 0.01));
		double spanH = Math.toDegrees(Math.atan(width * fit / (2 * dist)));
		double spanV = Math.toDegrees(Math.atan(9 * unit() * fit / (2 * dist)));

		// turned toward the player; single words are also knocked slightly off square
		Vec3 off = spot.subtract(eye);
		float k = words.length > 1 ? 0f : 1f;
		float yaw = (float) Math.toDegrees(Math.atan2(off.z, off.x)) - 90f + (rng.nextFloat() - 0.5f) * 44f * k;
		float pitch = (float) -Math.toDegrees(Math.atan2(off.y, Math.hypot(off.x, off.z)));
		float roll = (rng.nextFloat() - 0.5f) * 14f * k;
		if (words.length > 1) { // keep long lines square so their far end does not smear
			yaw = -AfkCamera.yaw(mc);
			pitch = 0;
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

				// sideways: whole word on screen with room for the slow cloud drift, mostly near the middle
				// looking down, the band lies below the horizon and only a strip at the top of the screen is left: then the whole
				// width of that strip is used, so the words do not pile up on each other
				boolean squeezed = viewElev + fovV * spreadV(c.skySpread) < SKY_MIN_ELEV + 2 * spanV;
				double wide = fovH / 2 - EDGE_DEG - SKY_DRIFT_DEG;
				double dxLimit = Math.max(0, (squeezed ? wide : Math.min(wide, fovH * spreadH(c.skySpread))) - spanH);
				double dx = squeezed ? (rng.nextDouble() * 2 - 1) * dxLimit : Math.max(-dxLimit, Math.min(dxLimit, rng.nextGaussian() * dxLimit * 0.6));
				// up and down, in this order: always above the horizon (below it the word would be inside the ground), whole
				// on screen (looking down, that means up at the top edge), and if both allow it in the band around the middle
				double band = Math.max(0, fovV * spreadV(c.skySpread) - spanV);
				double elev = viewElev + Math.max(-band, Math.min(band, band * 0.35 + rng.nextGaussian() * band * 0.45));
				double top = viewElev + fovV / 2 - spanV - EDGE_DEG, bottom = viewElev - fovV / 2 + spanV + EDGE_DEG;
				elev = Math.max(elev, bottom);
				elev = Math.min(elev, top);
				elev = Math.max(elev, SKY_MIN_ELEV + spanV); // above the horizon wins over everything else
				// in front of open sky, not of a mountain or a forest: go up step by step until nothing is in the way
				boolean open = openSky(mc, eye, viewYaw + dx, elev, dist, spanH, spanV);
				for (double up = elev + 3; !open && up <= Math.max(elev, top); up += 3) {
					if (openSky(mc, eye, viewYaw + dx, up, dist, spanH, spanV)) {
						elev = up;
						open = true;
					}
				}

				Vec3 spot = eye.add(dirOf(viewYaw + dx, -elev).scale(dist));
				double gap = skyGap(eye, spot, spanH, spanV);
				if (!open) gap -= 500; // terrain in front of it: only if there is no open sky anywhere
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

		double unitW = unitNow();
		double width = pixelWidth(mc, words) * unitW;
		double scale = width > 3.5 ? 3.5 / width : 1.0;
		double unit = unitW * scale;
		double halfW = width * scale / 2, halfH = 9 * unit / 2;

		Item best = null;
		double bestScore = -1e9;
		double yawOff = MovePredictor.enabled() ? MovePredictor.yawOffset(mc, c.predictSeconds, fovH * 0.3) : 0;
		for (int i = 0; i < 32; i++) {
			double dx = Math.max(-fovH * 0.42, Math.min(fovH * 0.42, yawOff + (rng.nextDouble() * 2 - 1) * fovH * (yawOff != 0 ? 0.2 : 0.38)));
			double dy = (rng.nextDouble() * 2 - 1) * fovV * 0.38;
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
			double score = Math.min(gap, 6) * 10 - Math.hypot(dx - yawOff, dy) * 0.05 + facing * 25;
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

}
