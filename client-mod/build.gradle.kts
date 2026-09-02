plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.glassclient"
version = "0.1.0"

java {
    toolchain {
        // Match whatever Minecraft version you target first (1.20.5+ needs 21,
        // older 1.20.x needs 17). Adjust to match your compile-time jar below.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    // Mixin's POM marks its own runtime deps (asm/gson/guava) as
    // provided/optional rather than transitive compile deps — Forge/Fabric's
    // build plugins paper over this automatically; standalone, we have to
    // declare them ourselves on both the compile/runtime classpath (for the
    // shadow/fat jar to actually bundle them) and the annotationProcessor
    // classpath (or the AP itself crashes with NoClassDefFoundError).
    implementation("org.spongepowered:mixin:0.8.5")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.guava:guava:33.3.1-jre")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("org.ow2.asm:asm-analysis:9.7")

    // The annotation processor that generates mixins.glassclient.refmap.json
    // at compile time — required for mixins to resolve targets at runtime.
    annotationProcessor("org.spongepowered:mixin:0.8.5")
    annotationProcessor("com.google.code.gson:gson:2.11.0")
    annotationProcessor("com.google.guava:guava:33.3.1-jre")
    annotationProcessor("org.ow2.asm:asm:9.7")
    annotationProcessor("org.ow2.asm:asm-commons:9.7")
    annotationProcessor("org.ow2.asm:asm-tree:9.7")
    annotationProcessor("org.ow2.asm:asm-util:9.7")
    annotationProcessor("org.ow2.asm:asm-analysis:9.7")

    // Mojang-mapped, deobfuscated client jar, obtained by temporarily
    // borrowing a throwaway Fabric Loom project's remapping pipeline (see
    // client-mod/README.md). Targets 1.21.8, not whatever's currently
    // "latest" in the launcher (26.2 as of writing) — Mojang hasn't
    // published official mappings for the 26.x line yet, a real external
    // constraint, not a choice. NOT checked in (see .gitignore) — never
    // bundle Mojang's files, per ARCHITECTURE.md's legal notes.
    //
    // Tried switching this to 1.21.11 (libs/minecraft-1.21.11-mojmap.jar,
    // generated the same way — still in libs/, just unused) — real
    // internal API changes between 1.21.8 and 1.21.11 broke the build:
    // RenderType, ResourceLocation, PlayerModel, PlayerRenderer, and
    // PlayerRenderState all moved packages or were renamed. Not a quick
    // fix — every mixin target needs re-verifying against 1.21.11's actual
    // current structure, the same research process each 1.21.8 mixin
    // already went through once. Reverted here to keep the build working;
    // see client-mod/README.md for the follow-up plan.
    compileOnly(files("libs/minecraft-1.21.8-mojmap.jar"))

    // JOML — used for GuiGraphics.pose()'s Matrix3x2fStack (HUD scaling).
    // compileOnly, not implementation/shadowed: the real game already has
    // this exact version on its own classpath at runtime (confirmed against
    // the launcher's downloaded libraries), so bundling our own copy into
    // the fat jar would just risk a duplicate-class conflict for no reason.
    compileOnly("org.joml:joml:1.10.8")

    // Brigadier — Component implements its Message interface, so anything
    // touching Component (Screen/GUI code) transitively needs it on the
    // compile classpath. Same compileOnly reasoning as JOML above.
    compileOnly("com.mojang:brigadier:1.3.10")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        listOf(
            // No -AoutRefMapFile, and mixins.glassclient.json has no "refmap"
            // entry: refmaps exist to remap identifiers between a dev
            // environment's clean names and production's obfuscated ones
            // (the Forge/Fabric use case). We ship a fat jar compiled
            // directly against real Mojang-named classes with no obfuscation
            // layer at runtime, so there's nothing to remap — Mixin just
            // uses each @Inject's raw method-name string directly against
            // the real target class, which matches exactly since it's not
            // obfuscated. Requesting a refmap anyway is what caused a hard,
            // inconsistent "Unable to locate obfuscation mapping" compile
            // error (see git history) — the AP tried to look up SRG/searge
            // cross-references we don't have and don't need, via
            // ObfuscationServiceMCP, a service Mixin's own jar bundles and
            // the AP auto-discovers via ServiceLoader regardless of whether
            // we asked for it.
            "-AdisableTargetValidator=true",
            // Real tradeoff of this flag: turns off the AP's compile-time
            // check that an @Inject's target method/signature actually
            // exists, so a typo'd method name now only surfaces as a
            // runtime Mixin error instead of a build failure. Manually
            // cross-checked each target against the decompiled
            // libs/minecraft-1.21.8-mojmap.jar sources to compensate — see
            // each mixin's own comments.

            // -AdisableTargetValidator does NOT touch this one — it's a
            // separate check (AnnotatedMixinElementHandlerInjector) that
            // fires unconditionally whenever zero obfuscation environments
            // are registered, which is always, since we never configure any
            // SRG/notch mapping file. Confirmed with --no-daemon (removes
            // Gradle daemon-reuse noise) that this is a HARD, deterministic
            // compile error otherwise — an earlier "it built fine" was a
            // false negative from a stale reused daemon coincidentally
            // tripping Mixin's own IDE-detection quench, not a real fix.
            // MessageType's own documented -AMSG_<NAME>=<kind> mechanism
            // (see IMessagerEx.MessageType.applyOptions in Mixin's source)
            // is the sanctioned way to control this specific check's
            // severity. Note: the documented "disabled" value did NOT
            // actually prevent the hard compile error in practice (verified
            // with --no-daemon across multiple clean runs — worth
            // investigating further if picked back up, may be a Mixin AP
            // bug) — downgrading to "warning" is what's actually confirmed
            // deterministic, and is an accurate description anyway: this
            // really is a "worth knowing" note, not nothing.
            "-AMSG_NO_OBFDATA_FOR_TARGET=warning",
            "-AMSG_NO_OBFDATA_FOR_CTOR=warning"
        )
    )
}

val agentManifest =
    mapOf(
        // Lets the launcher attach us with `-javaagent:glassclient-mod-<version>-all.jar`
        "Premain-Class" to "dev.glassclient.GlassClientAgent",
        "Launcher-Agent-Class" to "dev.glassclient.GlassClientAgent",
        "Can-Redefine-Classes" to "true",
        "Can-Retransform-Classes" to "true"
    )

tasks.jar {
    manifest { attributes(agentManifest) }
}

// The plain `jar` task's output has no bundled dependencies (Mixin, ASM,
// Gson, Guava) and can't run standalone as a -javaagent. Use the shadowJar
// output (glassclient-mod-<version>-all.jar) instead — that's the one with
// everything it needs baked in.
tasks.shadowJar {
    manifest { attributes(agentManifest) }
    // Mixin's own jar already ships META-INF/services entries for
    // IGlobalPropertyService etc. — without merging, Shadow's default
    // "last one wins" resource handling can drop our own registrations.
    mergeServiceFiles()
}
