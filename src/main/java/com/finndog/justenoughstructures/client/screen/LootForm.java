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

        /** The same, for a field with a list of what it can be: they're listed under it while it's typed in. */
        void edit(String id, int[] rect, String text, Consumer<String> commit, LootOptions.Source options);

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
                    pick(g, cx, y(), cw - FIELD - 4, namePath, LootOptions.ITEMS, false);
                    itemButton(g, cx + cw - FIELD - 2, y(), namePath);
                });
            }
            case "tag" -> {
                y = row(g, x, y, w, "tag", (cx, cw) -> pick(g, cx, y(), cw, JsonPaths.join(path, "name"), LootOptions.ITEM_TAG_IDS, false));
                y = row(g, x, y, w, "expand", (cx, cw) -> check(g, cx, y(), JsonPaths.join(path, "expand"), false));
            }
            case "loot_table" -> y = row(g, x, y, w, "table", (cx, cw) -> pick(g, cx, y(), cw, JsonPaths.join(path, "name"), LootOptions.LOOT_TABLES, false));
            case "dynamic" -> y = row(g, x, y, w, "name", (cx, cw) -> choice(g, cx, y(), cw, JsonPaths.join(path, "name"), LootOptions.DYNAMIC, false));
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
        String fn = LootTypes.shortId(type);
        switch (fn) {
            case "set_count" -> {
                y = row(g, x, y, w, "count", (cx, cw) -> provider(g, cx, y(), cw, at(path, "count"), 1));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), at(path, "add"), false));
            }
            case "enchant_with_levels" -> {
                y = row(g, x, y, w, "levels", (cx, cw) -> provider(g, cx, y(), cw, at(path, "levels"), 30));
                y = row(g, x, y, w, "treasure", (cx, cw) -> check(g, cx, y(), at(path, "treasure"), false));
            }
            case "set_damage" -> {
                y = row(g, x, y, w, "damage", (cx, cw) -> provider(g, cx, y(), cw, at(path, "damage"), 1));
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), at(path, "add"), false));
            }
            case "set_enchantments" -> {
                y = idMap(g, x, y, w, "enchantments", at(path, "enchantments"), LootOptions.ENCHANTMENTS, 1);
                y = row(g, x, y, w, "add", (cx, cw) -> check(g, cx, y(), at(path, "add"), false));
            }
            case "enchant_randomly" -> y = idList(g, x, y, w, "enchantments", at(path, "enchantments"), LootOptions.ENCHANTMENTS, text("any_enchantment"));
            case "set_potion" -> y = row(g, x, y, w, "potion", (cx, cw) -> pick(g, cx, y(), cw, at(path, "id"), LootOptions.POTIONS, false));
            case "set_stew_effect" -> y = objectList(g, x, y, w, "effects", at(path, "effects"), () -> object("type", "minecraft:speed", "duration", 5),
                    (ox, oy, ow, op) -> {
                        int ny = row(g, ox, oy, ow, "effect", (cx, cw) -> pick(g, cx, y(), cw, at(op, "type"), LootOptions.EFFECTS, false));
                        return row(g, ox, ny, ow, "duration", (cx, cw) -> provider(g, cx, y(), cw, at(op, "duration"), 5));
                    });
            case "set_name" -> {
                y = row(g, x, y, w, "name", (cx, cw) -> textComponent(g, cx, y(), cw, at(path, "name")));
                y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, true));
            }
            case "set_lore" -> {
                y = textList(g, x, y, w, "lore", at(path, "lore"));
                y = row(g, x, y, w, "entity", (cx, cw) -> choice(g, cx, y(), cw, at(path, "entity"), LootOptions.ENTITY_TARGETS, true));
                y = row(g, x, y, w, "replace", (cx, cw) -> check(g, cx, y(), at(path, "replace"), false));
            }
            case "set_nbt" -> y = row(g, x, y, w, "nbt", (cx, cw) -> text(g, cx, y(), cw, at(path, "tag"), false));
            case "set_instrument" -> y = row(g, x, y, w, "options", (cx, cw) -> pick(g, cx, y(), cw, at(path, "options"), LootOptions.INSTRUMENT_TAGS, false));
            case "exploration_map" -> {
                y = row(g, x, y, w, "destination", (cx, cw) -> pick(g, cx, y(), cw, at(path, "destination"), LootOptions.STRUCTURE_TAGS, true));
                y = row(g, x, y, w, "decoration", (cx, cw) -> choice(g, cx, y(), cw, at(path, "decoration"), LootOptions.MAP_DECORATIONS, true));
                y = row(g, x, y, w, "zoom", (cx, cw) -> number(g, cx, y(), 50, at(path, "zoom"), Kind.INT, "2"));
                y = row(g, x, y, w, "search_radius", (cx, cw) -> number(g, cx, y(), 50, at(path, "search_radius"), Kind.INT, "50"));
                y = row(g, x, y, w, "skip_existing", (cx, cw) -> check(g, cx, y(), at(path, "skip_existing_chunks"), true));
            }
            case "limit_count" -> {
                y = row(g, x, y, w, "at_least", (cx, cw) -> number(g, cx, y(), 50, at(path, "limit.min"), Kind.NUMBER, ""));
                y = row(g, x, y, w, "at_most", (cx, cw) -> number(g, cx, y(), 50, at(path, "limit.max"), Kind.NUMBER, ""));
            }
            case "looting_enchant" -> {
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
            case "copy_nbt" -> {
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
            case "set_attributes" -> y = objectList(g, x, y, w, "modifiers", at(path, "modifiers"),
                    () -> object("attribute", "minecraft:generic.max_health", "name", "Modifier", "amount", 1, "operation", "addition", "slot", "mainhand"),
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
            case "set_banner_pattern" -> {
                y = objectList(g, x, y, w, "patterns", at(path, "patterns"), () -> object("pattern", "minecraft:stripe_bottom", "color", "white"),
                        (ox, oy, ow, op) -> {
                            int ny = row(g, ox, oy, ow, "pattern", (cx, cw) -> pick(g, cx, y(), cw, at(op, "pattern"), LootOptions.BANNER_PATTERNS, false));
                            return row(g, ox, ny, ow, "color", (cx, cw) -> choice(g, cx, y(), cw, at(op, "color"), LootOptions.DYE_COLORS, false));
                        });
                y = row(g, x, y, w, "append", (cx, cw) -> check(g, cx, y(), at(path, "append"), false));
            }
            case "set_contents" -> {
                y = row(g, x, y, w, "block_entity", (cx, cw) -> pick(g, cx, y(), cw, at(path, "type"), LootOptions.BLOCK_ENTITY_TYPES, false));
                y = row(g, x, y, w, "entries", (cx, cw) -> raw(g, cx, y(), cw, at(path, "entries")));
            }
            case "set_loot_table" -> {
                y = row(g, x, y, w, "table", (cx, cw) -> pick(g, cx, y(), cw, at(path, "name"), LootOptions.LOOT_TABLES, false));
                y = row(g, x, y, w, "block_entity", (cx, cw) -> pick(g, cx, y(), cw, at(path, "type"), LootOptions.BLOCK_ENTITY_TYPES, false));
                y = row(g, x, y, w, "seed", (cx, cw) -> number(g, cx, y(), Math.min(cw, 120), at(path, "seed"), Kind.INT, "0"));
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
            case "random_chance" -> y = row(g, x, y, w, "chance", (cx, cw) -> number(g, cx, y(), 60, at(path, "chance"), Kind.NUMBER, "0.5"));
            case "random_chance_with_looting" -> {
                y = row(g, x, y, w, "chance", (cx, cw) -> number(g, cx, y(), 60, at(path, "chance"), Kind.NUMBER, "0.1"));
                y = row(g, x, y, w, "per_looting", (cx, cw) -> number(g, cx, y(), 60, at(path, "looting_multiplier"), Kind.NUMBER, "0"));
            }
            // 1.20.1 reads nothing else for these: "inverse" on killed_by_player is from later versions.
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

    // ------------------------------------------------------------------ predicates

    /** What an entity has to be: its type, with the rest of the predicate as JSON. */
    private int entityPredicate(GuiGraphics g, int x, int y, int w, String path) {
        y = row(g, x, y, w, "entity_type", (cx, cw) -> pick(g, cx, y(), cw, at(path, "type"), LootOptions.ENTITY_TYPES_OR_TAGS, true));
        return row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, List.of("type")));
    }

    /** Where it has to be: a biome, a structure, a dimension, with the rest of the predicate as JSON. */
    private int locationPredicate(GuiGraphics g, int x, int y, int w, String path) {
        y = row(g, x, y, w, "biome", (cx, cw) -> pick(g, cx, y(), cw, at(path, "biome"), LootOptions.BIOMES, true));
        y = row(g, x, y, w, "structure", (cx, cw) -> pick(g, cx, y(), cw, at(path, "structure"), LootOptions.STRUCTURES, true));
        y = row(g, x, y, w, "dimension", (cx, cw) -> pick(g, cx, y(), cw, at(path, "dimension"), LootOptions.DIMENSIONS, true));
        y = row(g, x, y, w, "smokey", (cx, cw) -> maybe(g, cx, y(), cw, at(path, "smokey")));
        return row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, List.of("biome", "structure", "dimension", "smokey")));
    }

    /** What the tool has to be: items, a tag, a potion, enchantments, with the rest of the predicate as JSON. */
    private int itemPredicate(GuiGraphics g, int x, int y, int w, String path) {
        y = idList(g, x, y, w, "items", at(path, "items"), LootOptions.ITEMS, text("any_item"));
        y = row(g, x, y, w, "item_tag", (cx, cw) -> pick(g, cx, y(), cw, at(path, "tag"), LootOptions.ITEM_TAG_IDS, true));
        y = row(g, x, y, w, "potion", (cx, cw) -> pick(g, cx, y(), cw, at(path, "potion"), LootOptions.POTIONS, true));
        y = objectList(g, x, y, w, "enchantments", at(path, "enchantments"), () -> object("enchantment", "minecraft:silk_touch"), (ox, oy, ow, op) -> {
            int ny = row(g, ox, oy, ow, "enchantment", (cx, cw) -> pick(g, cx, y(), cw, at(op, "enchantment"), LootOptions.ENCHANTMENTS, false));
            return row(g, ox, ny, ow, "levels", (cx, cw) -> range(g, cx, y(), cw, at(op, "levels")));
        });
        return row(g, x, y, w, "more", (cx, cw) -> rest(g, cx, y(), cw, path, List.of("items", "tag", "potion", "enchantments")));
    }

    /**
     * The rest of an object as JSON, leaving out the keys the form has fields for, so anything the
     * form doesn't know can still be set. What's typed goes back in beside those keys.
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

    /** A block's state properties to match, each a property of the block picked and one of its values. */
    private int stateMap(GuiGraphics g, int x, int y, int w, String path, String block) {
        JsonObject map = JsonPaths.object(draft(), path);
        List<String> keys = map == null ? List.of() : List.copyOf(map.keySet());
        LootOptions.Source properties = LootOptions.properties(block);
        for (int i = 0; i <= keys.size(); i++) {
            int index = i;
            String label = i == 0 ? text("field.properties").getString() : "";
            y = labelled(g, x, y, w, label, (cx, cw) -> {
                if (index == keys.size()) {
                    Component add = text("add");
                    ui.link(g, add, cx + cw - Gui.fineWidth(font, add.getString()), y() + 3, true, () -> {
                        List<LootOptions.Option> all = properties.list();
                        String first = all.stream().map(LootOptions.Option::id).filter(p -> !keys.contains(p)).findFirst().orElse(null);
                        if (first != null) {
                            List<LootOptions.Option> values = LootOptions.values(block, first).list();
                            set(at(path, first), new JsonPrimitive(values.isEmpty() ? "" : values.get(0).id()));
                        }
                    });
                    return;
                }
                String key = keys.get(index);
                String valuePath = at(path, key);
                int half = (cw - 12) / 2;
                choiceBox(g, cx, y(), half, path + "#key" + index, properties, key, false, picked -> renameKey(path, key, picked));
                if (JsonPaths.get(draft(), valuePath) instanceof JsonObject) {
                    range(g, cx + half + 4, y(), cw - half - 16, valuePath);
                } else {
                    choice(g, cx + half + 4, y(), cw - half - 16, valuePath, LootOptions.values(block, key), false);
                }
                removeX(g, cx + cw - 8, y(), () -> {
                    JsonPaths.remove(draft(), valuePath);
                    host.changed();
                });
            });
        }
        return y;
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

    /** A yes, no, or either: true, false, or left out. */
    private void maybe(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        String current = value == null ? "" : String.valueOf(JsonPaths.bool(draft(), path));
        dropdown(g, x, y, Math.min(w, 80), path, List.of("", "true", "false"),
                option -> text(option.isEmpty() ? "either" : option.equals("true") ? "yes" : "no").getString(), current,
                picked -> set(path, picked.isEmpty() ? null : new JsonPrimitive(Boolean.parseBoolean(picked))));
    }

    /** A range of whole numbers, "from" and "to", either of which can be left empty; one number is kept as it is. */
    private void range(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        if (value != null && value.isJsonPrimitive()) {
            number(g, x, y, Math.min(w, 60), path, Kind.NUMBER, "");
            return;
        }
        String from = text("range_from").getString();
        String to = text("range_to").getString();
        int each = Math.max(14, Math.min(60, (w - Gui.fineWidth(font, from) - Gui.fineWidth(font, to) - 10) / 2));
        Gui.fine(g, font, from, x, y + 4, Gui.LABEL_SOFT);
        int fx = x + Gui.fineWidth(font, from) + 3;
        number(g, fx, y, each, at(path, "min"), Kind.NUMBER, "");
        int tx = fx + each + 4;
        Gui.fine(g, font, to, tx, y + 4, Gui.LABEL_SOFT);
        number(g, tx + Gui.fineWidth(font, to) + 3, y, each, at(path, "max"), Kind.NUMBER, "");
    }

    /** A list of numbers, like a chance for each enchantment level, each with an x, and a link to add one. */
    private int numberList(GuiGraphics g, int x, int y, int w, String key, String path) {
        JsonArray items = JsonPaths.array(draft(), path);
        int count = items == null ? 0 : items.size();
        for (int i = 0; i <= count; i++) {
            int index = i;
            String itemPath = at(path, i);
            String label = i == 0 ? text("field." + key).getString() : "";
            y = labelled(g, x, y, w, label, (cx, cw) -> {
                if (index == count) {
                    Component add = text("add");
                    ui.link(g, add, cx + cw - Gui.fineWidth(font, add.getString()), y() + 3, true, () -> {
                        JsonPaths.append(draft(), path, JsonPaths.number(0));
                        host.changed();
                    });
                    return;
                }
                Gui.fine(g, font, text("level", index).getString(), cx, y() + 3, Gui.LABEL_SOFT);
                number(g, cx + 40, y(), 50, itemPath, Kind.NUMBER, "0");
                removeX(g, cx + 96, y(), () -> {
                    JsonPaths.remove(draft(), itemPath);
                    host.changed();
                });
            });
        }
        return y;
    }

    /** Lines of text, like lore: a box for each, an x to take it out, and a link to add another. */
    private int textList(GuiGraphics g, int x, int y, int w, String key, String path) {
        JsonArray items = JsonPaths.array(draft(), path);
        int count = items == null ? 0 : items.size();
        for (int i = 0; i <= count; i++) {
            int index = i;
            String itemPath = at(path, i);
            String label = i == 0 ? text("field." + key).getString() : "";
            y = labelled(g, x, y, w, label, (cx, cw) -> {
                if (index == count) {
                    Component add = text("add");
                    ui.link(g, add, cx + cw - Gui.fineWidth(font, add.getString()), y() + 3, true, () -> {
                        JsonPaths.append(draft(), path, new JsonPrimitive(""));
                        host.changed();
                    });
                    return;
                }
                textComponent(g, cx, y(), cw - 12, itemPath);
                removeX(g, cx + cw - 8, y(), () -> {
                    JsonPaths.remove(draft(), itemPath);
                    host.changed();
                });
            });
        }
        return y;
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
    enum Kind { TEXT, OPTIONAL_TEXT, INT, NUMBER, RAW, ITEM, COMPONENT }

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

    /** Yes or no; {@code fallback} is what the game takes when it's left out, as some default to yes. */
    private void check(GuiGraphics g, int x, int y, String path, boolean fallback) {
        JsonElement value = JsonPaths.get(draft(), path);
        boolean on = value == null ? fallback : JsonPaths.bool(draft(), path);
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

    /** A field just added to a list, to start typing in as soon as it's drawn. */
    private String pendingEdit;

    /**
     * A box for an id with a list of what it can be, like an enchantment or a potion, listed under it
     * while it's typed in. It shows the picked one's name, and the id while it's typed in; an id the
     * game doesn't know shows in amber.
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
     * A list of ids, like the enchantments one is picked from: a box for each with an x to take it
     * out, and a link to add another. {@code empty} says what no ids at all means.
     */
    private int idList(GuiGraphics g, int x, int y, int w, String key, String path, LootOptions.Source options, Component empty) {
        JsonArray items = JsonPaths.array(draft(), path);
        int count = items == null ? 0 : items.size();
        int controlW = w - labelW - 4;
        for (int i = 0; i <= count; i++) {
            int index = i;
            String itemPath = JsonPaths.join(path, i);
            String label = i == 0 ? text("field." + key).getString() : "";
            y = labelled(g, x, y, w, label, (cx, cw) -> {
                if (index == count) {
                    if (count == 0 && empty != null) {
                        Gui.fine(g, font, empty.getString(), cx, y() + 3, Gui.LABEL_SOFT);
                    }
                    Component add = text("add");
                    int addW = Gui.fineWidth(font, add.getString());
                    ui.link(g, add, cx + cw - addW, y() + 3, true, () -> {
                        JsonPaths.append(draft(), path, new JsonPrimitive(""));
                        pendingEdit = itemPath;
                        host.changed();
                    });
                    return;
                }
                // Emptied, it's taken out of the list rather than left as an id that's nothing.
                pickBox(g, cx, y(), cw - 12, itemPath, JsonPaths.string(draft(), itemPath, ""), options, text -> {
                    if (text.isBlank()) {
                        JsonPaths.remove(draft(), itemPath);
                        host.changed();
                    } else {
                        commit(itemPath, Kind.TEXT, text);
                    }
                });
                removeX(g, cx + cw - 8, y(), () -> {
                    JsonPaths.remove(draft(), itemPath);
                    host.changed();
                });
            });
        }
        return y;
    }

    /**
     * A map from ids to amounts, like enchantments to their levels: each id in a box with its list,
     * its amount beside it, an x to take it out, and a link to add another.
     */
    private int idMap(GuiGraphics g, int x, int y, int w, String key, String path, LootOptions.Source options, double fallback) {
        JsonObject map = JsonPaths.object(draft(), path);
        List<String> keys = map == null ? List.of() : List.copyOf(map.keySet());
        for (int i = 0; i <= keys.size(); i++) {
            int index = i;
            String label = i == 0 ? text("field." + key).getString() : "";
            y = labelled(g, x, y, w, label, (cx, cw) -> {
                if (index == keys.size()) {
                    Component add = text("add");
                    ui.link(g, add, cx + cw - Gui.fineWidth(font, add.getString()), y() + 3, true, () -> {
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
                    });
                    return;
                }
                String id = keys.get(index);
                int pickW = Math.max(40, (cw - 12) * 55 / 100);
                pickBox(g, cx, y(), pickW, path + "#key" + index, id, options, text -> renameKey(path, id, text.trim()));
                if (!id.isEmpty()) {
                    provider(g, cx + pickW + 4, y(), cw - pickW - 16, JsonPaths.join(path, id), fallback);
                }
                removeX(g, cx + cw - 8, y(), () -> {
                    JsonPaths.remove(draft(), JsonPaths.join(path, id));
                    host.changed();
                });
            });
        }
        return y;
    }

    /** Gives a key of an object another name, keeping its place and value. */
    private void renameKey(String path, String from, String to) {
        JsonObject map = JsonPaths.object(draft(), path);
        if (map == null || to.equals(from) && !to.isEmpty()) {
            return;
        }
        if (to.isEmpty()) {
            // A new one left without an id goes again; one that had an id keeps it.
            if (from.isEmpty()) {
                JsonPaths.remove(draft(), JsonPaths.join(path, from));
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

    /** What draws the fields of one object in a list. Returns the y below them. */
    private interface ObjectFields {
        int draw(int x, int y, int w, String path);
    }

    /**
     * A list of objects, like a stew's effects: a small card for each, its fields drawn by
     * {@code fields}, with an x to take it out, and a link to add another made by {@code fresh}.
     */
    private int objectList(GuiGraphics g, int x, int y, int w, String key, String path, java.util.function.Supplier<JsonObject> fresh, ObjectFields fields) {
        JsonArray items = JsonPaths.array(draft(), path);
        int count = items == null ? 0 : items.size();
        y += 2;
        Gui.fineClipped(g, font, text("field." + key).getString(), x + 4, y + 1, w - 60, Gui.LABEL_SOFT);
        Component add = text("add");
        ui.link(g, add, x + w - 4 - Gui.fineWidth(font, add.getString()), y + 1, true, () -> {
            JsonPaths.append(draft(), path, fresh.get());
            host.changed();
        });
        y += Gui.fineLine(font) + 4;
        for (int i = 0; i < count; i++) {
            String itemPath = JsonPaths.join(path, i);
            int top = y;
            int cx = x + INDENT;
            int cw = w - INDENT * 2;
            g.fill(cx, y, cx + cw, y + 12, 0xFFB0B0B0);
            Gui.fine(g, font, String.valueOf(i + 1), cx + 4, y + 2, Gui.LABEL_SOFT);
            removeX(g, cx + cw - 9, y, () -> {
                JsonPaths.remove(draft(), itemPath);
                host.changed();
            });
            y = fields.draw(cx, y + 14, cw, itemPath);
            y = end(g, cx, top, y, cw) + 3;
        }
        return y;
    }

    /** A small x that takes something out. */
    private void removeX(GuiGraphics g, int x, int y, Runnable action) {
        boolean over = ui.hovered(x - 2, y + 1, 10, 12);
        g.drawString(font, "x", x, y + 3, over ? 0xFFFFFF55 : 0xFF6A1010, false);
        ui.spot(x - 2, y + 1, 10, 12, action);
        ui.tooltip(x - 2, y + 1, 10, 12, text("remove"));
    }

    /**
     * Text the game shows, like a name or a line of lore. Plain text is kept as it's typed; JSON text
     * components, like {"text": "...", "color": "gold"}, are kept as they are.
     */
    private void textComponent(GuiGraphics g, int x, int y, int w, String path) {
        JsonElement value = JsonPaths.get(draft(), path);
        String shown = value == null ? "" : value.isJsonPrimitive() ? value.getAsString()
                : value instanceof JsonObject o && o.size() == 1 && o.has("text") && o.get("text").isJsonPrimitive() ? o.get("text").getAsString() : value.toString();
        field(g, x, y, w, path, Kind.COMPONENT, shown);
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
        JsonPaths.set(draft(), path, value);
        host.changed();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.justenoughstructures.editor." + key, args);
    }
}
