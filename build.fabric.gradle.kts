plugins {
    // Applies the Loom variant matching this node's Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

fun prop(key: String): String = sc.properties.get<String>(key)

val modId = property("mod_id").toString()
val modName = property("mod_name").toString()
val modAuthor = property("mod_author").toString()
val requiredJava: JavaVersion = JavaVersion.toVersion(prop("mod.java"))
val mcBuild: String = prop("mod.mc_build")

version = property("mod_version").toString()
base.archivesName = "${property("archives_base_name")}-fabric-$mcBuild"

sourceSets.main {
    // Loader code sits in fabric/forge/neoforge packages; each loader compiles only its own.
    java.exclude("**/forge/**", "**/neoforge/**")
    resources.srcDir(rootProject.file("src/fabric/resources"))
}

// Game tests build as a second mod that never ships. It sees the main classes through main's output.
val gametest: SourceSet = sourceSets.create("gametest") {
    java.exclude("**/forge/**", "**/neoforge/**")
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

repositories {
    maven("https://maven.terraformersmc.com/releases") { name = "TerraformersMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcBuild")
    // No-op on the unobfuscated versions; applies Mojang mappings on the obfuscated ones.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
}

loom {
    runs {
        // Per-node game directory, so worlds are never opened by a different Minecraft version.
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("../../run/${project.name}")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("../../run/${project.name}")
        }
        // Boots a headless server, runs every game test, writes a JUnit report and exits non-zero
        // if anything failed.
        register("gameTest") {
            server()
            configName = "Fabric Game Test"
            ideConfigGenerated(true)
            source(gametest)
            runDir("build/gametest")
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${layout.buildDirectory.get().asFile}/gametest/report.xml")
        }
        // Opens the structure browser in a throwaway superflat world, saves screenshots to
        // build/autoshot/screenshots and quits. -Pstructures=a:b,c:d picks the structures,
        // -Pmode=review|open|showcase plays a scripted tour or recording instead, -Pwidth and
        // -Pheight size the window and -Pshow keeps it visible.
        register("autoshot") {
            client()
            configName = "Fabric Autoshot"
            source(gametest)
            runDir("build/autoshot")
            vmArg("-Djes.autoshot=screenshots")
            vmArg("-Djes.autoshot.structures=${findProperty("structures") ?: ""}")
            vmArg("-Djes.autoshot.hidden=${!hasProperty("show")}")
            vmArg("-Djes.autoshot.mode=${findProperty("mode") ?: "gallery"}")
            vmArg("-Djes.autoshot.gui=${findProperty("gui") ?: 2}")
            programArgs("--width", "${findProperty("width") ?: 1600}", "--height", "${findProperty("height") ?: 900}")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
        register("${modId}_gametest") {
            sourceSet(gametest)
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to version.toString(),
            "mod_id" to modId,
            "mod_name" to modName,
            "description" to property("description").toString(),
            "mod_author" to modAuthor,
            "mc_compat" to prop("mod.mc_compat"),
            "fabric_loader_dep" to prop("mod.fabric_loader_dep"),
            "java_version" to requiredJava.majorVersion,
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modName" } }
        manifest {
            attributes(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to version,
                "Implementation-Title" to "fabric",
                "Implementation-Version" to version,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to mcBuild,
            )
        }
    }

    // Every run starts from a fresh world, so nothing one run leaves behind can make the next pass or fail.
    named("runGameTest") {
        val world = layout.buildDirectory.dir("gametest/world")
        doFirst { delete(world) }
    }

    named("runAutoshot") {
        val dir = layout.buildDirectory.dir("autoshot")
        doFirst {
            val root = dir.get().asFile
            delete(File(root, "saves"), File(root, "screenshots"))
            root.mkdirs()
            // Skip first-launch screens and keep the game running when the window isn't focused. No
            // clouds, so the world behind the screen doesn't change from frame to frame.
            File(root, "options.txt").writeText(
                "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\njoinedFirstServer:true\n" +
                    "skipMultiplayerWarning:true\nsoundCategory_master:0.0\nguiScale:2\nrenderClouds:\"false\"\n"
            )
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$version"))
    }
}
