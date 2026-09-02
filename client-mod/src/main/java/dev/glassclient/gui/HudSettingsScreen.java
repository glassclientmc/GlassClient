package dev.glassclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.glassclient.GlassClientConfig;

/**
 * GlassClient's mod settings screen — opened via Right Shift (same default
 * key Lunar Client uses), same idea as Lunar's own mod-config GUI: toggle
 * HUD elements on/off.
 */
public class HudSettingsScreen extends Screen {

    public HudSettingsScreen() {
        super(Component.literal("GlassClient Settings"));
    }

    // Left margin from the screen edge, not centered — a fixed column of
    // toggles reads more like a real settings panel than centered floating
    // checkboxes. Tighter row spacing (18 vs the original 24) and a
    // shorter checkbox label width both read as "smaller" without risking
    // scaling the actual interactive widgets (their click bounds are in
    // unscaled screen space — visually shrinking them via a matrix scale
    // without also correcting hit-testing would misalign clicks).
    private static final int LEFT_MARGIN = 20;
    private static final int ROW_HEIGHT = 18;
    private static final int TITLE_Y = 16;
    private static final int FIRST_ROW_Y = TITLE_Y + 24;

    @Override
    protected void init() {
        int x = LEFT_MARGIN;
        int y = FIRST_ROW_Y;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show FPS"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showFps())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowFps(value))
                .build()
        );
        y += ROW_HEIGHT;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show Coordinates"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showCoords())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowCoords(value))
                .build()
        );
        y += ROW_HEIGHT;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show Keystrokes"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showKeystrokes())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowKeystrokes(value))
                .build()
        );
        y += ROW_HEIGHT;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show CPS"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showCps())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowCps(value))
                .build()
        );
        y += ROW_HEIGHT;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show Hitboxes"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showHitboxes())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowHitboxes(value))
                .build()
        );
        y += ROW_HEIGHT;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show Reach"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showReach())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowReach(value))
                .build()
        );
        y += ROW_HEIGHT;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show GlassClient Cape"), this.font)
                .pos(x, y)
                .selected(GlassClientConfig.showCosmeticCape())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowCosmeticCape(value))
                .build()
        );
        y += ROW_HEIGHT + 10;

        this.addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(x, y, 60, 16)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Not calling this.renderBackground(...): in 1.21.8 that triggers
        // Screen's blurred-menu-background path
        // (Screen.renderBlurredBackground -> GuiGraphics.blurBeforeThisStratum),
        // which threw "IllegalStateException: Can only blur once per frame"
        // in practice — confirmed via a real crash report — because
        // something else in the render pipeline already blurs once that
        // same frame. A plain dim fill avoids the conflict entirely and
        // looks close enough for a small toggle screen.
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawString(this.font, this.title, LEFT_MARGIN, TITLE_Y, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
