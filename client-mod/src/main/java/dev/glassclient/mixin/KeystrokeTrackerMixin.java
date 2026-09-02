package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;

import dev.glassclient.GlassClientInputTracker;

/**
 * Feeds W/A/S/D state into {@link GlassClientInputTracker} for the
 * keystrokes HUD widget. Separate from {@link SettingsKeybindMixin} (also
 * targets keyPress) so each mixin has one clear job — Mixin merges multiple
 * mixins on the same target method fine.
 */
@Mixin(KeyboardHandler.class)
public class KeystrokeTrackerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void glassclient$onKeyPress(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        GlassClientInputTracker.onKey(key, action);
    }
}
