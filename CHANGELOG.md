# Changelog

## [Unreleased]

### Added
- A settings button beside a structure's mod name opens that mod's settings, in single player
  ![The settings button beside a mod name](https://i.moogsmods.com/images/be9d9857624f4367ba1a422f7be36306.png)
  - When another mod turns a structure off or replaces it, the Info tab links to that mod's settings
    ![The Info tab linking to a mod's settings](https://i.moogsmods.com/images/bb9bcb39a5cf4c2bb31fbf84517cec88.png)
- A Command only setting: the browser opens only with /jes open, and nothing is added to recipe viewers or other mods' screens; Pack tools changes still apply
- A fly button in the preview: fly through a structure with your movement keys and the mouse, like spectator mode, and click chests and spawners to open them
  ![Flying through a structure](https://i.moogsmods.com/images/f90e35677b9c4e6dbf7eef88e54cf64b.png)

### Fixed
- In packs with Tierify, such as Prominence II, loot chances show many times faster and the full loot list is ready much sooner
- Pictures in the structure list are drawn again after a GUI scale or resource pack change, instead of staying blurry or out of date until you rejoin
- Looking up a potion, a tipped arrow, or a book a chest names (like Swift Sneak) in EMI, JEI or REI now finds the structures that give it
  ![Structures that give a potion, shown in EMI](https://i.moogsmods.com/images/f1c42766c82f400eab4cfc16e11d83fc.png)
- In the loot editor, turning an entry into an alternatives, group or sequence keeps its conditions
- Items that Item Obliterator removes no longer show in loot chances, rolled chests or Found in
- Quitting or reloading while the loot list is still being built no longer starts it over: it carries on from where it stopped
- Hovering a row in the Blocks tab no longer makes the preview stutter on big structures
- The loot editor no longer offers functions on alternatives, groups and sequences before Minecraft 26.3, as the game ignores them there
- Closing the browser no longer keeps the last structure it showed in memory
- Very rare items in the combined chances now read <0.1% too, instead of 0.0%
- The small Nether ruins from Moog's Nether Structures now show a preview

### Changed
- Chances for chest loot no mod changes as it's rolled are now exact, worked out from the loot table, and shown at once
- Loot chances and Found in lists are ready sooner in big packs, on every loader, as chests no mod changes are no longer rolled in full
- On a first start or after your mods change, Found in and item search show most of the loot soon after you join, and the rest fills in as it's found
- EMI, JEI and REI show Just Enough Structures' loot as soon as you join, instead of waiting for the full list
- Items other mods add to chests through loot modifiers are listed straight away, before any chances are rolled
- Rare items NeoForge mods add to chests are now listed with their exact chances
- Loot chances are kept between game starts, so they show at once after the first start until the pack's mods or loot change
- The game's log now says why when loot can't all be listed, or the browser's settings or notes can't be read or saved
- Loot chances for potions, tipped arrows, enchanted books and enchanted gear now show every kind they come in: the picture cycles through them and the tooltip gives each one's chance
  ![The tooltip listing every enchanted book kind and its chance](https://i.moogsmods.com/images/fef311dfd2054d849189bee535102aa0.png)
- On servers that let everyone find structures, players below operator level wait 10 seconds between finds, so it can't be used to slow the server down
- In packs with Loot Integrations, such as Prominence II, Found in now lists everything it can add to a chest, the same every time, and the full loot list is ready sooner
- The preview only spins until you move the camera, and spins again after Reset view or when you open another structure

### Coming soon
- Just Enough Structures will be optional for players: you'll be able to join a server that has it without installing it yourself

## [1.2.0] - 2026-11-05

### Added
- FTB Quests: clicking a Find Structure task opens that structure in Just Enough Structures, and the task's tooltip says so
- Search by structure tag with #, like #minecraft:village. A Find Structure task for a tag opens the browser on that search

### Fixed
- EMI now loads once when you join a world, instead of loading again a few seconds later
  - The exception is the first game after your mods or datapacks change, such as after an update: Just Enough Structures then looks through every structure again, and EMI loads once more when it has finished, to add the full loot lists
- Clicking the top edge of a row in the structure list no longer picks the row above or folds the mod's heading
- A mod's loot table that fails now and then no longer shows chances from only a few rolls, or "Never gives anything"
- JEI no longer pauses and uses more memory each time JES's loot list is sent again, for example after /reload
- The loot list is ready a few seconds sooner after joining a world, as some mods' chests are no longer rolled again every time

## [1.1.0] - 2026-10-27

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

## [1.0.0] - 2026-10-22

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
