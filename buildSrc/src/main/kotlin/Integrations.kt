/**
 * Source files, the mod's and its tests', to leave out for each integration whose deps.* version is
 * blank for this node in stonecutter.properties.toml. Code elsewhere that refers to them sits behind
 * `//? if <name>`. [has] says whether a mod has a build here.
 */
fun integrationsToLeaveOut(has: (String) -> Boolean): List<String> = buildList {
    if (!has("jei")) add("**/compat/jei/**")
    if (!has("emi")) add("**/compat/emi/**")
    if (!has("rei")) addAll(listOf("**/compat/rei/**", "**/*ReiForgePlugin.java", "**/*ReiNeoForgePlugin.java"))
    if (!has("cloth_config")) add("**/compat/cloth/**")
    if (!has("explorers_compass")) addAll(listOf("**/compat/explorerscompass/**", "**/*ExplorersCompass.java"))
    // The link's mixins and tests are named FtbQuests* wherever they are.
    if (!has("ftbquests")) addAll(listOf("**/compat/ftbquests/**", "**/FtbQuests*.java"))
    if (!has("modmenu") || !has("cloth_config")) add("**/fabric/JesModMenu.java")
}
