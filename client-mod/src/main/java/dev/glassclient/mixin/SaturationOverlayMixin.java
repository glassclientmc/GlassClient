package dev.glassclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import dev.glassclient.GlassClientConfig;
import dev.glassclient.cosmetic.GlassClientHungerIconMask;

/**
 * Saturation display, Lunar-style — a thin gold outline drawn directly
 * over the real hunger icons, one icon per whole point of saturation plus
 * a half-outline for the remainder (e.g. 5.5 saturation = 5 fully
 * outlined icons + 1 half-outlined). Saturation is a real value the game
 * tracks internally (a buffer that delays hunger depletion) but never
 * shows in the vanilla UI.
 *
 * Traces the icon's actual drumstick silhouette (via
 * {@link GlassClientHungerIconMask}, a 1px-dilated edge ring built from
 * the real texture's alpha channel), not a bounding-box rectangle — a
 * first version drew a plain rectangular border and it looked wrong per
 * feedback after seeing it in-game.
 *
 * Injects into Gui.renderFood itself (private, but @Inject doesn't care
 * about visibility — only @Shadow does) at TAIL, reusing its exact i/j
 * params to replicate vanilla's own per-icon position formula
 * (x = j - l*8 - 9, y = i, read directly from that method's source)
 * rather than recomputing icon layout independently and risking drift
 * from whatever vanilla actually does.
 *
 * Displayed value eases toward the real saturation rather than snapping
 * to it instantly — the real value almost always moves in whole-integer
 * steps (exhaustion depletes saturation by exactly 1.0 per threshold
 * crossing, and gains get capped to the current, always-whole-integer
 * food level via min(saturation + gain, foodLevel)), so it very rarely
 * actually passes through a real .5 value on its own. Without easing, a
 * full icon's outline would just disappear in one frame instead of
 * visibly draining through the half state — same wall-clock-time-based
 * eased-progress technique as GlassClientInputTracker's zoom transition.
 */
@Mixin(Gui.class)
public class SaturationOverlayMixin {

    private static final int OUTLINE_COLOR = 0xFFFFD700; // gold
    // Gains (eating) ease in much faster than depletion eases out — eating
    // is an instant, deliberate action, so a slow rise reads as sluggish
    // in a way slow depletion doesn't.
    private static final float RISE_SPEED = 10.0f; // saturation units/sec
    private static final float FALL_SPEED = 3.0f; // saturation units/sec
    private static float displayedSaturation = -1f;
    private static long lastUpdateMs = System.currentTimeMillis();
    private static float glassclient$debugLastRaw = -1f;

    @Inject(method = "renderFood", at = @At("TAIL"))
    private void glassclient$onRenderFood(GuiGraphics guiGraphics, Player player, int i, int j, CallbackInfo ci) {
        if (!GlassClientConfig.showSaturation()) {
            return;
        }

        GlassClientHungerIconMask.ensureLoaded();
        if (!GlassClientHungerIconMask.isLoaded()) {
            return;
        }

        float rawSaturation = player.getFoodData().getSaturationLevel();
        float saturation = updateAndGetDisplayedSaturation(rawSaturation);
        if (rawSaturation != glassclient$debugLastRaw) {
            System.out.println("[GlassClient][DEBUG] raw=" + rawSaturation + " displayed=" + saturation
                + " food=" + player.getFoodData().getFoodLevel());
            glassclient$debugLastRaw = rawSaturation;
        }
        int maskWidth = GlassClientHungerIconMask.width();
        int maskHeight = GlassClientHungerIconMask.height();

        for (int l = 0; l < 10; l++) {
            int x = j - l * 8 - 9;
            int y = i;

            if (saturation >= l + 1) {
                drawRing(guiGraphics, x, y, maskHeight, 0, maskWidth);
            } else if (saturation >= l + 0.5F) {
                // Half-fills the icon's own right side first, then the
                // left as it approaches a full point — per feedback, the
                // opposite of the first version (which filled left-first).
                int halfWidth = maskWidth / 2 + 1;
                drawRing(guiGraphics, x, y, maskHeight, maskWidth - halfWidth, maskWidth);
            }
        }
    }

    private static float updateAndGetDisplayedSaturation(float realSaturation) {
        if (displayedSaturation < 0f) {
            displayedSaturation = realSaturation; // first frame, no prior value to ease from
        }

        long now = System.currentTimeMillis();
        float dt = (now - lastUpdateMs) / 1000f;
        lastUpdateMs = now;

        if (displayedSaturation < realSaturation) {
            displayedSaturation = Math.min(realSaturation, displayedSaturation + RISE_SPEED * dt);
        } else if (displayedSaturation > realSaturation) {
            displayedSaturation = Math.max(realSaturation, displayedSaturation - FALL_SPEED * dt);
        }
        return displayedSaturation;
    }

    private static void drawRing(GuiGraphics guiGraphics, int x, int y, int maskHeight, int fromX, int toX) {
        for (int my = 0; my < maskHeight; my++) {
            for (int mx = fromX; mx < toX; mx++) {
                if (GlassClientHungerIconMask.isRing(mx, my)) {
                    guiGraphics.fill(x + mx, y + my, x + mx + 1, y + my + 1, OUTLINE_COLOR);
                }
            }
        }
    }
}
