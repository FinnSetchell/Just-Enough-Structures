package com.finndog.justenoughstructures;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JustEnoughStructures {
    public static final String MOD_ID = "justenoughstructures";
    public static final Logger LOGGER = LoggerFactory.getLogger("Just Enough Structures");

    private static Function<String, String> modNames = namespace -> namespace;
    private static Path configDir = Path.of("config");
    private static Path gameDir = Path.of(".");
    private static Supplier<Map<String, String>> modVersions = Map::of;

    private JustEnoughStructures() {
    }

    public static void init() {
        JesLog.install();
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

    /** Set by the loader: the game's folder, or the server's. */
    public static void setGameDir(Path dir) {
        gameDir = dir;
    }

    /** Where this mod keeps what it works out once and saves for next time. Safe to delete. */
    public static Path cacheDir() {
        return gameDir.resolve(".cache").resolve(MOD_ID);
    }

    /** The debug log, next to the game's own logs. Only written with -Djustenoughstructures.debug=true. */
    public static Path debugLogFile() {
        return gameDir.resolve("logs").resolve("justenoughstructures-debug.log");
    }

    /** Set by the loader: every installed mod's id and version. */
    public static void setModVersions(Supplier<Map<String, String>> versions) {
        modVersions = versions;
    }

    public static Map<String, String> modVersions() {
        return modVersions.get();
    }
}
