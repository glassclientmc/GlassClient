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
 * HUD elements on/off, nothing more sophisticated yet since there's only
 * two toggles to expose so far.
 */
public class HudSettingsScreen extends Screen {

    public HudSettingsScreen() {
        super(Component.literal("GlassClient Settings"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 40;

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show FPS"), this.font)
                .pos(centerX - 80, top)
                .selected(GlassClientConfig.showFps())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowFps(value))
                .build()
        );

        this.addRenderableWidget(
            Checkbox.builder(Component.literal("Show Coordinates"), this.font)
                .pos(centerX - 80, top + 24)
                .selected(GlassClientConfig.showCoords())
                .onValueChange((checkbox, value) -> GlassClientConfig.setShowCoords(value))
                .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(centerX - 40, top + 60, 80, 20)
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
