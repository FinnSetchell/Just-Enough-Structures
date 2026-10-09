/**
 * The source files to leave out for the mods JES links up with that have no build for this version and
 * loader: their version is blank in stonecutter.properties.toml, this keeps their code out of the build,
 * and `//? if <name>` leaves out what refers to it. [has] says whether a mod has a build here.
 */
fun integrationsToLeaveOut(has: (String) -> Boolean): List<String> = buildList {
    if (!has("jei")) add("**/compat/jei/**")
    if (!has("emi")) add("**/compat/emi/**")
    if (!has("rei")) addAll(listOf("**/compat/rei/**", "**/*ReiForgePlugin.java", "**/*ReiNeoForgePlugin.java"))
    if (!has("cloth_config")) add("**/compat/cloth/**")
    if (!has("explorers_compass")) addAll(listOf("**/compat/explorerscompass/**", "**/*ExplorersCompass.java"))
    if (!has("modmenu") || !has("cloth_config")) add("**/fabric/JesModMenu.java")
}
