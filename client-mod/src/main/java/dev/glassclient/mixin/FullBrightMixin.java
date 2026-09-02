package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.DimensionSpecialEffects;

import dev.glassclient.GlassClientConfig;

/**
 * Full Bright — forces every dimension to render at max lightmap
 * brightness. Reuses DimensionSpecialEffects.forceBrightLightmap(), the
 * exact mechanism vanilla itself uses for always-bright dimensions (its
 * per-dimension subclasses set this in their own constructors) — found by
 * tracing LightTexture.updateLightTexture()'s actual light calculation
 * back to where "bl = clientLevel.effects().forceBrightLightmap()" feeds
 * into it, rather than fighting the lightmap shader math directly.
 */
@Mixin(DimensionSpecialEffects.class)
public class FullBrightMixin {

    @Inject(method = "forceBrightLightmap", at = @At("HEAD"), cancellable = true)
    private void glassclient$forceBrightLightmap(CallbackInfoReturnable<Boolean> cir) {
        if (GlassClientConfig.fullBright()) {
            cir.setReturnValue(true);
        }
    }
}
