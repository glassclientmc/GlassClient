plugins {
    java
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
    implementation("org.spongepowered:mixin:0.8.5")

    // The annotation processor that generates mixins.glassclient.refmap.json
    // at compile time — required for mixins to resolve targets at runtime.
    annotationProcessor("org.spongepowered:mixin:0.8.5")

    // NOT checked in. You need a copy of the Minecraft client jar, remapped
    // to Mojang's official (non-obfuscated) mappings, to compile mixins
    // against. See client-mod/README.md for how to obtain one. Drop it at
    // client-mod/libs/minecraft-<version>-mojmap.jar and uncomment:
    // compileOnly(files("libs/minecraft-<version>-mojmap.jar"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        listOf(
            "-AoutRefMapFile=${layout.buildDirectory.file("resources/main/mixins.glassclient.refmap.json").get().asFile}"
        )
    )
}

tasks.jar {
    manifest {
        attributes(
            // Lets the launcher attach us with `-javaagent:glassclient-mod.jar`
            "Premain-Class" to "dev.glassclient.GlassClientAgent",
            "Launcher-Agent-Class" to "dev.glassclient.GlassClientAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
}
