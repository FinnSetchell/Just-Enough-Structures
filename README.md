# Just Enough Structures

A structure browser in the style of JEI. Pick any structure in the game, vanilla or modded, turn it
around in 3D, peel it apart layer by layer and open its chests to see what they can hold.

Structures are built by the code that owns them. Just Enough Structures asks each structure to
generate itself in a sandbox, the same way `/place structure` does, and only draws the result. That
is why it works with structures from any mod or datapack without knowing anything about them.

This is early work. The first release targets Minecraft 1.20.1 on Fabric and Forge, 1.21.1 and
26.1 on Fabric, NeoForge and Forge, and 26.2 on Fabric and NeoForge, with more versions to follow.

## Using it

Press **K** (you can change it in Controls) to open the browser.

- Search the list on the left. A search also finds items in structures' loot: click one to see
  every structure it can come from, best chance first. `@mod` shows one mod's structures and
  `$item` shows the structures whose loot can give an item.
- Drag to turn the preview, shift-drag or right-drag to move it, scroll to zoom. It turns slowly
  on its own while the mouse is away from it. The button in the top corner makes the preview fill
  the screen.
- **New layout** generates the structure again from a new seed. **Layers** hides the top of it
  so you can see inside.
- Click a chest, barrel or suspicious block (or the marker floating over it) to open it and see a
  real roll of its loot. **Roll again** rolls it again.
- The **Info** tab says where it spawns. **Loot** lists every loot table in it with the chance of
  each item, **Blocks** is a material list you can copy, and **Mobs** lists what it places, its
  spawners and what keeps spawning there.
- With cheats on (or as an operator), the recovery compass button finds the nearest one.
  Ctrl-click it to teleport there.

The browser remembers how you left it: markers, the ground, maximise and so on. With
[Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config)
installed, its Config button opens a settings screen for those, where the preview can also be
kept from spinning, and for the server settings of worlds you play or host from your game.

With [JEI](https://modrinth.com/mod/jei), [EMI](https://modrinth.com/mod/emi) or
[REI](https://modrinth.com/mod/rei) installed, an item's recipes get a **Found in structures** page
listing every structure whose loot can give it, with the chance in each container. Click a
structure to open it in the browser.

With [Explorer's Compass](https://modrinth.com/mod/explorers-compass) installed, its structure
screen gets a **Preview** button that opens the picked structure in the browser, and Esc takes you
back. The other way round, while you hold the compass the browser has a button that opens the
compass on the structure you're looking at, and Ctrl-clicking it sets the compass searching
straight away. Searching is left to the compass, with its own costs and rules, and the Info tab
shows what the compass in your hand has found.

## Server settings

`config/justenoughstructures/server.json5` is written the first time a server or world starts.
It can hide structures, or every structure from a mod, so they can't be browsed, previewed or
located, and it sets who can locate and teleport. Out of the box that's operators, the same as
`/locate` and `/tp`. Edit it and run `/reload` to apply the changes.

`show_loot_locations` in the same file keeps where every structure's loot is out of the browser, for
packs where finding hidden chests is part of the fun. There are no loot markers and chests can't be
opened in the preview, but the Loot tab still lists what the loot can be. The server leaves the
loot out of the previews it sends, so this can't be undone on the client.

## Loading times

Nothing is worked out while the game or server starts, or while players join. Item search and the
Found in structures pages need a scan of every structure's loot, which takes a few minutes with a lot of structure
mods. It runs in the background and is saved in `.cache/justenoughstructures`, so it's only done
again when mods, their versions or datapacks change. A dedicated server starts it on its own after
starting up; in singleplayer it waits until something needs it. The pictures in the structure list
are saved there too. The folder is safe to delete.

## Editing loot tables

Players allowed to (`edit_permission` in `server.json5`, the server owner by default, or you in
singleplayer with cheats on) get an **Edit** link on the Loot tab. It opens the table in an editor:
its pools and entries, a form for items, weights, counts and rolls, a JSON view for everything
else, and the odds of your version, rolled as you edit. Saving writes an override to
`config/justenoughstructures/loot_overrides`, a datapack that's always on and applies from the next
`/reload`, so it ships with a modpack like any other config.

It's built not to break anything. An edit the game couldn't load is refused, and a file broken by
hand later is left out, so the mod's own table is used instead. If a mod changes or drops a table
you've overridden, the editor flags it and offers the changes side by side, a merge that brings
theirs into yours, or keeping yours as it is. Nothing is ever deleted: removing an override keeps
a dated copy.

### Changing one container's table

Open a chest in the preview and the same players get a **Change** link. Pick any loot table the
structures use, type an id, or make a new table in the editor and pick it once it's saved. The
change goes in `containers.json` in the same folder, as the template, the container's spot in it
and the table, and is applied as the game loads the template from the next `/reload`. The
structure's own files are never touched, so when its mod updates, the change carries over as long
as that container is still in the same spot. If it isn't, or the block there has changed, the
change is skipped and logged. **Undo** puts the container back and keeps the old entry in the
file.

A change applies wherever that template is used, which can be more than one structure. Only
containers placed from a template can be changed on their own; ones a structure's code places,
like the desert pyramid's chests, get an **Edit table** link instead. Setting `container_changes`
to `false` in `server.json5` stops using every change from the next `/reload` without losing them.

## For mod and modpack authors

A structure can have a file at `data/<namespace>/justenoughstructures/structures/<path>.json` for
the structure `<namespace>:<path>`, in a mod's jar or any datapack. Every field is optional.

```json
{
  "notes": "Shown in an Author's notes section on the Info tab.",
  "author": "Moog",
  "hide_loot_locations": true
}
```

- `notes` is a plain string or any text component, so it can be translated with
  `{"translate": "..."}` and styled. Use `\n` for a new line.
- `author` changes the section's title to "Notes from Moog".
- `hide_loot_locations` hides where just this structure's loot is, the same way
  `show_loot_locations` does for every structure.

Structure names come from the translation key `structure.<namespace>.<path>`, the same one
Explorer's Compass uses.

## Requirements

- Minecraft 1.20.1, 1.21.1, 26.1 (26.1.2 on NeoForge and Forge) or 26.2
- Fabric Loader and Fabric API, Forge, or NeoForge on 1.21.1, 26.1.2 and 26.2
- Installed on both the client and the server. In singleplayer that's just your game.

## Building

The project uses [Stonecutter](https://stonecutter.kikugie.dev/), so each Minecraft version and
loader is its own node under `versions/`.

```
./gradlew :1.20.1-fabric:build
./gradlew :1.20.1-fabric:runGameTest
./gradlew :1.20.1-fabric:runAutoshot
```

The other nodes have the same three, as `:1.21.1-fabric:build`, `:1.20.1-forge:build`,
`:1.21.1-neoforge:build`, `:26.1.2-fabric:build` and so on.
The Forge 1.20.1 jar is reobfuscated, as Forge 1.20.1 runs on SRG names, and the Forge 1.21.1 jar
carries MixinExtras, which that Forge doesn't ship. On Forge and NeoForge `runAutoshot` takes a list
of structures but has no scripted clips.

`runGameTest` generates every vanilla structure headlessly and checks the loot, the network format
and that previews leave the world untouched. `runAutoshot` opens the browser in a throwaway world,
saves a screenshot of a list of structures to `versions/1.20.1-fabric/build/autoshot/screenshots`
and quits. Pick the structures with `-Pstructures=minecraft:igloo,mymod:tower` and add `-Pshow` to
watch it. `-Pmode=open` and `-Pmode=showcase` play a scripted clip instead and save every frame, for
making GIFs.

Gradle needs Java 21 or newer to run, and Java 25 for the 26.x nodes' tasks. The Java each node
compiles with is downloaded for you.

## License

LGPL-3.0-or-later. See [LICENSE](LICENSE).
