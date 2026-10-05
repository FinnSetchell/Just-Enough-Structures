plugins {
    id("dev.kikugie.stonecutter")
}

// Must stay a string literal: Stonecutter rewrites this line when switching versions.
stonecutter active "1.20.1-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Lets `[fabric."1.20.1"]` tables in stonecutter.properties.toml resolve for this node.
    properties {
        tags(version, loader)
    }

    // `//? if fabric {` and friends, for the few places loader code shares a file with common code.
    // Likewise `//? if cloth_config {` and the rest for each mod JES links up with: a node only builds
    // the pages and links for those that have a build for its version and loader, by their version
    // being set in stonecutter.properties.toml.
    constants {
        match(loader, "fabric", "forge", "neoforge")
        for (mod in listOf("jei", "emi", "rei", "cloth_config", "explorers_compass", "modmenu")) {
            put(mod, !properties.getOrNull<String>("deps.$mod").isNullOrEmpty())
        }
    }

    replacements {
        // 1.21.11 renamed ResourceLocation to Identifier across the whole codebase. Below that it runs
        // the other way, which would also catch names that merely contain Identifier, like REI's
        // CategoryIdentifier, so those lines turn it off with `//~ !identifier`.
        string(current.parsed >= "1.21.11", "!identifier") {
            replace("ResourceLocation", "Identifier")
        }

        // Screens: 1.20.2 gave scrolling a sideways amount, and moving to the end of a text box whether to select.
        string(current.parsed >= "1.21") {
            replace("mouseScrolled(double mouseX, double mouseY, double delta)", "mouseScrolled(double mouseX, double mouseY, double scrollX, double delta)")
            replace("super.mouseScrolled(mouseX, mouseY, delta)", "super.mouseScrolled(mouseX, mouseY, scrollX, delta)")
            replace(".moveCursorToEnd()", ".moveCursorToEnd(false)")
        }

        // Classes that only moved package.
        string(current.parsed >= "1.21") {
            replace("net.minecraft.world.level.chunk.ChunkStatus;", "net.minecraft.world.level.chunk.status.ChunkStatus;")
        }

        // 1.21.2 renamed getting a registry out of the game's registries. What else it renamed on
        // registries is in Regs, as those names now mean something else.
        string(current.parsed >= "1.21.2") {
            replace(".registryOrThrow(", ".lookupOrThrow(")
            replace(".registryAccess().registry(", ".registryAccess().lookup(")
        }

        // 26.1 moved Util, and screens now collect what to draw rather than drawing it, so the
        // graphics they're handed got a new name and its methods new names. These only catch calls
        // on a graphics named g, which every screen here uses. What changed more than its name is
        // in Gui and the bridges in BackdropScreen and JesButton.
        string(current.parsed >= "26.1") {
            replace("net.minecraft.Util", "net.minecraft.util.Util")
            replace("GuiGraphics", "GuiGraphicsExtractor")
            replace("g.drawString(", "g.text(")
            replace("g.drawCenteredString(", "g.centeredText(")
            replace("g.drawWordWrap(", "g.textWithWordWrap(")
            replace("g.renderItem(", "g.item(")
            replace("g.renderFakeItem(", "g.fakeItem(")
            replace("g.renderItemDecorations(", "g.itemDecorations(")
            replace("g.renderTooltip(", "g.setTooltipForNextFrame(")
            replace("g.renderComponentTooltip(", "g.setComponentTooltipForNextFrame(")
            replace("g.hLine(", "g.horizontalLine(")
            replace("g.vLine(", "g.verticalLine(")
            replace("g.renderOutline(", "g.outline(")
        }

        // Fabric API for 26.1 took the game's own names for these.
        if (loader == "fabric") {
            string(current.parsed >= "26.1") {
                replace("PayloadTypeRegistry.playC2S()", "PayloadTypeRegistry.serverboundPlay()")
                replace("PayloadTypeRegistry.playS2C()", "PayloadTypeRegistry.clientboundPlay()")
                replace("client.keybinding.v1.KeyBindingHelper", "client.keymapping.v1.KeyMappingHelper")
                replace("KeyBindingHelper.registerKeyBinding(", "KeyMappingHelper.registerKeyMapping(")
                replace("Screens.getButtons(", "Screens.getWidgets(")
            }
            // Its game tests take Fabric's own annotation, with the vanilla one's settings under new
            // names. A test's batch becomes the environment it runs in, one for each batch.
            string(current.parsed >= "26.1") {
                replace("import net.minecraft.gametest.framework.GameTest;", "import net.fabricmc.fabric.api.gametest.v1.GameTest;")
                replace("@GameTest(template = ", "@GameTest(structure = ")
                replace("timeoutTicks = ", "maxTicks = ")
                replace("batch = \"", "environment = \"justenoughstructures_gametest:")
            }
        }
        // NeoForge for 26.1 has no annotation for game tests, so the test mod has one of its own, with
        // the same settings as Fabric's, and registers what it marks. Only on these nodes, as elsewhere
        // Stonecutter would run the rules backwards.
        if (loader == "neoforge" && current.parsed >= "26.1") {
            string(true) {
                replace("import net.minecraft.gametest.framework.GameTest;", "import com.finndog.justenoughstructures.gametest.neoforge.GameTest;")
                replace("@GameTest(template = ", "@GameTest(structure = ")
                replace("timeoutTicks = ", "maxTicks = ")
                replace("batch = \"", "environment = \"justenoughstructures_gametest:")
            }
        }

        // Explorer's Compass names a few things differently from its Fabric 1.20.1 build. Only the files
        // that use it turn these on, with `//~ compass_names`. Each loader only gets its own: Stonecutter
        // also matches a rule's other side, which would get in the way of another loader's rule. Its
        // NeoForge build names them as its Forge one does, until 26.1.
        if (loader == "forge" || loader == "neoforge" && current.parsed < "26.1") {
            string(true, "compass_names") {
                replace("EXPLORERS_COMPASS_ITEM", "explorersCompass")
                replace("getAllowedStructureIDs", "getAllowedStructureKeys")
                replace("allowedStructureIDs", "allowedStructureKeys")
                replace("getStructureName", "getPrettyStructureName")
                replace("getStructureID", "getStructureKey")
            }
        }
        if (loader == "fabric") {
            string(current.parsed >= "1.21", "compass_names") {
                replace("getStructureID()", "getStructureId()")
                replace("compass.getState(", "compass.getCompassState(")
            }
            // Its build for 26.1 moved its item to another package, and spells ID as Id.
            string(current.parsed >= "26.1", "compass_names") {
                replace("explorerscompass.items.", "explorerscompass.item.")
                replace("getAllowedStructureIDs", "getAllowedStructureIds")
                replace("ExplorersCompass.allowedStructureIDs", "ExplorersCompass.allowedStructures")
            }
        }
        // Its NeoForge build for 26.1 took the Fabric build's names, keeping only its item's own. Only on
        // these nodes, as run backwards on older NeoForge ones it would undo the rule above.
        if (loader == "neoforge" && current.parsed >= "26.1") {
            string(true, "compass_names") {
                replace("EXPLORERS_COMPASS_ITEM", "explorersCompass")
                replace("explorerscompass.items.", "explorerscompass.item.")
                replace("getAllowedStructureIDs", "getAllowedStructureIds")
                replace("ExplorersCompass.allowedStructureIDs", "ExplorersCompass.allowedStructures")
                replace("getStructureID()", "getStructureId()")
                replace("compass.getState(", "compass.getCompassState(")
            }
        }
    }
}
