# client-mod

The part of GlassClient that runs inside the Minecraft process. See the root
[ARCHITECTURE.md](../ARCHITECTURE.md) for the full picture — this is the
genuinely hard, multi-month part of the project.

## Status

**Builds and boots cleanly. Bytecode transformation doesn't fire yet — see
"Known issue" below.** This is real, hands-on-verified progress, not a
draft: JDK 21 + the bundled Gradle wrapper + Shadow (fat-jar) build all work,
and a from-scratch `-javaagent`-based Mixin bootstrap (no Forge, no Fabric)
successfully initializes with zero errors. What's still broken is the very
last step — actual mixin application.

Run it yourself:

```bash
JAVA_HOME="/path/to/jdk-21" ./gradlew shadowJar
java -javaagent:build/libs/glassclient-mod-0.1.0-all.jar -cp build/libs/glassclient-mod-0.1.0-all.jar YourMainClass
```

You'll see:

```
[HH:MM:SS] [mixin/INFO] SpongePowered MIXIN Subsystem Version=0.8.5 ... Service=GlassClient Env=CLIENT
[GlassClient] mixins registered, handing off to Minecraft
```

...and your program runs normally, but any `@Mixin` classes registered in
`mixins.glassclient.json` won't actually apply yet.

## Known issue: mixin application doesn't fire

Confirmed via debug instrumentation (temporarily added, since removed):

- `MixinBootstrap.init()` succeeds — our custom `GlassClientMixinService`
  (see below) gets correctly selected via `ServiceLoader`, `offer()` receives
  the `IMixinTransformerFactory`, and `createTransformer()` succeeds.
- The `ClassFileTransformer` registered in `GlassClientAgent` correctly
  receives every class the JVM loads, including a test target class.
- But `IMixinTransformer.transformClassBytes(name, name, bytes)` returns the
  bytes completely unchanged — and, critically, **it never calls back into
  our `IClassProvider.findClass()` or `IClassBytecodeProvider.getClassNode()`
  at all**, for either the mixin class or its target. That means Mixin isn't
  even attempting to resolve the mixin — something upstream of that decides
  there's nothing to do, silently.
- Tried and ruled out: forcing a PREINIT→DEFAULT phase transition (broke
  things worse — configs got orphaned in the old phase), moving the mixin
  between the config's `"client"` and unconditional `"mixins"` arrays (no
  difference, rules out side-filtering), passing dot-separated vs
  slash-separated class names to `transformClassBytes` (no difference).

**Leading suspect**, found by reading Mixin 0.8.5's actual source
(`MixinConfig.select()`):

```java
this.visited = true;
return this.env == environment;
```

`select()` — which is what actually promotes a loaded config into one that
gets applied — does a **reference-equality** check between the
`MixinEnvironment` instance captured when the config was registered and
whatever's "current" later, from `MixinProcessor.checkSelect()`. In a
Forge/Fabric-managed boot sequence this reference always matches because
their launch wrapper drives the whole phase lifecycle in a specific order;
driving Mixin from a bare `-javaagent` with a hand-written service may not
reproduce whatever sequencing makes that reference check hold true — or the
issue is elsewhere entirely and this is a red herring. Wasn't confirmed
either way before time ran out on this investigation session.

**Next step for whoever picks this up**: attach an actual Java debugger
(not print-statement debugging) to a breakpoint in
`MixinProcessor.checkSelect()` / `MixinConfig.select()` and step through
what's happening on the first `transformClassBytes` call. That will settle
in minutes what took hours to narrow down from the outside. Mixin 0.8.5
source: https://github.com/SpongePowered/Mixin/tree/0.8.5

## What's here

- `build.gradle.kts` — JDK 21 toolchain, SpongePowered Mixin 0.8.5 +
  annotation processor, and the **Shadow plugin** (produces
  `glassclient-mod-<version>-all.jar`, a fat jar bundling Mixin/ASM/Gson/
  Guava — required, since a plain jar can't run standalone as a
  `-javaagent`). A few non-obvious things it does, each commented inline:
  - Declares Gson/Guava/ASM on **both** `implementation` and
    `annotationProcessor` — Mixin's POM marks them provided/optional rather
    than transitive, so both the AP and the actual runtime classpath crash
    with `NoClassDefFoundError` without this.
  - `-AdisableTargetValidator=true` — relaxes compile-time member validation
    for mixin targets (we don't have a Mojang-mapped Minecraft jar yet, see
    below). Note: this does **not** let you skip having the target class
    available at compile time entirely — Mixin still needs to resolve the
    class itself, just not validate every member reference against it.
  - `mergeServiceFiles()` — Mixin's own jar ships its own
    `META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService`
    entry; without merging, Shadow's default resource handling can drop our
    own registration.
- `gradlew` / `gradlew.bat` / `gradle/` — the Gradle wrapper (Gradle 9.7.1),
  checked in so nobody else needs Gradle pre-installed.
- `src/main/java/dev/glassclient/GlassClientAgent.java` — the `-javaagent`
  entry point.
- `src/main/java/dev/glassclient/mixin/GlassClientMixinService.java` — a
  custom `IMixinService` + `IGlobalPropertyService` backed by the JVM's
  `Instrumentation` API, since Mixin's bundled implementations (`Blackboard`
  for LaunchWrapper/ModLauncher) reference those frameworks directly in
  their constructors and throw `NoClassDefFoundError` standalone. This is
  the piece that took the most work to get right — see the git history for
  everything that had to be worked around (annotation processor deps, the
  global property service, compatibility-level limits, etc.).
- `src/main/java/dev/glassclient/mixin/ExampleHudMixin.java` — a template,
  not a real mixin, since we don't have a Mojang-mapped Minecraft jar to
  compile a real one against yet. See the comments in that file.
- `src/main/resources/mixins.glassclient.json` — the mixin config. Empty
  `mixins` array until the transform issue above is fixed and a real mixin
  is added.

## Prerequisites to actually build a real Minecraft mixin

The Gradle/JDK/service-wiring side is done. What's still missing:

A **Mojang-mapped Minecraft client jar** to compile mixins against — not
something we can check into the repo (see [ARCHITECTURE.md](../ARCHITECTURE.md)
legal notes: never bundle Mojang's files). The practical way to get one:
temporarily create a throwaway Fabric mod project with
[Fabric Loom](https://fabricmc.net/wiki/tutorial:setup), run its
`genSources`/dev-jar tasks to get a Mojang-mapped, deobfuscated client jar
out of Loom's cache, then copy that jar into
`client-mod/libs/minecraft-<version>-mojmap.jar` and point
`build.gradle.kts`'s commented-out `compileOnly` line at it. You are only
borrowing Loom's remapping pipeline here — the actual GlassClient runtime
never uses Fabric loader. This only has to happen once per Minecraft version
targeted.

## First milestone

1. Fix the transform-doesn't-fire issue above (a debugger session, not more
   guessing, is the fastest path).
2. Once that works against the throwaway test setup (any plain Java class,
   no Minecraft needed to prove the pipeline), get the Mojang-mapped jar per
   above and write a real `ExampleHudMixin` targeting Minecraft's actual HUD
   render class, injecting one debug log line.
3. See it print once per frame when real Minecraft is launched with the
   agent attached. That's the whole pipeline proven end to end — every
   feature mod after that is "just" more mixins on top of a working
   pipeline.
