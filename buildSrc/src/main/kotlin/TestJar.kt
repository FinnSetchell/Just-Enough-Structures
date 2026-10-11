/**
 * Stand-ins for other mods' classes, which the game tests check JES's hooks for those mods against.
 * The test mod's jar for a real game leaves them out: with the real mod installed too, two mods would
 * hold one package, and NeoForge won't start like that.
 */
val standInsToLeaveOut: List<String> = listOf("com/ishland/**")
