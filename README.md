# Just Enough Structures

A structure browser in the style of JEI. Pick any structure in the game, vanilla or modded, turn it
around in 3D, peel it apart layer by layer and open its chests to see what they can hold.

Structures are built by the code that owns them. Just Enough Structures asks each structure to
generate itself in a sandbox, the same way `/place structure` does, and only draws the result. That
is why it works with structures from any mod or datapack without knowing anything about them.

This is early work. The first release targets Minecraft 1.20.1 on Fabric, with more versions and
loaders to follow.

## Using it

Press **K** (you can change it in Controls) to open the browser.

- Search the list on the left. A search also finds items in structures' loot: click one to see
  every structure it can come from, best chance first. `@mod` shows one mod's structures and
  `$item` shows the structures whose loot can give an item.
- Drag to turn the preview, shift-drag or right-drag to move it, scroll to zoom. The button in
  the top corner makes the preview fill the screen.
- **New layout** generates the structure again from a new seed. **Layers** hides the top of it
  so you can see inside.
- Click a chest, barrel or suspicious block (or the marker floating over it) to open it and see a
  real roll of its loot. **Roll again** rolls it again.
- The **Info** tab says where it spawns. **Loot** lists every loot table in it with the chance of
  each item, **Blocks** is a material list you can copy, and **Mobs** lists what it places, its
  spawners and what keeps spawning there.
- With cheats on (or as an operator), the recovery compass button finds the nearest one.
  Ctrl-click it to teleport there.

The browser remembers how you left it: spin, markers, the ground, maximise and so on.

With [JEI](https://modrinth.com/mod/jei) installed, an item's recipes get a **Found in structures**
page listing every structure whose loot can give it, with the chance in each container. Click a
structure to open it in the browser.

## Server settings

`config/justenoughstructures/server.json5` is written the first time a server or world starts.
It can hide structures, or every structure from a mod, so they can't be browsed, previewed or
located, and it sets who can locate and teleport. Out of the box that's operators, the same as
`/locate` and `/tp`. Edit it and run `/reload` to apply the changes.

## Requirements

- Minecraft 1.20.1
- Fabric Loader and Fabric API
- Installed on both the client and the server. In singleplayer that's just your game.

## Building

The project uses [Stonecutter](https://stonecutter.kikugie.dev/), so each Minecraft version and
loader is its own node under `versions/`.

```
./gradlew :1.20.1-fabric:build
./gradlew :1.20.1-fabric:runGameTest
./gradlew :1.20.1-fabric:runAutoshot
```

`runGameTest` generates every vanilla structure headlessly and checks the loot, the network format
and that previews leave the world untouched. `runAutoshot` opens the browser in a throwaway world,
saves a screenshot of a list of structures to `versions/1.20.1-fabric/build/autoshot/screenshots`
and quits. Pick the structures with `-Pstructures=minecraft:igloo,mymod:tower` and add `-Pshow` to
watch it. `-Pmode=open` and `-Pmode=showcase` play a scripted clip instead and save every frame, for
making GIFs.

Gradle needs Java 21 or newer to run. The Java each node compiles with is downloaded for you.

## License

LGPL-3.0-or-later. See [LICENSE](LICENSE).
