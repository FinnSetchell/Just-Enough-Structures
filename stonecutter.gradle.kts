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
    constants {
        match(loader, "fabric", "forge", "neoforge")
    }

    replacements {
        // 1.21.11 renamed ResourceLocation to Identifier across the whole codebase. Below that it runs
        // the other way, which would also catch names that merely contain Identifier, like REI's
        // CategoryIdentifier, so those lines turn it off with `//~ !identifier`.
        string(current.parsed >= "1.21.11", "!identifier") {
            replace("ResourceLocation", "Identifier")
        }

        // Explorer's Compass names a few things differently in its Forge build. Only the files that
        // use it turn this on, with `//~ compass_names`.
        string(loader == "forge", "compass_names") {
            replace("EXPLORERS_COMPASS_ITEM", "explorersCompass")
            replace("getAllowedStructureIDs", "getAllowedStructureKeys")
            replace("allowedStructureIDs", "allowedStructureKeys")
            replace("getStructureName", "getPrettyStructureName")
            replace("getStructureID", "getStructureKey")
        }
    }
}
