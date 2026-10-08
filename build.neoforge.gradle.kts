plugins {
    // NeoForge runs on the official names the source is written in, so its jar needs no remapping.
    id("net.neoforged.moddev") version "2.0.147"
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
base.archivesName = "${property("archives_base_name")}-neoforge-$mcBuild"

// The mods JES links up with that have no build for this version and loader are left out: their
// version is blank in stonecutter.properties.toml, this keeps their code out of the build, and
// `//? if <name>` leaves out what refers to it.
fun has(mod: String) = !sc.properties.getOrNull<String>("deps.$mod").isNullOrEmpty()
val withoutIntegrations = buildList {
    if (!has("jei")) add("**/compat/jei/**")
    if (!has("emi")) add("**/compat/emi/**")
    if (!has("rei")) addAll(listOf("**/compat/rei/**", "**/*ReiForgePlugin.java", "**/*ReiNeoForgePlugin.java"))
    if (!has("cloth_config")) add("**/compat/cloth/**")
    if (!has("explorers_compass")) addAll(listOf("**/compat/explorerscompass/**", "**/*ExplorersCompass.java"))
    if (!has("modmenu") || !has("cloth_config")) add("**/fabric/JesModMenu.java")
}

sourceSets.main {
    // Loader code sits in fabric/forge/neoforge packages; each loader compiles only its own.
    java.exclude("**/fabric/**", "**/forge/**")
    java.exclude(withoutIntegrations)
    resources.srcDir(rootProject.file("src/neoforge/resources"))
}

// Game tests build as a second mod that never ships, as on the other loaders. Its Fabric wiring stays
// out: NeoForge runs the same tests and screenshot scripts through its own classes.
val gametest: SourceSet = sourceSets.create("gametest") {
    java.exclude("**/fabric/**", "**/forge/**")
    resources.exclude("fabric.mod.json")
    // From 26.1 the tests' environments are registered along with the tests, as NeoForge offers no
    // way to look up the ones in data.
    resources.exclude("data/justenoughstructures_gametest/test_environment/**")
    resources.srcDir(rootProject.file("src/neoforge/gametest-resources"))
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

repositories {
    maven("https://maven.shedaniel.me/") { name = "Shedaniel" }
    maven("https://maven.blamejared.com/") {
        name = "BlameJared"
        // JEI, and the config library its later builds need.
        content {
            includeGroup("mezz.jei")
            includeGroup("net.mezzdev.config")
        }
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

// The same structure mods as the other dev runtimes, in their NeoForge builds, so the browser can be
// tried against a modpack's worth of structures. Off on CI, and -Pdev_mods=false turns them off.
val devMods = mapOf(
    "1.21.1" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // YUNG's
        "yungs-better-dungeons:D6aZn0Em", "yungs-better-mineshafts:Go3nbneL", "yungs-better-strongholds:8U0dIfSM",
        "yungs-better-ocean-monuments:yFjEcj2g", "yungs-better-desert-temples:GQ9iNWkI", "yungs-better-jungle-temples:P00i2hJn",
        "yungs-better-witch-huts:AvedwcIe", "yungs-better-nether-fortresses:iopJiJQp", "yungs-better-end-island:I52NZ1qK",
        "yungs-bridges:urkCzBf6", "yungs-extras:N2EpMhR7",
        // Other big structure mods. Repurposed Structures keeps its NeoForge builds on its Forge project, and
        // Structory Towers' newest build for 1.21.1 is one made for 26.2, which NeoForge 1.21.1 won't load.
        "repurposed-structures-forge:8duZuWci", "towns-and-towers:DZxwgj6V", "structory:TUbwu7eG", "structory-towers:lefqbuOP",
        "when-dungeons-arise:XIRJSFQ0", "dungeons-and-taverns:BYUUUeZA", "explorify:CuBdAr31",
        // Structure compass
        "explorers-compass:hIJ2Ev1Q",
        // Libraries the above need
        "moogs-structure-lib:wcg4mE4e", "yungs-api:2prKITKh", "cristel-lib:Sduz0AWP", "cloth-config:izKINKFg",
        "resourceful-config:lSbyRD6v", "midnightlib:6Gv5jvTB",
    ),
    "26.1.2" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // YUNG's
        "yungs-better-dungeons:e1qEvr8D", "yungs-better-mineshafts:MXPxqNif", "yungs-better-strongholds:jRm5H60F",
        "yungs-better-ocean-monuments:HwZo1wRg", "yungs-better-desert-temples:c5C5dZJ8", "yungs-better-jungle-temples:ECcz9HDe",
        "yungs-better-witch-huts:FuqDSl5q", "yungs-better-nether-fortresses:CXSmjEju", "yungs-better-end-island:o9zTzuap",
        "yungs-bridges:fi6Ilg6W", "yungs-extras:nxaj9k0R",
        // Other big structure mods. Repurposed Structures and When Dungeons Arise have no 26.1 build.
        "towns-and-towers:eN3WLQ3P", "structory:TUbwu7eG", "structory-towers:ziO4YIv1", "dungeons-and-taverns:aNzOBwdJ",
        "explorify:CuBdAr31",
        // Structure compass
        "explorers-compass:OuPMcvp4",
        // Libraries the above need
        "moogs-structure-lib:xK8AFMA5", "yungs-api:jPWeixDa", "cristel-lib:WWbbl4Rn", "cloth-config:TimoYzse",
        "resourceful-config:BLREkCgZ", "midnightlib:aDODZlso",
    ),
    "26.2" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // Other big structure mods. YUNG's has no 26.2 build.
        "towns-and-towers:eN3WLQ3P", "structory:TUbwu7eG", "structory-towers:ziO4YIv1", "dungeons-and-taverns:9wgjmpuF",
        "explorify:CuBdAr31",
        // Structure compass
        "explorers-compass:r9Okm6YW",
        // Libraries the above need
        "moogs-structure-lib:Bn1trW8V", "cristel-lib:aRIryrE0", "cloth-config:zErG1kOw",
        "resourceful-config:8xLtoyZG", "midnightlib:FRwfpcuC",
    ),
    "26.3" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // Other big structure mods. YUNG's and Explorify have no 26.3 build.
        "towns-and-towers:yajFnGZS", "structory:GjOkOVW4", "structory-towers:5ntmnN83", "dungeons-and-taverns:2AjppPPt",
        // Structure compass
        "explorers-compass:MhLYpmvJ",
        // Libraries the above need
        "moogs-structure-lib:DyrpWnuR", "cristel-lib:v197aEhs", "cloth-config:GchRXPnb",
        "resourceful-config:WEYdpGaO", "midnightlib:lNImYCDG",
    ),
)
val useDevMods = System.getenv("CI") == null && findProperty("dev_mods")?.toString() != "false"
// Which recipe viewer the dev runtime has: -Pviewer=jei (the default), emi or rei.
val viewer = findProperty("viewer")?.toString() ?: "jei"

neoForge {
    version = prop("deps.neoforge_version")

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
            systemProperty("neoforge.enabledGameTestNamespaces", "$modId,${modId}_gametest")
            // -Pperf also captures every installed structure and times it, into build/gametest/perf.csv.
            systemProperty("jes.perf", hasProperty("perf").toString())
            // The mod's own debug log, in build/gametest/logs.
            systemProperty("justenoughstructures.debug", "true")
        }
        // Opens the browser in a throwaway superflat world, saves a screenshot of each structure to
        // build/autoshot/screenshots and quits, as on the other loaders. -Pstructures=a:b,c:d picks
        // the structures, -Pwidth and -Pheight size the window, -Pshow keeps it visible and
        // -Pjoin=host:port joins that server instead of making a world.
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
            systemProperty("jes.autoshot.join", hasProperty("join").toString())
            findProperty("join")?.let { programArguments.addAll("--quickPlayMultiplayer", it.toString()) }
        }
    }
}

dependencies {
    // The JEI plugin only loads when JEI is installed, so its API is only needed to compile.
    if (has("jei")) compileOnly("mezz.jei:jei-$mcBuild-common-api:${prop("deps.jei")}")
    // The settings screen, drawn by Cloth Config when it's installed.
    if (has("cloth_config")) compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${prop("deps.cloth_config")}")
    // Explorer's Compass has no API: the link calls its own search.
    if (has("explorers_compass")) compileOnly("maven.modrinth:explorers-compass:${prop("deps.explorers_compass")}")
    // And the EMI and REI pages.
    if (has("emi")) compileOnly("maven.modrinth:emi:${prop("deps.emi")}")
    if (has("rei")) {
        compileOnly("maven.modrinth:rei:${prop("deps.rei")}")
        compileOnly("maven.modrinth:architectury-api:${prop("deps.architectury")}")
    }

    if (useDevMods) {
        devMods[mcBuild].orEmpty().forEach { runtimeOnly("maven.modrinth:$it") }
        when {
            viewer == "emi" && has("emi") -> runtimeOnly("maven.modrinth:emi:${prop("deps.emi")}")
            viewer == "rei" && has("rei") -> {
                runtimeOnly("maven.modrinth:rei:${prop("deps.rei")}")
                runtimeOnly("maven.modrinth:architectury-api:${prop("deps.architectury")}")
            }
            has("jei") -> runtimeOnly("mezz.jei:jei-$mcBuild-neoforge:${prop("deps.jei")}")
        }
    }
}

// The test mod as a jar for a real NeoForge game, so the screenshot gallery can run against a release
// build in a launcher instance. Never shipped, and kept out of build/libs, where a release looks.
tasks.register<Jar>("gametestJar") {
    from(gametest.output)
    archiveClassifier = "gametest"
    destinationDirectory = layout.buildDirectory.dir("testlibs")
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
            "neoforge_min" to prop("deps.neoforge_min"),
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        exclude("fabric.mod.json")
    }

    named<ProcessResources>("processGametestResources") {
        exclude("fabric.mod.json")
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modName" } }
        manifest {
            attributes(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to version,
                "Implementation-Title" to "neoforge",
                "Implementation-Version" to version,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to mcBuild,
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
            // A superflat world with seed 0 and no structures, as the tests run in on every loader.
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
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$version"))
    }
}
