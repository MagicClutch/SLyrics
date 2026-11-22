package dev.slyrics;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Guesses where the player will be, and which way they will be looking, a moment from now: from how fast they are moving and
 * how fast they are turning (both smoothed, so a single jitter does not throw the words around). LyricScene uses it to put
 * the next word on the path ahead instead of where the crosshair is right now. Runs on the render thread.
 */
public final class MovePredictor {
	/** Below this speed (blocks per second) the player counts as standing still: the words stay in the view as usual. */
	private static final double MIN_SPEED = 1.0;
	/** How hard the words are pulled toward the walking direction (1 = fully, 0 = only the look direction). */
	private static final double MOVE_PULL = 0.6;

	private static Vec3 lastPos;
	private static float lastYaw;
	private static long lastNs;
	/** Smoothed velocity in blocks per second, and turning speed in degrees per second. */
	private static Vec3 vel = Vec3.ZERO;
	private static double yawRate;

	private MovePredictor() {}

	public static boolean enabled() {
		return !AfkCamera.active(); // the AFK camera has its own idea of where words go
	}

	/** Called every frame. */
	public static void update(Minecraft mc) {
		if (mc.player == null) {
			lastPos = null;
			return;
		}
		long ns = System.nanoTime();
		Vec3 pos = mc.player.position();
		float yaw = mc.player.getYRot();
		if (lastPos == null || pos.distanceToSqr(lastPos) > 100) { // first frame or a teleport
			lastPos = pos;
			lastYaw = yaw;
			lastNs = ns;
			vel = Vec3.ZERO;
			yawRate = 0;
			return;
		}
		double dt = (ns - lastNs) / 1e9;
		if (dt < 0.005) return; // same tick on a fast frame: wait for a real step
		dt = Math.min(dt, 0.25);
		Vec3 v = pos.subtract(lastPos).scale(1 / dt);
		double turn = wrap(yaw - lastYaw) / dt;
		double k = 1 - Math.exp(-dt * 5); // about 0.2 s of smoothing
		vel = vel.add(v.subtract(vel).scale(k));
		yawRate += (turn - yawRate) * k;
		lastPos = pos;
		lastYaw = yaw;
		lastNs = ns;
	}

	/**
	 * The eye position in {@code seconds} from now, if the player keeps moving like this. On foot only sideways and forward
	 * (jumps and falls should not fling the words up or down); while flying (elytra, creative) along the whole 3D path, so the
	 * words also wait ahead when diving or climbing.
	 */
	public static Vec3 eyeIn(Minecraft mc, double seconds) {
		Vec3 eye = AfkCamera.eye(mc);
		if (flying(mc)) return eye.add(vel.scale(seconds));
		double vy = Math.max(-2, Math.min(2, vel.y));
		return eye.add(vel.x * seconds, vy * seconds * 0.5, vel.z * seconds);
	}

	/** Gliding with an elytra or flying in creative. */
	public static boolean flying(Minecraft mc) {
		var p = mc.player;
		return p != null && (p.isFallFlying() || p.getAbilities().flying);
	}

	/** How far ahead (blocks) the prediction can place words right now: the distance covered in {@code seconds}. */
	public static double reachAhead(double seconds) {
		return vel.length() * seconds;
	}

	/**
	 * How many degrees to the side of the current look direction the next word should go, so it lands where the player is
	 * heading: the turning carries on for {@code seconds}, and when walking the words lean toward the walking direction.
	 * Capped at {@code limit} so the word always stays inside the view.
	 */
	public static double yawOffset(Minecraft mc, double seconds, double limit) {
		double off = yawRate * seconds;
		double speed = Math.hypot(vel.x, vel.z);
		if (speed > MIN_SPEED) {
			double moveYaw = Math.toDegrees(Math.atan2(-vel.x, vel.z)); // same convention as Minecraft's yaw
			double diff = wrap(moveYaw - AfkCamera.yaw(mc));
			if (Math.abs(diff) < 100) off += diff * MOVE_PULL * Math.min(1, (speed - MIN_SPEED) / 3); // walking backwards: keep it in view
		}
		return Math.max(-limit, Math.min(limit, off));
	}

	public static double speed() {
		return Math.hypot(vel.x, vel.z);
	}

	/** An angle in degrees brought to -180..180. */
	static double wrap(double deg) {
		deg %= 360;
		if (deg > 180) deg -= 360;
		if (deg < -180) deg += 360;
		return deg;
	}
}
