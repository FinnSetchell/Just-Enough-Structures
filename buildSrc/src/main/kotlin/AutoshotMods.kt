import java.io.File
import java.security.MessageDigest
import org.gradle.api.GradleException
import org.gradle.api.Project

// Other mods' real jars in the screenshot run, to see JES beside the mods themselves.

/**
 * EMI Loot per node, for -Pemi_loot, with the two mods it needs: Fzzy Config and Fabric Language
 * Kotlin, the same files as the Prominence II modpack has. Fabric Language Kotlin keeps Kotlin in
 * nested jars, which only load from a mods folder, so the jars go in a folder of their own that the
 * game is given with fabric.addMods, not on the dev classpath.
 */
val emiLootForAutoshot = mapOf(
    // EMI Loot 0.7.9, Fzzy Config 0.7.0 and Fabric Language Kotlin 1.13.9
    "1.20.1-fabric" to listOf(
        ModrinthFile("emi-loot", "vxly1O9p",
            "52f46e46f06c133081dff5eb31a9f844eff8614ddcaa07d24455eab90ebfa50c5b6605494738de721af125e9180e4c13b985de9133e1a07e02a525bf701305c4"),
        ModrinthFile("fzzy-config", "tbpj74o7",
            "d6fbad0e32508fe0f33719610d6c70548e5f4cafc3eb359b93ff58ed3a4346895c8a5fc0b38b739b5ca0cb3512d63a7c453f1a2c325df5928fad3f0992520497"),
        ModrinthFile("fabric-language-kotlin", "ViT4gucI",
            "498672ee88cf703685026e74f82a85e30d980c62a1c8cc14744cb73add09a857db8d585b405e19f558ec490613642750eb00e09d8ef5a3c9578bc52b53568d51"),
    ),
)

/**
 * The other mods a node's screenshot run was asked for: EMI Loot with -Pemi_loot, beside EMI
 * (-Pviewer=emi). Like the other dev mods it's off on CI and with -Pdev_mods=false, and never part of
 * a built jar.
 */
class AutoshotMods(project: Project, devMods: Boolean, private val viewer: String) {
    private val node = project.name

    /** Whether the run has EMI Loot. Passed to the game as the folder to load it from. */
    val emiLoot = devMods && project.hasProperty("emi_loot")

    private val pinned = if (emiLoot) emiLootForAutoshot[node].orEmpty() else listOf()
    private val jars = pinned.map { file ->
        file to project.configurations.detachedConfiguration(project.dependencies.create("maven.modrinth:${file.project}:${file.version}"))
            .apply { isTransitive = false }
    }

    /**
     * Puts them in [folder], which holds nothing else, so a run never finds what the one before it
     * was given. Each jar is checked against the hash Modrinth lists for it first.
     */
    fun fill(folder: File) {
        if (!emiLoot) return
        if (pinned.isEmpty()) {
            throw GradleException("EMI Loot isn't pinned for $node, so its screenshot run can't have it")
        }
        if (viewer != "emi") {
            throw GradleException("-Pemi_loot needs -Pviewer=emi")
        }
        if (!folder.deleteRecursively() || !folder.mkdirs()) {
            throw GradleException("Couldn't clear $folder")
        }
        for ((file, configuration) in jars) {
            val jar = configuration.singleFile
            val sha512 = MessageDigest.getInstance("SHA-512").digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
            if (sha512 != file.sha512) {
                throw GradleException("$jar isn't the file Modrinth lists for ${file.project} ${file.version}")
            }
            jar.copyTo(File(folder, jar.name))
        }
    }
}
