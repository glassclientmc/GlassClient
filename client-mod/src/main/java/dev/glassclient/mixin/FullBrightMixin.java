package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;

import dev.glassclient.GlassClientConfig;

/**
 * Full Bright — overrides the gamma value LightTexture.updateLightTexture()
 * reads when computing the lightmap, the same approach Lunar Client itself
 * actually uses ("sets gamma to 1000%"), not vanilla's normal 0-1 slider
 * range.
 *
 * First version instead forced DimensionSpecialEffects.forceBrightLightmap()
 * — technically the mechanism vanilla itself uses for always-bright
 * dimensions, but it only feeds one boolean into the lightmap shader's
 * broader blend of ambient light / sky darken / darkness effect / gamma,
 * and didn't look right in testing (confirmed "working weirdly" once
 * actually tried). Overriding gamma directly is what actually dominates
 * the shader's brightness output.
 *
 * @Redirect is scoped to updateLightTexture specifically (the `method =`
 * attribute), but OptionInstance.get() is a generic method used by every
 * option, and that same method also calls darknessEffectScale().get() —
 * so this checks the redirected instance really is the gamma option
 * (reference-compared against Minecraft's own options.gamma()) before
 * overriding, rather than assuming ordinal/position would reliably single
 * out the right call.
 */
@Mixin(LightTexture.class)
public class FullBrightMixin {

    @Redirect(
        method = "updateLightTexture",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;")
    )
    private Object glassclient$overrideGamma(OptionInstance<?> instance) {
        if (GlassClientConfig.fullBright() && instance == Minecraft.getInstance().options.gamma()) {
            return 1000.0; // matches Lunar's own "1000%" full-bright gamma value
        }
        return instance.get();
    }
}
