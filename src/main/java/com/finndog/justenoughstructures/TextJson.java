package com.finndog.justenoughstructures;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
//? if >=1.21 {
/*import com.finndog.justenoughstructures.server.JesServer;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
*///?}

/** Text as JSON and back, as datapacks write it, the same on every version. */
public final class TextJson {
    private TextJson() {
    }

    public static Component fromJson(JsonElement json) {
        //? if >=1.21 {
        /*return ComponentSerialization.CODEC.parse(ops(), json).getOrThrow(JsonParseException::new);
        *///?} else {
        return Component.Serializer.fromJson(json);
        //?}
    }

    public static JsonElement toJson(Component text) {
        //? if >=1.21 {
        /*return ComponentSerialization.CODEC.encodeStart(ops(), text).getOrThrow(JsonParseException::new);
        *///?} else {
        return Component.Serializer.toJsonTree(text);
        //?}
    }

    //? if >=1.21 {
    /*// Text can name items, which takes the game's registries, when a world is running.
    private static DynamicOps<JsonElement> ops() {
        MinecraftServer server = JesServer.running();
        return server != null ? RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()) : JsonOps.INSTANCE;
    }
    *///?}
}
