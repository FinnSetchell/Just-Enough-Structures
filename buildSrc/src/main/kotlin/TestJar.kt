/**
 * The test mod's mixin config as a real game gets it, one line at a time: of its mixins, only the two
 * that stop scripted runs taking the real cursor, the game's grab and the fly camera's. The others are
 * only for the game tests, and so is the config's plugin, which adds more of them on Forge. The two
 * kept are listed in this order in the config, the last one without a comma after it.
 */
fun realGameMixinLine(line: String): String {
    val entry = line.trim().removeSuffix(",").removeSurrounding("\"")
    return when {
        entry == "FlyInputMixin" -> line
        entry == "LeaveTheMouseMixin" -> line.trimEnd().removeSuffix(",")
        entry.endsWith("Mixin") || line.trim().startsWith("\"plugin\"") -> ""
        else -> line
    }
}
