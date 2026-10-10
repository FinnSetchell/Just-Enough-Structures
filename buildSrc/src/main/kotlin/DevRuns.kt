import java.io.File
import java.util.zip.GZIPInputStream
import org.gradle.api.GradleException

// What the loaders' dev runs set up before they start, and check once they're done.

/** Clears what the last screenshot run left behind, and sets the options every run is shot with. */
fun prepareAutoshotFolder(root: File, modId: String) {
    // The browser remembers its toggles in config, which would carry over from the last run.
    for (old in listOf("saves", "screenshots", "config/$modId")) {
        if (!File(root, old).deleteRecursively()) {
            throw GradleException("Couldn't clear ${File(root, old)}")
        }
    }
    root.mkdirs()
    // Skip first-launch screens and keep the game running when the window isn't focused. No
    // clouds, so the world behind the screen doesn't change from frame to frame.
    File(root, "options.txt").writeText(
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\njoinedFirstServer:true\n" +
            "skipMultiplayerWarning:true\nsoundCategory_master:0.0\nguiScale:2\nrenderClouds:\"false\"\n"
    )
}

/**
 * A fresh world for the game test server, so nothing one run leaves behind can make the next pass or
 * fail. Forge's and NeoForge's test servers make it from server.properties, where Fabric's makes a
 * superflat one with seed 0 and no structures, so that's what it says. The last run's logs go too, as
 * a run is judged by what it logged.
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
