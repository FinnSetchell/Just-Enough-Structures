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

        // Explorer's Compass names a few things differently from its Fabric 1.20.1 build. Only the files
        // that use it turn these on, with `//~ compass_names`. Each loader only gets its own: Stonecutter
        // also matches a rule's other side, which would get in the way of another loader's rule. Its
        // NeoForge build names them as its Forge one does.
        if (loader == "forge" || loader == "neoforge") {
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
        }
    }
}
