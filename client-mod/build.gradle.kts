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

    // NOT checked in. You need a copy of the Minecraft client jar, remapped
    // to Mojang's official (non-obfuscated) mappings, to compile mixins
    // against. See client-mod/README.md for how to obtain one. Drop it at
    // client-mod/libs/minecraft-<version>-mojmap.jar and uncomment:
    // compileOnly(files("libs/minecraft-<version>-mojmap.jar"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        listOf(
            "-AoutRefMapFile=${layout.buildDirectory.file("resources/main/mixins.glassclient.refmap.json").get().asFile}",
            // We don't have a compile-time Minecraft jar yet (see README), so
            // mixin targets are plain strings the AP can't resolve. Without
            // this, compilation fails on every @Mixin(targets = "...").
            "-AdisableTargetValidator=true"
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
