plugins {
    // From 1.20.5 Forge runs on the official names the source is written in, so its jar needs no
    // reobfuscation, and ForgeGradle 7 builds it.
    id("net.minecraftforge.gradle") version "[7.0.29,8.0)"
    // Forge 1.21.1 doesn't ship MixinExtras, which the mixins use, so the jar carries it.
    id("net.minecraftforge.jarjar") version "0.2.3"
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
// Read here: inside a run, project means the run's own.
val nodeName: String = project.name

version = property("mod_version").toString()
base.archivesName = "${property("archives_base_name")}-forge-$mcBuild"

// The mods JES links up with that have no build for this version and loader are left out: their
// version is blank in stonecutter.properties.toml, this keeps their code out of the build, and
// `//? if <name>` leaves out what refers to it.
fun has(mod: String) = !sc.properties.getOrNull<String>("deps.$mod").isNullOrEmpty()
// Forge 1.21.1 doesn't ship MixinExtras, which the mixins use, so its jar carries it. Forge for 26.1
// has its own.
val carriesMixinExtras = has("mixinextras")
// The pack formats the jar's resources and data are made for: one number before 26.1, and from 26.1,
// which numbers the two apart, the range from one to the other, given as "84-101".
val packFormats: String = prop("mod.pack_format").split("-").let {
    if (it.size == 2) "\"min_format\": ${it[0]}, \"max_format\": ${it[1]}" else "\"pack_format\": ${it[0]}"
}
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
    java.exclude("**/fabric/**", "**/neoforge/**")
    java.exclude(withoutIntegrations)
    resources.srcDir(rootProject.file("src/forge/resources"))
}

// Game tests build as a second mod that never ships, as on the other loaders. Its Fabric wiring and
// the screenshot scripts stay out: Forge runs the same tests through its own class.
val gametest: SourceSet = sourceSets.create("gametest") {
    java.exclude("**/fabric/**", "**/neoforge/**")
    resources.exclude("fabric.mod.json")
    resources.srcDir(rootProject.file("src/forge/gametest-resources"))
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}
// ForgeGradle gives a source set its own run tasks when Forge is among its dependencies.
configurations.named(gametest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }

// The same structure mods as the other dev runtimes, in their Forge builds, so the browser can be
// tried against a modpack's worth of structures. Off on CI, and -Pdev_mods=false turns them off.
val devMods = mapOf(
    "1.21.1" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // YUNG's
        "yungs-better-dungeons:pzPJomj9",
        // Other big structure mods
        "structory:TUbwu7eG", "dungeons-and-taverns:wH004B85", "explorify:CuBdAr31",
        // Structure compass
        "explorers-compass:${prop("deps.explorers_compass")}",
        // Libraries the above need
        "moogs-structure-lib:Fm0UjMHR", "yungs-api:GKQLlzpD", "cloth-config:XMYFN6Zc",
    ),
    // YUNG's, Explorer's Compass and Cloth Config have no Forge build for 26.1, and Structory Towers'
    // build for it freezes Forge 26.1.2 at the loading screen.
    "26.1.2" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // Other big structure mods
        "structory:TUbwu7eG", "dungeons-and-taverns:nwBBc4Lf", "explorify:CuBdAr31",
        // Libraries the above need
        "moogs-structure-lib:7uEyqo4R",
    ),
    // Structory Towers' build for 26.2 freezes Forge at the loading screen too.
    "26.2" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // Other big structure mods
        "structory:TUbwu7eG", "dungeons-and-taverns:AXS4pp9z", "explorify:CuBdAr31",
        // Libraries the above need
        "moogs-structure-lib:gkOcGRxT",
    ),
    "26.3" to listOf(
        // Moog's
        "mes-moogs-end-structures:S7bUhX4n", "moogs-voyager-structures:PiFoSPXI", "mns-moogs-nether-structures:OLTqXnsN",
        "mss-moogs-soaring-structures:O20bIWkj", "mmv-moogs-missing-villages:fjpmujqZ", "mtr-moogs-temples-reimagined:RvfqP9Zb",
        "mmr-moogs-mineshafts-reimagined:JJc7pNHb", "mos-moogs-ocean-structures:QfMITqh9",
        // Other big structure mods. Explorify has no 26.3 build.
        "structory:GjOkOVW4", "structory-towers:5ntmnN83", "dungeons-and-taverns:GgiqvM0c",
        // Libraries the above need
        "moogs-structure-lib:wXDXV82i",
    ),
)
val useDevMods = System.getenv("CI") == null && findProperty("dev_mods")?.toString() != "false"

minecraft {
    mappings("official", mcBuild)

    runs {
        // Per-node game directory, so worlds are never opened by a different Minecraft version.
        configureEach {
            workingDir.set(rootProject.file("run/$nodeName"))
            // A dev run has no jar for Forge to read the mixin configs from.
            args("--mixin.config", "$modId.mixins.json")
        }
        register("client")
        register("server") { args("--nogui") }
        // Every run also gets a task for the test source set, runGametest<run>, which loads the test
        // mod too. runGameTest and runAutoshot below run those.
        register("gameTestServer") {
            this.with(gametest) {
                workingDir.set(layout.buildDirectory.dir("gametest"))
                // Forge 1.21 runs the tests whose batch is named here. The generated capture tests have their own.
                systemProperty("forge.enabledGameTestNamespaces", "$modId,${modId}_gametest,capture")
                systemProperty("forge.enableGameTest", "true")
                systemProperty("forge.gameTestServer", "true")
                // -Pperf also captures every installed structure and times it, into build/gametest/perf.csv.
                systemProperty("jes.perf", hasProperty("perf").toString())
                // The mod's own debug log, in build/gametest/logs.
                systemProperty("justenoughstructures.debug", "true")
                args("--mixin.config", "${modId}_gametest.mixins.json")
                mods {
                    register(modId) { source(sourceSets.main.get()) }
                    register("${modId}_gametest") { source(gametest) }
                }
            }
        }
        // Opens the browser in a throwaway superflat world, saves a screenshot of each structure to
        // build/autoshot/screenshots and quits, as on the other loaders. -Pstructures=a:b,c:d picks
        // the structures, -Pwidth and -Pheight size the window, -Pshow keeps it visible and
        // -Pjoin=host:port joins that server instead of making a world.
        named("client") {
            this.with(gametest) {
                workingDir.set(layout.buildDirectory.dir("autoshot"))
                systemProperty("jes.autoshot", "screenshots")
                systemProperty("jes.autoshot.structures", findProperty("structures")?.toString() ?: "")
                systemProperty("jes.autoshot.hidden", (!hasProperty("show")).toString())
                systemProperty("jes.autoshot.gui", findProperty("gui")?.toString() ?: "2")
                systemProperty("justenoughstructures.debug", "true")
                args("--mixin.config", "${modId}_gametest.mixins.json")
                args("--width", findProperty("width")?.toString() ?: "1600", "--height", findProperty("height")?.toString() ?: "900")
                systemProperty("jes.autoshot.join", hasProperty("join").toString())
                findProperty("join")?.let { args("--quickPlayMultiplayer", it.toString()) }
                mods {
                    register(modId) { source(sourceSets.main.get()) }
                    register("${modId}_gametest") { source(gametest) }
                }
            }
        }
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
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

if (carriesMixinExtras) {
    jarJar.register()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$mcBuild-${prop("deps.forge_version")}"))

    if (carriesMixinExtras) {
        compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
        implementation("io.github.llamalad7:mixinextras-forge:${prop("deps.mixinextras")}")
        "jarJar"("io.github.llamalad7:mixinextras-forge:${prop("deps.mixinextras")}")
    }

    // The JEI plugin only loads when JEI is installed, so its API is only needed to compile.
    if (has("jei")) compileOnly("mezz.jei:jei-$mcBuild-common-api:${prop("deps.jei")}")
    // The settings screen, drawn by Cloth Config when it's installed.
    if (has("cloth_config")) compileOnly("me.shedaniel.cloth:cloth-config-forge:${prop("deps.cloth_config")}") { isTransitive = false }
    // Explorer's Compass has no API: the link calls its own search.
    if (has("explorers_compass")) compileOnly("maven.modrinth:explorers-compass:${prop("deps.explorers_compass")}")

    if (useDevMods) {
        devMods[mcBuild].orEmpty().forEach { runtimeOnly("maven.modrinth:$it") }
        // Just the one jar, which holds all of JEI: the parts it also lists would be the same packages twice.
        if (has("jei")) runtimeOnly("mezz.jei:jei-$mcBuild-forge:${prop("deps.jei")}") { isTransitive = false }
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
            "description" to modDescription,
            "mod_author" to modAuthor,
            "license" to modLicense,
            "mc_compat" to prop("mod.mc_compat"),
            "forge_min" to prop("deps.forge_min"),
            "pack_formats" to packFormats,
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        exclude("fabric.mod.json")
    }

    named<ProcessResources>("processGametestResources") {
        val props = mapOf("forge_min" to prop("deps.forge_min"), "pack_formats" to packFormats)
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        exclude("fabric.mod.json")
    }

    jar {
        // Where the jar carries MixinExtras, this one without it is never the one shipped.
        if (carriesMixinExtras) {
            archiveClassifier = "slim"
        }
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

    // The shipped jar, with MixinExtras inside it.
    if (carriesMixinExtras) {
        named<Jar>("jarJar") {
            archiveClassifier = null as String?
        }
        assemble { dependsOn("jarJar") }
    }

    // Minecraft must not be set up before Stonecutter has written this node's sources.
    withType<JavaCompile>().configureEach {
        dependsOn("stonecutterGenerate")
    }

    // The runs that load the test mod, under the names the other loaders' nodes use.
    register("runGameTest") {
        group = "forgegradle runs"
        description = "Runs every game test on a headless server and fails unless they all pass."
        dependsOn("runGametestGameTestServer")
    }
    register("runAutoshot") {
        group = "forgegradle runs"
        description = "Saves a screenshot of each structure in a throwaway world, then quits."
        dependsOn("runGametestClient")
    }

    // Every run starts from a fresh world, so nothing one run leaves behind can make the next pass or fail.
    // ForgeGradle registers its run tasks late, so these are found by name once they are.
    matching { it.name == "runGametestGameTestServer" }.configureEach {
        val world = layout.buildDirectory.dir("gametest/world")
        val properties = layout.buildDirectory.file("gametest/server.properties")
        val log = layout.buildDirectory.file("gametest/logs/latest.log")
        doFirst {
            prepareGameTestWorld(world.get().asFile, properties.get().asFile)
        }
        doLast {
            requireGameTestsPassed(log.get().asFile)
        }
    }

    matching { it.name == "runGametestClient" }.configureEach {
        val dir = layout.buildDirectory.dir("autoshot")
        doFirst {
            prepareAutoshotFolder(dir.get().asFile, modId)
        }
    }

    // The test mod as a jar for a real Forge game, so the screenshot gallery can run against a release
    // build in a launcher instance. Never shipped, and kept out of build/libs, where a release looks.
    register<Jar>("gametestJar") {
        from(gametest.output)
        archiveClassifier = "gametest"
        destinationDirectory = layout.buildDirectory.dir("testlibs")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to build/libs/{mod version}/"
        from((if (carriesMixinExtras) named<Jar>("jarJar") else jar).flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$version"))
    }
}
