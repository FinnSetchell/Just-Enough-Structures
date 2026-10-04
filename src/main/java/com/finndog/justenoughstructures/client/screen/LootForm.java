package com.finndog.justenoughstructures.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The editor's form, drawn from the table's JSON each frame: a card for the table, a pool or an
 * entry, with fields for what each holds and cards for its functions and conditions. Kinds the
 * form knows get proper fields; any other kind, a mod's own included, gets a field per setting with
 * its JSON in it. Nothing the form doesn't show is touched.
 */
final class LootForm {
    static final int FIELD = 14;
    private static final int LABEL_W = 72;
    /** How wide the labels are on the left this frame: less in a narrow form, so its controls keep room. */
    private int labelW = LABEL_W;
    private static final int GAP = 3;
    private static final int INDENT = 6;
    private static final List<String> PROVIDERS = List.of("constant", "uniform", "binomial");

    /** What the form needs from the editor around it. */
    interface Host {
        JsonObject draft();

        /** Something in the table changed. */
        void changed();

        /** A field was clicked: type in it, and {@code commit} gets the text when done. */
        void edit(String id, int[] rect, String text, Consumer<String> commit);

        /** The id of the field being typed in, if any, so it's left for the text box to draw. */
        String editing();

        /** Where a field was drawn this frame. */
        void drawn(String id, int[] rect);

        /** A list to pick one of {@code options} from, under {@code rect}. */
        void choose(int[] rect, List<String> options, Function<String, String> names, String current, Consumer<String> pick);

        /** The item picker, for the item at {@code path}. */
        void pickItem(String path);

        void select(int pool, int entry);
    }

    private final Font font;
    private final ToolsUi ui;
    private final Host host;

    LootForm(Font font, ToolsUi ui, Host host) {
        this.font = font;
        this.ui = ui;
        this.host = host;
    }

    private JsonObject draft() {
        return host.draft();
    }

    /** Draws the card for what's picked. Returns the y below it. */
    int render(GuiGraphics g, int x, int y, int w, int pool, int entry) {
        labelW = Math.max(48, Math.min(LABEL_W, w / 4));
        if (pool < 0) {
            return tableCard(g, x, y, w);
        }
        String poolPath = JsonPaths.join("pools", pool);
        if (JsonPaths.object(draft(), poolPath) == null) {
            return Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.editor.pick_something"), x, y + 2, w, Gui.LABEL_SOFT);
        }
        if (entry < 0) {
            return poolCard(g, x, y, w, pool, poolPath);
        }
        String entryPath = JsonPaths.join(JsonPaths.join(poolPath, "entries"), entry);
        if (JsonPaths.object(draft(), entryPath) == null) {
            return y;
        }
        return entryCard(g, x, y, w, pool, entryPath);
    }

    // ------------------------------------------------------------------ cards

    private int tableCard(GuiGraphics g, int x, int y, int w) {
        int top = y;
        y = head(g, x, y, w, text("card.table"), null, null);
        y = row(g, x, y, w, "type", (cx, cw) -> dropdown(g, cx, y(), cw, "type", LootTypes.TABLE_TYPES,
                id -> LootTypes.name("table_type", id), JsonPaths.string(draft(), "type", "minecraft:chest"), type -> set("type", new JsonPrimitive(type))));
        y = row(g, x, y, w, "random_sequence", (cx, cw) -> text(g, cx, y(), cw, "random_sequence", true));
        JsonArray pools = JsonPaths.array(draft(), "pools");
        y = row(g, x, y, w, "pools", (cx, cw) -> soft(g, cx, String.valueOf(pools == null ? 0 : pools.size())));
        y = list(g, x, y, w, "functions", "functions", true);
        return end(g, x, top, y, w);
    }

    private int poolCard(GuiGraphics g, int x, int y, int w, int pool, String path) {
        int top = y;
        y = head(g, x, y, w, Component.translatable("screen.justenoughstructures.editor.pool", pool + 1), text("remove_pool"), () -> {
            JsonPaths.remove(draft(), path);
            host.select(-1, -1);
            host.changed();
        });
        y = row(g, x, y, w, "rolls", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "rolls"), 1));
        y = row(g, x, y, w, "bonus_rolls", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "bonus_rolls"), 0));
        JsonArray entries = JsonPaths.array(draft(), JsonPaths.join(path, "entries"));
        y = row(g, x, y, w, "entries", (cx, cw) -> soft(g, cx, String.valueOf(entries == null ? 0 : entries.size())));
        y = list(g, x, y, w, "functions", JsonPaths.join(path, "functions"), true);
        y = list(g, x, y, w, "conditions", JsonPaths.join(path, "conditions"), false);
        return end(g, x, top, y, w);
    }

    private int entryCard(GuiGraphics g, int x, int y, int w, int pool, String path) {
        int top = y;
        JsonObject entry = JsonPaths.object(draft(), path);
        String type = JsonPaths.string(draft(), JsonPaths.join(path, "type"), "minecraft:item");
        String kind = LootTypes.shortId(type);
        y = head(g, x, y, w, text("card.entry"), text("remove_entry"), () -> {
            JsonPaths.remove(draft(), path);
            host.select(pool, -1);
            host.changed();
        });
        y = row(g, x, y, w, "entry_type", (cx, cw) -> dropdown(g, cx, y(), cw, JsonPaths.join(path, "type"), LootTypes.entryTypes(),
                id -> LootTypes.name("entry_type", id), type, picked -> {
                    if (!picked.equals(type)) {
                        set(path, LootTypes.entry(picked, entry));
                    }
                }));
        boolean composite = List.of("alternatives", "group", "sequence").contains(kind);
        switch (kind) {
            case "item" -> {
                String namePath = JsonPaths.join(path, "name");
                y = row(g, x, y, w, "item", (cx, cw) -> {
                    field(g, cx, y(), cw - FIELD - 4, namePath, Kind.ITEM, JsonPaths.string(draft(), namePath, ""));
                    itemButton(g, cx + cw - FIELD - 2, y(), namePath);
                });
                Component itemName = itemName(JsonPaths.string(draft(), namePath, ""));
                Gui.fineClipped(g, font, itemName.getString(), x + labelW, y - 1, w - labelW - 8, itemExists(JsonPaths.string(draft(), namePath, "")) ? Gui.LABEL_SOFT : ToolsUi.BAD);
                y += Gui.fineLine(font) + 2;
            }
            case "tag" -> {
                y = row(g, x, y, w, "tag", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "name"), false));
                y = row(g, x, y, w, "expand", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "expand")));
            }
            case "loot_table" -> y = row(g, x, y, w, "table", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "name"), false));
            case "dynamic" -> y = row(g, x, y, w, "name", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "name"), false));
            default -> {
            }
        }
        if (composite) {
            JsonArray children = JsonPaths.array(draft(), JsonPaths.join(path, "children"));
            y = Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.editor.children", children == null ? 0 : children.size()),
                    x + 4, y + 1, w - 8, Gui.LABEL_SOFT) + 2;
        } else {
            y = row(g, x, y, w, "weight", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "weight"), Kind.INT, "1"));
            y = row(g, x, y, w, "quality", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "quality"), Kind.INT, "0"));
        }
        y = list(g, x, y, w, "functions", JsonPaths.join(path, "functions"), true);
        y = list(g, x, y, w, "conditions", JsonPaths.join(path, "conditions"), false);
        return end(g, x, top, y, w);
    }

    /** A function: its kind, what it needs, and conditions of its own. */
    private int functionCard(GuiGraphics g, int x, int y, int w, String path) {
        int top = y;
        String type = JsonPaths.string(draft(), JsonPaths.join(path, "function"), "?");
        y = typeHead(g, x, y, w, JsonPaths.join(path, "function"), LootTypes.functionTypes(), "function", type, picked -> {
            JsonObject now = LootTypes.function(picked);
            JsonElement conditions = JsonPaths.get(draft(), JsonPaths.join(path, "conditions"));
            if (conditions != null) {
                now.add("conditions", conditions.deepCopy());
            }
            set(path, now);
        }, path);
        int before = y;
        switch (LootTypes.shortId(type)) {
            case "set_count" -> {
                y = row(g, x, y, w, "count", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "count"), 1));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "add")));
            }
            case "enchant_with_levels" -> {
                y = row(g, x, y, w, "levels", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "levels"), 30));
                y = row(g, x, y, w, "treasure", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "treasure")));
            }
            case "set_damage" -> {
                y = row(g, x, y, w, "damage", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "damage"), 1));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "add")));
            }
            case "set_enchantments" -> {
                y = row(g, x, y, w, "enchantments", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "enchantments")));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "add")));
            }
            case "enchant_randomly" -> y = row(g, x, y, w, "enchantments", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "enchantments")));
            case "set_potion" -> y = row(g, x, y, w, "potion", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "id"), false));
            case "set_stew_effect" -> y = row(g, x, y, w, "effects", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "effects")));
            case "set_name" -> {
                y = row(g, x, y, w, "name", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "name")));
                y = row(g, x, y, w, "entity", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "entity"), true));
            }
            case "set_lore" -> {
                y = row(g, x, y, w, "lore", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "lore")));
                y = row(g, x, y, w, "replace", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "replace")));
            }
            case "set_nbt" -> y = row(g, x, y, w, "nbt", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "tag"), false));
            case "set_instrument" -> y = row(g, x, y, w, "options", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "options"), false));
            case "exploration_map" -> {
                y = row(g, x, y, w, "destination", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "destination"), true));
                y = row(g, x, y, w, "decoration", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "decoration"), true));
                y = row(g, x, y, w, "zoom", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "zoom"), Kind.INT, "2"));
                y = row(g, x, y, w, "search_radius", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "search_radius"), Kind.INT, "50"));
                y = row(g, x, y, w, "skip_existing", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "skip_existing_chunks")));
            }
            case "limit_count" -> {
                y = row(g, x, y, w, "at_least", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "limit.min"), Kind.NUMBER, ""));
                y = row(g, x, y, w, "at_most", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "limit.max"), Kind.NUMBER, ""));
            }
            case "looting_enchant" -> {
                y = row(g, x, y, w, "count", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "count"), 1));
                y = row(g, x, y, w, "limit", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "limit"), Kind.INT, "0"));
            }
            case "apply_bonus" -> {
                y = row(g, x, y, w, "enchantment", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "enchantment"), false));
                y = row(g, x, y, w, "formula", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "formula"), false));
            }
            case "explosion_decay", "furnace_smelt" -> {
            }
            default -> y = generic(g, x, y, w, path, List.of("function", "conditions"));
        }
        if (y == before) {
            Gui.fine(g, font, text("nothing_to_set").getString(), x + 4, y + 1, Gui.LABEL_SOFT);
            y += Gui.fineLine(font) + 4;
        }
        y = list(g, x, y, w, "conditions", JsonPaths.join(path, "conditions"), false);
        return end(g, x, top, y, w);
    }

    /** A condition: its kind and what it needs; some hold more conditions. */
    private int conditionCard(GuiGraphics g, int x, int y, int w, String path) {
        int top = y;
        String type = JsonPaths.string(draft(), JsonPaths.join(path, "condition"), "?");
        y = typeHead(g, x, y, w, JsonPaths.join(path, "condition"), LootTypes.conditionTypes(), "condition", type,
                picked -> set(path, LootTypes.condition(picked)), path);
        int before = y;
        switch (LootTypes.shortId(type)) {
            case "random_chance" -> y = row(g, x, y, w, "chance", (cx, cw) -> number(g, cx, y(), 60, JsonPaths.join(path, "chance"), Kind.NUMBER, "0.5"));
            case "random_chance_with_looting" -> {
                y = row(g, x, y, w, "chance", (cx, cw) -> number(g, cx, y(), 60, JsonPaths.join(path, "chance"), Kind.NUMBER, "0.1"));
                y = row(g, x, y, w, "per_looting", (cx, cw) -> number(g, cx, y(), 60, JsonPaths.join(path, "looting_multiplier"), Kind.NUMBER, "0"));
            }
            case "killed_by_player" -> y = row(g, x, y, w, "inverse", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "inverse")));
            case "survives_explosion" -> {
            }
            case "inverted" -> {
                String term = JsonPaths.join(path, "term");
                if (JsonPaths.object(draft(), term) == null) {
                    ui.link(g, text("add_condition"), x + 4, y + 2, true, () -> set(term, LootTypes.condition("minecraft:random_chance")));
                    y += Gui.fineLine(font) + 6;
                } else {
                    y = conditionCard(g, x + INDENT, y, w - INDENT * 2, term) + 3;
                }
            }
            case "any_of", "all_of", "alternative" -> y = list(g, x, y, w, "terms", JsonPaths.join(path, "terms"), false);
            case "table_bonus" -> {
                y = row(g, x, y, w, "enchantment", (cx, cw) -> text(g, cx, y(), cw, JsonPaths.join(path, "enchantment"), false));
                y = row(g, x, y, w, "chances", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "chances")));
            }
            default -> y = generic(g, x, y, w, path, List.of("condition"));
        }
        if (y == before) {
            Gui.fine(g, font, text("nothing_to_set").getString(), x + 4, y + 1, Gui.LABEL_SOFT);
            y += Gui.fineLine(font) + 4;
        }
        return end(g, x, top, y, w);
    }

    /** A field for each setting of a kind the form doesn't know, with its JSON in it. */
    private int generic(GuiGraphics g, int x, int y, int w, String path, List<String> skip) {
        JsonObject object = JsonPaths.object(draft(), path);
        if (object == null) {
            return y;
        }
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            if (skip.contains(e.getKey())) {
                continue;
            }
            String key = e.getKey();
            y = labelled(g, x, y, w, StructureNames.pretty(key), (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, key)));
        }
        return y;
    }

    // ------------------------------------------------------------------ lists

    /** A heading with a count and a link to add one, then a card for each function or condition. */
    private int list(GuiGraphics g, int x, int y, int w, String key, String path, boolean functions) {
        JsonArray items = JsonPaths.array(draft(), path);
        int count = items == null ? 0 : items.size();
        y += 2;
        g.drawString(font, text("list." + key), x + 4, y + 1, ToolsUi.TEXT, false);
        String countText = count == 0 ? text("none").getString() : String.valueOf(count);
        Gui.fine(g, font, countText, x + 8 + font.width(text("list." + key)), y + 2, Gui.LABEL_SOFT);
        Component add = text(functions ? "add_function" : "add_condition");
        int addW = Gui.fineWidth(font, add.getString());
        ui.link(g, add, x + w - 4 - addW, y + 2, true, () -> {
            JsonPaths.append(draft(), path, functions ? LootTypes.function("minecraft:set_count") : LootTypes.condition("minecraft:random_chance"));
            host.changed();
        });
        y += font.lineHeight + 3;
        for (int i = 0; i < count; i++) {
            String itemPath = JsonPaths.join(path, i);
            y = (functions ? functionCard(g, x + INDENT, y, w - INDENT * 2, itemPath) : conditionCard(g, x + INDENT, y, w - INDENT * 2, itemPath)) + 3;
        }
        return y;
    }

    // ------------------------------------------------------------------ parts of a card

    /** The top of a card: a title, and a link at the right such as Remove. Returns the y below it. */
    private int head(GuiGraphics g, int x, int y, int w, Component title, Component action, Runnable onAction) {
        g.fill(x, y, x + w, y + 14, 0xFF8B8B8B);
        Gui.drawClipped(g, font, title.getString(), x + 4, y + 3, w - 60, 0xFFFFFFFF, true);
        if (action != null) {
            int aw = Gui.fineWidth(font, action.getString());
            ui.link(g, action, x + w - 4 - aw, y + 4, true, onAction);
        }
        return y + 16;
    }

    /** The top of a function or condition card: which kind it is, to pick another, and an x to take it away. */
    private int typeHead(GuiGraphics g, int x, int y, int w, String id, List<String> options, String kind, String current,
                         Consumer<String> pick, String path) {
        g.fill(x, y, x + w, y + 16, 0xFF9D9D9D);
        dropdown(g, x + 2, y + 1, Math.min(w - 22, 150), id, options, option -> LootTypes.name(kind, option), current, pick);
        String remove = "x";
        int rx = x + w - 10;
        boolean over = ui.hovered(rx - 2, y + 2, 10, 12);
        g.drawString(font, remove, rx, y + 4, over ? 0xFFFFFF55 : 0xFF6A1010, false);
        ui.spot(rx - 2, y + 2, 10, 12, () -> {
            JsonPaths.remove(draft(), path);
            host.changed();
        });
        ui.tooltip(rx - 2, y + 2, 10, 12, text(kind.equals("function") ? "remove_function" : "remove_condition"));
        return y + 19;
    }

    /** The edges of a card, once its height is known. Returns the y below it. */
    private int end(GuiGraphics g, int x, int top, int y, int w) {
        int bottom = y + 2;
        g.fill(x, top, x + 1, bottom, 0xFF8B8B8B);
        g.fill(x + w - 1, top, x + w, bottom, 0xFF8B8B8B);
        g.fill(x, bottom - 1, x + w, bottom, 0xFF8B8B8B);
        return bottom;
    }

    /** Where the control in the row being drawn goes, for the lambdas that draw them. */
    private int rowY;

    private int y() {
        return rowY;
    }

    private interface Control {
        void draw(int x, int w);
    }

    /** A label and its control. Returns the y below them. */
    private int row(GuiGraphics g, int x, int y, int w, String key, Control control) {
        return labelled(g, x, y, w, text("field." + key).getString(), control);
    }

    private int labelled(GuiGraphics g, int x, int y, int w, String label, Control control) {
        Gui.fineClipped(g, font, label, x + 4, y + (FIELD - Gui.fineLine(font)) / 2 + 1, labelW - 8, Gui.LABEL_SOFT);
        rowY = y;
        control.draw(x + labelW, w - labelW - 4);
        return y + FIELD + GAP;
    }

    private void soft(GuiGraphics g, int x, String text) {
        g.drawString(font, text, x, rowY + 3, Gui.LABEL_SOFT, false);
    }

    // ------------------------------------------------------------------ controls

    /** What a typed field holds, and so how it's read back. */
    enum Kind { TEXT, OPTIONAL_TEXT, INT, NUMBER, RAW, ITEM }

    /** A box to type in: drawn here, and typed in through the editor's one text box when clicked. */
    private void field(GuiGraphics g, int x, int y, int w, String path, Kind kind, String shown) {
        int[] rect = {x, y, w, FIELD};
        host.drawn(path, rect);
        boolean editing = path.equals(host.editing());
        boolean over = ui.hovered(x, y, w, FIELD);
        g.fill(x, y, x + w, y + FIELD, over || editing ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD - 1, 0xFF000000);
        if (!editing) {
            Gui.drawClipped(g, font, shown, x + 4, y + 3, w - 8, 0xFFE0E0E0, false);
        }
        ui.spot(x, y, w, FIELD, () -> host.edit(path, rect, shown, text -> commit(path, kind, text)));
        if (kind == Kind.RAW && font.width(shown) > w - 8) {
            ui.tooltip(x, y, w, FIELD, Component.literal(shown));
        }
    }

    private void text(GuiGraphics g, int x, int y, int w, String path, boolean optional) {
        field(g, x, y, w, path, optional ? Kind.OPTIONAL_TEXT : Kind.TEXT, JsonPaths.string(draft(), path, ""));
    }

    private void number(GuiGraphics g, int x, int y, int w, String path, Kind kind, String fallback) {
        JsonElement value = JsonPaths.get(draft(), path);
        field(g, x, y, w, path, kind, value == null ? fallback : shown(value));
    }

    /** A value as it reads in a field: whole numbers without ".0", as people write them. */
    static String shown(JsonElement value) {
        if (value instanceof JsonPrimitive p && p.isNumber()) {
            double number = p.getAsDouble();
            return number == Math.rint(number) && Math.abs(number) < 1e15 ? String.valueOf((long) number) : p.getAsString();
        }
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private void raw(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        String shown = value == null ? "" : value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
        field(g, x, y, w, path, Kind.RAW, shown);
    }

    private void check(GuiGraphics g, int x, int y, String path) {
        boolean on = JsonPaths.bool(draft(), path);
        ui.check(g, text(on ? "yes" : "no"), x, y + 3, on, () -> set(path, new JsonPrimitive(!on)));
    }

    /** A box showing the choice, which opens the list of them. */
    private void dropdown(GuiGraphics g, int x, int y, int w, String id, List<String> options, Function<String, String> names, String current,
                          Consumer<String> pick) {
        int[] rect = {x, y, w, FIELD};
        host.drawn(id, rect);
        boolean over = ui.hovered(x, y, w, FIELD);
        g.fill(x, y, x + w, y + FIELD, over ? 0xFFFFFFFF : 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD - 1, over ? 0xFF7A7A7A : 0xFF6D6D6D);
        Gui.drawClipped(g, font, names.apply(current), x + 4, y + 3, w - 16, 0xFFFFFFFF, true);
        // A small arrow pointing down, at the right.
        for (int i = 0; i < 4; i++) {
            g.fill(x + w - 10 + i, y + 5 + i, x + w - 3 - i, y + 6 + i, 0xFFFFFFFF);
        }
        ui.spot(x, y, w, FIELD, () -> host.choose(rect, options, names, current, pick));
    }

    /**
     * A number that can be a constant, a range or a binomial, as loot tables allow: which one,
     * and the numbers for it. Emptying a constant leaves it out, so the game uses {@code fallback}.
     */
    private void provider(GuiGraphics g, int x, int y, int w, String path, double fallback) {
        JsonElement value = JsonPaths.get(draft(), path);
        String kind = providerKind(value);
        int pickW = Math.min(70, w / 3);
        if (kind.equals("other")) {
            raw(g, x, y, w, path);
            return;
        }
        dropdown(g, x, y, pickW, path + "#kind", PROVIDERS, option -> text("provider." + option).getString(), kind, picked -> {
            if (!picked.equals(kind)) {
                double now = providerNumber(value, fallback);
                JsonElement next = switch (picked) {
                    case "uniform" -> {
                        JsonObject range = new JsonObject();
                        range.addProperty("type", "minecraft:uniform");
                        range.add("min", JsonPaths.number(now));
                        range.add("max", JsonPaths.number(now));
                        yield range;
                    }
                    case "binomial" -> {
                        JsonObject binomial = new JsonObject();
                        binomial.addProperty("type", "minecraft:binomial");
                        binomial.add("n", JsonPaths.number(Math.max(1, Math.round(now))));
                        binomial.addProperty("p", 0.5);
                        yield binomial;
                    }
                    default -> JsonPaths.number(now);
                };
                set(path, next);
            }
        });
        int fx = x + pickW + 4;
        int rest = w - pickW - 4;
        switch (kind) {
            case "uniform" -> {
                int words = Gui.fineWidth(font, text("range_from").getString()) + Gui.fineWidth(font, text("range_to").getString());
                int each = Math.max(14, Math.min(60, (rest - words - 10) / 2));
                Gui.fine(g, font, text("range_from").getString(), fx, y + 4, Gui.LABEL_SOFT);
                int from = fx + Gui.fineWidth(font, text("range_from").getString()) + 3;
                number(g, from, y, each, JsonPaths.join(path, "min"), Kind.NUMBER, "0");
                int toX = from + each + 4;
                Gui.fine(g, font, text("range_to").getString(), toX, y + 4, Gui.LABEL_SOFT);
                number(g, toX + Gui.fineWidth(font, text("range_to").getString()) + 3, y, each, JsonPaths.join(path, "max"), Kind.NUMBER, "0");
            }
            case "binomial" -> {
                int each = Math.max(14, Math.min(60, (rest - 20) / 2));
                Gui.fine(g, font, "n", fx, y + 4, Gui.LABEL_SOFT);
                number(g, fx + 8, y, each, JsonPaths.join(path, "n"), Kind.INT, "1");
                int pX = fx + 8 + each + 4;
                Gui.fine(g, font, "p", pX, y + 4, Gui.LABEL_SOFT);
                number(g, pX + 8, y, each, JsonPaths.join(path, "p"), Kind.NUMBER, "0.5");
            }
            default -> {
                boolean wrapped = value instanceof JsonObject;
                String numberPath = wrapped ? JsonPaths.join(path, "value") : path;
                JsonElement shown = JsonPaths.get(draft(), numberPath);
                field(g, fx, y, Math.min(60, rest), numberPath, Kind.NUMBER, shown == null ? trim(fallback) : shown(shown));
            }
        }
    }

    static String providerKind(JsonElement value) {
        if (value == null || value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return "constant";
        }
        if (value instanceof JsonObject object) {
            String type = object.has("type") && object.get("type").isJsonPrimitive() ? object.get("type").getAsString() : "";
            if (type.endsWith("constant")) {
                return "constant";
            }
            if (type.endsWith("binomial")) {
                return "binomial";
            }
            if (type.endsWith("uniform") || type.isEmpty() && (object.has("min") || object.has("max"))) {
                return "uniform";
            }
        }
        return "other";
    }

    private static double providerNumber(JsonElement value, double fallback) {
        try {
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsDouble();
            }
            if (value instanceof JsonObject object) {
                for (String key : List.of("value", "min", "n")) {
                    if (object.has(key) && object.get(key).isJsonPrimitive()) {
                        return object.get(key).getAsDouble();
                    }
                }
            }
        } catch (RuntimeException e) {
            // Not a plain number: the fallback, then.
        }
        return fallback;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** The picked item's icon, which opens the item picker. */
    private void itemButton(GuiGraphics g, int x, int y, String path) {
        boolean over = ui.hovered(x, y, FIELD + 2, FIELD);
        g.fill(x, y, x + FIELD + 2, y + FIELD, over ? 0xFFFFFFFF : 0xFF555555);
        g.fill(x + 1, y + 1, x + FIELD + 1, y + FIELD - 1, 0xFF8B8B8B);
        String id = JsonPaths.string(draft(), path, "");
        ItemStack stack = itemExists(id) ? new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id))) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 2, y + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        ui.spot(x, y, FIELD + 2, FIELD, () -> host.pickItem(path));
        ui.tooltip(x, y, FIELD + 2, FIELD, text("pick_item"));
    }

    static boolean itemExists(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id.trim());
        return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed) && !parsed.getPath().equals("air");
    }

    static Component itemName(String id) {
        if (itemExists(id)) {
            return BuiltInRegistries.ITEM.get(new ResourceLocation(id.trim())).getDescription();
        }
        return Component.translatable("screen.justenoughstructures.editor.unknown_item");
    }

    // ------------------------------------------------------------------ changes

    private void set(String path, JsonElement value) {
        JsonPaths.set(draft(), path, value);
        host.changed();
    }

    /** What was typed in a field, read back as the field's kind. Text that doesn't read as that is left out. */
    private void commit(String path, Kind kind, String text) {
        String trimmed = text.trim();
        JsonElement before = JsonPaths.get(draft(), path);
        JsonElement value;
        switch (kind) {
            case INT, NUMBER -> {
                if (trimmed.isEmpty()) {
                    value = null;
                } else {
                    try {
                        double number = Double.parseDouble(trimmed);
                        value = kind == Kind.INT ? new JsonPrimitive(Math.round(number)) : JsonPaths.number(number);
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
            }
            case RAW -> {
                if (trimmed.isEmpty()) {
                    value = null;
                } else {
                    JsonElement parsed;
                    try {
                        parsed = JsonParser.parseString(trimmed);
                    } catch (JsonParseException e) {
                        parsed = new JsonPrimitive(trimmed);
                    }
                    value = parsed;
                }
            }
            case OPTIONAL_TEXT -> value = trimmed.isEmpty() ? null : new JsonPrimitive(trimmed);
            default -> value = new JsonPrimitive(trimmed);
        }
        if (value == null ? before == null : value.equals(before)) {
            return;
        }
        JsonPaths.set(draft(), path, value);
        host.changed();
    }

    private static Component text(String key) {
        return Component.translatable("screen.justenoughstructures.editor." + key);
    }
}
