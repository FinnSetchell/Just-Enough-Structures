package com.finndog.justenoughstructures;

import java.nio.file.Path;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JustEnoughStructures {
    public static final String MOD_ID = "justenoughstructures";
    public static final Logger LOGGER = LoggerFactory.getLogger("Just Enough Structures");

    private static Function<String, String> modNames = namespace -> namespace;
    private static Path configDir = Path.of("config");

    private JustEnoughStructures() {
    }

    public static void init() {
    }

    public static ResourceLocation id(String path) {
        //? if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        *///?} else {
        return new ResourceLocation(MOD_ID, path);
        //?}
    }

    /** Set by the loader: turns a namespace into the name of the mod that owns it. */
    public static void setModNames(Function<String, String> lookup) {
        modNames = lookup;
    }

    public static String modName(String namespace) {
        return "minecraft".equals(namespace) ? "Minecraft" : modNames.apply(namespace);
    }

    /** Set by the loader: the game's config folder. */
    public static void setConfigDir(Path dir) {
        configDir = dir;
    }

    /** Where this mod keeps its files, inside the config folder. */
    public static Path configDir() {
        return configDir.resolve(MOD_ID);
    }
}
