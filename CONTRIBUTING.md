# Contributing to Just Enough Structures

## How the repo is laid out

Everything happens on `main`. One source tree builds every Minecraft version and every loader, using
[Stonecutter](https://stonecutter.kikugie.dev/).

Each Minecraft version and loader pair is its own Gradle project, called a node, for example
`1.21.1-fabric`. The nodes are listed in `settings.gradle.kts` and Gradle creates them under `versions/`.
A node is compiled against one Minecraft version, and its jar runs on the versions in its row:

| Node | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| `1.20.1` | 1.20.1 | 1.20.1 | no |
| `1.21.1` | 1.21-1.21.1 | 1.21.1 | 1.21.1 |
| `26.1.2` | 26.1-26.1.2 | 26.1.2 | 26.1.2 |
| `26.2` | 26.2 | 26.2 | 26.2 |
| `26.3` | 26.3 | 26.3 | 26.3 |

## Setup

You need Java 25 to run Gradle. The Java 17 and 21 toolchains that the older nodes use are
downloaded for you.

In IntelliJ, install the Stonecutter Dev plugin. It adds a dropdown for picking which version the
code is shown as.

## Building

Build one node:

```bash
./gradlew :1.21.1-fabric:build
```

Build every node and copy the jars to `build/libs/<mod version>/`:

```bash
./gradlew buildAndCollect
```

The first full build takes a while, because every node sets up its own copy of Minecraft.

The Forge 1.20.1 jar is reobfuscated, as Forge 1.20.1 runs on SRG names, and the Forge 1.21.1 jar
carries MixinExtras, which that Forge doesn't ship.

## Testing

Every node has the same three tasks:

```bash
./gradlew :1.20.1-fabric:runClient
./gradlew :1.20.1-fabric:runGameTest
./gradlew :1.20.1-fabric:runAutoshot
```

`runClient` starts a development game with a set of popular structure mods to try the browser on.
`-Pdev_mods=false` leaves them out.

`runGameTest` generates every vanilla structure headlessly and checks the loot, the network format
and that previews leave the world untouched. CI runs it on every node.

`runAutoshot` opens the browser in a throwaway world, saves a screenshot of a list of structures to
`versions/<node>/build/autoshot/screenshots` and quits. Pick the structures with
`-Pstructures=minecraft:igloo,mymod:tower` and add `-Pshow` to watch it. `-Pmode=open` and
`-Pmode=showcase` play a scripted clip instead and save every frame. Clips play on Fabric, NeoForge
and Forge 1.20.1.

On Fabric, `runLoadTest` starts a dedicated server where crowds of simulated players use the browser,
and writes how it held up to `versions/<node>/build/loadtest/report.md`. `-Pplayers=30,60,100` sets the
crowds and `-Pminutes=3` how long each browses. Like any dev server it needs an accepted `eula.txt` in
that folder.

## Where things are

- `src/main/java` - all the Java. Loader code goes in a `fabric`, `forge` or `neoforge` package, and
  each loader's build leaves out the other two.
  - `capture` builds a structure apart from the world by running its own generation code.
  - `catalog` lists the structures and what's known about each.
  - `client` has the screens and the preview.
  - `loot` rolls loot tables, works out the odds and keeps the index item search uses.
  - `server` has the server settings, commands, permissions and Pack tools.
  - `overrides` applies what Pack tools changes: loot tables, containers and spawners.
  - `network` is what the server and client send each other.
  - `compat` links up with JEI, EMI, REI, Explorer's Compass and Cloth Config.
- `src/main/resources` - resources every loader uses, like the lang file.
- `src/<loader>/resources` - loader metadata and that loader's own files.
- `src/gametest` - the game tests, and the autoshot runner that takes screenshots and plays clips.
- `stonecutter.properties.toml` - settings for each node: the Minecraft version it compiles against,
  the range it supports and its dependency versions.
- `stonecutter.gradle.kts` - the names that changed between versions, which Stonecutter swaps for you.
- `build.fabric.gradle.kts`, `build.forge.gradle.kts`, `build.neoforge.gradle.kts` - one build script
  per loader. `build.forge-legacy.gradle.kts` builds Forge 1.20.1.

## Writing code for different versions

The code in the repo is written for 1.20.1 Fabric. When something is different on other versions,
wrap it in a Stonecutter comment:

```java
public static ResourceLocation parse(String id) {
    //? if >=1.21 {
    /*return ResourceLocation.parse(id);
    *///?} else {
    return new ResourceLocation(id);
    //?}
}
```

The code for the versions you're not looking at stays commented out. Switch versions with the
IntelliJ plugin, or with `./gradlew "Set active project to 26.3-fabric"`. Run
`./gradlew "Reset active project"` before you commit, so the repo goes back to 1.20.1 Fabric.

`//? if fabric {`, `//? if forge {` and `//? if neoforge {` work the same way, for the few places
loader code shares a file with common code. So do `//? if jei {` and the like for the mods JES links
up with, which are only built for nodes that mod has a version for.

Some things to know:

- Write `ResourceLocation`. Stonecutter changes it to `Identifier` on the 26.x nodes and back on the
  rest. Going back also hits longer names with `Identifier` in them, like REI's `CategoryIdentifier`,
  so a file that uses one turns the swap off with `//~ !identifier` near its top.
- The other swaps in `stonecutter.gradle.kts` are plain text too, so pick names that don't contain
  them.
- Don't mix `&&` and `||` in one condition. Stonecutter reads them left to right, so
  `a && b || c && d` means `((a && b) || c) && d`. Use separate `else if` branches instead.
- A `//?` inside code another block has commented out is never read. Put blocks side by side, not
  inside each other.
- Don't start a version block with a comment line. Stonecutter thinks the block is already commented
  out and breaks it.
- A swap runs backwards on the nodes its condition doesn't match. One that's only meant for some
  nodes goes inside a Kotlin `if` with `string(true)`, like the 26.2 and 26.3 ones.
- If a call changes between versions, a small helper like `Ids.parse` is usually easier to read than
  lots of version blocks.

## Text

Everything a player can read goes in `src/main/resources/assets/justenoughstructures/lang/en_us.json`,
including messages the server sends: send a translatable component, not finished English. Log
messages stay in the code.

Translations are welcome. Add a file for your language next to `en_us.json`.

## Adding a Minecraft version

1. Add the node to `settings.gradle.kts`.
2. Add its settings to `stonecutter.properties.toml`.
3. Switch to it and fix whatever doesn't compile, using version blocks.
4. Run its game tests, and compare a `runAutoshot` of a few structures with the previous version's.
5. Add its game tests to `.github/workflows/build.yml`.
6. Add a build for it to each target in `.github/moogs-publish.yml`.

## Issues

Bugs and ideas go in [Issues](https://github.com/FinnSetchell/Just-Enough-Structures/issues),
through the bug report or feature request form. For questions, the
[Discord server](https://moogsmods.com/discord?r=github-jes) gets replies faster.

## Pull requests

- Keep a pull request to one change.
- Build every node with `./gradlew buildAndCollect`, and run the game tests on the nodes you changed.
  CI runs both on every pull request.
- If players will notice the change, add a line for it at the top of `CHANGELOG.md`. Write it for
  someone who has never seen the code: what they'll see, not how it works.
- Commit messages are one short line saying what changed, like "Stop mobs in previews shaking".

## Releasing

Move the changes into a section for the new version in `CHANGELOG.md` and set `mod_version` in
`gradle.properties`. Then tag `main` once for each loader, for example `0.2.0-fabric`, `0.2.0-forge`
and `0.2.0-neoforge`. An alpha keeps `mod_version` as it is and is tagged `0.2.0-alpha.1-fabric` and
so on.

Each tag builds every version for that loader and posts a review card in Discord. Which Minecraft
versions each jar is published for is set in `.github/moogs-publish.yml`.
