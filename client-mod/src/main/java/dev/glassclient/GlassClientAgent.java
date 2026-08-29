package dev.glassclient;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import dev.glassclient.mixin.GlassClientMixinService;

/**
 * Entry point attached to the JVM via `-javaagent:glassclient-mod-<version>-all.jar`
 * (set by the launcher's classpath builder). Runs before Minecraft's own
 * main() so our mixins are registered before any target class loads.
 *
 * STATUS (see client-mod/README.md "Known issue" section for the full
 * writeup): everything up through Mixin's bootstrap is proven working —
 * {@link GlassClientMixinService} (a custom IMixinService/IGlobalPropertyService
 * pair, since Forge/Fabric's built-in ones don't work standalone) gets
 * correctly selected via ServiceLoader, MixinBootstrap.init() succeeds with
 * no errors, and this class's ClassFileTransformer correctly receives every
 * loaded class. What's NOT yet working: the actual bytecode transformation
 * never fires — confirmed via debug instrumentation that Mixin never even
 * attempts to resolve the mixin/target classes, despite the config loading
 * without error. Leading suspect: MixinConfig.select() does a reference
 * equality check between environments (`this.env == environment`) that may
 * never hold true when driven by a from-scratch service outside Mixin's
 * usual Forge/Fabric-managed bootstrap sequence. Next step for whoever
 * picks this up: trace MixinProcessor.checkSelect() / MixinConfig.select()
 * against Mixin 0.8.5 source with a debugger attached, rather than guessing
 * further from the outside.
 */
public final class GlassClientAgent {

    private GlassClientAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        MixinBootstrap.init();
        MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("mixins.glassclient.json");

        GlassClientMixinService service = GlassClientMixinService.INSTANCE;
        if (service == null) {
            throw new IllegalStateException(
                "GlassClientMixinService.INSTANCE is null — Mixin didn't select our service. "
                    + "Check META-INF/services/org.spongepowered.asm.service.IMixinService is on the classpath."
            );
        }

        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer
            ) {
                if (className == null) return null;
                try {
                    return service.transform(className, classfileBuffer);
                } catch (Throwable t) {
                    System.err.println("[GlassClient] Failed to transform " + className + ": " + t);
                    return null;
                }
            }
        }, true);

        System.out.println("[GlassClient] mixins registered, handing off to Minecraft");
    }
}
