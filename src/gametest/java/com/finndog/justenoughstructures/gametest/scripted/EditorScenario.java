package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.editor;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** The loot table editor: the Loot tab's Edit link, the editor with an entry picked, and the JSON view. */
final class EditorScenario {
    private static final ResourceLocation TABLE = Ids.parse("chests/desert_pyramid");

    private EditorScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(run(() -> browser(mc).showLoot(TABLE.toString())))
                .then(pause(60))
                .then(shoot("e01_loot_tab_edit_link"))
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, TABLE, "Desert Pyramid"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(40))
                .then(shoot("e02_editor"))
                .then(run(() -> editor(mc).pick(0, 1)))
                .then(pause(40))
                .then(shoot("e03_editor_entry"))
                .then(moveTo(() -> at(mc, "+ Add function"), 8))
                .then(click())
                .then(pause(30))
                .then(shoot("e03b_function_added"))
                // Typing a weight into its field, and making the new function's count a range.
                .then(moveTo(() -> field(mc, "pools.0.entries.1.weight"), 8))
                .then(click())
                .then(pressKey(InputConstants.KEY_BACKSPACE))
                .then(pressKey(InputConstants.KEY_BACKSPACE))
                .then(type("40", 2))
                .then(pause(10))
                .then(shoot("e03e_typing_weight"))
                .then(pressKey(InputConstants.KEY_RETURN))
                .then(moveTo(() -> field(mc, "pools.0.entries.1.functions.1.count#kind"), 8))
                .then(click())
                .then(pause(10))
                .then(shoot("e03f_provider_list"))
                .then(moveTo(() -> option(mc, "uniform"), 8))
                .then(click())
                .then(pause(30))
                .then(shoot("e03g_count_range"))
                .then(run(() -> editor(mc).openItemPicker()))
                .then(pause(20))
                .then(shoot("e03c_item_picker"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                // The enchanted book, whose function's fields differ between versions.
                .then(run(() -> editor(mc).pick(0, 11)))
                .then(pause(20))
                .then(shoot("e03h_book_enchantments"))
                .then(run(() -> editor(mc).pick(-1, -1)))
                .then(pause(20))
                .then(shoot("e03d_table_card"))
                // The JSON/Form toggle is the toolbar's first button, at the bottom left.
                .then(moveTo(() -> new int[]{24, mc.getWindow().getGuiScaledHeight() - 22}, 8))
                .then(click())
                .then(pause(20))
                .then(shoot("e04_editor_json"))
                // An override made from a version of the table the mod has since changed.
                .then(run(() -> flaggedOverride(mc)))
                .then(pause(20))
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, TABLE, "Desert Pyramid"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(40))
                .then(shoot("e05_editor_flagged"))
                .then(run(() -> editor(mc).showChanges()))
                .then(pause(10))
                .then(shoot("e06_changes"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> editor(mc) != null, 40))
                .then(run(() -> editor(mc).mergeNow()))
                .then(pause(40))
                .then(shoot("e07_merged"));
        return d;
    }

    private static int[] field(Minecraft mc, String path) {
        int[] at = editor(mc) == null ? null : editor(mc).fieldAt(path);
        return at == null ? new int[]{0, 0} : at;
    }

    private static int[] option(Minecraft mc, String option) {
        int[] at = editor(mc) == null ? null : editor(mc).optionAt(option);
        return at == null ? new int[]{0, 0} : at;
    }

    private static int[] at(Minecraft mc, String label) {
        int[] at = editor(mc) == null ? null : editor(mc).buttonAt(label);
        return at == null ? new int[]{0, 0} : at;
    }

    /** Sets the weight of an item in a table's first pool. */
    private static void setWeight(JsonObject table, String item, int weight) {
        for (JsonElement entry : table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")) {
            JsonObject object = entry.getAsJsonObject();
            if (object.has("name") && object.get("name").getAsString().equals(item)) {
                object.addProperty("weight", weight);
            }
        }
    }

    /**
     * Saves an edit that makes diamonds commoner, then makes out it was made from an older version
     * of the table, with more gold, which the mod has since changed.
     */
    private static void flaggedOverride(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            String original = LootOverrides.original(server.getResourceManager(), TABLE);
            JsonObject mine = JsonParser.parseString(original).getAsJsonObject();
            JsonObject older = mine.deepCopy();
            setWeight(mine, "minecraft:diamond", 40);
            setWeight(older, "minecraft:gold_ingot", 50);
            LootOverrides.save(server.getResourceManager(), TABLE, mine.toString());
            try {
                Path root = LootOverrides.folder();
                Path meta = root.resolve("overrides.json");
                JsonObject json = JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
                json.getAsJsonObject(TABLE.toString()).addProperty("base", "an older version");
                Files.writeString(meta, json.toString());
                Files.writeString(root.resolve("originals/minecraft/chests/desert_pyramid.json"), older.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
