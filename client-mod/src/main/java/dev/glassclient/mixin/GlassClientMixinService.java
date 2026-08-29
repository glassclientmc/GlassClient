package dev.glassclient.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.ReEntranceLock;

/**
 * A minimal standalone Mixin service backed by the JVM's own
 * {@link java.lang.instrument.Instrumentation} API, rather than a custom
 * classloader (which is how Forge/Fabric/most reference implementations —
 * e.g. vectrix-space/ignite — do it). {@link GlassClientAgent} registers a
 * plain {@code ClassFileTransformer} that calls into
 * {@link #transform(String, byte[])} for every class the JVM loads;
 * mixin transformation happens there.
 *
 * Registered via META-INF/services/org.spongepowered.asm.service.IMixinService
 * so Mixin's own service discovery picks it up.
 */
public final class GlassClientMixinService
    implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {

  private static final IContainerHandle CONTAINER =
      new IContainerHandle() {
        @Override
        public String getAttribute(String name) {
          return null;
        }

        @Override
        public Collection<IContainerHandle> getNestedContainers() {
          return Collections.emptyList();
        }
      };

  /**
   * Set by the constructor when Mixin's own ServiceLoader instantiates this
   * class during bootstrap — the agent needs this same instance afterward
   * to feed it bytecode via {@link #transform(String, byte[])}.
   */
  public static volatile GlassClientMixinService INSTANCE;

  private final ReEntranceLock lock = new ReEntranceLock(1);
  private IMixinTransformer transformer;

  public GlassClientMixinService() {
    INSTANCE = this;
  }

  /** Diagnostic only — logs Mixin's own summary of what it thinks is loaded/applied. */
  public void auditNow() {
    if (this.transformer != null) {
      this.transformer.audit(MixinEnvironment.getCurrentEnvironment());
    }
  }

  /** Called by {@link GlassClientAgent}'s ClassFileTransformer for every loaded class. */
  public byte[] transform(String className, byte[] classBytes) {
    if (this.transformer == null) return classBytes;
    return this.transformer.transformClassBytes(className, className, classBytes);
  }

  // ---- IMixinService ----

  @Override
  public String getName() {
    return "GlassClient";
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void prepare() {}

  @Override
  public MixinEnvironment.Phase getInitialPhase() {
    return MixinEnvironment.Phase.PREINIT;
  }

  @Override
  public void offer(IMixinInternal internal) {
    if (internal instanceof IMixinTransformerFactory) {
      try {
        this.transformer = ((IMixinTransformerFactory) internal).createTransformer();
      } catch (MixinInitialisationError e) {
        throw new RuntimeException("Failed to create Mixin transformer", e);
      }
    }
  }

  @Override
  public void init() {}

  @Override
  public void beginPhase() {}

  @Override
  public void checkEnv(Object bootSource) {}

  @Override
  public String getSideName() {
    return Constants.SIDE_CLIENT;
  }

  @Override
  public ILogger getLogger(String name) {
    return new LoggerAdapterConsole(name);
  }

  @Override
  public ReEntranceLock getReEntranceLock() {
    return this.lock;
  }

  @Override
  public IClassProvider getClassProvider() {
    return this;
  }

  @Override
  public IClassBytecodeProvider getBytecodeProvider() {
    return this;
  }

  @Override
  public ITransformerProvider getTransformerProvider() {
    return this;
  }

  @Override
  public IClassTracker getClassTracker() {
    return this;
  }

  @Override
  public IMixinAuditTrail getAuditTrail() {
    return null;
  }

  @Override
  public Collection<String> getPlatformAgents() {
    return Collections.emptyList();
  }

  @Override
  public IContainerHandle getPrimaryContainer() {
    return CONTAINER;
  }

  @Override
  public Collection<IContainerHandle> getMixinContainers() {
    return Collections.emptyList();
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    return ClassLoader.getSystemClassLoader().getResourceAsStream(name);
  }

  @Override
  public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
    return MixinEnvironment.CompatibilityLevel.JAVA_8;
  }

  @Override
  public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
    return MixinEnvironment.CompatibilityLevel.JAVA_17;
  }

  // ---- IClassProvider ----

  @Override
  public URL[] getClassPath() {
    return new URL[0];
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    return Class.forName(name);
  }

  @Override
  public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
    return Class.forName(name, initialize, ClassLoader.getSystemClassLoader());
  }

  @Override
  public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
    return Class.forName(name, initialize, GlassClientMixinService.class.getClassLoader());
  }

  // ---- IClassBytecodeProvider ----

  @Override
  public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
    return getClassNode(name, true);
  }

  @Override
  public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
    String resourcePath = name.replace('.', '/') + ".class";
    try (InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) throw new ClassNotFoundException(name);
      ClassNode node = new ClassNode();
      new ClassReader(in).accept(node, 0);
      return node;
    }
  }

  // ---- ITransformerProvider ----

  @Override
  public Collection<ITransformer> getTransformers() {
    return Collections.emptyList();
  }

  @Override
  public Collection<ITransformer> getDelegatedTransformers() {
    return Collections.emptyList();
  }

  @Override
  public void addTransformerExclusion(String name) {}

  // ---- IClassTracker ----

  @Override
  public void registerInvalidClass(String name) {}

  @Override
  public boolean isClassLoaded(String name) {
    return false;
  }

  @Override
  public String getClassRestrictions(String name) {
    return "";
  }
}
