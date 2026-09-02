package dev.glassclient.mixin;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import dev.glassclient.GlassClientConfig;

/**
 * GlassClient's first real feature mixin, styled to match Lunar Client's
 * own HUD look: slightly smaller text on a semi-transparent black box,
 * rather than plain full-size text floating on nothing. Toggled via
 * {@link GlassClientConfig}, itself editable from
 * {@link dev.glassclient.gui.HudSettingsScreen} (Right Shift in-game).
 */
@Mixin(Gui.class)
public class HudOverlayMixin {

    private static final float SCALE = 0.75f;
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    // GuiGraphics colors are ARGB — a zero alpha byte silently draws
    // nothing at all (GuiGraphics.drawString/fill both bail out early on
    // ARGB.alpha(color) == 0), so these can't be plain 0xRRGGBB.
    private static final int BACKGROUND_COLOR = 0x90000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    @Inject(method = "render", at = @At("TAIL"))
    private void glassclient$onRenderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();

        if (GlassClientConfig.showFps()) {
            lines.add("GlassClient | " + mc.getFps() + " fps");
        }

        LocalPlayer player = mc.player;
        if (GlassClientConfig.showCoords() && player != null) {
            lines.add(String.format(
                "XYZ: %.1f / %.1f / %.1f",
                player.getX(),
                player.getY(),
                player.getZ()
            ));
        }

        HitResult hitResult = mc.hitResult;
        if (GlassClientConfig.showReach() && hitResult instanceof EntityHitResult entityHit && player != null) {
            double reach = player.getEyePosition().distanceTo(hitResult.getLocation());
            lines.add(String.format("Reach: %.2f (%s)", reach, entityHit.getEntity().getType().toShortString()));
        }

        if (lines.isEmpty()) {
            return;
        }

        Font font = mc.font;
        int lineHeight = font.lineHeight + 2;
        int contentWidth = 0;
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        int boxWidth = contentWidth + PADDING * 2;
        int boxHeight = lines.size() * lineHeight + PADDING * 2 - 2;

        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.scale(SCALE, SCALE);

        // Coordinates from here on are in the scaled matrix's space, so
        // divide the intended real screen margin by SCALE to land at the
        // same visual position regardless of scale factor.
        int boxX = (int) (MARGIN / SCALE);
        int boxY = (int) (MARGIN / SCALE);

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BACKGROUND_COLOR);

        int textY = boxY + PADDING;
        for (String line : lines) {
            guiGraphics.drawString(font, line, boxX + PADDING, textY, TEXT_COLOR);
            textY += lineHeight;
        }

        pose.popMatrix();
    }
}
