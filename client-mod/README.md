# client-mod

The part of GlassClient that runs inside the Minecraft process. See the root
[ARCHITECTURE.md](../ARCHITECTURE.md) for the full picture — this is the
genuinely hard, multi-month part of the project. Treat everything here as a
first draft: **it has not been compiled or run**, because this machine only
has Java 8 and no Gradle installed. Nothing below is verified yet.

## Prerequisites to actually build this

1. **JDK 21** (or 17, matching whichever Minecraft version you target —
   adjust `build.gradle.kts` if you pick an older one). The machine this was
   scaffolded on only has Java 8, which is far too old for modern Minecraft
   or this project.
2. **Gradle** (or use a Gradle wrapper — not generated yet, run
   `gradle wrapper` once Gradle is installed to add one).
3. A **Mojang-mapped Minecraft client jar** to compile mixins against — this
   is not something we can check into the repo (see
   [ARCHITECTURE.md](../ARCHITECTURE.md) legal notes: never bundle Mojang's
   files). The practical way to get one:
   - Easiest: temporarily create a throwaway Fabric mod project with
     [Fabric Loom](https://fabricmc.net/wiki/tutorial:setup), run its
     `genSources`/dev-jar tasks to get a Mojang-mapped, deobfuscated client
     jar out of Loom's cache, then copy that jar into
     `client-mod/libs/minecraft-<version>-mojmap.jar` and point
     `build.gradle.kts`'s commented-out `compileOnly` line at it. You are
     only borrowing Loom's remapping pipeline here — the actual GlassClient
     runtime never uses Fabric loader.
   - This step only has to happen once per Minecraft version you target.

## What's here

- `build.gradle.kts` — Java 21 toolchain, SpongePowered Mixin dependency +
  annotation processor (generates the refmap the mixin transformer needs at
  runtime).
- `src/main/java/dev/glassclient/GlassClientAgent.java` — the `-javaagent`
  entry point: initializes Mixin's bootstrap, registers our config, then
  hands off to Minecraft's real `main()`. **Unverified** — standalone Mixin
  (outside Forge/Fabric's managed class loading) needs careful service
  wiring; this is the first thing to get compiling and confirm actually
  works.
- `src/main/java/dev/glassclient/mixin/ExampleHudMixin.java` — a template,
  not a real mixin. Deliberately left as fill-in-the-blank rather than a
  guessed target — see the comments in that file for the exact steps to turn
  it into your first working mixin once you have a mapped jar to check
  against.
- `src/main/resources/mixins.glassclient.json` — the mixin config. Empty
  `mixins` array until `ExampleHudMixin` (or its real successor) is filled in
  and added here.

## First milestone

Get `ExampleHudMixin` (renamed to whatever it actually targets) to inject one
line of debug logging into the HUD render loop and see it print once per
frame when Minecraft is launched with the agent attached. That proves the
whole pipeline — agent premain -> Mixin bootstrap -> config load -> bytecode
transform -> injected code executing inside the real game loop — end to end.
Every feature mod after that is "just" more mixins on top of a working
pipeline.
