package dev.slyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * AFK mode: after a configurable idle time the camera leaves first person and slowly orbits the
 * player like a cinematic shot. Any input brings the normal camera back.
 */
public final class AfkCamera {
	private static boolean active;
	private static long lastActivity = System.currentTimeMillis();
	private static long startedAt;
	private static long lastNs;
	private static double angle;
	private static CameraType saved = CameraType.FIRST_PERSON;
	private static float lastYaw, lastPitch;
	private static Vec3 startPos;
	private static float startYaw, startPitch;

	/** Where the camera is right now (valid while active). */
	private static Vec3 camPos;
	private static float camYaw, camPitch;

	private AfkCamera() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AfkCamera::tick);
	}

	public static boolean active() {
		return active && camPos != null;
	}

	/** Eye position of whatever the player is currently looking through. */
	public static Vec3 eye(Minecraft mc) {
		return active() ? camPos : mc.player.getEyePosition();
	}

	public static float yaw(Minecraft mc) {
		return active() ? camYaw : mc.player.getYRot();
	}

	public static float pitch(Minecraft mc) {
		return active() ? camPitch : mc.player.getXRot();
	}

	private static boolean userActive(Minecraft mc) {
		var o = mc.options;
		var p = mc.player;
		boolean keys = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown() || o.keyJump.isDown()
			|| o.keyShift.isDown() || o.keySprint.isDown() || o.keyAttack.isDown() || o.keyUse.isDown();
		boolean look = Math.abs(p.getYRot() - lastYaw) > 0.01f || Math.abs(p.getXRot() - lastPitch) > 0.01f;
		lastYaw = p.getYRot();
		lastPitch = p.getXRot();
		return keys || look || mc.gui.screen() != null;
	}

	private static void tick(Minecraft mc) {
		Config c = Config.INSTANCE;
		long now = System.currentTimeMillis();
		if (mc.player == null) {
			if (active) stop(mc);
			lastActivity = now;
			return;
		}
		boolean busy = userActive(mc);
		if (!c.afkEnabled) {
			if (active) stop(mc);
			lastActivity = now;
			return;
		}
		if (busy) {
			lastActivity = now;
			if (active) stop(mc);
			return;
		}
		if (!active && now - lastActivity >= c.afkSeconds * 1000L && (!c.afkOnlyWhilePlaying || Spotify.playing)) start(mc, now);
	}

	private static void start(Minecraft mc, long now) {
		active = true;
		startedAt = now;
		lastNs = System.nanoTime();
		var p = mc.player;
		startPos = p.getEyePosition();
		startYaw = p.getYRot();
		startPitch = p.getXRot();
		// begin the orbit behind the player so the first frame is close to the old view
		angle = Math.toRadians(startYaw + 90);
		saved = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		camPos = null;
	}

	private static void stop(Minecraft mc) {
		active = false;
		camPos = null;
		mc.options.setCameraType(saved);
	}

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	private static float lerpAngle(float a, float b, float t) {
		float d = ((b - a + 540f) % 360f) - 180f;
		return a + d * t;
	}

	/** Camera pose for this frame: {x, y, z, yaw, pitch}, or null when the normal camera is used. */
	public static double[] pose(Minecraft mc, float partialTicks) {
		if (!active || mc.player == null || mc.level == null) return null;
		Config c = Config.INSTANCE;
		var p = mc.player;
		long now = System.currentTimeMillis();
		long ns = System.nanoTime();
		double dt = Math.max(0.0, Math.min(0.1, (ns - lastNs) / 1e9));
		lastNs = ns;
		angle += Math.toRadians(c.afkSpeed) * dt;
		double t = (now - startedAt) / 1000.0;

		Vec3 target = p.getPosition(partialTicks).add(0, p.getEyeHeight(), 0);
		double radius = c.afkRadius * (1.0 + 0.15 * Math.sin(t * 0.17 + 1.0));
		double height = c.afkHeight + 0.9 * Math.sin(t * 0.25);
		Vec3 desired = target.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

		// do not let the camera go through blocks
		var hit = mc.level.clip(new ClipContext(target, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 d = hit.getLocation().subtract(target);
			double len = d.length();
			if (len > 0.01) desired = target.add(d.scale(Math.max(0.3, len - 0.3) / len));
		}

		Vec3 look = target.add(0, -0.25, 0).subtract(desired);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));

		// ease from the first-person view into the orbit
		float k = smooth((now - startedAt) / 1500f);
		Vec3 pos = startPos.add(desired.subtract(startPos).scale(k));
		yaw = lerpAngle(startYaw, yaw, k);
		pitch = startPitch + (pitch - startPitch) * k;

		camPos = pos;
		camYaw = yaw;
		camPitch = pitch;
		return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
	}
}
