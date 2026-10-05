pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Picks the Loom variant per node: remapping Loom for obfuscated Minecraft, plain Loom from 26.1.
    id("dev.kikugie.loom-back-compat") version "0.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * One node per loader for a Minecraft version, as `versions/{mc}-{loader}`, built by
         * `build.{loader}.gradle.kts`. Pass the version separately: as a single argument SemVer would
         * read `fabric` as a pre-release tag and order `1.20.1-fabric` below `1.20.1`.
         */
        fun match(mc: String, vararg loaders: String) {
            for (loader in loaders) {
                version("$mc-$loader", mc).buildscript("build.$loader.gradle.kts")
            }
        }

        match("1.20.1", "fabric")
        // Forge 1.20.1 runs on SRG names, so its jar has to be reobfuscated, which only ModDevGradle's
        // legacy Forge plugin does. The node keeps the -forge name, so `//? if forge` covers it too.
        version("1.20.1-forge", "1.20.1").buildscript("build.forge-legacy.gradle.kts")
        // From 1.20.5 Forge runs on official names, so later Forge nodes build with ForgeGradle 7.
        match("1.21.1", "fabric", "neoforge", "forge")
        // From 26.1 Minecraft ships unobfuscated, and needs Gradle itself on Java 25.
        match("26.1.2", "fabric", "neoforge", "forge")
        match("26.2", "fabric", "neoforge")

        vcsVersion = "1.20.1-fabric"
    }
}

rootProject.name = "JustEnoughStructures"
