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

- Search the list on the left, or type `@` and part of a mod's name to show only that mod.
- Drag to turn the preview, shift-drag or right-drag to move it, scroll to zoom.
- **Reroll** generates the structure again from a new seed. **Layers** hides the top of it so you
  can see inside.
- Click a chest, barrel or suspicious block (or the marker floating over it) to open it and see a
  real roll of its loot. **Reroll loot** rolls again.
- The **Loot** tab lists every loot table in the structure with the odds of each item. **Blocks**
  counts what it's made of and **Mobs** lists what it places.

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
watch it.

Gradle needs Java 21 or newer to run. The Java each node compiles with is downloaded for you.

## License

LGPL-3.0-or-later. See [LICENSE](LICENSE).
