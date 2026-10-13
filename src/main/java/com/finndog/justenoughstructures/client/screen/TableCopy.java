package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;

/**
 * Starting a new loot table as a copy of another, which is how to change what one chest gives
 * without changing every chest that shares its table: the id the copy is offered, and what it
 * starts as.
 */
public final class TableCopy {
    private TableCopy() {
    }

    /**
     * An id for a copy of {@code of} that none of {@code taken} has: the same path under this mod's
     * namespace with _copy after it, then _copy2 and so on. A copy of a copy is the table's next copy.
     */
    public static ResourceLocation id(ResourceLocation of, Collection<ResourceLocation> taken) {
        String path = of.getPath().replaceFirst("_copy\\d*$", "");
        for (int n = 1; ; n++) {
            ResourceLocation id = Ids.of(JustEnoughStructures.MOD_ID, path + "_copy" + (n == 1 ? "" : String.valueOf(n)));
            if (!taken.contains(id)) {
                return id;
            }
        }
    }

    /**
     * What the copy starts as: the other table's JSON without its random sequence, which is named
     * after that table and would have the two share one run of rolls. Text that isn't a table is
     * left as it is.
     */
    public static String start(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed.isJsonObject()) {
                parsed.getAsJsonObject().remove("random_sequence");
                return parsed.toString();
            }
        } catch (JsonParseException e) {
            // Not JSON, so there's nothing to take out of it.
        }
        return json;
    }
}
