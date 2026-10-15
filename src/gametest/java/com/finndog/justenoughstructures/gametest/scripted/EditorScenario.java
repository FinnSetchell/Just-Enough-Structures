package com.finndog.justenoughstructures.gametest.scripted;

import com.mojang.blaze3d.platform.InputConstants;
import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
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
import com.finndog.justenoughstructures.client.screen.Scroll;
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
        int[] counted = new int[1];
        String[] clipboard = new String[1];
        String[] dragged = new String[1];
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
                // Hovering a field's label says what the field is for.
                .then(moveTo(() -> {
                    int[] weight = field(mc, "pools.0.entries.1.weight");
                    return new int[]{weight[0] - 45, weight[1]};
                }, 8))
                .then(pause(10))
                .then(run(() -> {
                    if (!(Reflect.get(Reflect.get(editor(mc), "ui"), "tooltip") instanceof java.util.List<?> lines) || lines.isEmpty()) {
                        throw new IllegalStateException("Hovering the Weight label showed nothing about it");
                    }
                }))
                .then(shoot("e03a_field_help"))
                .then(moveTo(() -> at(mc, "Add function"), 8))
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
                // What isn't a number is turned down with a word about it, and the weight stays.
                .then(moveTo(() -> field(mc, "pools.0.entries.1.weight"), 8))
                .then(click())
                .then(type("x", 2))
                .then(pressKey(InputConstants.KEY_RETURN))
                .then(pause(5))
                .then(run(() -> {
                    String weight = String.valueOf(JsonParser.parseString(editor(mc).draftText()).getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject()
                            .getAsJsonArray("entries").get(1).getAsJsonObject().get("weight"));
                    if (!weight.equals("40") || Reflect.get(editor(mc), "message") == null) {
                        throw new IllegalStateException("Typing 40x as a weight left it " + weight + " and said " + Reflect.get(editor(mc), "message"));
                    }
                }))
                .then(shoot("e03e2_not_a_number"))
                .then(moveTo(() -> field(mc, "pools.0.entries.1.functions.1.count#kind"), 8))
                .then(click())
                .then(pause(10))
                .then(shoot("e03f_provider_list"))
                .then(moveTo(() -> option(mc, "uniform"), 8))
                .then(click())
                .then(pause(30))
                .then(shoot("e03g_count_range"))
                // The last change is taken back and put back again, as Ctrl+Z and Ctrl+Y do.
                .then(run(() -> {
                    String changed = editor(mc).draftText();
                    editor(mc).undoNow();
                    String before = editor(mc).draftText();
                    if (before.equals(changed)) {
                        throw new IllegalStateException("Undo left the table as it was");
                    }
                    editor(mc).redoNow();
                    if (!editor(mc).draftText().equals(changed)) {
                        throw new IllegalStateException("Redo didn't put the change back");
                    }
                    editor(mc).undoNow();
                }))
                .then(pause(10))
                .then(shoot("e03i_undone"))
                .then(run(() -> editor(mc).redoNow()))
                // The picked entry's row of the list has buttons to copy it and to take it out, and is dragged to move it.
                .then(run(() -> counted[0] = entries(mc)))
                .then(moveTo(() -> at(mc, "Duplicate"), 8))
                .then(click())
                .then(pause(10))
                .then(run(() -> {
                    if (entries(mc) != counted[0] + 1) {
                        throw new IllegalStateException("Duplicate left the pool with " + entries(mc) + " entries, where it had " + counted[0]);
                    }
                }))
                .then(shoot("e03j_duplicated"))
                // The copy, third in its pool, is dragged to the top of it.
                .then(run(() -> dragged[0] = entry(mc, 2)))
                .then(moveTo(() -> row(mc, 2, 0), 8))
                .then(dragTo(() -> row(mc, 0, -6), 15, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(10))
                .then(run(() -> {
                    if (!entry(mc, 0).equals(dragged[0])) {
                        throw new IllegalStateException("Dragging the third entry to the top of its pool left the first as " + entry(mc, 0));
                    }
                }))
                .then(shoot("e03k_dragged_up"))
                .then(moveTo(() -> at(mc, "Remove entry"), 8))
                .then(pause(5))
                .then(shoot("e03l_row_buttons"))
                .then(click())
                .then(pause(10))
                .then(run(() -> {
                    if (entries(mc) != counted[0] || entry(mc, 0).equals(dragged[0])) {
                        throw new IllegalStateException("Taking the dragged entry out left " + entries(mc) + " entries, the first " + entry(mc, 0));
                    }
                    editor(mc).undoNow();
                    editor(mc).undoNow();
                    editor(mc).undoNow();
                    if (entries(mc) != counted[0]) {
                        throw new IllegalStateException("Taking the removal, the move and the copy back left " + entries(mc) + " entries");
                    }
                }))
                // Delete takes out the picked entry, as its cross does.
                .then(run(() -> editor(mc).pick(0, 1)))
                .then(pause(5))
                .then(pressKey(InputConstants.KEY_DELETE))
                .then(pause(5))
                .then(run(() -> {
                    if (entries(mc) != counted[0] - 1) {
                        throw new IllegalStateException("Delete left the pool with " + entries(mc) + " entries, where it had " + counted[0]);
                    }
                    editor(mc).undoNow();
                }))
                // The first pool's last entry is dragged into the second pool, to the top of it. The list
                // doesn't scroll while a row is held, so it's scrolled to its end first.
                .then(moveTo(() -> row(mc, 0, 0), 8))
                .then(wheel(-20))
                .then(pause(10))
                .then(run(() -> {
                    dragged[0] = entry(mc, entries(mc) - 1);
                    editor(mc).pick(0, entries(mc) - 1);
                }))
                .then(pause(5))
                .then(moveTo(() -> row(mc, entries(mc) - 1, 0), 8))
                .then(dragTo(() -> poolRow(mc, 1, 7), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(10))
                .then(run(() -> {
                    if (entries(mc) != counted[0] - 1 || !entryIn(mc, 1, 0).equals(dragged[0])) {
                        throw new IllegalStateException("Dragging into the second pool left the first with " + entries(mc)
                                + " entries and the second's first as " + entryIn(mc, 1, 0));
                    }
                }))
                .then(shoot("e03m_dragged_to_pool_2"))
                .then(run(() -> editor(mc).undoNow()))
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
                // The compare screen's own class isn't open to the harness, so its bar is found by its workings.
                .then(moveTo(() -> diffBar(mc, 0.02f), 8))
                .then(dragTo(() -> diffBar(mc, 0.7f), 15, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(run(() -> {
                    if (!(((Scroll) Reflect.get(mc.screen, "scroll")).offset() > 0)) {
                        throw new IllegalStateException("Dragging the compare screen's bar didn't scroll it");
                    }
                }))
                .then(shoot("e06b_changes_dragged"))
                .then(pressKey(InputConstants.KEY_ESCAPE))
                .then(until(() -> editor(mc) != null, 40))
                .then(run(() -> editor(mc).mergeNow()))
                .then(pause(40))
                .then(shoot("e07_merged"))
                // Entries inside another are listed under it and open like any other: the oak leaves'
                // table is one set of alternatives.
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, Ids.parse("blocks/oak_leaves"), "Oak Leaves"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(40))
                .then(run(() -> editor(mc).pickChild(0, 0, "children.1")))
                .then(pause(20))
                .then(run(() -> {
                    if (editor(mc).fieldAt("pools.0.entries.0.children.1.weight") == null) {
                        throw new IllegalStateException("The form doesn't show the entry inside the alternatives");
                    }
                }))
                .then(shoot("e08_entry_inside"))
                // A kind the form has no rows for, like a mod's function, is given a setting by its name.
                .then(run(() -> editor(mc).showJson("{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", "
                        + "\"name\": \"minecraft:stick\", \"functions\": [{\"function\": \"examplemod:sparkle\", \"colour\": \"red\"}]}]}]}")))
                .then(pause(10))
                .then(moveTo(() -> new int[]{24, mc.getWindow().getGuiScaledHeight() - 22}, 8))
                .then(click())
                .then(pause(10))
                .then(run(() -> editor(mc).pick(0, 0)))
                .then(pause(10))
                .then(moveTo(() -> at(mc, "Add setting"), 8))
                .then(click())
                .then(pause(5))
                .then(type("power", 2))
                .then(pressKey(InputConstants.KEY_RETURN))
                .then(pause(5))
                .then(type("3", 2))
                .then(pressKey(InputConstants.KEY_RETURN))
                .then(pause(10))
                .then(run(() -> {
                    if (!editor(mc).draftText().contains("\"colour\":\"red\",\"power\":3")) {
                        throw new IllegalStateException("Adding a setting called power, set to 3, left the table as " + editor(mc).draftText());
                    }
                }))
                .then(shoot("e09_setting_added"))
                // The table a loot table entry names opens from a link beside its name: fishing's first
                // entry is another table.
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, Ids.parse("gameplay/fishing"), "Fishing"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(40))
                .then(run(() -> editor(mc).pick(0, 0)))
                .then(pause(20))
                .then(shoot("e10_table_entry"))
                .then(moveTo(() -> at(mc, "Open"), 8))
                .then(click())
                .then(pause(10))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(20))
                .then(run(() -> {
                    String opened = String.valueOf(Reflect.get(editor(mc), "tableId"));
                    if (!opened.startsWith("minecraft:gameplay/fishing/")) {
                        throw new IllegalStateException("Open on fishing's first entry left the editor on " + opened);
                    }
                }))
                .then(shoot("e11_table_opened"))
                // The JSON view's Copy and Paste carry a table out and bring one in. A string stands in for
                // the clipboard: the hidden window isn't let at the real one, and it's whoever's at the PC's.
                .then(run(() -> {
                    LootEditorScreen.copyTo = text -> clipboard[0] = text;
                    LootEditorScreen.pasteFrom = () -> clipboard[0];
                    editor(mc).showJson("{\"type\": \"minecraft:chest\", \"pools\": []}");
                }))
                .then(pause(10))
                .then(run(() -> {
                    editor(mc).copyJsonNow();
                    String copied = clipboard[0];
                    clipboard[0] = "{\"type\": \"minecraft:fishing\", \"pools\": []}";
                    editor(mc).pasteJsonNow();
                    LootEditorScreen.copyTo = null;
                    LootEditorScreen.pasteFrom = null;
                    if (copied == null || !copied.contains("minecraft:chest")) {
                        throw new IllegalStateException("Copy gave this: " + copied);
                    }
                }))
                .then(pause(10))
                .then(run(() -> {
                    if (!String.valueOf(Reflect.get(editor(mc), "rawText")).contains("minecraft:fishing")) {
                        throw new IllegalStateException("Paste left the JSON view with " + Reflect.get(editor(mc), "rawText"));
                    }
                }))
                .then(shoot("e12_json_pasted"))
                // A name's style is set beside its words: here it's made bold with a click on its B.
                .then(run(() -> editor(mc).showJson("{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\", "
                        + "\"name\": \"minecraft:stick\", \"functions\": [{\"function\": \"minecraft:set_name\", \"name\": \"Fang\"}]}]}]}")))
                .then(pause(10))
                .then(moveTo(() -> new int[]{24, mc.getWindow().getGuiScaledHeight() - 22}, 8))
                .then(click())
                .then(pause(10))
                .then(run(() -> editor(mc).pick(0, 0)))
                .then(pause(10))
                .then(moveTo(() -> field(mc, "pools.0.entries.0.functions.0.name#bold"), 8))
                .then(click())
                .then(pause(10))
                .then(run(() -> {
                    if (!editor(mc).draftText().contains("{\"text\":\"Fang\",\"bold\":true}")) {
                        throw new IllegalStateException("Clicking the name's B left the table as " + editor(mc).draftText());
                    }
                }))
                .then(shoot("e13_name_made_bold"));
        return d;
    }

    /** A point part of the way down the compare screen's scroll bar. */
    private static int[] diffBar(Minecraft mc, float along) {
        int right = (Integer) Reflect.call(mc.screen, "barRight");
        int top = (Integer) Reflect.call(mc.screen, "top");
        int bottom = (Integer) Reflect.call(mc.screen, "bottom");
        return new int[]{right - 3, top + 2 + Math.round((bottom - top - 5) * along)};
    }

    /** One of the first pool's entries, as text. */
    private static String entry(Minecraft mc, int index) {
        return JsonParser.parseString(editor(mc).draftText()).getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(index).toString();
    }

    /** One of a pool's entries, as text. */
    private static String entryIn(Minecraft mc, int pool, int index) {
        return JsonParser.parseString(editor(mc).draftText()).getAsJsonObject().getAsJsonArray("pools").get(pool).getAsJsonObject()
                .getAsJsonArray("entries").get(index).toString();
    }

    /** A point on a pool's own row of the list, {@code dy} down from its middle. */
    private static int[] poolRow(Minecraft mc, int pool, int dy) {
        int[] at = editor(mc) == null ? null : editor(mc).rowAt(pool, -1, "");
        return at == null ? new int[]{0, 0} : new int[]{at[0], at[1] + dy};
    }

    /** A point on the row of one of the first pool's entries in the list, {@code dy} down from its middle. */
    private static int[] row(Minecraft mc, int entry, int dy) {
        int[] at = editor(mc) == null ? null : editor(mc).rowAt(0, entry, "");
        return at == null ? new int[]{0, 0} : new int[]{at[0], at[1] + dy};
    }

    /** How many entries the first pool of the table being edited has. */
    private static int entries(Minecraft mc) {
        return JsonParser.parseString(editor(mc).draftText()).getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").size();
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
