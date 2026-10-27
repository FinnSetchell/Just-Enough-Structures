# Changelog

## [1.1.0] - Unreleased

### Added
- Found in now lists items structures place in the world, like the End city's elytra
- Found in now lists items other mods add to structure chests
- JEI, EMI and REI show the Found in tab straight away, with its progress, while structures are still being scanned
- A setting to choose whether JEI, EMI and REI show JES's Found in tab, other mods' chest loot tabs, or both
- Clicking a loot table's name in EMI Loot opens the structures that use it

### Fixed
- Loot chances are now more accurate
- Markers outside the preview no longer take clicks from the structure list, Reroll or the layer slider
- Found in no longer lists Book where chests only have enchanted books
- Treasure maps now show their real chance in Found in, the Loot tab and chest previews
- Very rare loot reads <0.1% instead of 0.0%, including the chance of finding at least one
- Keeping a structure's loot secret or hiding a structure in Pack tools now applies straight away, without /reload
- The chances tooltip no longer says things like "shulker boxs"
- /jes open now tells players without the mod, or with a different version, what to install
- Tabs, links and other controls in the browser, its popups and Pack tools now click like buttons and are read by the narrator
- Saving the settings screen while in a world no longer makes Pack tools forget the changes waiting for a /reload
- A comma after the last entry in a settings file no longer makes the whole file be ignored, which on servers showed hidden structures again
- A typo in the server settings no longer undoes them on /reload: the settings already in use are kept until it's fixed
- Chances and chest previews are now the same for every player, whichever dimension the first one to look was in
- Chances and other numbers now look the same on computers set to other languages
- EMI no longer shows a delay warning for JES while the loot list is on its way
- NeoForge no longer warns about JES's logo when the game starts

### Changed
- Clearer wording in the browser, Pack tools and the loot editor, and permission levels read the same on both settings screens

## [1.0.0] - Unreleased

First public release.

- Press K to browse every structure from vanilla, mods and datapacks, built by their own mod's code
- Turn, zoom and slice the 3D preview, or make it fill the screen
- See each structure's loot, with every item's chance from thousands of real rolls
- Search for an item to find the structures that can give it
- See a structure's mobs and spawners, where it spawns, and a block list you can copy
- With cheats on, find the nearest one and teleport there
- Star favourites and write your own notes on any structure
- Works with JEI, EMI, REI, Explorer's Compass, Mod Menu and resource packs
- Pack tools for modpack makers and server owners: edit loot tables, change chest loot and spawner mobs, hide or rename structures, and set who can do what
- Runs on Minecraft 1.20.1 with Fabric or Forge, and 1.21.1, 26.1.2, 26.2 and 26.3 with Fabric, NeoForge or Forge
- Required on the client and server
