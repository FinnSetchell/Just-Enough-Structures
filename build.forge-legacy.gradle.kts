plugins {
    // Forge below 1.20.5 runs on SRG names, so the jar is reobfuscated from the official names the
    // source is written in. ForgeGradle 7 can't do that; ModDevGradle's legacy Forge plugin can.
    id("net.neoforged.moddev.legacyforge") version "2.0.147"
    id("minecraft-mutex")
}

fun prop(key: String): String = sc.properties.get<String>(key)

val modId = property("mod_id").toString()
val modName = property("mod_name").toString()
val modAuthor = property("mod_author").toString()
// Read here: inside a task, property() looks at the task, and a task has its own description.
val modDescription = property("description").toString()
val modLicense = property("license").toString()
val requiredJava: JavaVersion = JavaVersion.toVersion(prop("mod.java"))
val mcBuild: String = prop("mod.mc_build")
// Read here: inside a run, project means the run's own, which is deprecated.
val nodeName: String = project.name

version = property("mod_version").toString()
base.archivesName = "${property("archives_base_name")}-forge-$mcBuild"

sourceSets.main {
    // Loader code sits in fabric/forge/neoforge packages; each loader compiles only its own.
    java.exclude("**/fabric/**", "**/neoforge/**")
    resources.srcDir(rootProject.file("src/forge/resources"))
}

// Game tests build as a second mod that never ships, as on Fabric. Its Fabric wiring and the
// screenshot scripts stay out: Forge runs the same tests through its own class.
val gametest: SourceSet = sourceSets.create("gametest") {
    java.exclude("**/fabric/**", "**/neoforge/**")
    resources.exclude("fabric.mod.json")
    resources.srcDir(rootProject.file("src/forge/gametest-resources"))
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

repositories {
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

// The same structure mods as the Fabric dev runtime, in their Forge builds, so the browser can be
// tried against a modpack's worth of structures. Off on CI, and -Pdev_mods=false turns them off.
val devMods = mapOf(
    "1.20.1" to listOf(
        // Moog's
        "mes-moogs-end-structures:ROvChtQg", "moogs-voyager-structures:dMwX4GPR", "mns-moogs-nether-structures:bcwhyj8t",
        "mss-moogs-soaring-structures:Fu2E23KF", "mmv-moogs-missing-villages:uwxEdo7J", "mtr-moogs-temples-reimagined:Gcx1X3Ji",
        "mmr-moogs-mineshafts-reimagined:9dM5WGbU", "mos-moogs-ocean-structures:O1LxGHhC",
        // YUNG's
        "yungs-better-dungeons:kPiQ6v4q", "yungs-better-mineshafts:kVO57zxB", "yungs-better-strongholds:rwiShgsc",
        "yungs-better-ocean-monuments:SN4iZ7wf", "yungs-better-desert-temples:lRK2ZA9U", "yungs-better-jungle-temples:CXQc6EnZ",
        "yungs-better-witch-huts:mwlYB7rq", "yungs-better-nether-fortresses:2nUEz0zq", "yungs-better-end-island:Izqhg3Va",
        "yungs-bridges:KgO1gfM2", "yungs-extras:h4m8J7w8",
        // Other big structure mods
        "repurposed-structures-forge:GgUh2Zx7", "towns-and-towers:7ZwnSrVW", "structory:FkaSuQb0", "structory-towers:fTl6NfPL",
        "when-dungeons-arise:6hQpx5Tc", "dungeons-and-taverns:ojHpWOrz", "explorify:CuBdAr31",
        // Structure compass
        "explorers-compass:7ZdJbCOx",
        // Libraries the above need
        "moogs-structure-lib:zuKYMmUt", "yungs-api:PJOYAmAs", "cristel-lib:DOsSK4NK", "cloth-config:t8TXrZvZ",
    ),
)
val useDevMods = System.getenv("CI") == null && findProperty("dev_mods")?.toString() != "false"
// Which recipe viewer the dev runtime has: -Pviewer=jei (the default), emi or rei.
val viewer = findProperty("viewer")?.toString() ?: "jei"

legacyForge {
    version = "$mcBuild-${prop("deps.forge_version")}"

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
        register("${modId}_gametest") {
            sourceSet(gametest)
        }
    }

    runs {
        // Per-node game directory, so worlds are never opened by a different Minecraft version.
        register("client") {
            client()
            gameDirectory = rootProject.file("run/$nodeName")
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run/$nodeName")
        }
        // Boots a headless server, runs every game test and exits with the number that failed.
        register("gameTest") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = layout.buildDirectory.dir("gametest").get().asFile
            systemProperty("forge.enabledGameTestNamespaces", "$modId,${modId}_gametest")
            // -Pperf also captures every installed structure and times it, into build/gametest/perf.csv.
            systemProperty("jes.perf", hasProperty("perf").toString())
            // The mod's own debug log, in build/gametest/logs.
            systemProperty("justenoughstructures.debug", "true")
            // The test mod's own mixins, only on this run, where the test mod is loaded.
            programArguments.addAll("--mixin.config", "${modId}_gametest.mixins.json")
        }
        // Opens the browser in a throwaway superflat world, saves a screenshot of each structure to
        // build/autoshot/screenshots and quits, as on Fabric. -Pstructures=a:b,c:d picks the
        // structures, -Pwidth and -Pheight size the window and -Pshow keeps it visible.
        register("autoshot") {
            client()
            sourceSet = gametest
            gameDirectory = layout.buildDirectory.dir("autoshot").get().asFile
            systemProperty("jes.autoshot", "screenshots")
            systemProperty("jes.autoshot.structures", findProperty("structures")?.toString() ?: "")
            systemProperty("jes.autoshot.hidden", (!hasProperty("show")).toString())
            systemProperty("jes.autoshot.gui", findProperty("gui")?.toString() ?: "2")
            systemProperty("justenoughstructures.debug", "true")
            programArguments.addAll("--width", findProperty("width")?.toString() ?: "1600", "--height", findProperty("height")?.toString() ?: "900")
            programArguments.addAll("--mixin.config", "${modId}_gametest.mixins.json")
        }
    }
}

mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.mixins.json")
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    // The mixins use MixinExtras, which Forge 1.20.1 doesn't ship, so the jar carries it.
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")!!)
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:${prop("deps.mixinextras")}")!!)

    // The JEI plugin only loads when JEI is installed, so its API is only needed to compile.
    compileOnly("mezz.jei:jei-$mcBuild-common-api:${prop("deps.jei")}")
    // The settings screen, drawn by Cloth Config when it's installed.
    modCompileOnly("me.shedaniel.cloth:cloth-config-forge:${prop("deps.cloth_config")}")
    // Explorer's Compass has no API: the link calls its own search.
    modCompileOnly("maven.modrinth:explorers-compass:${prop("deps.explorers_compass")}")
    // And the EMI and REI pages.
    modCompileOnly("maven.modrinth:emi:${prop("deps.emi")}")
    modCompileOnly("maven.modrinth:rei:${prop("deps.rei")}")
    modCompileOnly("maven.modrinth:architectury-api:${prop("deps.architectury")}")

    if (useDevMods) {
        devMods[mcBuild].orEmpty().forEach { modRuntimeOnly("maven.modrinth:$it") }
        when (viewer) {
            "emi" -> modRuntimeOnly("maven.modrinth:emi:${prop("deps.emi")}")
            "rei" -> {
                modRuntimeOnly("maven.modrinth:rei:${prop("deps.rei")}")
                modRuntimeOnly("maven.modrinth:architectury-api:${prop("deps.architectury")}")
            }
            else -> modRuntimeOnly("mezz.jei:jei-$mcBuild-forge:${prop("deps.jei")}")
        }
    }
}

// The test mod as a jar for a real Forge game, reobfuscated like the mod's own, so the screenshot
// script can run against a release build in a launcher instance. Never shipped.
val gametestJar = tasks.register<Jar>("gametestJar") {
    from(gametest.output)
    archiveClassifier = "gametest"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
}
// Set up as the plugin's own reobfuscate does, which needs published variants a test source set hasn't got.
tasks.register<net.neoforged.moddevgradle.legacyforge.tasks.RemapJar>("reobfGametestJar") {
    input.set(gametestJar.flatMap { it.archiveFile })
    // Away from build/libs, which a release looks in for the mod's jar.
    destinationDirectory = layout.buildDirectory.dir("testlibs")
    archiveBaseName = base.archivesName
    archiveVersion = version.toString()
    archiveClassifier = "gametest"
    libraries.from(gametest.compileClasspath)
    obfuscation.configureNamedToSrgOperation(remapOperation)
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
            "description" to modDescription,
            "mod_author" to modAuthor,
            "license" to modLicense,
            "mc_compat" to prop("mod.mc_compat"),
            "forge_min" to prop("deps.forge_min"),
            "pack_format" to prop("mod.pack_format"),
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        // Forge finds a mixin's refmap through its config; Loom adds Fabric's without being asked.
        filesMatching("$modId.mixins.json") {
            filter { line -> line.replace("\"package\":", "\"refmap\": \"$modId.refmap.json\",\n  \"package\":") }
        }
        exclude("fabric.mod.json")
    }

    named<ProcessResources>("processGametestResources") {
        val props = mapOf("forge_min" to prop("deps.forge_min"), "pack_format" to prop("mod.pack_format"))
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        exclude("fabric.mod.json")
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modName" } }
        manifest {
            attributes(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to version,
                "Implementation-Title" to "forge",
                "Implementation-Version" to version,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to mcBuild,
                "MixinConfigs" to "$modId.mixins.json",
            )
        }
    }

    // Minecraft must not be set up before Stonecutter has written this node's sources.
    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    withType<JavaCompile>().configureEach {
        dependsOn("stonecutterGenerate")
    }

    // Every run starts from a fresh world, so nothing one run leaves behind can make the next pass or fail.
    named("runGameTest") {
        val world = layout.buildDirectory.dir("gametest/world")
        val properties = layout.buildDirectory.file("gametest/server.properties")
        val log = layout.buildDirectory.file("gametest/logs/latest.log")
        doFirst {
            delete(world)
            // Forge's test server makes its world from server.properties, where the game's own makes a
            // superflat one with seed 0 and no structures, as Fabric's tests run in. The same here.
            properties.get().asFile.apply { parentFile.mkdirs() }
                .writeText("level-type=minecraft:flat\nlevel-seed=0\ngenerate-structures=false\n")
        }
        // A test server that fails to start still exits cleanly, so this goes by what it logged.
        doLast {
            val text = log.get().asFile.takeIf { it.exists() }?.readText().orEmpty()
            if (!Regex("All \\d+ required tests passed").containsMatchIn(text)) {
                throw GradleException("Not every game test passed, see ${log.get().asFile}")
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
        // The reobfuscated jar, not `jar`, which keeps official names and only runs in a dev environment.
        val reobf = named("reobfJar")
        dependsOn(reobf)
        from(reobf.map { it.outputs.files })
        into(rootProject.layout.buildDirectory.dir("libs/$version"))
    }
}
