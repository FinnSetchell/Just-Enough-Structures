package com.finndog.justenoughstructures;

import java.util.Locale;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Makes ids the same way on every Minecraft version, as 1.21 replaced the constructors with these, and tidies them up for showing. */
public final class Ids {
    private Ids() {
    }

    /** An id as the game reads one typed in a command, "minecraft:" when there's no namespace. Throws on a bad one. */
    public static ResourceLocation parse(String id) {
        //? if >=1.21 {
        /*return ResourceLocation.parse(id);
        *///?} else {
        return new ResourceLocation(id);
        //?}
    }

    public static ResourceLocation of(String namespace, String path) {
        //? if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }

    /** The last part of a path tidied up for showing, like "Twilight Forest" for {@code twilight_forest}. */
    public static String pretty(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : last.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    /**
     * Whether an id can name a file of its own under a folder: one with an empty, "." or ".." part
     * would reach outside it. Ids that come over the network are checked before they're made a path.
     */
    public static boolean fileSafe(ResourceLocation id) {
        if (id.getNamespace().equals(".") || id.getNamespace().equals("..")) {
            return false;
        }
        for (String part : id.getPath().split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** The id a registry key names. */
    public static ResourceLocation of(ResourceKey<?> key) {
        //? if >=1.21.11 {
        /*return key.identifier();
        *///?} else {
        return key.location();
        //?}
    }
}
