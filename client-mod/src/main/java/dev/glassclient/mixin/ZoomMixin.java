package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

import dev.glassclient.GlassClientInputTracker;

/**
 * Zoom — hold C (OptiFine/Lunar's own classic default) to narrow the FOV
 * for a zoomed-in view. Injects at the RETURN of GameRenderer.getFov and
 * divides whatever FOV the game already computed (options FOV, death
 * zoom-out, fluid FOV effect, etc. all still apply underneath) rather than
 * replacing it outright, so zoom composes correctly with everything else
 * that adjusts FOV instead of fighting it. Eases in/out via
 * GlassClientInputTracker's zoom progress rather than snapping instantly —
 * an instant jump looked choppy in testing.
 *
 * Also cancels GameRenderer.bobView() (the normal walk view-bob) while
 * zoomed — a narrow FOV massively amplifies how much visual movement the
 * same physical head-bob motion produces, which read as "choppy while
 * moving" in testing even after the FOV transition itself was smoothed.
 */
@Mixin(GameRenderer.class)
public class ZoomMixin {

    private static final float ZOOM_DIVISOR = 4.0F;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void glassclient$onGetFov(Camera camera, float partialTick, boolean useFov, CallbackInfoReturnable<Float> cir) {
        float progress = GlassClientInputTracker.updateAndGetZoomProgress();
        if (progress > 0f) {
            float divisor = 1f + (ZOOM_DIVISOR - 1f) * progress;
            cir.setReturnValue(cir.getReturnValue() / divisor);
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void glassclient$onBobView(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (GlassClientInputTracker.updateAndGetZoomProgress() > 0f) {
            ci.cancel();
        }
    }
}
