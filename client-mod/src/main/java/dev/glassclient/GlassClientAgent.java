package dev.glassclient;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.GlassClientEnvironmentBridge;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import dev.glassclient.mixin.GlassClientMixinService;

/**
 * Entry point attached to the JVM via `-javaagent:glassclient-mod-<version>-all.jar`
 * (set by the launcher's classpath builder). Runs before Minecraft's own
 * main() so our mixins are registered before any target class loads.
 *
 * FIXED (see client-mod/README.md for the full writeup, including two dead
 * ends along the way): the pipeline through Mixin's bootstrap was already
 * correct — {@link GlassClientMixinService} (a custom IMixinService/
 * IGlobalPropertyService pair, since Forge/Fabric's built-in ones don't work
 * standalone) gets correctly selected via ServiceLoader, and
 * MixinBootstrap.init() succeeds with no errors. Two real bugs combined to
 * make the actual transform silently do nothing:
 * 1. The Mixin environment never transitioned from Phase.PREINIT to
 *    Phase.DEFAULT (MixinConfig.select()'s reference-equality check between
 *    environments always failed as a result), fixed via
 *    {@link GlassClientEnvironmentBridge}.
 * 2. {@link GlassClientMixinService#transform} was passing the JVM-internal
 *    slash-separated class name straight through to
 *    IMixinTransformer.transformClassBytes's `transformedName` parameter,
 *    which Mixin's target matching expects in dotted form — so no mixin
 *    target string could ever match, independent of bug 1.
 */
public final class GlassClientAgent {

    private GlassClientAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        MixinBootstrap.init();
        MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("mixins.glassclient.json");
        GlassClientEnvironmentBridge.gotoDefaultPhase();

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
