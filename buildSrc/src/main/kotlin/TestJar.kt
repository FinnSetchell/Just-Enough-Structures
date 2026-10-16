/**
 * Stand-ins for other mods' classes, which the game tests check JES's hooks for those mods against.
 * The test mod's jar for a real game leaves them out: with the real mod installed too, two mods would
 * hold one package, and NeoForge won't start like that.
 */
val standInsToLeaveOut: List<String> = listOf("com/ishland/**")

/**
 * The test mod's mixin config as a real game gets it, one line at a time: of its mixins, only the one
 * that keeps the scripted harness off the PC's mouse. The others are the game tests' own, and so is
 * the config's plugin, which adds more of them on Forge.
 */
fun realGameMixinLine(line: String): String {
    val entry = line.trim().removeSuffix(",").removeSurrounding("\"")
    return when {
        entry == "LeaveTheMouseMixin" -> line.trimEnd().removeSuffix(",")
        entry.endsWith("Mixin") || line.trim().startsWith("\"plugin\"") -> ""
        else -> line
    }
}
