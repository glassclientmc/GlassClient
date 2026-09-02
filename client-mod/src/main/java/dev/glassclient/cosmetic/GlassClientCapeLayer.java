package dev.glassclient.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerCapeModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

import dev.glassclient.GlassClientConfig;

/**
 * GlassClient's own cosmetic cape — structured like vanilla's own CapeLayer
 * (net.minecraft.client.renderer.entity.layers.CapeLayer), reusing the same
 * PlayerCapeModel, but rendering unconditionally per
 * {@link GlassClientConfig#showCosmeticCape()} rather than gating on
 * Mojang cape ownership (playerRenderState.skin.capeTexture() != null),
 * since this is our own self-hosted cosmetic, not a real Mojang cape.
 */
public class GlassClientCapeLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    private final PlayerCapeModel<PlayerRenderState> model;

    public GlassClientCapeLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent, EntityModelSet modelSet) {
        super(parent);
        this.model = new PlayerCapeModel<>(modelSet.bakeLayer(ModelLayers.PLAYER_CAPE));
    }

    @Override
    public void render(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int light,
        PlayerRenderState playerRenderState,
        float yRot,
        float xRot
    ) {
        if (!GlassClientConfig.showCosmeticCape() || playerRenderState.isInvisible || !playerRenderState.showCape) {
            return;
        }

        GlassClientCapeTexture.update();

        poseStack.pushPose();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entitySolid(GlassClientCapeTexture.LOCATION));
        this.getParentModel().copyPropertiesTo(this.model);
        this.model.setupAnim(playerRenderState);
        this.model.renderToBuffer(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
