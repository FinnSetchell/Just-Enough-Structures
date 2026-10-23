package com.finndog.justenoughstructures.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
/*import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.RegistryOps;
*///?} else {
import net.minecraft.nbt.CompoundTag;
//?}

/**
 * Makes a loot table's entry give one particular stack: the item, how many of it, and anything that
 * sets it apart from a plain item of its kind, like a name, enchantments or damage. The loot editor
 * uses it to take the item a player is holding, which saves writing that out by hand.
 */
public final class StackEntry {
    /** The functions an entry gets its count and its data from, which taking a stack replaces. Other functions stay. */
    private static final List<String> REPLACED = List.of("set_count", "set_components", "set_nbt", "set_custom_data");

    private StackEntry() {
    }

    /**
     * Turns {@code entry} into an item entry for {@code stack}. Its weight, quality and conditions are
     * kept, as are functions that don't set a count or the item's data. {@code registries} are the
     * world's, used to encode the stack's data.
     */
    public static void fill(JsonObject entry, ItemStack stack, HolderLookup.Provider registries) {
        JsonArray functions = new JsonArray();
        if (stack.getCount() > 1) {
            JsonObject count = new JsonObject();
            count.addProperty("function", "minecraft:set_count");
            count.addProperty("count", stack.getCount());
            functions.add(count);
        }
        JsonObject data = data(stack, registries);
        if (data != null) {
            functions.add(data);
        }
        if (entry.get("functions") instanceof JsonArray before) {
            for (JsonElement function : before) {
                String kind = function.isJsonObject() && function.getAsJsonObject().has("function") ? function.getAsJsonObject().get("function").getAsString() : "";
                if (!REPLACED.contains(kind.substring(kind.indexOf(':') + 1))) {
                    functions.add(function);
                }
            }
        }
        // Remove fields that only other entry types use, in case it was one.
        for (String key : List.of("value", "items", "expand", "children")) {
            entry.remove(key);
        }
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (functions.isEmpty()) {
            entry.remove("functions");
        } else {
            entry.add("functions", functions);
        }
    }

    /** The function that gives an item this stack's data, or null for a plain one of its kind. */
    private static JsonObject data(ItemStack stack, HolderLookup.Provider registries) {
        JsonObject function = new JsonObject();
        //? if >=1.21 {
        /*// Since 1.20.5 an item's data is its components, and a stack knows which of them differ from its kind's.
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return null;
        }
        function.addProperty("function", "minecraft:set_components");
        function.add("components", DataComponentPatch.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), patch).getOrThrow());
        *///?} else {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        function.addProperty("function", "minecraft:set_nbt");
        function.addProperty("tag", tag.toString());
        //?}
        return function;
    }
}
