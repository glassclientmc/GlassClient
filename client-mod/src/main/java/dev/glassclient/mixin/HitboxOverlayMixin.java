package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import dev.glassclient.GlassClientConfig;

/**
 * PvP hitbox overlay — a wireframe outline around other living entities'
 * actual hitbox, the same category of info overlay Lunar/Badlion ship
 * (visual only, no automation). Uses ShapeRenderer.renderLineBox — the
 * exact same helper EntityRenderDispatcher's own vanilla F3+B hitbox
 * rendering uses internally (renderHitbox, further down this same class),
 * found by reading that code rather than guessing at box-outline geometry.
 * An earlier version used DebugRenderer.renderFilledBox (a solid tinted
 * box) — switched to a real wireframe outline per feedback after seeing it
 * in-game.
 *
 * Injects into EntityRenderDispatcher.render, a generic method
 * (<E extends Entity> void render(E, double, double, double, float,
 * PoseStack, MultiBufferSource, int)); the injected handler uses the
 * erased Entity parameter type, matching the real compiled descriptor.
 * x/y/z here are already camera-relative (confirmed by cross-referencing
 * how vanilla's own debug renderers consume the same convention) — no
 * separate camera-position lookup needed to line the box up correctly.
 *
 * Injects at TAIL, not HEAD: drawing the box before the entity's own model
 * renders let the box write to the depth buffer first, so the entity model
 * drawn afterward got partially depth-clipped wherever the box already
 * "claimed" that depth — visible in testing as chunks of the mob appearing
 * to vanish/see-through where the box overlapped it. Drawing the box after
 * the real model has already been rendered avoids that entirely.
 */
@Mixin(EntityRenderDispatcher.class)
public class HitboxOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void glassclient$onRenderEntity(
        Entity entity,
        double x,
        double y,
        double z,
        float yaw,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int light,
        CallbackInfo ci
    ) {
        if (!GlassClientConfig.showHitboxes()) {
            return;
        }
        if (!(entity instanceof LivingEntity) || entity == Minecraft.getInstance().player) {
            return;
        }

        float halfWidth = entity.getBbWidth() / 2f;
        float height = entity.getBbHeight();
        AABB box = new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        ShapeRenderer.renderLineBox(poseStack, lines, box, 1.0F, 0.2F, 0.2F, 1.0F);
    }
}
