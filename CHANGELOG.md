# Changelog

## [0.1.0] - Unreleased

First release.

- Press K to open the structure browser: every structure from vanilla, mods and datapacks, searchable and grouped by mod
- Structures are built by their own mod's code, so previews match what generates in a world
  - Previews are built apart from your world, so nothing a structure mod does while building one can change, load or play sounds in it
- Turn, zoom and move the preview, generate a new layout, and hide its top layers to see inside
  - The preview turns slowly on its own and stops while your mouse is over it, which can be turned off in the settings
- The preview can be made to fill the whole screen
- Arrows above the preview step through structures and jump between mods
- Back and Forward along the top of every screen go between the structures, tabs, popups and screens you've looked at, with Backspace, Shift+Backspace or your mouse's side buttons
- Star a structure to keep it in a Favourites section at the top of the list
- Click a chest, barrel or suspicious block to see a real roll of its loot, and roll it again, or every item's chance in it
- The Info tab says where a structure spawns and in which biomes
- The Loot tab lists the containers in the layout and every item's chance of turning up anywhere in the structure, from thousands of rolls of the real loot tables
  - Rare items stand out, and hovering an item shows how many usually come and from which containers
  - Loot tables the structure uses in its other layouts open in a popup of their own
  - Enchantments and potions an item can come with are listed too
- Search for an item to find the structures whose loot can give it, with the best chance first
  - Searching for an item now finds loot in every piece a structure can have
- The Blocks tab is a material list you can copy
- Ids and the Info tab's details only show with advanced tooltips (F3+H), as with items
- Hovering a kind of block, a group of chests or a spawner in the details lights up where they are in the preview, even through walls
- The Mobs tab shows what a structure places, its spawners and what keeps spawning there
  - Spawners that get a random mob list every mob they could have, with each one's chance
- With cheats on, the recovery compass button finds the nearest one of the structure you're looking at, and Ctrl-clicking it takes you there
- The list fills in with small pictures of each structure as you scroll
- The browser remembers how you left it, like whether the preview fills the screen
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
- Pack tools, from a button beside the details' tabs, keeps everything a modpack maker or server owner changes in one place
  - Edit any loot table in a form for every pool, entry, function and condition, pick items from every item in the game, and see one possible roll and every item's chance as you go
  - Edits are saved to the config folder and used from the next /reload, so they ship with a modpack, and Reload now puts everything waiting in use
  - When a mod updates a table you've edited, you can see what changed, merge it in, or keep yours
  - Pick a chest in the browser to switch it to a different loot table, or a brand new one, and undo it later
  - Hide structures or whole mods, keep where a structure's loot is a secret, and write notes for players on its Info tab
  - Choose who can locate, teleport and use Pack tools, and what players see, without leaving the game
  - It's there in singleplayer with cheats on, and on a server for players given its permission, listed by name, or with a permission level
  - Its button can be hidden in the settings
- Mods and modpacks can add their own notes to a structure, shown on its Info tab
- Mods, modpacks and servers can keep where a structure's loot is a secret, while still showing what it can hold
- Just Enough Structures no longer fills the game log with warnings about other mods' structures
- Has to be installed on the server as well
