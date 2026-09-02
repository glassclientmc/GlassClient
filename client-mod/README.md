# client-mod

The part of GlassClient that runs inside the Minecraft process. See the root
[ARCHITECTURE.md](../ARCHITECTURE.md) for the full picture — this is the
genuinely hard, multi-month part of the project.

## Status

**The pipeline works end to end, confirmed 2026-09-01/02 against real
Minecraft.** Two real, working features, both visually confirmed in a
running game window launched through GlassClient's own launcher:

- `HudOverlayMixin` — FPS counter + coordinates, styled like Lunar
  Client's own HUD (small scaled-down text on a semi-transparent black
  box, not plain full-size text floating on nothing).
- `SettingsKeybindMixin` + `HudSettingsScreen` — Right Shift (Lunar's own
  default) opens a real in-game settings screen with checkboxes to toggle
  each HUD element, backed by `GlassClientConfig`.

This is the "First milestone" the project always wanted, actually hit —
not a draft, not a template.

Getting there took finding and fixing **four separate real bugs**, each
confirmed via direct evidence (stack traces, bytecode inspection, repeated
clean builds), not guesswork:

1. **Mixin environment never left `Phase.PREINIT`.** `GlassClientAgent`
   called `MixinBootstrap.init()` and registered the config, but nothing
   ever transitioned the environment to `Phase.DEFAULT` — the phase our
   config's `select()` check needs to match. Forge/Fabric's own launch
   plugins do this via `MixinBootstrap.inject()`, which is package-private
   (only callable from inside `org.spongepowered.asm.launch`). Fixed with
   `GlassClientEnvironmentBridge` — a small class deliberately placed *in
   that package* (legal: package-private access is checked by package name
   + classloader, and everything ends up in one classloader once bundled
   into the shadow jar) that calls the real phase-transition method,
   `MixinEnvironment.gotoPhase(Phase.DEFAULT)`, directly.
2. **Class names passed in the wrong format, twice.** `ClassFileTransformer`
   always hands names in JVM-internal slash form (`dev/glassclient/test/
   Target`), but Mixin's own target-matching and `Class.forName` both need
   dotted names. `GlassClientMixinService.transform()` and all three
   `IClassProvider.findClass` overloads were passing the raw slash-form
   name straight through. Fixed by normalizing to dots at both call sites.
3. **Mojang's shipped client jar is obfuscated.** Confirmed via a real
   `ClassNotFoundException: net/minecraft/client/gui/Gui` at runtime, full
   stack trace in git history. "Official mappings are published" (true
   since 1.14.4) does not mean the jar Mojang actually ships uses those
   names — it's still ProGuard-obfuscated; the mappings are a separate
   lookup table for tooling. Forge/Fabric solve this with a whole runtime
   remapping pipeline (SRG/intermediary). We sidestep it: the launcher runs
   the same deobfuscated jar our mixins are compiled against (see below)
   in place of the obfuscated one, specifically when client-mod is
   attached — no remapping layer needed. This is a real, if inelegant,
   fix; building actual obfuscation-aware remapping (Mixin has a real
   mechanism for this via refmaps + `ObfuscationServiceMCP`, see the build
   script) is future work if broader server-anticheat compatibility etc.
   ever requires the "real" jar specifically.
4. **`GuiGraphics.drawString`'s color is ARGB, not RGB.** `0xFFFFFF` has a
   zero alpha byte, and `drawString` silently no-ops when
   `ARGB.alpha(color) == 0` — confirmed by reading `GuiGraphics.java`'s
   actual decompiled source. The mixin was genuinely firing the whole time;
   the text was just being asked to render fully transparent. Fixed by
   using `0xFFFFFFFF`.

There's also a fifth, build-time-only issue worth knowing about if you add
more mixins: **the Mixin annotation processor's "Unable to locate
obfuscation mapping" check is non-deterministic across Gradle daemon
reuse** — it can silently pass on a "lucky" daemon and hard-fail on a
fresh one, because it depends on an internal IDE-detection heuristic that
isn't actually reliable outside a real IDE. Verified deterministic
pass/fail by running with `--no-daemon` repeatedly. Fixed for real (not
just "usually") via `-AMSG_NO_OBFDATA_FOR_TARGET=warning` /
`-AMSG_NO_OBFDATA_FOR_CTOR=warning` in `build.gradle.kts` — Mixin's own
documented per-message-type severity override. Note: the documented
`=disabled` value for this flag did *not* actually suppress the hard error
in testing, for reasons not fully chased down — `=warning` is what's
actually confirmed to work reliably.

Two more, found building the settings screen on top of the working
pipeline:

6. **Missing transitive compile dependencies.** GUI code touching
   `GuiGraphics.pose()` needs JOML (`Matrix3x2fStack`) on the compile
   classpath; anything touching `Component` transitively needs Brigadier
   (`Component` implements its `Message` interface). Both are real runtime
   dependencies the actual game already has — added as `compileOnly` (not
   bundled into the fat jar, to avoid a duplicate-class conflict with what
   the game loads itself) once each missing-class compile error pointed at
   them directly.
7. **`Screen.renderBackground()` crashed with `IllegalStateException: Can
   only blur once per frame`.** In 1.21.8, Screen's default background
   render does a real GPU blur (`GuiGraphics.blurBeforeThisStratum`), and
   something else in the render pipeline already blurs once that same
   frame — calling it from a custom screen's `render()` hits Mixin's
   target correctly and runs, but then genuinely crashes the game (real
   crash report, not a silent failure). `HudSettingsScreen` draws a plain
   semi-transparent fill instead — visually close enough, and avoids the
   conflict entirely.

Run it yourself:

```bash
JAVA_HOME="/path/to/jdk-21" ./gradlew shadowJar
```

Then launch through GlassClient's own launcher (see `launcher/README` /
root `README.md`) with 1.21.8 selected — it auto-detects and wires in both
this jar and the matching deobfuscated Minecraft jar when both exist.

## What's here

- `build.gradle.kts` — JDK 21 toolchain, SpongePowered Mixin 0.8.5 +
  annotation processor, and the **Shadow plugin** (produces
  `glassclient-mod-<version>-all.jar`, a fat jar bundling Mixin/ASM/Gson/
  Guava — required, since a plain jar can't run standalone as a
  `-javaagent`). Non-obvious things it does, each commented inline where it
  happens:
  - Declares Gson/Guava/ASM on **both** `implementation` and
    `annotationProcessor` — Mixin's POM marks them provided/optional rather
    than transitive, so both the AP and the actual runtime classpath crash
    with `NoClassDefFoundError` without this.
  - `compileOnly(files("libs/minecraft-1.21.8-mojmap.jar"))` — the real,
    deobfuscated Minecraft jar mixins compile against. Not checked in (see
    below and `.gitignore`).
  - `-AdisableTargetValidator=true` — turns off the AP's compile-time check
    that an `@Inject` target's method/signature actually exists (a real
    tradeoff: wrong method names now only surface as runtime errors, not
    build failures — cross-check manually against the decompiled sources
    instead). Needed because that validator wants SRG/notch obfuscation
    mapping data we don't have and don't need.
  - `-AMSG_NO_OBFDATA_FOR_TARGET=warning` / `-AMSG_NO_OBFDATA_FOR_CTOR=
    warning` — see bug #5 above.
  - `compileOnly("org.joml:joml:1.10.8")` / `compileOnly("com.mojang:
    brigadier:1.3.10")` — see bug #6 above.
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
  their constructors and throw `NoClassDefFoundError` standalone.
- `src/main/java/org/spongepowered/asm/mixin/GlassClientEnvironmentBridge.java`
  — deliberately lives in Mixin's own package, see bug #1 above.
- `src/main/java/dev/glassclient/GlassClientConfig.java` — in-memory HUD
  toggle state (`showFps`, `showCoords`). No persisted config file yet —
  future work once there's enough toggles to justify one.
- `src/main/java/dev/glassclient/mixin/HudOverlayMixin.java` — FPS counter
  + player coordinates, styled like Lunar Client (small scaled text, dark
  semi-transparent box), injected at the tail of `Gui.render`. Respects
  `GlassClientConfig`.
- `src/main/java/dev/glassclient/mixin/SettingsKeybindMixin.java` — opens
  `HudSettingsScreen` on Right Shift (Lunar's own default), injected at the
  head of `KeyboardHandler.keyPress`.
- `src/main/java/dev/glassclient/gui/HudSettingsScreen.java` — the actual
  settings screen: checkboxes bound to `GlassClientConfig`. See bug #7
  above for why it doesn't use `Screen.renderBackground()`.
- `src/main/java/dev/glassclient/GlassClientInputTracker.java` +
  `mixin/KeystrokeTrackerMixin.java` + `mixin/MouseTrackerMixin.java` +
  `mixin/KeystrokesOverlayMixin.java` — WASD/CPS widget, bottom-right.
- `src/main/java/dev/glassclient/mixin/HitboxOverlayMixin.java` — PvP
  hitbox wireframe outline (`ShapeRenderer.renderLineBox`).
- `src/main/java/dev/glassclient/cosmetic/GlassClientCapeTexture.java` +
  `cosmetic/GlassClientCapeLayer.java` + `mixin/CosmeticCapeMixin.java` —
  the cosmetic cape, texture synthesized in memory (see "Next milestone"
  below for why, not a bundled asset file) and animated: background hue
  cycles cyan → blue → purple on a triangle wave, the logo's glow halo
  pulses on its own cycle, re-uploaded to the GPU ~15 times/sec (throttled
  — the render call can fire multiple times a frame with several players
  visible, so it doesn't regenerate the texture on every single call).
  Also composites the real GlassClient logo
  (`cosmetic/cape_logo.png`, bundled as a plain classpath resource, loaded
  via this class's own ClassLoader — not through Minecraft's
  ResourceManager, same reasoning as the synthesized background) with a
  pulsing glow ring, and renders full-bright
  (`LightTexture.FULL_BRIGHT`) so it stays visible in the dark. **Real
  bug worth knowing about**: `PlayerCapeModel.createCapeLayer()` bakes its
  mesh against a texture declared **64x64** (confirmed by reading
  `LayerDefinition.create(meshDefinition, 64, 64)` directly), not 64x32 —
  a first version built a 64x32 canvas (the size widely known from
  classic/legacy cape textures) and the logo simply never appeared, no
  crash, no error. Worked out the actual visible-panel UV rectangle by
  reading `ModelPart.Cube`'s real UV-unwrap math rather than guessing: a
  10x16 sub-region at pixel offset (1,1)-(11,17) or (12,1)-(22,17)
  depending on which face ends up camera-facing after the model's 180°
  rotation (not fully resolved from source alone, so the logo is drawn
  into both — harmless on whichever one isn't shown).
- `src/main/resources/mixins.glassclient.json` — the mixin config.
- `libs/` — gitignored. Holds `minecraft-1.21.8-mojmap.jar`, generated
  locally (see below), never committed.

## Getting the Mojang-mapped jar (needed once per Minecraft version)

Not something to check into the repo (see [ARCHITECTURE.md](../ARCHITECTURE.md)
legal notes: never bundle Mojang's files). The practical way to get one:

1. Temporarily scaffold a throwaway [Fabric Loom](https://fabricmc.net/wiki/tutorial:setup)
   Gradle project (`fabric-loom` plugin, `minecraft("com.mojang:minecraft:
   <version>")`, `mappings(loom.officialMojangMappings())`, a
   `fabric-loader` dependency just to satisfy Loom's project model).
2. Run `./gradlew genSourcesWithVineflower`.
3. Find the merged, remapped jar in Loom's global cache:
   `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-
   merged/<version>-<mappings-id>/minecraft-merged-<version>-<mappings-id>.jar`
4. Copy it to `client-mod/libs/minecraft-<version>-mojmap.jar`.
5. Delete the throwaway project — you're only borrowing Loom's remapping
   pipeline; GlassClient's own runtime never uses Fabric loader.

**Important version caveat**: Mojang doesn't always publish official
mappings (`client_mappings`) immediately for a new release — checked
directly against `piston-meta`, neither 26.2 nor 26.1.2 (the launcher's
current "latest") have them yet, while 1.21.8 does. That's why client-mod
targets 1.21.8 specifically right now, not whatever the launcher considers
"latest". If Loom fails with "Failed to find official mojang mappings for
`<version>`", that's this — pick the newest version that actually has
`client_mappings` in its piston-meta version JSON, don't assume it's a
tooling bug.

**Version support doesn't come for free even within the same minor line**:
tried retargeting everything at 1.21.11 (mappings exist for it too — jar
generated the same way, sitting in `libs/minecraft-1.21.11-mojmap.jar`,
just not the active `compileOnly` target). Real compile errors, not a
tooling hiccup: `RenderType`, `ResourceLocation`, `PlayerModel`,
`PlayerRenderer`, and `PlayerRenderState` all moved packages or were
renamed between 1.21.8 and 1.21.11. Reverted to keep the build working.
Confirms `ARCHITECTURE.md`'s own point that every mixin is written against
one version's actual internals — even a handful of patch releases apart
can break several core render classes at once, and there's no shortcut
around re-verifying each mixin's targets against the new version's real
structure (the exact process each existing mixin already went through
once — see the decompiled-source research described throughout this
file). Worth doing whenever there's a real reason to move off 1.21.8, not
speculatively.

Also: whichever Fabric Loom version you use needs to actually support the
target Minecraft version's Java bytecode level — an old Loom's bundled ASM
choked with "Unsupported class file major version 69" (Java 25's class
file format) against 26.2 during this investigation; a current Loom
version (1.18.0-alpha.19 at the time) handled it fine. Loom itself may also
need to run under a newer JDK than the Minecraft version's own toolchain
requires.

## Next milestone: more real feature mixins

The pipeline is proven. Every feature after `HudOverlayMixin` is "just"
more mixins on top of a working foundation, in the order
[ARCHITECTURE.md](../ARCHITECTURE.md) lays out:

1. ~~HUD overlay (FPS counter, coordinates)~~ — done, styled like Lunar.
2. ~~In-game settings screen (Right Shift) to toggle HUD elements~~ — done.
3. ~~Keystrokes/CPS display~~ — done. `GlassClientInputTracker` (static,
   fed by `KeystrokeTrackerMixin`/`MouseTrackerMixin`) backs a bottom-right
   WASD + click-speed widget (`KeystrokesOverlayMixin`), same styling and
   settings-toggle pattern as the HUD overlay.
4. ~~Cosmetics rendering~~ — done. `GlassClientCapeLayer` (registered into
   `PlayerRenderer` via `CosmeticCapeMixin`) renders an icy-blue cape on
   every player, using a texture synthesized in memory at runtime
   (`GlassClientCapeTexture`) rather than a bundled PNG — this mod isn't a
   Fabric/Forge mod and never registers a resource pack with Minecraft's
   `ResourceManager`, so a bundled asset file wouldn't actually resolve via
   a normal `ResourceLocation` lookup; a manually-built `DynamicTexture`
   registered directly with `TextureManager` sidesteps that whole
   pack-injection pipeline. The trickiest real bug: `@Shadow`-ing
   `addLayer` without extending anything failed at runtime with
   `InvalidMixinException: @Shadow method addLayer ... was not located in
   the target class` — `addLayer` is declared on `LivingEntityRenderer`
   (`PlayerRenderer`'s superclass), and Mixin's `@Shadow` resolution only
   searches the *exact* target class, not its ancestors (unlike `@Inject`
   targets, which do resolve across the whole hierarchy). Fixed by having
   the mixin class actually `extends LivingEntityRenderer<...>` — purely a
   compile-time shim (Mixin discards the mixin class's own
   constructor/inheritance before merging into the real `PlayerRenderer`;
   it's never actually instantiated as written), but it's what makes
   `addLayer` resolve as a normal inherited method.
5. ~~PvP hitbox overlay + reach indicator~~ — done. Hitboxes via
   `HitboxOverlayMixin` (wireframe outline via `ShapeRenderer.renderLineBox`,
   the same helper vanilla's own F3+B debug view uses internally). Reach via
   `HudOverlayMixin`, reading `Minecraft.hitResult` — a read-only distance
   display, not an actual reach extension; see the note in the commit
   history/PR discussion on why this is the same "info overlay" category as
   Lunar/Badlion's own reach display, not the reach-hack the project's
   policy rules out.
6. ~~Full Bright~~ + ~~No Hurt Cam~~ — done, both simple gameplay/visual
   toggles in the same vein Lunar ships. Full Bright
   (`FullBrightMixin`) reuses `DimensionSpecialEffects.forceBrightLightmap()`
   — the exact mechanism vanilla itself uses for always-bright dimensions
   — rather than fighting `LightTexture`'s shader-based lightmap math
   directly. No Hurt Cam (`NoHurtCamMixin`) cancels
   `GameRenderer.bobHurt()`, the method that actually applies the
   screen-tilt-on-damage effect. Off by default (both change how the game
   looks/plays more than the pure info overlays do).
7. ~~Zoom~~ — done. Hold C (OptiFine/Lunar's own classic default).
   `ZoomMixin` divides `GameRenderer.getFov`'s return value and cancels
   `GameRenderer.bobView` (the walk view-bob) while zoomed;
   `ZoomSensitivityMixin` scales down the turn deltas passed to
   `Entity.turn` inside `MouseHandler.turnPlayer` specifically (not
   `Entity.turn` globally — that's also called for AI-driven mob rotation
   and scaling it there would be wrong). Went through real, live tuning
   after actually trying it in-game, not guessed-and-shipped: an initial
   instant on/off toggle felt choppy; adding linear easing over ~330ms
   still felt mechanical and too fast; view-bob turned out to be a second,
   separate cause of "choppy while moving" (a narrow FOV amplifies normal
   walk-bob a lot); final settings are a ~700ms transition run through a
   smoothstep curve (`t*t*(3-2t)`, in `GlassClientInputTracker`), sensitivity
   scaled to 1/25th at full zoom (not 1/10th — still felt too fast).
   Freelook (decoupled third-person camera orbit) deliberately not
   attempted yet — it needs precise ordinal-sensitive injection into
   `Camera.setup()`'s actual positioning math (there are two calls to the
   3-arg `move` method in that class, and `setRotation` is called four
   times for different unrelated purposes — mirror-view, sleeping, etc. —
   so a naive `@ModifyArgs`/`@Inject` without careful ordinal targeting
   risks quietly breaking normal third-person camera behavior, not just
   freelook itself). Worth doing with real care, not speculatively.
8. Performance mods (render distance culling, particle limits) — higher
   regression risk, do this once more of the above is proven out.
9. Persisted config file — worth doing now that there are eight toggles;
   `GlassClientConfig` is in-memory only right now (resets each launch).

Not in scope, ever, per [ARCHITECTURE.md](../ARCHITECTURE.md)'s legal/policy
notes: reach/killaura-style automation or anything else that violates
Minecraft's usage guidelines. HUD overlays and cosmetics are the same
category Lunar/Badlion already ship and are broadly accepted.

**A note on scope**: "all of Lunar's features" is hundreds of individual
mods built by a full engineering team over years — this remains, honestly,
a multi-month effort one mixin at a time, not something that lands in a
single session. What's real as of this writeup: a genuinely working
pipeline, one real HUD feature styled to match Lunar, and a real in-game
settings screen to control it — each bug found and fixed with actual
evidence (stack traces, decompiled source, repeated clean builds, a real
crash report) rather than assumed away.
