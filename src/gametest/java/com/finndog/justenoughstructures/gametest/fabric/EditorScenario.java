package com.finndog.justenoughstructures.gametest.fabric;

import static com.finndog.justenoughstructures.gametest.fabric.Director.click;
import static com.finndog.justenoughstructures.gametest.fabric.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pause;
import static com.finndog.justenoughstructures.gametest.fabric.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.fabric.Director.run;
import static com.finndog.justenoughstructures.gametest.fabric.Director.shoot;
import static com.finndog.justenoughstructures.gametest.fabric.Director.type;
import static com.finndog.justenoughstructures.gametest.fabric.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.TableDraft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.glfw.GLFW;

/** The loot table editor: the Loot tab's Edit link, the editor with an entry picked, and the JSON view. */
final class EditorScenario {
    private static final ResourceLocation TABLE = Ids.parse("chests/desert_pyramid");

    private EditorScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc, null);
        d.then(pressKey(GLFW.GLFW_KEY_K))
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
                .then(pressKey(GLFW.GLFW_KEY_BACKSPACE))
                .then(pressKey(GLFW.GLFW_KEY_BACKSPACE))
                .then(type("40", 2))
                .then(pause(10))
                .then(shoot("e03e_typing_weight"))
                .then(pressKey(GLFW.GLFW_KEY_ENTER))
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
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
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
                .then(run(() -> mc.setScreen(new LootEditorScreen(browserBehind(mc), TABLE, "Desert Pyramid"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(40))
                .then(shoot("e05_editor_flagged"))
                .then(run(() -> editor(mc).showChanges()))
                .then(pause(10))
                .then(shoot("e06_changes"))
                .then(pressKey(GLFW.GLFW_KEY_ESCAPE))
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
            for (JsonObject entry : TableDraft.entryList(TableDraft.poolList(mine).get(0))) {
                if (TableDraft.name(entry).equals("minecraft:diamond")) {
                    TableDraft.setWeight(entry, 40);
                }
            }
            for (JsonObject entry : TableDraft.entryList(TableDraft.poolList(older).get(0))) {
                if (TableDraft.name(entry).equals("minecraft:gold_ingot")) {
                    TableDraft.setWeight(entry, 50);
                }
            }
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

    private static Screen browserBehind(Minecraft mc) {
        return mc.screen instanceof LootEditorScreen editor ? editor : mc.screen;
    }

    private static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    private static LootEditorScreen editor(Minecraft mc) {
        return mc.screen instanceof LootEditorScreen s ? s : null;
    }
}
