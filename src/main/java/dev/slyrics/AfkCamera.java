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
	private static CameraType saved = CameraType.FIRST_PERSON;
	private static float lastYaw, lastPitch;
	/** Pose the current blend starts from (previous shot or the player's own view). */
	private static Vec3 startPos;
	private static float startYaw, startPitch;
	private static boolean blendIn;

	private enum Shot { ORBIT, RISE, DESCEND, DIAGONAL, FLYBY, PUSH_IN, PULL_OUT }

	private static Shot shot = Shot.ORBIT;
	private static int shotIndex = -1;
	private static long shotStart;
	private static double shotAngle;
	private static int shotSide = 1;
	/** Fade-to-black between shots. */
	private static long fadeStart;
	private static boolean fading, fadeSwitched;

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
		var p = mc.player;
		startPos = p.getEyePosition();
		startYaw = p.getYRot();
		startPitch = p.getXRot();
		blendIn = true;
		fading = false;
		shotIndex = -1;
		saved = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		camPos = null;
		nextShot(now, p.getYRot(), false);
	}

	private static void stop(Minecraft mc) {
		active = false;
		camPos = null;
		fading = false;
		mc.options.setCameraType(saved);
	}

	/** 0..1 black overlay while fading between shots. */
	public static float fadeAlpha() {
		if (!active || !fading) return 0f;
		float t = (System.currentTimeMillis() - fadeStart) / 900f;
		if (t >= 1f) return 0f;
		return t < 0.5f ? t * 2f : (1f - t) * 2f;
	}

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static float lerpAngle(float a, float b, float t) {
		float d = ((b - a + 540f) % 360f) - 180f;
		return a + d * t;
	}

	private static java.util.List<Shot> enabledShots() {
		Config c = Config.INSTANCE;
		java.util.List<Shot> l = new java.util.ArrayList<>();
		if (c.afkOrbit) l.add(Shot.ORBIT);
		if (c.afkRise) l.add(Shot.RISE);
		if (c.afkDescend) l.add(Shot.DESCEND);
		if (c.afkDiagonal) l.add(Shot.DIAGONAL);
		if (c.afkFlyby) l.add(Shot.FLYBY);
		if (c.afkPushIn) l.add(Shot.PUSH_IN);
		if (c.afkPullOut) l.add(Shot.PULL_OUT);
		if (l.isEmpty()) l.add(Shot.ORBIT);
		return l;
	}

	private static final java.util.Random RNG = new java.util.Random();

	/** Starts the next shot. With glide=true the camera blends over from where it is now. */
	private static void nextShot(long now, float refYaw, boolean glide) {
		Config c = Config.INSTANCE;
		java.util.List<Shot> shots = enabledShots();
		if (c.afkRandomOrder && shots.size() > 1) {
			Shot pick;
			do {
				pick = shots.get(RNG.nextInt(shots.size()));
			} while (pick == shot && shotIndex >= 0);
			shot = pick;
		} else {
			shotIndex = (shotIndex + 1) % shots.size();
			shot = shots.get(shotIndex);
		}
		if (shotIndex < 0) shotIndex = 0;
		shotStart = now;
		shotAngle = Math.toRadians(c.afkRandomOrder || shotIndex == 0 ? RNG.nextInt(360) : refYaw + 90);
		shotSide = RNG.nextBoolean() ? 1 : -1;
		if (glide && camPos != null) {
			startPos = camPos;
			startYaw = camYaw;
			startPitch = camPitch;
			startedAt = now;
			blendIn = true;
		} else if (!glide) {
			blendIn = shot != null && startPos != null && startedAt == now;
		}
	}

	/** Offset of the camera from the player's eye for the current shot. u is progress 0..1, t is seconds into the shot. */
	private static Vec3 offset(double u, double t) {
		Config c = Config.INSTANCE;
		double r = c.afkRadius, h = c.afkHeight, e = smooth((float) u), a = shotAngle;
		switch (shot) {
			case RISE: {
				double ang = a + shotSide * Math.toRadians(25) * u, rad = r * lerp(0.9, 1.3, e);
				return new Vec3(Math.cos(ang) * rad, lerp(-0.9, h + r * 0.9, e), Math.sin(ang) * rad);
			}
			case DESCEND: {
				double ang = a + shotSide * Math.toRadians(25) * u, rad = r * lerp(1.3, 0.9, e);
				return new Vec3(Math.cos(ang) * rad, lerp(h + r * 0.9, -0.5, e), Math.sin(ang) * rad);
			}
			case DIAGONAL: {
				double ang = a + shotSide * Math.toRadians(80) * e, rad = r * lerp(1.2, 0.85, e);
				return new Vec3(Math.cos(ang) * rad, lerp(-0.7, h + r * 0.7, e), Math.sin(ang) * rad);
			}
			case FLYBY: {
				double along = lerp(-r * 1.4, r * 1.4, e) * shotSide;
				return new Vec3(Math.cos(a) * r * 0.9 - Math.sin(a) * along, h * 0.7 + 0.3 * Math.sin(u * Math.PI), Math.sin(a) * r * 0.9 + Math.cos(a) * along);
			}
			case PUSH_IN: {
				double rad = lerp(r * 2.0, r * 0.6, e);
				return new Vec3(Math.cos(a) * rad, lerp(h + 2, h * 0.6, e), Math.sin(a) * rad);
			}
			case PULL_OUT: {
				double rad = lerp(r * 0.6, r * 2.0, e);
				return new Vec3(Math.cos(a) * rad, lerp(h * 0.6, h + 2, e), Math.sin(a) * rad);
			}
			default: {
				double ang = a + Math.toRadians(c.afkSpeed) * t * shotSide;
				return new Vec3(Math.cos(ang) * r * (1 + 0.15 * Math.sin(t * 0.17 + 1)), h + 0.9 * Math.sin(t * 0.25), Math.sin(ang) * r * (1 + 0.15 * Math.sin(t * 0.17 + 1)));
			}
		}
	}

	/** Camera pose for this frame: {x, y, z, yaw, pitch}, or null when the normal camera is used. */
	public static double[] pose(Minecraft mc, float partialTicks) {
		if (!active || mc.player == null || mc.level == null) return null;
		Config c = Config.INSTANCE;
		var p = mc.player;
		long now = System.currentTimeMillis();

		double dur = Math.max(3, c.afkShotSeconds) * 1000.0;
		double u = Math.min(1.0, (now - shotStart) / dur);
		if (u >= 1.0) {
			if (c.afkFadeBetween) {
				if (!fading) {
					fading = true;
					fadeSwitched = false;
					fadeStart = now;
				}
			} else {
				nextShot(now, p.getYRot(), true);
				u = 0;
			}
		}
		if (fading) {
			float ft = (now - fadeStart) / 900f;
			if (!fadeSwitched && ft >= 0.5f) {
				fadeSwitched = true;
				startPos = null; // jump while the screen is black
				nextShot(now, p.getYRot(), false);
				blendIn = false;
				u = 0;
			}
			if (ft >= 1f) fading = false;
		}
		double t = (now - shotStart) / 1000.0;

		Vec3 target = p.getPosition(partialTicks).add(0, p.getEyeHeight(), 0);
		Vec3 desired = target.add(offset(u, t));

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

		Vec3 pos = desired;
		if (blendIn && startPos != null) {
			float k = smooth((now - startedAt) / 1500f);
			pos = startPos.add(desired.subtract(startPos).scale(k));
			yaw = lerpAngle(startYaw, yaw, k);
			pitch = startPitch + (pitch - startPitch) * k;
			if (k >= 1f) blendIn = false;
		}

		camPos = pos;
		camYaw = yaw;
		camPitch = pitch;
		return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
	}
}
