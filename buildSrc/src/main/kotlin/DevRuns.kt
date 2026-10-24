import java.io.File
import java.util.zip.GZIPInputStream
import org.gradle.api.GradleException

// What the loaders' dev runs set up before they start, and check once they're done.

/**
 * Clears what the last screenshot run left behind, and sets the options every run is shot with. A
 * resource pack put in the run's resourcepacks folder is switched on, to see the browser as that
 * pack makes it.
 *
 * The screenshot run (runAutoshot) opens the browser in a throwaway superflat world, saves
 * screenshots to build/autoshot/screenshots and quits. Its flags:
 * - -Pstructures=a:b,c:d picks the structures.
 * - -Pmode=name plays a scripted tour or recording instead (the modes are in Scenarios.java).
 * - -Pwidth and -Pheight size the window, 1600 by 900 by default.
 * - -Pgui sets the GUI scale, 2 by default.
 * - -Pshow keeps the window visible.
 * - -Pjoin=host:port joins that server instead of making a world.
 * - -Pusername=name joins as that player. Not on Forge 1.21.1 and later.
 * - -Pemi_loot adds EMI Loot beside EMI (-Pviewer=emi). Fabric 1.20.1 only (AutoshotMods.kt).
 */
fun prepareAutoshotFolder(root: File, modId: String) {
    // The browser remembers its toggles in config, which would carry over from the last run.
    for (old in listOf("saves", "screenshots", "config/$modId")) {
        if (!File(root, old).deleteRecursively()) {
            throw GradleException("Couldn't clear ${File(root, old)}")
        }
    }
    root.mkdirs()
    val packs = File(root, "resourcepacks").listFiles { file -> file.name.endsWith(".zip") }.orEmpty().sortedBy { it.name }
    // Skip first-launch screens and keep the game running when the window isn't focused. No
    // clouds, so the world behind the screen doesn't change from frame to frame.
    File(root, "options.txt").writeText(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\njoinedFirstServer:true\n" +
            "skipMultiplayerWarning:true\nsoundCategory_master:0.0\nguiScale:2\nrenderClouds:\"false\"\n" +
            if (packs.isEmpty()) "" else
                "resourcePacks:[" + (listOf("vanilla") + packs.map { "file/${it.name}" }).joinToString(",") { "\"$it\"" } + "]\n"
    )
}

/**
 * A fresh world for the game test server, so nothing one run leaves behind can make the next pass or
 * fail. GameTestMods.fill does the same for the run's mods folder. Forge's and NeoForge's test
 * servers make the world from server.properties, so this writes the world Fabric's test server makes:
 * superflat, seed 0, no structures. Old logs go too, as the result is read from the log.
 */
fun prepareGameTestWorld(world: File, properties: File) {
    // The server keeps its logs beside server.properties.
    for (old in listOf(world, File(properties.parentFile, "logs"))) {
        if (!old.deleteRecursively()) {
            throw GradleException("Couldn't clear $old")
        }
    }
    properties.parentFile.mkdirs()
    properties.writeText("level-type=minecraft:flat\nlevel-seed=0\ngenerate-structures=false\n")
}

/**
 * A test server that fails to start still exits cleanly, so this goes by what it logged. At midnight
 * the game starts a new latest.log and zips the one before beside it, so a run that crosses midnight
 * has its result in the zipped one.
 */
fun requireGameTestsPassed(log: File) {
    val passed = Regex("All \\d+ required tests passed")
    val zipped = log.parentFile.listFiles { file -> file.name.endsWith(".log.gz") }.orEmpty()
    val logged = sequenceOf(log).filter { it.exists() }.map { it.readText() } +
        zipped.asSequence().map { file -> GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() } }
    if (logged.none { passed.containsMatchIn(it) }) {
        throw GradleException("Not every game test passed, see $log")
    }
}
