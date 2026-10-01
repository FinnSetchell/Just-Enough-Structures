# Changelog

## [0.1.0] - Unreleased

First release.

- Press K to open the structure browser: every structure from vanilla, mods and datapacks, searchable and grouped by mod
- Structures are built by their own mod's code, so previews match what generates in a world
- Turn, zoom and move the preview, generate a new layout, and hide its top layers to see inside
- The preview can be made to fill the whole screen
- Arrows above the preview step through structures and jump between mods
- Click a chest, barrel or suspicious block to see a real roll of its loot, and roll it again
- The Info tab says where a structure spawns and in which biomes
- The Loot tab shows the chance of every item in each chest, from thousands of rolls of the real loot table
  - Rare items stand out, and hovering an item shows its chance of turning up anywhere in the structure
  - Enchantments and potions an item can come with are listed too
- Search for an item to find the structures whose loot can give it, with the best chance first
  - Searching for an item now finds loot in every piece a structure can have
- The Blocks tab is a material list you can copy
- The Mobs tab shows what a structure places, its spawners and what keeps spawning there
- With cheats on, the recovery compass button finds the nearest one of the structure you're looking at, and Ctrl-clicking it takes you there
- The list fills in with small pictures of each structure as you scroll
- The browser remembers how you left it, like whether the preview spins and fills the screen
- With Mod Menu and Cloth Config installed, there's a settings screen for the browser and for the worlds you host
- Server owners can hide structures or whole mods from the browser, and choose who can locate and teleport
- With JEI, EMI or REI installed, looking up how to get an item also lists the structures it's found in, and clicking one opens it in the browser
- With Explorer's Compass installed, you can jump between the compass and the browser
  - The compass's structure list gets a Preview button that opens the structure in the browser
  - While holding the compass, a button in the browser opens the compass on the structure you're looking at
  - Ctrl-click that button to set the compass searching for it straight away
  - The Info tab shows what the compass in your hand has found
- Item search and the Found in structures pages are ready straight away after the first time, as the loot scan is saved and only redone when mods or datapacks change
- The structure list keeps its pictures between sessions
- Modpack makers can edit loot tables in game, from the Loot tab
  - A form for items, weights, counts and rolls, JSON for everything else, and the odds of your version as you edit
  - Edits are saved to the config folder and used from the next /reload, so they ship with a modpack
  - When a mod updates a table you've edited, you can see what changed, merge it in, or keep yours
  - A single chest in a structure can be switched to a different loot table, or a brand new one, from its popup in the preview
- Mods and modpacks can add their own notes to a structure, shown on its Info tab
- Mods, modpacks and servers can keep where a structure's loot is a secret, while still showing what it can hold
- Has to be installed on the server as well
