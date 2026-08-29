package dev.glassclient.mixin;

/**
 * TEMPLATE — not a real, compilable mixin yet.
 *
 * This is intentionally left as a fill-in-the-blank rather than a guessed
 * target, because Minecraft's internal class/method names and signatures
 * change between versions and I don't have a copy of the actual Mojang-
 * mapped jar to verify against in this environment. Faking a plausible-
 * looking target here would silently fail to compile (or worse, compile
 * against the wrong overload) rather than telling you clearly what's
 * missing.
 *
 * Steps to turn this into your first real mixin, once you have the
 * Mojang-mapped client jar (see client-mod/README.md):
 *
 *   1. Open the jar in a decompiler (Vineflower/CFR — Fabric Loom's
 *      `genSourcesWithVineflower` task does this automatically if you use
 *      Loom just to obtain the mapped jar for compiling against).
 *   2. Find the HUD render class — historically `net.minecraft.client.gui.Gui`
 *      — and the method that renders the F3-style / crosshair overlay each
 *      frame. Confirm the exact method name and parameter types for your
 *      target version.
 *   3. Replace the placeholders below with the real class/method/descriptor.
 *   4. Add "dev.glassclient.mixin.ExampleHudMixin" to the "mixins" array in
 *      src/main/resources/mixins.glassclient.json.
 *   5. Build and launch with the agent attached; confirm the injected log
 *      line prints once per frame — that's the whole pipeline (agent ->
 *      Mixin bootstrap -> config -> transform -> injected code runs)
 *      proven end to end. Everything after that is "just" adding more
 *      mixins.
 *
 * Example shape once filled in:
 *
 * <pre>{@code
 * @Mixin(Gui.class)
 * public class ExampleHudMixin {
 *     @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
 *     private void glassclient$onRenderHud(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
 *         // draw FPS counter, coords, etc. here
 *     }
 * }
 * }</pre>
 */
public final class ExampleHudMixin {
    private ExampleHudMixin() {}
}
