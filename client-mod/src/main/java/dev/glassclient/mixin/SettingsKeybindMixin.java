package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

import dev.glassclient.gui.HudSettingsScreen;

/**
 * Opens GlassClient's settings screen on Right Shift, same default key
 * Lunar Client uses for its own mod GUI. Only fires when no other screen is
 * already open (in-game, not already in a menu) and on key-down, not
 * key-up/repeat — GLFW_KEY_RIGHT_SHIFT = 344, GLFW_PRESS = 1, stable GLFW
 * constants (LWJGL ships the same values every version, safe to hardcode).
 */
@Mixin(KeyboardHandler.class)
public class SettingsKeybindMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void glassclient$onKeyPress(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (key == 344 && action == 1 && this.minecraft.screen == null) {
            this.minecraft.setScreen(new HudSettingsScreen());
        }
    }
}
