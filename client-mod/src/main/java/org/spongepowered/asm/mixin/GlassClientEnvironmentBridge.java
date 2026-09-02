package org.spongepowered.asm.mixin;

/**
 * Lives in Mixin's own package on purpose: {@link MixinEnvironment#gotoPhase}
 * is package-private, since Mixin expects only Forge/Fabric's own launch
 * plugins (which live inside org.spongepowered.asm.* and get to call it
 * directly) to drive phase transitions. We're not going through either of
 * those, so this class exists purely to stand in the same package and
 * forward the call. Safe: at runtime everything (this class and Mixin's own)
 * loads through the same classloader (bundled into one shadow jar), so the
 * package-private access check holds exactly the way it would for a real
 * Mixin-internal caller.
 *
 * See client-mod/README.md for the full writeup of why this was needed:
 * MixinPlatformManager.inject() (the public, "sanctioned" entry point) turns
 * out to be a no-op without a full Forge/Fabric-style mod-container/agent
 * setup — it never calls gotoPhase itself. This bridges directly to the
 * actual phase transition instead of trying to emulate that container model.
 */
public final class GlassClientEnvironmentBridge {

    private GlassClientEnvironmentBridge() {}

    public static void gotoDefaultPhase() {
        MixinEnvironment.gotoPhase(MixinEnvironment.Phase.DEFAULT);
    }
}
