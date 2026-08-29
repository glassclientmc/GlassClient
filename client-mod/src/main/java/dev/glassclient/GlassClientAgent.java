package dev.glassclient;

import java.lang.instrument.Instrumentation;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Entry point attached to the JVM via `-javaagent:glassclient-mod.jar`
 * (set by the launcher, see launcher/src/main/services — launch command
 * builder, not yet implemented). Runs before Minecraft's own main() so our
 * mixins are registered before any target class loads.
 *
 * UNVERIFIED: written against the standalone Mixin bootstrap API by hand in
 * an environment without JDK 21 or Gradle installed, so it hasn't been
 * compiled or run yet. Treat this as a first draft — the first real task
 * once you have a working JDK 21 + Gradle setup is getting this to compile
 * and confirming MixinBootstrap actually initializes cleanly outside of a
 * Forge/Fabric-managed class loader (standalone Mixin needs its own
 * IMixinTransformerFactory / service wiring — check the current Mixin docs,
 * this is the part most likely to need adjustment).
 */
public final class GlassClientAgent {

    private GlassClientAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        MixinBootstrap.init();

        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);

        Mixins.addConfiguration("mixins.glassclient.json");

        System.out.println("[GlassClient] mixins registered, handing off to Minecraft");
    }
}
