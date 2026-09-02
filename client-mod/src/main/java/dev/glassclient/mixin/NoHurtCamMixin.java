package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;

import dev.glassclient.GlassClientConfig;

/**
 * No Hurt Cam — cancels the screen-tilt effect applied when taking damage.
 * GameRenderer.bobHurt() is that exact effect (rotates the view pose based
 * on LivingEntity.hurtTime/hurtDir/getHurtDir()) — cancelling the whole
 * method before it touches the pose stack leaves the camera untouched.
 */
@Mixin(GameRenderer.class)
public class NoHurtCamMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void glassclient$bobHurt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (GlassClientConfig.noHurtCam()) {
            ci.cancel();
        }
    }
}
