package com.finndog.justenoughstructures;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JustEnoughStructures {
    public static final String MOD_ID = "justenoughstructures";
    public static final Logger LOGGER = LoggerFactory.getLogger("Just Enough Structures");

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
}
