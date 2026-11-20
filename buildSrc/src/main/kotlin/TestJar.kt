/**
 * The test mod's mixin config as a real game gets it, one line at a time. Of its mixins, only the
 * ones a scripted run in a real game uses: the two that stop it taking the real cursor (the game's
 * grab and the fly camera's), and the four broken-mod and click-counting ones, which do nothing until
 * a check arms them. The rest are only for the game tests. So is the config's plugin, which adds more
 * of them on Forge, so a real game gets one that only notes which mixins were applied.
 *
 * Make one per file read: it notes how many entries of the current list it has kept, so that every
 * kept entry after the first starts with the comma, and leaving entries out never leaves a comma
 * dangling.
 */
class RealGameMixins {
    private var keptInList = 0

    fun line(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("\"plugin\"")) {
            return line.replace(".GameTestMixinPlugin\"", ".RealGameMixinPlugin\"")
        }
        if (trimmed.endsWith("[")) {
            keptInList = 0
            return line
        }
        val entry = trimmed.removeSuffix(",").removeSurrounding("\"")
        if (!entry.endsWith("Mixin")) {
            return line
        }
        if (entry !in KEPT) {
            return ""
        }
        val indent = line.substring(0, line.indexOf('"'))
        return indent + (if (keptInList++ > 0) "," else "") + "\"" + entry + "\""
    }

    private companion object {
        val KEPT = setOf(
            "FlyInputMixin", "LeaveTheMouseMixin",
            "FaultyDrawsMixin", "ThrowingNamesMixin", "ThrowingShapesMixin", "UiClicksMixin",
        )
    }
}
