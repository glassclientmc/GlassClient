package dev.glassclient.mixin;

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

import dev.glassclient.GlassClientConfig;
import dev.glassclient.GlassClientInputTracker;

/**
 * Lunar-style keystrokes (WASD) + CPS widget, bottom-right corner so it
 * doesn't collide with {@link HudOverlayMixin}'s top-left FPS/coords box.
 * Same scaled-text-on-dark-box look, same {@link GlassClientConfig}
 * toggle pattern.
 */
@Mixin(Gui.class)
public class KeystrokesOverlayMixin {

    private static final float SCALE = 0.75f;
    private static final int MARGIN = 4;
    private static final int KEY_SIZE = 16;
    private static final int KEY_GAP = 2;
    private static final int BACKGROUND_COLOR = 0x90000000;
    private static final int KEY_UP_COLOR = 0x90202020;
    private static final int KEY_DOWN_COLOR = 0xC0568CFF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    @Inject(method = "render", at = @At("TAIL"))
    private void glassclient$onRenderKeystrokes(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!GlassClientConfig.showKeystrokes() && !GlassClientConfig.showCps()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.scale(SCALE, SCALE);

        // Bottom-right corner, in the scaled matrix's coordinate space —
        // same reasoning as HudOverlayMixin: divide real screen size/margin
        // by SCALE to land at the intended visual position.
        int scaledScreenWidth = (int) (guiGraphics.guiWidth() / SCALE);
        int scaledScreenHeight = (int) (guiGraphics.guiHeight() / SCALE);
        int marginScaled = (int) (MARGIN / SCALE);

        int cursorY = scaledScreenHeight - marginScaled;

        if (GlassClientConfig.showCps()) {
            String cpsLine = "LMB " + GlassClientInputTracker.leftCps() + "  RMB " + GlassClientInputTracker.rightCps();
            int boxWidth = font.width(cpsLine) + 8;
            int boxHeight = font.lineHeight + 6;
            int boxX = scaledScreenWidth - marginScaled - boxWidth;
            int boxY = cursorY - boxHeight;

            guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BACKGROUND_COLOR);
            guiGraphics.drawString(font, cpsLine, boxX + 4, boxY + 3, TEXT_COLOR);

            cursorY = boxY - KEY_GAP;
        }

        if (GlassClientConfig.showKeystrokes()) {
            int gridWidth = KEY_SIZE * 3 + KEY_GAP * 2;
            int gridHeight = KEY_SIZE * 2 + KEY_GAP;
            int gridX = scaledScreenWidth - marginScaled - gridWidth;
            int gridY = cursorY - gridHeight;

            int wX = gridX + KEY_SIZE + KEY_GAP;
            drawKey(guiGraphics, font, wX, gridY, "W", GlassClientInputTracker.isW());

            int row2Y = gridY + KEY_SIZE + KEY_GAP;
            drawKey(guiGraphics, font, gridX, row2Y, "A", GlassClientInputTracker.isA());
            drawKey(guiGraphics, font, gridX + KEY_SIZE + KEY_GAP, row2Y, "S", GlassClientInputTracker.isS());
            drawKey(guiGraphics, font, gridX + (KEY_SIZE + KEY_GAP) * 2, row2Y, "D", GlassClientInputTracker.isD());
        }

        pose.popMatrix();
    }

    private static void drawKey(GuiGraphics guiGraphics, Font font, int x, int y, String label, boolean down) {
        guiGraphics.fill(x, y, x + KEY_SIZE, y + KEY_SIZE, down ? KEY_DOWN_COLOR : KEY_UP_COLOR);
        int textX = x + (KEY_SIZE - font.width(label)) / 2;
        int textY = y + (KEY_SIZE - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, textX, textY, TEXT_COLOR);
    }
}
