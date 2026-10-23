import java.io.File
import java.security.MessageDigest
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

// Other mods' real jars in a game test run, so the tests of JES's hooks into them run against the
// mods themselves. A stand-in for a mod's class proves nothing about the real one.

/** A mod's file on Modrinth: its project, a version id, and the SHA-512 Modrinth lists for the file. */
class ModrinthFile(val project: String, val version: String, val sha512: String)

/**
 * C2ME per node, for -Pc2me. It ships nested jars, which only load from a mods folder, so it goes in
 * the run's mods folder, not on the dev classpath. Forge has no C2ME.
 */
val c2meForGameTests = mapOf(
    // 0.2.0+alpha.11.18
    "1.20.1-fabric" to ModrinthFile("c2me-fabric", "fyt7FtgA",
        "5068a570cde47f105ec17bde14d0004c5f5d3f73b89d8e23a8ac5955a35f4a0dc59ff1d9e0626bfb29e04a7329dc0e9e089f41d637aff6843a6cd43d0573ece9"),
    // 0.4.0-alpha.0.30
    "1.21.1-fabric" to ModrinthFile("c2me-fabric", "KxcgERSN",
        "6fa50f7d626c0d0f9b877c76a2b176d8d22db17bf0530aa07ddd2a7d2f65a6328eb5326376aa28ae474d6c08610842c975548db4efc253f8386fdaefc60e08db"),
    // 0.4.0-alpha.0.123
    "1.21.1-neoforge" to ModrinthFile("c2me-neoforge", "avwWYPbG",
        "c7c485d856fa0118694ae8484e85a714db92ce7886195dbc355257d59d4254da2ce537ac18da8e05c9786c3a98f70cc5ffdfd089ad23b6822f854d72f3caa62d"),
    // 0.4.0-alpha.0.65
    "26.1.2-fabric" to ModrinthFile("c2me-fabric", "to1sDRnG",
        "15c374f57ce6dc867ed7d3529bed43d2894b33a9f83cb0d73fb45e047ddef84b408bfbee7470b596a0afde4726205aac39a4b3ecc8f60ba7c0fe77e13943d97f"),
    // 0.4.0-alpha.0.100
    "26.1.2-neoforge" to ModrinthFile("c2me-neoforge", "LEHKpyxE",
        "59f78d650877c87d6bd8c938f4cc83fca8633c15596b4f5c0cf65ccd831e43f6ce304ba57f5f87f6a6c27bbde8de332e57f66ef2b0964d82db19a9117c473d30"),
    // 0.4.2-alpha.0.60
    "26.2-fabric" to ModrinthFile("c2me-fabric", "4wjXxrDQ",
        "079ac7da5de422418a599e0fef1963b3094aa010b057582bc25233fa65bd62a5bea8659c7905eabbd4f6943914d56106c5c6ebe011b022a84583a061a99cc2d4"),
    // 0.4.2-alpha.0.98
    "26.2-neoforge" to ModrinthFile("c2me-neoforge", "XpuI7aLS",
        "9f760b83c09e7b02d65ca9742965aeb5129f86758d80a97648b71f7e3b1a2789d5e46d28b62ca5a2ca54697c4e7d4e12936e432db63f27210a39f4c00f228796"),
    // 0.4.2-alpha.0.106
    "26.3-fabric" to ModrinthFile("c2me-fabric", "gKKkuSYc",
        "047ed3fe3869d0185c6d1e94fd1e725b2ad40047e4929f22692090570af28d0075849cb65335b602658859c7d437cce51c94082c0c798617314764cfdc0a1d71"),
    // 0.4.2-alpha.0.105
    "26.3-neoforge" to ModrinthFile("c2me-neoforge", "Y11FDzxb",
        "7680706003eef8405f08e984d7c740de800f7f703f71cfac21be6c2bdd440d4948a5519b1a463e85f0010ea1d550e3c9e68d05ab3ac7d0bf24b32f129494526e"),
)

/**
 * The other mods a node's game test run was asked for. -Pc2me gives it the real C2ME, loaded in the
 * order a real Fabric instance loads mods in, sorted by id; -Pc2me=shuffled leaves the development
 * game's own random order, which on Fabric changes from run to run. Without it the run has none.
 */
class GameTestMods(project: Project) {
    private val node = project.name
    private val asked = project.findProperty("c2me")?.toString()

    /**
     * True when -Pc2me is set, unless it's false. Passed to the tests as jes.gametest.c2me, and they
     * fail if C2ME isn't loaded.
     */
    val c2me = asked != null && asked != "false"

    /** Whether Fabric's development game is left to shuffle the mods it loads. */
    val shuffled = asked == "shuffled"

    private val pinned = c2meForGameTests[node]
    private val jars: FileCollection = if (c2me && pinned != null) {
        project.configurations.detachedConfiguration(project.dependencies.create("maven.modrinth:${pinned.project}:${pinned.version}"))
            .apply { isTransitive = false }
    } else {
        project.files()
    }

    /**
     * Puts them in the run's mods folder, which holds nothing else, so a run never finds what the one
     * before it was given. Each jar is checked against the hash Modrinth lists for it first.
     */
    fun fill(folder: File) {
        if (c2me && pinned == null) {
            throw GradleException("C2ME has no build for $node, so its game tests can't have it")
        }
        if (!folder.deleteRecursively() || !folder.mkdirs()) {
            throw GradleException("Couldn't clear $folder")
        }
        for (jar in jars) {
            val sha512 = MessageDigest.getInstance("SHA-512").digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
            if (sha512 != pinned?.sha512) {
                throw GradleException("$jar isn't the file Modrinth lists for ${pinned?.project} ${pinned?.version}")
            }
            jar.copyTo(File(folder, jar.name))
        }
    }
}
