package com.finndog.justenoughstructures;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Makes ids the same way on every Minecraft version, as 1.21 replaced the constructors with these. */
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

    /** The id a registry key names. */
    public static ResourceLocation of(ResourceKey<?> key) {
        //? if >=1.21.11 {
        /*return key.identifier();
        *///?} else {
        return key.location();
        //?}
    }
}
