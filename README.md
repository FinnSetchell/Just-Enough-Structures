# Just Enough Structures

A structure browser in the style of JEI. Pick any structure in the game, vanilla or modded, turn it
around in 3D, peel it apart layer by layer and open its chests to see what they can hold.

Structures are built by the code that owns them. Just Enough Structures asks each structure to
generate itself in a sandbox, the same way `/place structure` does, and only draws the result. That
is why it works with structures from any mod or datapack without knowing anything about them.

This is early work. The first release targets Minecraft 1.20.1 on Fabric, with more versions and
loaders to follow.

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
```

Gradle needs Java 21 or newer to run. The Java each node compiles with is downloaded for you.

## License

LGPL-3.0-or-later. See [LICENSE](LICENSE).
