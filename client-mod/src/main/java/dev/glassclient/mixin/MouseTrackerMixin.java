package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import dev.glassclient.GlassClientInputTracker;

/**
 * Feeds left/right click events into {@link GlassClientInputTracker} for
 * the CPS HUD widget. onPress is private in MouseHandler — fine for a
 * Mixin injection target, visibility only matters for our own @Shadow
 * field access elsewhere, not for @Inject targets.
 */
@Mixin(MouseHandler.class)
public class MouseTrackerMixin {

    @Inject(method = "onPress", at = @At("HEAD"))
    private void glassclient$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        GlassClientInputTracker.onMouseButton(button, action);
    }
}
