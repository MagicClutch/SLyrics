package dev.slyrics.mixin;

import dev.slyrics.AfkCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the AFK camera take over the camera position and rotation. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void slyrics$afkCamera(float partialTicks, CallbackInfo ci) {
		double[] pose = AfkCamera.pose(Minecraft.getInstance(), partialTicks);
		if (pose == null) return;
		this.setPosition(new Vec3(pose[0], pose[1], pose[2]));
		this.setRotation((float) pose[3], (float) pose[4]);
		this.detached = true;
	}
}
