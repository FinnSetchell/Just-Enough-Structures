package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.loot.StackEntry;
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
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The editor's form, drawn from the table's JSON each frame: a card for the table, a pool or an
 * entry, with fields for its settings and cards for its functions and conditions. Known kinds get
 * proper fields; any other kind, including a mod's, gets a JSON field per setting. Settings the form
 * doesn't show are left untouched.
 */
final class LootForm {
    static final int FIELD = 14;
    private static final int LABEL_W = 72;
    /** Label column width this frame: narrower in a narrow form, so the controls keep room. */
    private int labelW = LABEL_W;
    private static final int GAP = 3;
    private static final int INDENT = 6;
    private static final List<String> PROVIDERS = List.of("constant", "uniform", "binomial");

    /** Callbacks from the form to the editor screen. */
    interface Host {
        JsonObject draft();

        /** Called after the table changes. */
        void changed();

        /** Starts typing in a clicked field; {@code commit} receives the text when done. */
        void edit(String id, int[] rect, String text, Consumer<String> commit);

        /** The same, for a field with suggested values, which are listed under it while typing. */
        void edit(String id, int[] rect, String text, Consumer<String> commit, LootOptions.Source options);

        /** Returns the id of the field being typed in, or null. The form leaves that field for the text box to draw. */
        String editing();

        /** Records where a field was drawn this frame. */
        void drawn(String id, int[] rect);

        /** Opens a list under {@code rect} to choose one of {@code options}. */
        void choose(int[] rect, List<String> options, Function<String, String> names, String current, Consumer<String> pick);

        /** Opens the item picker for the item at {@code path}. */
        void pickItem(String path);

        void select(int pool, int entry);

        /** Selects a nested entry by its path from the pool's top-level entry. */
        void select(int pool, int entry, String child);

        /** Shows why typed text was refused. */
        void refused(Component why);

        /** Opens a table named by an entry of this one, over this editor. */
        void openTable(String id);
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

    /** Draws the card for the selection. Returns the y below it. */
    int render(GuiGraphics g, int x, int y, int w, int pool, int entry, String child) {
        labelW = Math.max(48, Math.min(LABEL_W, w / 4));
        if (pool < 0) {
            return tableCard(g, x, y, w);
        }
        String poolPath = JsonPaths.join("pools", pool);
        if (JsonPaths.object(draft(), poolPath) == null) {
            return Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.editor.pick_something"), x, y + 2, w, Gui.labelSoft());
        }
        if (entry < 0) {
            return poolCard(g, x, y, w, pool, poolPath);
        }
        String entryPath = EntryTree.path(pool, entry, child);
        if (JsonPaths.object(draft(), entryPath) == null) {
            return y;
        }
        return entryCard(g, x, y, w, pool, entry, child, entryPath);
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
        // Its duplicate and remove buttons, and dragging to move it, are on its tree row.
        y = head(g, x, y, w, Component.translatable("screen.justenoughstructures.editor.pool", pool + 1), null, null);
        y = row(g, x, y, w, "rolls", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "rolls"), 1));
        y = row(g, x, y, w, "bonus_rolls", (cx, cw) -> provider(g, cx, y(), cw, JsonPaths.join(path, "bonus_rolls"), 0));
        JsonArray entries = JsonPaths.array(draft(), JsonPaths.join(path, "entries"));
        y = row(g, x, y, w, "entries", (cx, cw) -> soft(g, cx, String.valueOf(entries == null ? 0 : entries.size())));
        y = list(g, x, y, w, "functions", JsonPaths.join(path, "functions"), true);
        y = list(g, x, y, w, "conditions", JsonPaths.join(path, "conditions"), false);
        return end(g, x, top, y, w);
    }

    /** Draws an entry's card. For a nested entry, {@code child} is its path from the pool's entry at {@code index}; otherwise it's empty. */
    private int entryCard(GuiGraphics g, int x, int y, int w, int pool, int index, String child, String path) {
        int top = y;
        JsonObject entry = JsonPaths.object(draft(), path);
        String type = JsonPaths.string(draft(), JsonPaths.join(path, "type"), "minecraft:item");
        String kind = LootTypes.shortId(type);
        y = head(g, x, y, w, text("card.entry"), null, null);
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
                    pick(g, cx, y(), cw - FIELD - 4, namePath, LootOptions.ITEMS, false);
                    itemButton(g, cx + cw - FIELD - 2, y(), namePath);
                });
                y = heldLink(g, x, y, w, path);
            }
            case "tag" -> {
                //? if >=26.3 {
                /*// From 26.3 it's a set of items like any other: a tag with its "#", an item, or a list of items.
                y = holderSet(g, x, y, w, "tag", JsonPaths.join(path, LootTypes.TAG_KEY), LootOptions.TAGS_OR_ITEMS, LootOptions.ITEMS, null);
                *///?} else {
                y = row(g, x, y, w, "tag", (cx, cw) -> pick(g, cx, y(), cw, JsonPaths.join(path, LootTypes.TAG_KEY), LootOptions.ITEM_TAG_IDS, false));
                //?}
                y = row(g, x, y, w, "expand", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "expand"), false));
            }
            case "loot_table" -> {
                String tablePath = JsonPaths.join(path, LootTypes.TABLE_KEY);
                // An inline table in place of a name is kept as is, in a JSON field.
                y = JsonPaths.get(draft(), tablePath) instanceof JsonObject
                        ? row(g, x, y, w, "table", (cx, cw) -> raw(g, cx, y(), cw, tablePath))
                        : row(g, x, y, w, "table", (cx, cw) -> {
                            // An Open link beside the table's name. Not for a tag of tables, which 26.3 allows here.
                            String named = JsonPaths.string(draft(), tablePath, "").trim();
                            boolean opens = !named.isEmpty() && !named.startsWith("#") && ResourceLocation.tryParse(named) != null;
                            Component open = text("open_table");
                            int openW = opens ? Gui.fineWidth(font, open.getString()) + 6 : 0;
                            pick(g, cx, y(), cw - openW, tablePath, LootOptions.LOOT_TABLES, false);
                            if (opens) {
                                ui.link(g, open, cx + cw - openW + 6, y() + 3, true, () -> host.openTable(named));
                            }
                        });
            }
            case "dynamic" -> y = row(g, x, y, w, "name", (cx, cw) -> choice(g, cx, y(), cw, JsonPaths.join(path, "name"), LootOptions.DYNAMIC, false));
            case "slots" -> y = row(g, x, y, w, "slot_source", (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, "slot_source")));
            // A kind the form doesn't know, like a mod's: a field for each of its settings, as for functions.
            default -> {
                if (!List.of("empty", "alternatives", "group", "sequence").contains(kind)) {
                    y = generic(g, x, y, w, path, List.of("type", "weight", "quality", "functions", "conditions"));
                }
            }
        }
        if (composite) {
            JsonArray children = JsonPaths.array(draft(), JsonPaths.join(path, "children"));
            y = Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.editor.children", children == null ? 0 : children.size()),
                    x + 4, y + 1, w - 8, Gui.labelSoft()) + 2;
            ui.link(g, text("add_item"), x + 4, y + 1, true, () -> {
                String inside = JsonPaths.join(path, "children");
                if (JsonPaths.array(draft(), inside) == null) {
                    JsonPaths.set(draft(), inside, new JsonArray());
                }
                int added = JsonPaths.append(draft(), inside, LootTypes.entry("minecraft:item", null));
                host.select(pool, index, EntryTree.inside(child, added));
                host.changed();
            });
            y += Gui.fineLine(font) + 5;
        } else {
            y = row(g, x, y, w, "weight", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "weight"), Kind.INT, "1"));
            y = row(g, x, y, w, "quality", (cx, cw) -> number(g, cx, y(), 50, JsonPaths.join(path, "quality"), Kind.INT, "0"));
        }
        y = list(g, x, y, w, "functions", JsonPaths.join(path, "functions"), true);
        y = list(g, x, y, w, "conditions", JsonPaths.join(path, "conditions"), false);
        return end(g, x, top, y, w);
    }

    /** Draws a function's card: its kind, its settings and its conditions. */
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
        String fn = LootTypes.shortId(type);
        switch (fn) {
            case "set_count" -> {
                y = row(g, x, y, w, "count", (cx, cw) -> provider(g, cx, y(), cw, at(path, "count"), 1));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), at(path, "add"), false));
            }
            case "enchant_with_levels" -> {
                y = row(g, x, y, w, "levels", (cx, cw) -> provider(g, cx, y(), cw, at(path, "levels"), 30));
                //? if >=1.21 {
                /*y = holderSet(g, x, y, w, "options", at(path, "options"), LootOptions.ENCHANTMENTS_OR_TAGS, LootOptions.ENCHANTMENTS,
                        text("any_enchantment"));
                *///?} else {
                y = row(g, x, y, w, "treasure", (cx, cw) -> check(g, cx, y(), at(path, "treasure"), false));
                //?}
            }
            case "set_damage" -> {
                y = row(g, x, y, w, "damage", (cx, cw) -> provider(g, cx, y(), cw, at(path, "damage"), 1));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), at(path, "add"), false));
            }
            case "set_enchantments" -> {
                y = idMap(g, x, y, w, "enchantments", at(path, "enchantments"), LootOptions.ENCHANTMENTS, 1);
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), at(path, "add"), false));
            }
            //? if >=1.21 {
            /*case "enchant_randomly" -> {
                y = holderSet(g, x, y, w, "enchantments", at(path, "options"), LootOptions.ENCHANTMENTS_OR_TAGS, LootOptions.ENCHANTMENTS,
                        text("any_enchantment"));
                y = row(g, x, y, w, "only_compatible", (cx, cw) -> check(g, cx, y(), at(path, "only_compatible"), true));
            }
            *///?} else {
            case "enchant_randomly" -> y = idList(g, x, y, w, "enchantments", at(path, "enchantments"), LootOptions.ENCHANTMENTS, text("any_enchantment"));
            //?}
            case "set_potion" -> y = row(g, x, y, w, "potion", (cx, cw) -> pick(g, cx, y(), cw, at(path, "id"), LootOptions.POTIONS, false));
            case "set_stew_effect" -> y = objectList(g, x, y, w, "effects", at(path, "effects"), () -> object("type", "minecraft:speed", "duration", 5),
                    (ox, oy, ow, op) -> {
                        int ny = row(g, ox, oy, ow, "effect", (cx, cw) -> pick(g, cx, y(), cw, at(op, "type"), LootOptions.EFFECTS, false));
                        return row(g, ox, ny, ow, "duration", (cx, cw) -> provider(g, cx, y(), cw, at(op, "duration"), 5));
                    });
            case "set_name" -> {
                y = row(g, x, y, w, "name", (cx, cw) -> textComponent(g, cx, y(), cw, at(path, "name")));
                y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, true));
                //? if >=1.21 {
                /*y = row(g, x, y, w, "name_target", (cx, cw) -> choice(g, cx, y(), cw, at(path, "target"), LootOptions.NAME_TARGETS, true));
                *///?}
            }
            case "set_lore" -> {
                y = textList(g, x, y, w, "lore", at(path, "lore"));
                y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, true));
                //? if >=1.21 {
                /*y = listMode(g, x, y, w, path);
                *///?} else {
                y = row(g, x, y, w, "replace", (cx, cw) -> check(g, cx, y(), at(path, "replace"), false));
                //?}
            }
            // 1.20.5 renamed set_nbt to set_custom_data, and copy_nbt below to copy_custom_data.
            case "set_nbt", "set_custom_data" -> y = row(g, x, y, w, "nbt", (cx, cw) -> text(g, cx, y(), cw, at(path, "tag"), false));
            case "set_instrument" -> y = row(g, x, y, w, "options", (cx, cw) -> pick(g, cx, y(), cw, at(path, "options"), LootOptions.INSTRUMENT_TAGS, false));
            case "exploration_map" -> {
                //? if >=26.3 {
                /*// From 26.3 it's a set of structures: a tag with its "#", a structure, or a list of structures.
                y = holderSet(g, x, y, w, "destination", at(path, "destination"), LootOptions.TAGS_OR_STRUCTURES, LootOptions.STRUCTURES, null);
                *///?} else {
                y = row(g, x, y, w, "destination", (cx, cw) -> pick(g, cx, y(), cw, at(path, "destination"), LootOptions.STRUCTURE_TAGS, true));
                //?}
                y = row(g, x, y, w, "decoration", (cx, cw) -> choice(g, cx, y(), cw, at(path, "decoration"), LootOptions.MAP_DECORATIONS, true));
                y = row(g, x, y, w, "zoom", (cx, cw) -> number(g, cx, y(), 50, at(path, "zoom"), Kind.INT, "2"));
                y = row(g, x, y, w, "search_radius", (cx, cw) -> number(g, cx, y(), 50, at(path, "search_radius"), Kind.INT, "50"));
                y = row(g, x, y, w, "skip_existing", (cx, cw) -> check(g, cx, y(), at(path, "skip_existing_chunks"), true));
            }
            case "limit_count" -> {
                y = row(g, x, y, w, "at_least", (cx, cw) -> number(g, cx, y(), 50, at(path, "limit.min"), Kind.NUMBER, ""));
                y = row(g, x, y, w, "at_most", (cx, cw) -> number(g, cx, y(), 50, at(path, "limit.max"), Kind.NUMBER, ""));
            }
            // 1.21 replaced looting_enchant with enchanted_count_increase, which takes any enchantment.
            case "looting_enchant", "enchanted_count_increase" -> {
                if (fn.equals("enchanted_count_increase")) {
                    y = row(g, x, y, w, "enchantment", (cx, cw) -> pick(g, cx, y(), cw, at(path, "enchantment"), LootOptions.ENCHANTMENTS, false));
                }
                y = row(g, x, y, w, "count", (cx, cw) -> provider(g, cx, y(), cw, at(path, "count"), 1));
                y = row(g, x, y, w, "limit", (cx, cw) -> number(g, cx, y(), 50, at(path, "limit"), Kind.INT, "0"));
            }
            case "apply_bonus" -> {
                y = row(g, x, y, w, "enchantment", (cx, cw) -> pick(g, cx, y(), cw, at(path, "enchantment"), LootOptions.ENCHANTMENTS, false));
                y = row(g, x, y, w, "formula", (cx, cw) -> choice(g, cx, y(), cw, at(path, "formula"), LootOptions.FORMULAS, false));
                String formula = LootTypes.shortId(JsonPaths.string(draft(), at(path, "formula"), ""));
                if (formula.equals("uniform_bonus_count")) {
                    y = row(g, x, y, w, "bonus_multiplier", (cx, cw) -> number(g, cx, y(), 50, at(path, "parameters.bonusMultiplier"), Kind.INT, "1"));
                } else if (formula.equals("binomial_with_bonus_count")) {
                    y = row(g, x, y, w, "extra", (cx, cw) -> number(g, cx, y(), 50, at(path, "parameters.extra"), Kind.INT, "0"));
                    y = row(g, x, y, w, "probability", (cx, cw) -> number(g, cx, y(), 50, at(path, "parameters.probability"), Kind.NUMBER, "0.5"));
                }
            }
            case "copy_name" -> y = row(g, x, y, w, "source", (cx, cw) -> choice(g, cx, y(), cw, at(path, "source"), LootOptions.COPY_SOURCES, false));
            case "copy_nbt", "copy_custom_data" -> {
                y = JsonPaths.get(draft(), at(path, "source")) instanceof JsonObject
                        ? row(g, x, y, w, "source", (cx, cw) -> raw(g, cx, y(), cw, at(path, "source")))
                        : row(g, x, y, w, "source", (cx, cw) -> choice(g, cx, y(), cw, at(path, "source"), LootOptions.NBT_SOURCES, false));
                y = objectList(g, x, y, w, "ops", at(path, "ops"), () -> object("source", "", "target", "", "op", "replace"), (ox, oy, ow, op) -> {
                    int ny = row(g, ox, oy, ow, "from_path", (cx, cw) -> text(g, cx, y(), cw, at(op, "source"), false));
                    ny = row(g, ox, ny, ow, "to_path", (cx, cw) -> text(g, cx, y(), cw, at(op, "target"), false));
                    return row(g, ox, ny, ow, "op", (cx, cw) -> choice(g, cx, y(), cw, at(op, "op"), LootOptions.COPY_OPS, false));
                });
            }
            case "copy_state" -> {
                String block = JsonPaths.string(draft(), at(path, "block"), "");
                y = row(g, x, y, w, "block", (cx, cw) -> pick(g, cx, y(), cw, at(path, "block"), LootOptions.BLOCKS, false));
                y = idList(g, x, y, w, "properties", at(path, "properties"), LootOptions.properties(block), null);
            }
            case "fill_player_head" -> y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, false));
            case "reference" -> y = row(g, x, y, w, "modifier", (cx, cw) -> pick(g, cx, y(), cw, at(path, "name"), LootOptions.ITEM_MODIFIERS, false));
            //? if >=1.21 {
            /*// Since 1.21 a modifier has an id instead of a name and UUID, and by default replaces the item's modifiers.
            case "set_attributes" -> {
                y = objectList(g, x, y, w, "modifiers", at(path, "modifiers"),
                        LootTypes::modifier,
                        (ox, oy, ow, op) -> {
                            int ny = row(g, ox, oy, ow, "attribute", (cx, cw) -> pick(g, cx, y(), cw, at(op, "attribute"), LootOptions.ATTRIBUTES, false));
                            ny = row(g, ox, ny, ow, "modifier_id", (cx, cw) -> text(g, cx, y(), cw, at(op, "id"), false));
                            ny = row(g, ox, ny, ow, "amount", (cx, cw) -> provider(g, cx, y(), cw, at(op, "amount"), 1));
                            ny = row(g, ox, ny, ow, "operation", (cx, cw) -> choice(g, cx, y(), cw, at(op, "operation"), LootOptions.OPERATIONS, false));
                            return JsonPaths.get(draft(), at(op, "slot")) instanceof JsonArray
                                    ? idList(g, ox, ny, ow, "slot", at(op, "slot"), LootOptions.SLOTS, null)
                                    : row(g, ox, ny, ow, "slot", (cx, cw) -> choice(g, cx, y(), cw, at(op, "slot"), LootOptions.SLOTS, false));
                        });
                y = row(g, x, y, w, "replace", (cx, cw) -> check(g, cx, y(), at(path, "replace"), true));
            }
            *///?} else {
            case "set_attributes" -> y = objectList(g, x, y, w, "modifiers", at(path, "modifiers"),
                    LootTypes::modifier,
                    (ox, oy, ow, op) -> {
                        int ny = row(g, ox, oy, ow, "attribute", (cx, cw) -> pick(g, cx, y(), cw, at(op, "attribute"), LootOptions.ATTRIBUTES, false));
                        ny = row(g, ox, ny, ow, "modifier_name", (cx, cw) -> text(g, cx, y(), cw, at(op, "name"), false));
                        ny = row(g, ox, ny, ow, "amount", (cx, cw) -> provider(g, cx, y(), cw, at(op, "amount"), 1));
                        ny = row(g, ox, ny, ow, "operation", (cx, cw) -> choice(g, cx, y(), cw, at(op, "operation"), LootOptions.OPERATIONS, false));
                        ny = JsonPaths.get(draft(), at(op, "slot")) instanceof JsonArray
                                ? idList(g, ox, ny, ow, "slot", at(op, "slot"), LootOptions.SLOTS, null)
                                : row(g, ox, ny, ow, "slot", (cx, cw) -> choice(g, cx, y(), cw, at(op, "slot"), LootOptions.SLOTS, false));
                        return row(g, ox, ny, ow, "uuid", (cx, cw) -> text(g, cx, y(), cw, at(op, "id"), true));
                    });
            //?}
            case "set_banner_pattern" -> {
                y = objectList(g, x, y, w, "patterns", at(path, "patterns"), () -> object("pattern", "minecraft:stripe_bottom", "color", "white"),
                        (ox, oy, ow, op) -> {
                            int ny = row(g, ox, oy, ow, "pattern", (cx, cw) -> pick(g, cx, y(), cw, at(op, "pattern"), LootOptions.BANNER_PATTERNS, false));
                            return row(g, ox, ny, ow, "color", (cx, cw) -> choice(g, cx, y(), cw, at(op, "color"), LootOptions.DYE_COLORS, false));
                        });
                y = row(g, x, y, w, "append", (cx, cw) -> check(g, cx, y(), at(path, "append"), false));
            }
            case "set_contents" -> {
                //? if >=1.21 {
                /*// Since 1.20.5 the contents go in a component, as a shulker box's or a bundle's.
                y = row(g, x, y, w, "component", (cx, cw) -> choice(g, cx, y(), cw, at(path, "component"), LootOptions.CONTAINERS, false));
                *///?} else {
                y = row(g, x, y, w, "block_entity", (cx, cw) -> pick(g, cx, y(), cw, at(path, "type"), LootOptions.BLOCK_ENTITY_TYPES, false));
                //?}
                y = row(g, x, y, w, "entries", (cx, cw) -> raw(g, cx, y(), cw, at(path, "entries")));
            }
            case "set_loot_table" -> {
                y = row(g, x, y, w, "table", (cx, cw) -> pick(g, cx, y(), cw, at(path, LootTypes.CONTAINER_TABLE_KEY), LootOptions.LOOT_TABLES, false));
                //? if <26.3 {
                y = row(g, x, y, w, "block_entity", (cx, cw) -> pick(g, cx, y(), cw, at(path, "type"), LootOptions.BLOCK_ENTITY_TYPES, false));
                //?}
                y = row(g, x, y, w, "seed", (cx, cw) -> number(g, cx, y(), Math.min(cw, 120), at(path, "seed"), Kind.INT, "0"));
            }
            case "explosion_decay", "furnace_smelt" -> {
            }
            default -> y = generic(g, x, y, w, path, List.of("function", "conditions"));
        }
        if (y == before) {
            Gui.fine(g, font, text("nothing_to_set").getString(), x + 4, y + 1, Gui.labelSoft());
            y += Gui.fineLine(font) + 4;
        }
        y = list(g, x, y, w, "conditions", JsonPaths.join(path, "conditions"), false);
        return end(g, x, top, y, w);
    }

    /** Draws a condition's card: its kind and settings. Some conditions hold others. */
    private int conditionCard(GuiGraphics g, int x, int y, int w, String path) {
        int top = y;
        String type = JsonPaths.string(draft(), JsonPaths.join(path, "condition"), "?");
        y = typeHead(g, x, y, w, JsonPaths.join(path, "condition"), LootTypes.conditionTypes(), "condition", type,
                picked -> set(path, LootTypes.condition(picked)), path);
        int before = y;
        switch (LootTypes.shortId(type)) {
            case "random_chance" -> y = row(g, x, y, w, "chance", (cx, cw) -> number(g, cx, y(), 60, at(path, "chance"), Kind.NUMBER, "0.5"));
            case "random_chance_with_looting" -> {
                y = row(g, x, y, w, "chance", (cx, cw) -> number(g, cx, y(), 60, at(path, "chance"), Kind.NUMBER, "0.1"));
                y = row(g, x, y, w, "per_looting", (cx, cw) -> number(g, cx, y(), 60, at(path, "looting_multiplier"), Kind.NUMBER, "0"));
            }
            case "random_chance_with_enchanted_bonus" -> {
                y = row(g, x, y, w, "enchantment", (cx, cw) -> pick(g, cx, y(), cw, at(path, "enchantment"), LootOptions.ENCHANTMENTS, false));
                y = row(g, x, y, w, "unenchanted_chance", (cx, cw) -> number(g, cx, y(), 60, at(path, "unenchanted_chance"), Kind.NUMBER, "0"));
                y = row(g, x, y, w, "enchanted_chance", (cx, cw) -> raw(g, cx, y(), cw, at(path, "enchanted_chance")));
            }
            // Neither has settings. The game doesn't read an "inverse" key on killed_by_player.
            case "killed_by_player", "survives_explosion" -> {
            }
            case "inverted" -> {
                String term = at(path, "term");
                if (JsonPaths.object(draft(), term) == null) {
                    ui.link(g, text("add_condition"), x + 4, y + 2, true, () -> set(term, LootTypes.condition("minecraft:random_chance")));
                    y += Gui.fineLine(font) + 6;
                } else {
                    y = conditionCard(g, x + INDENT, y, w - INDENT * 2, term) + 3;
                }
            }
            case "any_of", "all_of", "alternative" -> y = list(g, x, y, w, "terms", at(path, "terms"), false);
            case "table_bonus" -> {
                y = row(g, x, y, w, "enchantment", (cx, cw) -> pick(g, cx, y(), cw, at(path, "enchantment"), LootOptions.ENCHANTMENTS, false));
                y = numberList(g, x, y, w, "chances", at(path, "chances"));
            }
            case "block_state_property" -> {
                String block = JsonPaths.string(draft(), at(path, "block"), "");
                y = row(g, x, y, w, "block", (cx, cw) -> pick(g, cx, y(), cw, at(path, "block"), LootOptions.BLOCKS, false));
                y = stateMap(g, x, y, w, at(path, "properties"), block);
            }
            case "entity_properties" -> {
                y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, false));
                y = entityPredicate(g, x, y, w, at(path, "predicate"));
            }
            case "entity_scores" -> {
                y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, false));
                y = row(g, x, y, w, "scores", (cx, cw) -> raw(g, cx, y(), cw, at(path, "scores")));
            }
            case "damage_source_properties" -> y = row(g, x, y, w, "predicate", (cx, cw) -> raw(g, cx, y(), cw, at(path, "predicate")));
            case "location_check" -> {
                y = locationPredicate(g, x, y, w, at(path, "predicate"));
                y = row(g, x, y, w, "offset_x", (cx, cw) -> number(g, cx, y(), 50, at(path, "offsetX"), Kind.INT, "0"));
                y = row(g, x, y, w, "offset_y", (cx, cw) -> number(g, cx, y(), 50, at(path, "offsetY"), Kind.INT, "0"));
                y = row(g, x, y, w, "offset_z", (cx, cw) -> number(g, cx, y(), 50, at(path, "offsetZ"), Kind.INT, "0"));
            }
            case "match_tool" -> y = itemPredicate(g, x, y, w, at(path, "predicate"));
            case "reference" -> y = row(g, x, y, w, "predicate_id", (cx, cw) -> pick(g, cx, y(), cw, at(path, "name"), LootOptions.PREDICATES, false));
            case "time_check" -> {
                //? if >=26.1 {
                /*// 26.1 keeps time by clocks, and a world can have more than one.
                y = row(g, x, y, w, "clock", (cx, cw) -> pick(g, cx, y(), cw, at(path, "clock"), LootOptions.CLOCKS, false));
                *///?}
                y = row(g, x, y, w, "time", (cx, cw) -> range(g, cx, y(), cw, at(path, "value")));
                y = row(g, x, y, w, "period", (cx, cw) -> number(g, cx, y(), 60, at(path, "period"), Kind.INT, ""));
            }
            case "value_check" -> {
                y = row(g, x, y, w, "value", (cx, cw) -> provider(g, cx, y(), cw, at(path, "value"), 1));
                y = row(g, x, y, w, "range", (cx, cw) -> range(g, cx, y(), cw, at(path, "range")));
            }
            case "weather_check" -> {
                y = row(g, x, y, w, "raining", (cx, cw) -> maybe(g, cx, y(), cw, at(path, "raining")));
                y = row(g, x, y, w, "thundering", (cx, cw) -> maybe(g, cx, y(), cw, at(path, "thundering")));
            }
            default -> y = generic(g, x, y, w, path, List.of("condition"));
        }
        if (y == before) {
            Gui.fine(g, font, text("nothing_to_set").getString(), x + 4, y + 1, Gui.labelSoft());
            y += Gui.fineLine(font) + 4;
        }
        return end(g, x, top, y, w);
    }

    /**
     * Draws a JSON field for each setting of an unknown kind, and a link to add a setting. A new
     * setting's name is typed first, then its value.
     */
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
            y = labelled(g, x, y, w, StructureNames.pretty(key), (cx, cw) -> raw(g, cx, y(), cw, JsonPaths.join(path, JsonPaths.key(key))));
        }
        if (!path.equals(naming)) {
            ui.link(g, text("add_setting"), x + 4, y + 1, true, () -> {
                naming = path;
                pendingEdit = path + "#setting";
            });
            return y + Gui.fineLine(font) + 5;
        }
        String id = path + "#setting";
        return row(g, x, y, w, "setting_name", (cx, cw) -> {
            int[] rect = {cx, y(), cw - 12, FIELD};
            host.drawn(id, rect);
            boolean lit = id.equals(host.editing()) || ui.hovered(rect[0], rect[1], rect[2], FIELD);
            g.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + FIELD, lit ? 0xFFFFFFFF : 0xFFA0A0A0);
            g.fill(rect[0] + 1, rect[1] + 1, rect[0] + rect[2] - 1, rect[1] + FIELD - 1, 0xFF000000);
            Consumer<String> named = name -> nameSetting(path, name.trim());
            ui.spot(rect[0], rect[1], rect[2], FIELD, () -> host.edit(id, rect, "", named));
            if (id.equals(pendingEdit)) {
                pendingEdit = null;
                host.edit(id, rect, "", named);
            }
            removeX(g, cx + cw - 8, y(), () -> naming = null);
        });
    }

    /**
     * Adds an empty setting with the typed name and starts editing its value. An empty name does
     * nothing; an existing name is refused.
     */
    private void nameSetting(String path, String name) {
        naming = null;
        JsonObject object = JsonPaths.object(draft(), path);
        if (object == null || name.isEmpty()) {
            return;
        }
        if (object.has(name)) {
            host.refused(text("setting_there", name));
            return;
        }
        pendingEdit = JsonPaths.join(path, JsonPaths.key(name));
        set(pendingEdit, new JsonPrimitive(""));
    }

    // ------------------------------------------------------------------ predicates

    /** Draws an entity predicate: its type, with the rest as JSON. */
    private int entityPredicate(GuiGraphics g, int x, int y, int w, String path) {
        //? if >=1.21 {
        /*y = holderSet(g, x, y, w, "entity_type", at(path, LootTypes.ENTITY_TYPE_KEY), LootOptions.ENTITY_TYPES_OR_TAGS, LootOptions.ENTITY_TYPES, null);
        *///?} else {
        y = row(g, x, y, w, "entity_type", (cx, cw) -> pick(g, cx, y(), cw, at(path, LootTypes.ENTITY_TYPE_KEY), LootOptions.ENTITY_TYPES_OR_TAGS, true));
        //?}
        return row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, List.of(LootTypes.ENTITY_TYPE_KEY)));
    }

    /** Draws a location predicate: biome, structure, dimension and smokey, with the rest as JSON. */
    private int locationPredicate(GuiGraphics g, int x, int y, int w, String path) {
        //? if >=1.21 {
        /*// 1.20.5 made these sets, of biomes and of structures.
        y = holderSet(g, x, y, w, "biomes", at(path, "biomes"), LootOptions.BIOMES_OR_TAGS, LootOptions.BIOMES, null);
        y = holderSet(g, x, y, w, "structures", at(path, "structures"), LootOptions.STRUCTURES_OR_TAGS, LootOptions.STRUCTURES, null);
        List<String> shown = List.of("biomes", "structures", "dimension", "smokey");
        *///?} else {
        y = row(g, x, y, w, "biome", (cx, cw) -> pick(g, cx, y(), cw, at(path, "biome"), LootOptions.BIOMES, true));
        y = row(g, x, y, w, "structure", (cx, cw) -> pick(g, cx, y(), cw, at(path, "structure"), LootOptions.STRUCTURES, true));
        List<String> shown = List.of("biome", "structure", "dimension", "smokey");
        //?}
        y = row(g, x, y, w, "dimension", (cx, cw) -> pick(g, cx, y(), cw, at(path, "dimension"), LootOptions.DIMENSIONS, true));
        y = row(g, x, y, w, "smokey", (cx, cw) -> maybe(g, cx, y(), cw, at(path, "smokey")));
        return row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, shown));
    }

    /** Draws an item predicate: items, tag, potion and enchantments, with the rest as JSON. */
    private int itemPredicate(GuiGraphics g, int x, int y, int w, String path) {
        //? if >=1.21 {
        /*// Since 1.20.5 a tag goes in items with its "#", and potions and enchantments are among the
        // predicates, each by its id.
        y = holderSet(g, x, y, w, "items", at(path, "items"), LootOptions.ITEMS_OR_TAGS, LootOptions.ITEMS, text("any_item"));
        String predicates = at(path, "predicates");
        y = holderSet(g, x, y, w, "potion", at(predicates, "minecraft:potion_contents"), LootOptions.POTIONS_OR_TAGS, LootOptions.POTIONS, null);
        y = objectList(g, x, y, w, "enchantments", at(predicates, "minecraft:enchantments"), () -> object("enchantments", "minecraft:silk_touch"),
                (ox, oy, ow, op) -> {
                    int ny = holderSet(g, ox, oy, ow, "enchantment", at(op, "enchantments"), LootOptions.ENCHANTMENTS_OR_TAGS, LootOptions.ENCHANTMENTS, null);
                    return row(g, ox, ny, ow, "levels", (cx, cw) -> range(g, cx, y(), cw, at(op, "levels")));
                });
        y = row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, List.of("items", "predicates")));
        return row(g, x, y, w, "more_predicates", (cx, cw) -> rest(g, cx, y(), cw, predicates, List.of("minecraft:potion_contents", "minecraft:enchantments")));
        *///?} else {
        y = idList(g, x, y, w, "items", at(path, "items"), LootOptions.ITEMS, text("any_item"));
        y = row(g, x, y, w, "item_tag", (cx, cw) -> pick(g, cx, y(), cw, at(path, "tag"), LootOptions.ITEM_TAG_IDS, true));
        y = row(g, x, y, w, "potion", (cx, cw) -> pick(g, cx, y(), cw, at(path, "potion"), LootOptions.POTIONS, true));
        y = objectList(g, x, y, w, "enchantments", at(path, "enchantments"), () -> object("enchantment", "minecraft:silk_touch"), (ox, oy, ow, op) -> {
            int ny = row(g, ox, oy, ow, "enchantment", (cx, cw) -> pick(g, cx, y(), cw, at(op, "enchantment"), LootOptions.ENCHANTMENTS, false));
            return row(g, ox, ny, ow, "levels", (cx, cw) -> range(g, cx, y(), cw, at(op, "levels")));
        });
        return row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, List.of("items", "tag", "potion", "enchantments")));
        //?}
    }

    /**
     * Draws a JSON field for the rest of an object, leaving out the keys the form has fields for, so
     * unknown settings can still be set. Typed JSON replaces those other keys and keeps the shown ones.
     */
    private void rest(GuiGraphics g, int x, int y, int w, String path, List<String> shownElsewhere) {
        JsonObject object = JsonPaths.object(draft(), path);
        JsonObject others = new JsonObject();
        if (object != null) {
            object.entrySet().forEach(e -> {
                if (!shownElsewhere.contains(e.getKey())) {
                    others.add(e.getKey(), e.getValue());
                }
            });
        }
        String shown = others.size() == 0 ? "" : others.toString();
        String id = path + "#rest";
        int[] rect = {x, y, w, FIELD};
        host.drawn(id, rect);
        boolean editing = id.equals(host.editing());
        boolean over = ui.hovered(x, y, w, FIELD);
        g.fill(x, y, x + w, y + FIELD, over || editing ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD - 1, 0xFF000000);
        if (!editing) {
            Gui.drawClipped(g, font, shown, x + 4, y + 3, w - 8, 0xFFE0E0E0, false);
        }
        ui.spot(x, y, w, FIELD, () -> host.edit(id, rect, shown, typed -> {
            JsonObject parsed;
            try {
                parsed = typed.isBlank() ? new JsonObject() : JsonParser.parseString(typed).getAsJsonObject();
            } catch (RuntimeException e) {
                return;
            }
            JsonObject now = new JsonObject();
            JsonObject before = JsonPaths.object(draft(), path);
            if (before != null) {
                before.entrySet().forEach(e -> {
                    if (shownElsewhere.contains(e.getKey())) {
                        now.add(e.getKey(), e.getValue());
                    }
                });
            }
            parsed.entrySet().forEach(e -> now.add(e.getKey(), e.getValue()));
            set(path, now);
        }));
    }

    /** Draws the block state properties to match: each a property of the chosen block and one of its values. */
    private int stateMap(GuiGraphics g, int x, int y, int w, String path, String block) {
        JsonObject map = JsonPaths.object(draft(), path);
        List<String> keys = map == null ? List.of() : List.copyOf(map.keySet());
        LootOptions.Source properties = LootOptions.properties(block);
        return listRows(g, x, y, w, text("field.properties").getString(), keys.size(), null, () -> {
            List<LootOptions.Option> all = properties.list();
            String first = all.stream().map(LootOptions.Option::id).filter(p -> !keys.contains(p)).findFirst().orElse(null);
            if (first != null) {
                List<LootOptions.Option> values = LootOptions.values(block, first).list();
                set(at(path, first), new JsonPrimitive(values.isEmpty() ? "" : values.get(0).id()));
            }
        }, (index, cx, cw) -> {
            String key = keys.get(index);
            String valuePath = at(path, key);
            int half = (cw - 12) / 2;
            choiceBox(g, cx, y(), half, path + "#key" + index, properties, key, false, picked -> renameKey(path, key, picked));
            if (JsonPaths.get(draft(), valuePath) instanceof JsonObject) {
                range(g, cx + half + 4, y(), cw - half - 16, valuePath);
            } else {
                choice(g, cx + half + 4, y(), cw - half - 16, valuePath, LootOptions.values(block, key), false);
            }
            removeX(g, cx + cw - 8, y(), () -> remove(valuePath));
        });
    }

    // ------------------------------------------------------------------ more kinds of field

    /** A dropdown of {@code options} for the value at {@code path}; an optional one can be left unset. */
    private void choice(GuiGraphics g, int x, int y, int w, String path, LootOptions.Source options, boolean optional) {
        String current = JsonPaths.get(draft(), path) == null ? "" : JsonPaths.string(draft(), path, "");
        choiceBox(g, x, y, w, path, options, current, optional, picked -> set(path, picked.isEmpty() ? null : new JsonPrimitive(picked)));
    }

    private void choiceBox(GuiGraphics g, int x, int y, int w, String id, LootOptions.Source options, String current, boolean optional,
                           Consumer<String> pick) {
        List<String> ids = new java.util.ArrayList<>();
        if (optional) {
            ids.add("");
        }
        options.list().forEach(o -> ids.add(o.id()));
        if (!current.isEmpty() && !ids.contains(current) && options.name(current) == null) {
            ids.add(current);
        }
        dropdown(g, x, y, w, id, ids, option -> {
            if (option.isEmpty()) {
                return text("not_set").getString();
            }
            String name = options.name(option);
            return name != null ? name : option;
        }, current, pick);
    }

    /** A yes, no or either dropdown: true, false, or left out. */
    private void maybe(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        String current = value == null ? "" : String.valueOf(JsonPaths.bool(draft(), path));
        dropdown(g, x, y, Math.min(w, 80), path, List.of("", "true", "false"),
                option -> text(option.isEmpty() ? "either" : option.equals("true") ? "yes" : "no").getString(), current,
                picked -> set(path, picked.isEmpty() ? null : new JsonPrimitive(Boolean.parseBoolean(picked))));
    }

    /** A min and max range, either of which can be empty. A single number stays a single number. */
    private void range(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        if (value != null && value.isJsonPrimitive()) {
            number(g, x, y, Math.min(w, 60), path, Kind.NUMBER, "");
            return;
        }
        String from = text("range_from").getString();
        String to = text("range_to").getString();
        int each = Math.max(14, Math.min(60, (w - Gui.fineWidth(font, from) - Gui.fineWidth(font, to) - 10) / 2));
        Gui.fine(g, font, from, x, y + 4, Gui.labelSoft());
        int fx = x + Gui.fineWidth(font, from) + 3;
        number(g, fx, y, each, at(path, "min"), Kind.NUMBER, "");
        int tx = fx + each + 4;
        Gui.fine(g, font, to, tx, y + 4, Gui.labelSoft());
        number(g, tx + Gui.fineWidth(font, to) + 3, y, each, at(path, "max"), Kind.NUMBER, "");
    }

    /** A list of numbers, like a chance for each enchantment level, each with an x, and a link to add one. */
    private int numberList(GuiGraphics g, int x, int y, int w, String key, String path) {
        return listRows(g, x, y, w, text("field." + key).getString(), size(path), null, () -> append(path, JsonPaths.number(0)), (index, cx, cw) -> {
            String itemPath = at(path, index);
            Gui.fine(g, font, text("level", index).getString(), cx, y() + 3, Gui.labelSoft());
            number(g, cx + 40, y(), 50, itemPath, Kind.NUMBER, "0");
            removeX(g, cx + 96, y(), () -> remove(itemPath));
        });
    }

    /** Lines of text, like lore: a box for each, an x to take it out, and a link to add another. */
    private int textList(GuiGraphics g, int x, int y, int w, String key, String path) {
        return listRows(g, x, y, w, text("field." + key).getString(), size(path), null, () -> append(path, new JsonPrimitive("")), (index, cx, cw) -> {
            String itemPath = at(path, index);
            textComponent(g, cx, y(), cw - 12, itemPath);
            removeX(g, cx + cw - 8, y(), () -> remove(itemPath));
        });
    }

    /** Draws the list item at {@code index} (from 0) in place of a row's control. */
    private interface ListRow {
        void draw(int index, int x, int w);
    }

    /**
     * Draws a row for each of a list's {@code count} items with {@code row}, the list's label on the
     * first, then a row with an add link that runs {@code add}. {@code empty}, if given, shows beside
     * that link when the list is empty, to say what that means. Returns the y below them.
     */
    private int listRows(GuiGraphics g, int x, int y, int w, String label, int count, Component empty, Runnable add, ListRow row) {
        for (int i = 0; i <= count; i++) {
            int index = i;
            y = labelled(g, x, y, w, i == 0 ? label : "", (cx, cw) -> {
                if (index < count) {
                    row.draw(index, cx, cw);
                    return;
                }
                if (count == 0 && empty != null) {
                    Gui.fine(g, font, empty.getString(), cx, y() + 3, Gui.labelSoft());
                }
                Component link = text("add");
                ui.link(g, link, cx + cw - Gui.fineWidth(font, link.getString()), y() + 3, true, add);
            });
        }
        return y;
    }

    /** Returns the size of the list at the path, or 0 if there's no list. */
    private int size(String path) {
        JsonArray items = JsonPaths.array(draft(), path);
        return items == null ? 0 : items.size();
    }

    private static String at(String path, Object part) {
        return JsonPaths.join(path, part);
    }

    /** A new object from key and value pairs, for adding to a list. */
    private static JsonObject object(Object... pairs) {
        JsonObject out = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            out.add((String) pairs[i], value instanceof Number n ? JsonPaths.number(n.doubleValue()) : new JsonPrimitive(String.valueOf(value)));
        }
        return out;
    }

    // ------------------------------------------------------------------ lists

    /** A heading with a count and a link to add one, then a card for each function or condition. */
    private int list(GuiGraphics g, int x, int y, int w, String key, String path, boolean functions) {
        int count = size(path);
        y += 2;
        g.drawString(font, text("list." + key), x + 4, y + 1, ToolsUi.TEXT, false);
        String countText = count == 0 ? text("none").getString() : String.valueOf(count);
        Gui.fine(g, font, countText, x + 8 + font.width(text("list." + key)), y + 2, Gui.labelSoft());
        Component add = text(functions ? "add_function" : "add_condition");
        int addW = Gui.fineWidth(font, add.getString());
        ui.link(g, add, x + w - 4 - addW, y + 2, true,
                () -> append(path, functions ? LootTypes.function("minecraft:set_count") : LootTypes.condition("minecraft:random_chance")));
        y += font.lineHeight + 3;
        for (int i = 0; i < count; i++) {
            String itemPath = JsonPaths.join(path, i);
            y = (functions ? functionCard(g, x + INDENT, y, w - INDENT * 2, itemPath) : conditionCard(g, x + INDENT, y, w - INDENT * 2, itemPath)) + 3;
        }
        return y;
    }

    // ------------------------------------------------------------------ parts of a card

    /** Draws a card's title bar, with an optional link at the right. Returns the y below it. */
    private int head(GuiGraphics g, int x, int y, int w, Component title, Component action, Runnable onAction) {
        g.fill(x, y, x + w, y + 14, 0xFF8B8B8B);
        int right = x + w - 4;
        if (action != null) {
            right -= Gui.fineWidth(font, action.getString());
            ui.link(g, action, right, y + 4, true, onAction);
        }
        Gui.drawClipped(g, font, title.getString(), x + 4, y + 3, Math.max(20, right - x - 10), 0xFFFFFFFF, true);
        return y + 16;
    }

    /** After a copy or move: if it worked ({@code to >= 0}), passes {@code to} to {@code picked} if given, and marks the table changed. */
    private void listChange(int to, IntConsumer picked) {
        if (to < 0) {
            return;
        }
        if (picked != null) {
            picked.accept(to);
        }
        host.changed();
    }

    /**
     * Draws the head of a function or condition card: a kind dropdown, then remove, duplicate and move
     * buttons. The buttons get no label, so the scripted scenarios' buttonAt finds the tree row's
     * buttons of the same name.
     */
    private int typeHead(GuiGraphics g, int x, int y, int w, String id, List<String> options, String kind, String current,
                         Consumer<String> pick, String path) {
        g.fill(x, y, x + w, y + 16, 0xFF9D9D9D);
        dropdown(g, x + 2, y + 1, Math.min(w - 58, 150), id, options, option -> LootTypes.name(kind, option), current, pick);
        int rx = x + w - 11;
        ui.iconButton(g, rx, y + 4, Icons.REMOVE, LootEditorScreen.REMOVE_LIT, text(kind.equals("function") ? "remove_function" : "remove_condition"),
                null, () -> remove(path));
        ui.iconButton(g, rx - 12, y + 4, Icons.COPY_SMALL, LootEditorScreen.BUTTON_LIT, text("duplicate"), null,
                () -> listChange(JsonPaths.duplicate(draft(), path), null));
        int index = JsonPaths.indexIn(draft(), path);
        if (index < JsonPaths.siblings(draft(), path) - 1) {
            ui.iconButton(g, rx - 24, y + 4, Icons.MOVE_DOWN, LootEditorScreen.BUTTON_LIT, text("move_down"), null,
                    () -> listChange(JsonPaths.move(draft(), path, 1), null));
        }
        if (index > 0) {
            ui.iconButton(g, rx - 36, y + 4, Icons.MOVE_UP, LootEditorScreen.BUTTON_LIT, text("move_up"), null,
                    () -> listChange(JsonPaths.move(draft(), path, -1), null));
        }
        return y + 19;
    }

    /** Draws a card's border once its height is known. Returns the y below it. */
    private int end(GuiGraphics g, int x, int top, int y, int w) {
        int bottom = y + 2;
        g.fill(x, top, x + 1, bottom, 0xFF8B8B8B);
        g.fill(x + w - 1, top, x + w, bottom, 0xFF8B8B8B);
        g.fill(x, bottom - 1, x + w, bottom, 0xFF8B8B8B);
        return bottom;
    }

    /** The y of the row being drawn, read by the control lambdas through y(). */
    private int rowY;

    private int y() {
        return rowY;
    }

    private interface Control {
        void draw(int x, int w);
    }

    /** A label and its control. Returns the y below them. */
    private int row(GuiGraphics g, int x, int y, int w, String key, Control control) {
        // A tooltip on the label explaining the field, where the lang file has one.
        String help = "screen.justenoughstructures.editor.help." + key;
        if (Language.getInstance().has(help)) {
            ui.tooltip(x, y, labelW, FIELD, Component.translatable(help));
        }
        return labelled(g, x, y, w, text("field." + key).getString(), control);
    }

    private int labelled(GuiGraphics g, int x, int y, int w, String label, Control control) {
        Gui.fineClipped(g, font, label, x + 4, y + (FIELD - Gui.fineLine(font)) / 2 + 1, labelW - 8, Gui.labelSoft());
        rowY = y;
        control.draw(x + labelW, w - labelW - 4);
        return y + FIELD + GAP;
    }

    private void soft(GuiGraphics g, int x, String text) {
        g.drawString(font, text, x, rowY + 3, Gui.labelSoft(), false);
    }

    // ------------------------------------------------------------------ controls

    /** What a typed field holds, which decides how its text is read back. */
    enum Kind { TEXT, OPTIONAL_TEXT, INT, NUMBER, RAW, COMPONENT }

    /** A text field, drawn here and edited through the editor's single text box when clicked. */
    private void field(GuiGraphics g, int x, int y, int w, String path, Kind kind, String shown) {
        box(g, x, y, w, path, shown, text -> commit(path, kind, text));
        if (kind == Kind.RAW && font.width(shown) > w - 8) {
            ui.tooltip(x, y, w, FIELD, Component.literal(shown));
        }
    }

    /** Draws a field box showing {@code shown}; typed text goes to {@code typed}. {@code id} identifies the box in the form. */
    private void box(GuiGraphics g, int x, int y, int w, String id, String shown, Consumer<String> typed) {
        int[] rect = {x, y, w, FIELD};
        host.drawn(id, rect);
        boolean editing = id.equals(host.editing());
        boolean over = ui.hovered(x, y, w, FIELD);
        g.fill(x, y, x + w, y + FIELD, over || editing ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD - 1, 0xFF000000);
        if (!editing) {
            Gui.drawClipped(g, font, shown, x + 4, y + 3, w - 8, 0xFFE0E0E0, false);
        }
        ui.spot(x, y, w, FIELD, () -> host.edit(id, rect, shown, typed));
        if (id.equals(pendingEdit)) {
            pendingEdit = null;
            host.edit(id, rect, shown, typed);
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

    /** Yes or no; {@code fallback} is what the game takes when it's left out, as some default to yes. */
    private void check(GuiGraphics g, int x, int y, String path, boolean fallback) {
        JsonElement value = JsonPaths.get(draft(), path);
        boolean on = value == null ? fallback : JsonPaths.bool(draft(), path);
        ui.check(g, text(on ? "yes" : "no"), x, y + 3, on, () -> set(path, new JsonPrimitive(!on)));
    }

    /** A box showing the current choice, which opens the list of options. */
    private void dropdown(GuiGraphics g, int x, int y, int w, String id, List<String> options, Function<String, String> names, String current,
                          Consumer<String> pick) {
        int[] rect = {x, y, w, FIELD};
        host.drawn(id, rect);
        boolean over = ui.hovered(x, y, w, FIELD);
        g.fill(x, y, x + w, y + FIELD, over ? 0xFFFFFFFF : 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD - 1, over ? 0xFF7A7A7A : 0xFF6D6D6D);
        Gui.drawClipped(g, font, names.apply(current), x + 4, y + 3, w - 16, 0xFFFFFFFF, true);
        Gui.blit(g, Icons.DROPDOWN, x + w - 10, y + 5, 0, 0, Icons.DROPDOWN_W, Icons.DROPDOWN_H, Icons.DROPDOWN_W, Icons.DROPDOWN_H);
        ui.spot(x, y, w, FIELD, () -> host.choose(rect, options, names, current, pick));
    }

    /** A field just added to a list, to start typing in as soon as it's drawn. */
    private String pendingEdit;
    /** Path of the function, condition or entry getting a new setting while its name is typed. Null for none. */
    private String naming;

    /**
     * A field for an id, like an enchantment or a potion, with suggested values listed under it while
     * typing. It shows the id's name, and the id itself while typing; an id the game doesn't know
     * shows in amber.
     */
    private void pick(GuiGraphics g, int x, int y, int w, String path, LootOptions.Source options, boolean optional) {
        String value = JsonPaths.string(draft(), path, "");
        pickBox(g, x, y, w, path, value, options, text -> commit(path, optional ? Kind.OPTIONAL_TEXT : Kind.TEXT, text));
    }

    private void pickBox(GuiGraphics g, int x, int y, int w, String id, String value, LootOptions.Source options, Consumer<String> commit) {
        int[] rect = {x, y, w, FIELD};
        host.drawn(id, rect);
        boolean editing = id.equals(host.editing());
        boolean over = ui.hovered(x, y, w, FIELD);
        g.fill(x, y, x + w, y + FIELD, over || editing ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + FIELD - 1, 0xFF000000);
        String name = options.name(value.trim());
        if (!editing) {
            int colour = name != null || value.isBlank() ? 0xFFE0E0E0 : 0xFFFFC060;
            Gui.drawClipped(g, font, name != null ? name : value, x + 4, y + 3, w - 16, colour, false);
            for (int i = 0; i < 4; i++) {
                g.fill(x + w - 10 + i, y + 5 + i, x + w - 3 - i, y + 6 + i, 0xFFA0A0A0);
            }
            if (over && !value.isBlank()) {
                ui.tooltip(x, y, w, FIELD, name != null ? Component.literal(value) : text("not_in_game"));
            }
        }
        ui.spot(x, y, w, FIELD, () -> host.edit(id, rect, value, commit, options));
        if (id.equals(pendingEdit)) {
            pendingEdit = null;
            host.edit(id, rect, value, commit, options);
        }
    }

    /**
     * A list of ids, like the enchantments to choose from: a box for each with a remove button, and a
     * link to add another. {@code empty} says what an empty list means.
     */
    private int idList(GuiGraphics g, int x, int y, int w, String key, String path, LootOptions.Source options, Component empty) {
        int count = size(path);
        return listRows(g, x, y, w, text("field." + key).getString(), count, empty, () -> {
            pendingEdit = at(path, count);
            append(path, new JsonPrimitive(""));
        }, (index, cx, cw) -> {
            String itemPath = at(path, index);
            // An emptied box is removed from the list, not left as an empty id.
            pickBox(g, cx, y(), cw - 12, itemPath, JsonPaths.string(draft(), itemPath, ""), options, text -> {
                if (text.isBlank()) {
                    remove(itemPath);
                } else {
                    commit(itemPath, Kind.TEXT, text);
                }
            });
            removeX(g, cx + cw - 8, y(), () -> remove(itemPath));
        });
    }

    /**
     * A map from ids to amounts, like enchantments to their levels: each id in a box with its list,
     * its amount beside it, an x to take it out, and a link to add another.
     */
    private int idMap(GuiGraphics g, int x, int y, int w, String key, String path, LootOptions.Source options, double fallback) {
        JsonObject map = JsonPaths.object(draft(), path);
        List<String> keys = map == null ? List.of() : List.copyOf(map.keySet());
        return listRows(g, x, y, w, text("field." + key).getString(), keys.size(), null, () -> {
            JsonObject now = JsonPaths.object(draft(), path);
            if (now == null) {
                now = new JsonObject();
                JsonPaths.set(draft(), path, now);
            }
            if (!now.has("")) {
                now.add("", new JsonPrimitive(1));
            }
            pendingEdit = path + "#key" + now.keySet().stream().toList().indexOf("");
            host.changed();
        }, (index, cx, cw) -> {
            String id = keys.get(index);
            int pickW = Math.max(40, (cw - 12) * 55 / 100);
            pickBox(g, cx, y(), pickW, path + "#key" + index, id, options, text -> renameKey(path, id, text.trim()));
            if (!id.isEmpty()) {
                provider(g, cx + pickW + 4, y(), cw - pickW - 16, at(path, JsonPaths.key(id)), fallback);
            }
            removeX(g, cx + cw - 8, y(), () -> remove(at(path, JsonPaths.key(id))));
        });
    }

    //? if >=1.21 {
    /*// A set of ids as 1.20.5 writes them: one id or a tag with its "#" in a single box, or a list of
    // ids with a box each. Adding to a single id makes it a list; a tag can't be listed with others.
    private int holderSet(GuiGraphics g, int x, int y, int w, String key, String path, LootOptions.Source one, LootOptions.Source each,
                          Component empty) {
        if (JsonPaths.get(draft(), path) instanceof JsonArray) {
            return idList(g, x, y, w, key, path, each, empty);
        }
        String current = JsonPaths.string(draft(), path, "");
        return row(g, x, y, w, key, (cx, cw) -> {
            Component add = text("add");
            boolean listable = !current.isBlank() && !current.startsWith("#");
            int addW = listable ? Gui.fineWidth(font, add.getString()) + 6 : 0;
            int boxW = cw - addW - (current.isBlank() ? 0 : 12);
            pickBox(g, cx, y(), boxW, path, current, one, typed -> {
                if (typed.isBlank()) {
                    remove(path);
                } else {
                    commit(path, Kind.TEXT, typed);
                }
            });
            if (current.isBlank() && empty != null && !path.equals(host.editing())) {
                Gui.fine(g, font, empty.getString(), cx + 4, y() + 3, Gui.labelSoft());
            }
            if (!current.isBlank()) {
                removeX(g, cx + boxW + 4, y(), () -> remove(path));
            }
            if (listable) {
                ui.link(g, add, cx + cw - addW + 6, y() + 3, true, () -> {
                    JsonArray list = new JsonArray();
                    list.add(current);
                    list.add("");
                    pendingEdit = at(path, 1);
                    set(path, list);
                });
            }
        });
    }

    // How a list is changed since 1.20.5: added to, put in at a line, or written over in part or whole.
    private int listMode(GuiGraphics g, int x, int y, int w, String path) {
        y = row(g, x, y, w, "mode", (cx, cw) -> choice(g, cx, y(), cw, at(path, "mode"), LootOptions.LIST_MODES, false));
        String mode = JsonPaths.string(draft(), at(path, "mode"), "");
        if (mode.equals("insert") || mode.equals("replace_section")) {
            y = row(g, x, y, w, "offset", (cx, cw) -> number(g, cx, y(), 50, at(path, "offset"), Kind.INT, "0"));
        }
        if (mode.equals("replace_section")) {
            y = row(g, x, y, w, "size", (cx, cw) -> number(g, cx, y(), 50, at(path, "size"), Kind.INT, ""));
        }
        return y;
    }

    *///?}
    /** Renames a key of an object, keeping its position and value. */
    private void renameKey(String path, String from, String to) {
        JsonObject map = JsonPaths.object(draft(), path);
        if (map == null || to.equals(from) && !to.isEmpty()) {
            return;
        }
        if (to.isEmpty()) {
            // An unnamed new key is removed; an existing key keeps its name.
            if (from.isEmpty()) {
                JsonPaths.remove(draft(), JsonPaths.join(path, JsonPaths.key(from)));
                map.remove("");
                host.changed();
            }
            return;
        }
        JsonObject renamed = new JsonObject();
        map.entrySet().forEach(e -> {
            if (e.getKey().equals(from)) {
                renamed.add(to, e.getValue());
            } else if (!e.getKey().equals(to)) {
                renamed.add(e.getKey(), e.getValue());
            }
        });
        set(path, renamed);
    }

    /** Draws the fields of one object in a list. Returns the y below them. */
    private interface ObjectFields {
        int draw(int x, int y, int w, String path);
    }

    /**
     * A list of objects, like a stew's effects: a small card for each, its fields drawn by
     * {@code fields}, with an x to take it out, and a link to add another made by {@code fresh}.
     */
    private int objectList(GuiGraphics g, int x, int y, int w, String key, String path, java.util.function.Supplier<JsonObject> fresh, ObjectFields fields) {
        int count = size(path);
        y += 2;
        Gui.fineClipped(g, font, text("field." + key).getString(), x + 4, y + 1, w - 60, Gui.labelSoft());
        Component add = text("add");
        ui.link(g, add, x + w - 4 - Gui.fineWidth(font, add.getString()), y + 1, true, () -> append(path, fresh.get()));
        y += Gui.fineLine(font) + 4;
        for (int i = 0; i < count; i++) {
            String itemPath = JsonPaths.join(path, i);
            int top = y;
            int cx = x + INDENT;
            int cw = w - INDENT * 2;
            g.fill(cx, y, cx + cw, y + 12, 0xFFB0B0B0);
            Gui.fine(g, font, String.valueOf(i + 1), cx + 4, y + 2, Gui.labelSoft());
            removeX(g, cx + cw - 9, y, () -> remove(itemPath));
            y = fields.draw(cx, y + 14, cw, itemPath);
            y = end(g, cx, top, y, cw) + 3;
        }
        return y;
    }

    /** A dark red remove button on a card. */
    private void removeX(GuiGraphics g, int x, int y, Runnable action) {
        ui.iconButton(g, x - 1, y + 3, Icons.REMOVE, LootEditorScreen.REMOVE_LIT, 0xFF6A1010, text("remove"), null, action);
    }

    /**
     * Text the game shows, like a name or a line of lore. Plain text is kept as it's typed; JSON text
     * components, like {"text": "...", "color": "gold"}, are kept as they are.
     */
    private void textComponent(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        if (!TextStyle.simple(value)) {
            // Too complex for the colour and style buttons, so it's shown as JSON.
            field(g, x, y, w, path, Kind.COMPONENT, value.isJsonPrimitive() ? value.getAsString() : value.toString());
            return;
        }
        int styleW = 38;
        box(g, x, y, w - styleW, path, TextStyle.text(value), typed -> {
            String trimmed = typed.trim();
            JsonElement parsed = null;
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    parsed = JsonParser.parseString(trimmed);
                } catch (JsonParseException e) {
                    // Not JSON after all: plain text.
                }
            }
            // The colour and style stay with the words as they're retyped.
            JsonElement now = trimmed.isEmpty() ? null : parsed != null ? parsed : TextStyle.with(value, "text", new JsonPrimitive(typed));
            if (now == null ? value != null : !now.equals(value)) {
                set(path, now);
            }
        });
        // A colour swatch, which opens the list of colours.
        int sx = x + w - styleW + 3;
        String colour = TextStyle.colour(value);
        int[] swatch = {sx, y + 2, 10, 10};
        host.drawn(path + "#color", swatch);
        g.fill(sx, y + 2, sx + 10, y + 12, ui.hovered(sx, y + 2, 10, 10) ? 0xFFFFFFFF : 0xFF000000);
        g.fill(sx + 1, y + 3, sx + 9, y + 11, 0xFF000000 | TextStyle.rgb(colour));
        List<String> colours = new java.util.ArrayList<>(List.of(""));
        colours.addAll(TextStyle.COLOURS);
        if (!colours.contains(colour)) {
            colours.add(colour);
        }
        int listW = Math.min(90, w);
        ui.spot(sx, y + 2, 10, 10, () -> host.choose(new int[]{x + w - listW, y, listW, FIELD}, colours,
                id -> id.isEmpty() ? text("not_set").getString() : StructureNames.pretty(id), colour,
                picked -> set(path, TextStyle.with(value, "color", picked.isEmpty() ? null : new JsonPrimitive(picked)))));
        ui.tooltip(sx, y + 2, 10, 10, text("text_colour", colour.isEmpty() ? text("not_set") : Component.literal(StructureNames.pretty(colour))));
        styleToggle(g, sx + 13, y, path, value, "bold", Icons.BOLD);
        styleToggle(g, sx + 24, y, path, value, "italic", Icons.ITALIC);
    }

    /**
     * Draws a bold or italic toggle with three states: on (green, pressed), off (red, struck through)
     * and unset (faint; the game decides, and it italicises names and lore by default). Each click
     * moves to the next.
     */
    private void styleToggle(GuiGraphics g, int x, int y, String path, JsonElement value, String key, ResourceLocation icon) {
        Boolean now = TextStyle.flag(value, key);
        host.drawn(path + "#" + key, new int[]{x, y + 1, 9, 12});
        boolean over = ui.hovered(x, y + 1, 9, 12);
        if (Boolean.TRUE.equals(now)) {
            g.fill(x, y + 2, x + 10, y + 13, 0xFF000000);
            g.fill(x + 1, y + 3, x + 10, y + 13, 0xFF2E5B1D);
        }
        int ink = over ? 0xFFFFFF55 : now == null ? 0xFF707070 : now ? 0xFFFFFFFF : 0xFF6A1010;
        // When on, the icon gets a button's shadow; otherwise the panel's soft shadow.
        Gui.icon(g, icon, x + 1, y + 3, Icons.SMALL, Icons.SMALL, ink, Boolean.TRUE.equals(now));
        if (Boolean.FALSE.equals(now)) {
            for (int i = 0; i < 9; i++) {
                g.fill(x + 1 + i, y + 11 - i, x + 2 + i, y + 12 - i, 0xFF6A1010);
            }
        }
        ui.spot(x, y + 1, 9, 12, () -> set(path, TextStyle.with(value, key, now == null ? new JsonPrimitive(true) : now ? new JsonPrimitive(false) : null)));
        ui.tooltip(x, y + 1, 9, 12, text("text_" + key + (now == null ? "_unset" : now ? "_yes" : "_no")));
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
                Gui.fine(g, font, text("range_from").getString(), fx, y + 4, Gui.labelSoft());
                int from = fx + Gui.fineWidth(font, text("range_from").getString()) + 3;
                number(g, from, y, each, JsonPaths.join(path, "min"), Kind.NUMBER, "0");
                int toX = from + each + 4;
                Gui.fine(g, font, text("range_to").getString(), toX, y + 4, Gui.labelSoft());
                number(g, toX + Gui.fineWidth(font, text("range_to").getString()) + 3, y, each, JsonPaths.join(path, "max"), Kind.NUMBER, "0");
            }
            case "binomial" -> {
                int each = Math.max(14, Math.min(60, (rest - 20) / 2));
                Gui.fine(g, font, "n", fx, y + 4, Gui.labelSoft());
                number(g, fx + 8, y, each, JsonPaths.join(path, "n"), Kind.INT, "1");
                int pX = fx + 8 + each + 4;
                Gui.fine(g, font, "p", pX, y + 4, Gui.labelSoft());
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
            // Not a plain number: use the fallback.
        }
        return fallback;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** The entry's item icon, which opens the item picker. */
    private void itemButton(GuiGraphics g, int x, int y, String path) {
        boolean over = ui.hovered(x, y, FIELD + 2, FIELD);
        g.fill(x, y, x + FIELD + 2, y + FIELD, over ? 0xFFFFFFFF : 0xFF555555);
        g.fill(x + 1, y + 1, x + FIELD + 1, y + FIELD - 1, 0xFF8B8B8B);
        String id = JsonPaths.string(draft(), path, "");
        ItemStack stack = itemExists(id) ? new ItemStack(Regs.value(BuiltInRegistries.ITEM, Ids.parse(id))) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            Gui.push(g);
            Gui.translate(g, x + 2, y + 1);
            Gui.scale(g, 0.75f);
            g.renderItem(stack, 0, 0);
            Gui.pop(g);
        }
        ui.spot(x, y, FIELD + 2, FIELD, () -> host.pickItem(path));
        ui.tooltip(x, y, FIELD + 2, FIELD, text("pick_item"));
    }

    /**
     * While the player holds an item, draws a link that makes the entry give exactly that stack, name
     * and enchantments included. Returns the y below it.
     */
    private int heldLink(GuiGraphics g, int x, int y, int w, String path) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        JsonObject entry = JsonPaths.object(draft(), path);
        if (held.isEmpty() || entry == null) {
            return y;
        }
        Component label = text("use_held", held.getHoverName());
        ui.link(g, label, x + labelW, y, true, () -> {
            try {
                StackEntry.fill(entry, held.copy(), player.level().registryAccess());
                host.changed();
            } catch (RuntimeException e) {
                JesLog.debug("Couldn't take the held item into the loot table", e);
            }
        }, text("use_held_hint"));
        return y + Gui.fineLine(font) + 4;
    }

    static boolean itemExists(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id.trim());
        return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed) && !parsed.getPath().equals("air");
    }

    static Component itemName(String id) {
        if (itemExists(id)) {
            return StructureNames.item(Regs.value(BuiltInRegistries.ITEM, Ids.parse(id.trim())));
        }
        return Component.translatable("screen.justenoughstructures.editor.unknown_item");
    }

    // ------------------------------------------------------------------ changes

    private void set(String path, JsonElement value) {
        JsonPaths.set(draft(), path, value);
        host.changed();
    }

    private void remove(String path) {
        JsonPaths.remove(draft(), path);
        host.changed();
    }

    private void append(String path, JsonElement value) {
        JsonPaths.append(draft(), path, value);
        host.changed();
    }

    /** Writes typed text to the path as the field's kind. A bad number is refused; RAW text that isn't JSON is stored as a string. */
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
                        host.refused(text("not_a_number", trimmed));
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
            case COMPONENT -> {
                // JSON as it is; anything else is plain text.
                JsonElement parsed = null;
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    try {
                        parsed = JsonParser.parseString(trimmed);
                    } catch (JsonParseException e) {
                        // Not JSON after all: plain text.
                    }
                }
                value = trimmed.isEmpty() ? null : parsed != null ? parsed : new JsonPrimitive(text);
            }
            default -> value = new JsonPrimitive(trimmed);
        }
        if (value == null ? before == null : value.equals(before)) {
            return;
        }
        set(path, value);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.justenoughstructures.editor." + key, args);
    }
}
