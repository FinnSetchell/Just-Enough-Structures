/**
 * The test mod's mixin config as a real game gets it, one line at a time: of its mixins, only the one
 * that stops scripted runs grabbing the real cursor. The others are only for the game tests, and so
 * is the config's plugin, which adds more of them on Forge.
 */
fun realGameMixinLine(line: String): String {
    val entry = line.trim().removeSuffix(",").removeSurrounding("\"")
    return when {
        entry == "LeaveTheMouseMixin" -> line.trimEnd().removeSuffix(",")
        entry.endsWith("Mixin") || line.trim().startsWith("\"plugin\"") -> ""
        else -> line
    }
}
