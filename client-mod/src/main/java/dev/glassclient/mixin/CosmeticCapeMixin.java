package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

import dev.glassclient.cosmetic.GlassClientCapeLayer;

/**
 * Registers {@link GlassClientCapeLayer} into PlayerRenderer's layer list,
 * the same way vanilla registers its own CapeLayer/WingsLayer/etc — see
 * PlayerRenderer's real constructor (this.addLayer(new CapeLayer(...))
 * etc.) for the pattern this mirrors.
 *
 * First version tried @Shadow-ing addLayer without extending anything —
 * failed at runtime with a real InvalidMixinException: "@Shadow method
 * addLayer ... was not located in the target class
 * net.minecraft.client.renderer.entity.player.PlayerRenderer." addLayer is
 * declared on LivingEntityRenderer (PlayerRenderer's superclass), and
 * Mixin's @Shadow resolution only searches the exact target class, not its
 * ancestors — unlike @Inject targets, which do resolve across the
 * hierarchy. Extending LivingEntityRenderer here (compile-time only — Mixin
 * discards this class's own constructor/inheritance before merging into the
 * real PlayerRenderer, it's never actually instantiated as written) makes
 * addLayer resolve as a normal inherited method instead.
 */
@Mixin(PlayerRenderer.class)
public abstract class CosmeticCapeMixin
    extends LivingEntityRenderer<AbstractClientPlayer, PlayerRenderState, PlayerModel> {

    public CosmeticCapeMixin(EntityRendererProvider.Context context, PlayerModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void glassclient$onInit(EntityRendererProvider.Context context, boolean bl, CallbackInfo ci) {
        this.addLayer(new GlassClientCapeLayer(this, context.getModelSet()));
    }
}
