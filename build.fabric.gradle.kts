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
    maven("https://maven.shedaniel.me/") { name = "Shedaniel" }
    maven("https://maven.blamejared.com/") {
        name = "BlameJared"
        content { includeGroup("mezz.jei") }
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

// Popular structure mods, and JEI, for trying the browser against a modpack's worth of structures.
// They're only on the dev runtime classpath, so they never end up in the built jar. Off on CI, and
// -Pdev_mods=false turns them off locally. Pinned to Modrinth version ids, per Minecraft version.
val devMods = mapOf(
    "1.20.1" to listOf(
        // Moog's
        "mes-moogs-end-structures:ROvChtQg", "moogs-voyager-structures:dMwX4GPR", "mns-moogs-nether-structures:bcwhyj8t",
        "mss-moogs-soaring-structures:Fu2E23KF", "mmv-moogs-missing-villages:uwxEdo7J", "mtr-moogs-temples-reimagined:Gcx1X3Ji",
        "mmr-moogs-mineshafts-reimagined:fjkyFY5g", "mos-moogs-ocean-structures:O1LxGHhC",
        // YUNG's
        "yungs-better-dungeons:nidyvq2m", "yungs-better-mineshafts:qLnQnqXS", "yungs-better-strongholds:yV6hn0bB",
        "yungs-better-ocean-monuments:4c00pjbt", "yungs-better-desert-temples:1Z9HNWpj", "yungs-better-jungle-temples:6LPrzuB0",
        "yungs-better-witch-huts:lYpHN3iF", "yungs-better-nether-fortresses:FL88RLRu", "yungs-better-end-island:qJTsmyiE",
        "yungs-bridges:hvfjXu8d", "yungs-extras:pfVTUz1L",
        // Other big structure mods
        "repurposed-structures-fabric:jaRcykAY", "towns-and-towers:7ZwnSrVW", "structory:FkaSuQb0", "structory-towers:fTl6NfPL",
        "when-dungeons-arise:Vd5XOXlj", "dungeons-and-taverns:d1sY0JqV", "explorify:CuBdAr31",
        // Recipe viewer and structure compass
        "jei:YRfUnbXb", "explorers-compass:qD2j03H6",
        // Libraries the above need
        "moogs-structure-lib:ynssyzOT", "yungs-api:lscV1N5k", "cristel-lib:tBnivdbu", "cloth-config:2xQdCMyG",
        "resourceful-config:2gStMKhM", "midnightlib:rXX4FCV8",
    ),
)
val useDevMods = System.getenv("CI") == null && findProperty("dev_mods")?.toString() != "false"

dependencies {
    minecraft("com.mojang:minecraft:$mcBuild")
    // No-op on the unobfuscated versions; applies Mojang mappings on the obfuscated ones.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    // The JEI plugin only loads when JEI is installed, so its API is only needed to compile.
    modCompileOnly("mezz.jei:jei-$mcBuild-common-api:${prop("deps.jei")}")
    // The settings screen: Mod Menu opens it and Cloth Config draws it. Both optional.
    modCompileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
    modCompileOnly("me.shedaniel.cloth:cloth-config-fabric:${prop("deps.cloth_config")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    // Likewise Explorer's Compass, which has no API: the link calls its own search.
    modCompileOnly("maven.modrinth:explorers-compass:${prop("deps.explorers_compass")}")

    if (useDevMods) {
        devMods[mcBuild].orEmpty().forEach { modLocalRuntime("maven.modrinth:$it") }
        modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
        // Libraries these mods bundle inside their jars, which Loom doesn't unpack in a dev environment:
        // YUNG's (Reflections), Cristel Lib (Jankson) and Cloth Config (basic-math).
        localRuntime("org.reflections:reflections:0.10.2")
        localRuntime("org.javassist:javassist:3.29.2-GA")
        localRuntime("blue.endless:jankson:1.2.3")
        localRuntime("me.shedaniel.cloth:basic-math:0.6.1")
    }
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
            // -Pperf also captures every installed structure and times it, into build/gametest/perf.csv.
            vmArg("-Djes.perf=${hasProperty("perf")}")
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

    // IntelliJ 2026.2 turns the dots in a node's name into underscores when it names modules
    // (JustEnoughStructures.1_20_1-fabric.main), but Loom writes its run configurations for
    // JustEnoughStructures.1.20.1-fabric.main, which then don't run. Once the IDE has imported the
    // project, point them at the name it actually uses.
    named("ideaSyncTask") {
        val ideaDir = rootProject.file(".idea")
        val dotted = "${rootProject.name}.${project.name}."
        val underscored = "${rootProject.name}.${project.name.replace('.', '_')}."
        doLast {
            val modules = File(ideaDir, "modules.xml")
            if (dotted == underscored || !modules.exists() || underscored !in modules.readText()) return@doLast
            File(ideaDir, "runConfigurations").listFiles { f -> f.extension == "xml" }?.forEach { f ->
                val text = f.readText()
                val fixed = text.replace("<module name=\"$dotted", "<module name=\"$underscored")
                if (fixed != text) f.writeText(fixed)
            }
        }
    }

    named("runAutoshot") {
        val dir = layout.buildDirectory.dir("autoshot")
        doFirst {
            val root = dir.get().asFile
            // The browser remembers its toggles in config, which would carry over from the last run.
            delete(File(root, "saves"), File(root, "screenshots"), File(root, "config/$modId"))
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
