package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.MouseHandler;

import dev.glassclient.GlassClientInputTracker;

/**
 * Scales down turn sensitivity while zoomed (Zoom, see ZoomMixin) — without
 * this, normal mouse sensitivity at 4x FOV zoom feels wildly twitchy (tiny
 * physical mouse movement swings the now-much-narrower view a lot), which
 * reads as "choppy" the same way an un-eased FOV jump does. Targets the
 * single `this.minecraft.player.turn(j, k * l)` call inside
 * MouseHandler.turnPlayer() specifically — not Entity.turn() directly,
 * which is also called for AI-driven mob rotation and would be wrong to
 * scale.
 */
@Mixin(MouseHandler.class)
public class ZoomSensitivityMixin {

    @ModifyArgs(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;turn(DD)V")
    )
    private void glassclient$scaleTurnForZoom(Args args) {
        float progress = GlassClientInputTracker.updateAndGetZoomProgress();
        if (progress <= 0f) {
            return;
        }
        double scale = 1.0 - progress * 0.96; // slows to 1/25 speed at full zoom
        double yaw = args.get(0);
        double pitch = args.get(1);
        args.set(0, yaw * scale);
        args.set(1, pitch * scale);
    }
}
