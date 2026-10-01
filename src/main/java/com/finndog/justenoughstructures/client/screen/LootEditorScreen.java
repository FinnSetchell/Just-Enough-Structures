package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.JsonMerge;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.TableDraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Edits a loot table in game. On the left are its pools and entries, in the middle a form for the
 * one picked, and on the right what the edit would give, rolled on the server as you go. Anything
 * the form doesn't cover can be edited as JSON. Saving writes an override, which applies from the
 * next /reload; the server refuses anything the game couldn't load.
 */
public final class LootEditorScreen extends Screen {
    private static final int PAD = 6;
    private static final int ROW = 18;
    private static final int GOOD = 0xFF2E7D1F;
    private static final int BAD = 0xFFB02020;
    private static final int WARN = 0xFF9A6200;
    private static final long PREVIEW_DELAY = 400;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Where a new table starts: a chest table with nothing in it yet. */
    private static final String BLANK = "{\"type\": \"minecraft:chest\", \"pools\": []}";

    private final Screen parent;
    private ResourceLocation tableId;
    /** The id it was opened with, which tableName is the name of. */
    private final ResourceLocation openedAs;
    private final String tableName;
    /** Told the id a table was saved under, for the table picker to pick a new one. Null if no one asked. */
    private final Consumer<ResourceLocation> onSaved;
    // A new table's id, which can still be changed until it's saved, and anything wrong with it.
    private EditBox idBox;
    private Component idProblem;
    private int idCheck;
    private boolean idChecking;

    private LootOverrides.View view;
    private Component loadProblem;
    private JsonObject draft;
    private boolean raw;
    private String rawText;
    private boolean dirty;
    private Component message;
    private int messageColour;

    private int selectedPool = -1;
    private int selectedEntry = -1;
    private double treeScroll;
    private double previewScroll;
    private List<Item> suggestions = List.of();

    private LootOdds preview;
    private Component previewProblem;
    private boolean previewing;
    private long previewDue;
    private int previewRequest;

    private MultiLineEditBox rawBox;
    private EditBox itemBox;
    private int contentTop, contentBottom, treeX, treeW, formX, formW, previewX, previewW;
    private int statusRight;
    private Component statusTooltip;
    private int suggestionsY;

    public LootEditorScreen(Screen parent, ResourceLocation tableId, String tableName) {
        this(parent, tableId, tableName, null);
    }

    public LootEditorScreen(Screen parent, ResourceLocation tableId, String tableName, Consumer<ResourceLocation> onSaved) {
        super(Component.translatable("screen.justenoughstructures.editor.title", tableName));
        this.parent = parent;
        this.tableId = tableId;
        this.openedAs = tableId;
        this.tableName = tableName;
        this.onSaved = onSaved;
        load();
    }

    /** A table no mod or datapack has and that hasn't been saved here yet. */
    private boolean isNew() {
        return view != null && view.original() == null && view.current() == null;
    }

    private void load() {
        ClientRequests.table(tableId).thenAccept(reply -> {
            if (reply.problem() != null) {
                loadProblem = reply.problem();
                return;
            }
            view = reply.view();
            if (draft == null && rawText == null) {
                startFrom(view.current() != null ? view.current() : BLANK, view.status() == LootOverrides.Status.BROKEN);
                dirty = false;
            }
            if (minecraft != null && minecraft.screen == this) {
                rebuildWidgets();
            }
        });
    }

    /** Puts a table's JSON in the editor, as the form if it reads as a table, otherwise as JSON to fix. */
    private void startFrom(String json, boolean asJson) {
        JsonObject parsed = parseObject(json);
        if (parsed == null || asJson) {
            raw = true;
            rawText = json == null ? "" : json;
            draft = parsed;
        } else {
            draft = parsed;
            rawText = null;
        }
        selectedPool = -1;
        selectedEntry = -1;
        dirty = true;
        schedulePreview();
    }

    private static JsonObject parseObject(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /** The edit as it stands, as JSON text. */
    private String json() {
        if (raw) {
            return rawBox != null ? rawBox.getValue() : rawText == null ? "" : rawText;
        }
        return draft == null ? "" : PRETTY.toJson(draft);
    }

    private void changed() {
        dirty = true;
        if (message != null && messageColour == BAD) {
            message = null;
        }
        schedulePreview();
    }

    private void schedulePreview() {
        previewDue = Util.getMillis() + PREVIEW_DELAY;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int left = PAD + 6;
        int right = width - PAD - 6;
        int content = right - left;
        contentTop = PAD + 38;
        contentBottom = height - PAD - 30;
        // Narrow windows lose the preview rather than squash the form.
        previewW = content >= 420 ? Math.max(110, content * 30 / 100) : 0;
        treeW = Math.max(110, (content - previewW) * 50 / 100);
        treeX = left;
        formX = treeX + treeW + 6;
        formW = content - treeW - 6 - (previewW > 0 ? previewW + 6 : 0);
        previewX = right - previewW;

        addToolbar(left, right);
        if (view == null) {
            return;
        }
        addStatusActions();
        idBox = null;
        if (isNew()) {
            int labelWidth = font.width(Component.translatable("screen.justenoughstructures.editor.id")) + 4;
            int boxX = PAD + 8 + labelWidth;
            idBox = addRenderableWidget(new EditBox(font, boxX, PAD + 18, Math.max(60, Math.min(240, statusRight - 8 - boxX)), 14,
                    Component.translatable("screen.justenoughstructures.editor.id")));
            idBox.setMaxLength(256);
            idBox.setValue(tableId.toString());
            idBox.setResponder(this::idChanged);
        }
        if (raw) {
            rawBox = addRenderableWidget(new JsonEditBox(font, treeX, contentTop, formX + formW - treeX, contentBottom - contentTop,
                    Component.empty(), Component.translatable("screen.justenoughstructures.editor.json")));
            rawBox.setCharacterLimit(Integer.MAX_VALUE);
            rawBox.setValue(rawText == null ? "" : rawText);
            rawBox.setValueListener(text -> {
                if (!text.equals(rawText)) {
                    rawText = text;
                    changed();
                }
            });
            return;
        }
        rawBox = null;
        int buttonY = contentBottom - 20;
        int half = (treeW - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.add_pool"), b -> {
            TableDraft.addPool(draft);
            selectedPool = TableDraft.poolList(draft).size() - 1;
            selectedEntry = -1;
            changed();
            rebuildWidgets();
        }).bounds(treeX, buttonY, half, 20).build());
        Button addItem = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.add_item"), b -> {
            JsonObject pool = pool();
            if (pool != null) {
                TableDraft.addItem(pool, "minecraft:stone");
                selectedEntry = TableDraft.entryList(pool).size() - 1;
                changed();
                rebuildWidgets();
            }
        }).bounds(treeX + half + 4, buttonY, treeW - half - 4, 20).build());
        addItem.active = pool() != null;
        addForm();
    }

    private void addToolbar(int left, int right) {
        int y = height - PAD - 26;
        Button mode = addRenderableWidget(Button.builder(Component.translatable(raw ? "screen.justenoughstructures.editor.form"
                : "screen.justenoughstructures.editor.json"), b -> toggleMode()).bounds(left, y, buttonWidth(raw ? "form" : "json"), 20).build());
        mode.active = view != null;

        int x = right;
        x -= buttonWidth("back");
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.back"), b -> onClose())
                .bounds(x, y, buttonWidth("back"), 20).build());
        boolean canReload = minecraft.player != null && minecraft.player.hasPermissions(2);
        if (canReload) {
            x -= buttonWidth("save_reload") + 4;
            Button saveReload = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.save_reload"),
                    b -> save(true)).bounds(x, y, buttonWidth("save_reload"), 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.editor.save_reload_hint"))).build());
            saveReload.active = view != null;
        }
        x -= buttonWidth("save") + 4;
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.save"), b -> save(false))
                .bounds(x, y, buttonWidth("save"), 20)
                .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.editor.save_hint"))).build());
        save.active = view != null;
        if (view != null && view.original() != null) {
            x -= buttonWidth("revert") + 4;
            addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.revert"), b -> {
                startFrom(view.original(), false);
                note(Component.translatable("screen.justenoughstructures.editor.reverted"), WARN);
                rebuildWidgets();
            }).bounds(x, y, buttonWidth("revert"), 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.editor.revert_hint"))).build());
        }
        if (view != null && view.status() != LootOverrides.Status.NONE) {
            x -= buttonWidth("remove") + 4;
            addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.remove"), b -> tableAction(JesNetwork.ACTION_REMOVE))
                    .bounds(x, y, buttonWidth("remove"), 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.editor.remove_hint"))).build());
        }
    }

    private int buttonWidth(String key) {
        return font.width(Component.translatable("screen.justenoughstructures.editor." + key)) + 12;
    }

    /** What can be done about the override's state, at the top right, next to what it says. */
    private void addStatusActions() {
        List<Button> actions = new ArrayList<>();
        if (view.status() == LootOverrides.Status.ORIGINAL_CHANGED) {
            actions.add(Button.builder(Component.translatable("screen.justenoughstructures.editor.see_changes"),
                    b -> minecraft.setScreen(new DiffScreen(this, tableName, view.original(), json()))).build());
            actions.add(Button.builder(Component.translatable("screen.justenoughstructures.editor.merge"), b -> merge())
                    .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.editor.merge_hint"))).build());
        }
        if (view.status() == LootOverrides.Status.ORIGINAL_CHANGED || view.status() == LootOverrides.Status.ORIGINAL_MISSING) {
            actions.add(Button.builder(Component.translatable("screen.justenoughstructures.editor.keep"), b -> tableAction(JesNetwork.ACTION_KEEP))
                    .tooltip(Tooltip.create(Component.translatable("screen.justenoughstructures.editor.keep_hint"))).build());
        }
        int x = width - PAD - 6;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Button button = actions.get(i);
            int w = font.width(button.getMessage()) + 12;
            x -= w;
            button.setX(x);
            button.setY(PAD + 7);
            button.setWidth(w);
            addRenderableWidget(button);
            x -= 4;
        }
        statusRight = x;
    }

    // Where the form's parts sit below its title, shared by the widgets and what's drawn around them.
    private static final int LABEL_1 = 19;
    private static final int FIELD_1 = 28;
    private static final int LABEL_2 = 56;
    private static final int FIELD_2 = 65;
    private static final int LABEL_3 = 87;
    private static final int FIELD_3 = 96;
    private static final int INFO = 118;

    /** The form for what's picked on the left. */
    private void addForm() {
        itemBox = null;
        suggestions = List.of();
        JsonObject pool = pool();
        int x = formX + 2;
        int y = contentTop;
        int w = formW - 4;
        if (pool == null) {
            return;
        }
        JsonObject entry = entry();
        if (entry == null) {
            TableDraft.Range rolls = TableDraft.rolls(pool);
            if (rolls != null) {
                rangeFields(x, y + FIELD_1, rolls, range -> TableDraft.setRolls(pool, range));
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.remove_pool"), b -> {
                TableDraft.removePool(draft, selectedPool);
                selectedPool = -1;
                changed();
                rebuildWidgets();
            }).bounds(x, contentBottom - 20, Math.min(w, buttonWidth("remove_pool")), 20).build());
            return;
        }
        if (TableDraft.isItem(entry)) {
            itemBox = addRenderableWidget(new EditBox(font, x + 1, y + FIELD_1, w - 2, 16, Component.translatable("screen.justenoughstructures.editor.field.item")));
            itemBox.setMaxLength(256);
            itemBox.setValue(TableDraft.name(entry));
            itemBox.setResponder(text -> {
                suggestions = matchingItems(text);
                ResourceLocation id = ResourceLocation.tryParse(text.trim());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id) && !TableDraft.name(entry).equals(id.toString())) {
                    TableDraft.setName(entry, id.toString());
                    changed();
                } else if (id != null && BuiltInRegistries.ITEM.containsKey(id) && message != null && messageColour == BAD) {
                    message = null;
                }
            });
            // Suggestions drop down over the fields below while typing, rather than pushing them down.
            suggestionsY = y + FIELD_1 + 17;
            EditBox weight = addRenderableWidget(new EditBox(font, x + 1, y + FIELD_2, 50, 16,
                    Component.translatable("screen.justenoughstructures.editor.field.weight")));
            weight.setFilter(s -> s.matches("\\d{0,5}"));
            weight.setValue(String.valueOf(TableDraft.weight(entry)));
            weight.setResponder(text -> {
                if (!text.isEmpty() && Integer.parseInt(text) != TableDraft.weight(entry)) {
                    TableDraft.setWeight(entry, Integer.parseInt(text));
                    changed();
                }
            });
            TableDraft.Range count = TableDraft.count(entry);
            if (count != null) {
                rangeFields(x, y + FIELD_3, count, range -> TableDraft.setCount(entry, range));
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.remove_entry"), b -> {
            TableDraft.removeEntry(pool, selectedEntry);
            selectedEntry = -1;
            changed();
            rebuildWidgets();
        }).bounds(x, contentBottom - 20, Math.min(w, buttonWidth("remove_entry")), 20).build());
    }

    /** Two number boxes, "from" and "to", for rolls or a count. */
    private void rangeFields(int x, int y, TableDraft.Range start, Consumer<TableDraft.Range> apply) {
        EditBox min = addRenderableWidget(new EditBox(font, x + 1, y, 40, 16, Component.translatable("screen.justenoughstructures.editor.from")));
        EditBox max = addRenderableWidget(new EditBox(font, x + 60, y, 40, 16, Component.translatable("screen.justenoughstructures.editor.to")));
        for (EditBox box : List.of(min, max)) {
            box.setFilter(s -> s.matches("\\d{0,4}"));
        }
        min.setValue(String.valueOf(start.min()));
        max.setValue(String.valueOf(start.max()));
        Runnable update = () -> {
            if (!min.getValue().isEmpty() && !max.getValue().isEmpty()) {
                apply.accept(new TableDraft.Range(Integer.parseInt(min.getValue()), Integer.parseInt(max.getValue())));
                changed();
            }
        };
        min.setResponder(text -> update.run());
        max.setResponder(text -> update.run());
    }

    private List<Item> matchingItems(String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }
        List<Item> matches = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (id.equals(query)) {
                return List.of();
            }
            if (id.contains(query) || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(item);
            }
        }
        matches.sort(Comparator.comparing((Item item) -> !BuiltInRegistries.ITEM.getKey(item).getPath().startsWith(query))
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return matches.subList(0, Math.min(5, matches.size()));
    }

    private JsonObject pool() {
        if (draft == null || selectedPool < 0) {
            return null;
        }
        List<JsonObject> pools = TableDraft.poolList(draft);
        return selectedPool < pools.size() ? pools.get(selectedPool) : null;
    }

    private JsonObject entry() {
        JsonObject pool = pool();
        if (pool == null || selectedEntry < 0) {
            return null;
        }
        List<JsonObject> entries = TableDraft.entryList(pool);
        return selectedEntry < entries.size() ? entries.get(selectedEntry) : null;
    }

    // ------------------------------------------------------------------ actions

    private void toggleMode() {
        if (raw) {
            JsonObject parsed = parseObject(json());
            if (parsed == null) {
                note(Component.translatable("screen.justenoughstructures.editor.fix_json"), BAD);
                return;
            }
            draft = parsed;
            raw = false;
            rawText = null;
        } else {
            rawText = json();
            raw = true;
        }
        rebuildWidgets();
    }

    private void merge() {
        JsonElement base = parseObject(view.base());
        JsonElement theirs = parseObject(view.original());
        JsonElement mine = parseObject(json());
        if (base == null || theirs == null || mine == null) {
            note(Component.translatable("screen.justenoughstructures.editor.no_base"), WARN);
            return;
        }
        JsonMerge.Result result = JsonMerge.merge(base, mine, theirs);
        draft = result.merged().getAsJsonObject();
        raw = false;
        rawText = null;
        changed();
        note(result.conflicts() == 0 ? Component.translatable("screen.justenoughstructures.editor.merged")
                : Component.translatable("screen.justenoughstructures.editor.merged_conflicts", result.conflicts()), result.conflicts() == 0 ? GOOD : WARN);
        rebuildWidgets();
    }

    /** What has to be fixed before saving, that the table itself doesn't show: a bad item or id. Null if nothing. */
    private Component unsaveable() {
        if (itemBox != null) {
            String text = itemBox.getValue().trim();
            ResourceLocation item = ResourceLocation.tryParse(text);
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) {
                return Component.translatable("screen.justenoughstructures.editor.fix_item", text);
            }
        }
        if (isNew()) {
            if (idProblem != null) {
                return idProblem;
            }
            if (idChecking) {
                return Component.translatable("screen.justenoughstructures.editor.id_checking");
            }
        }
        return null;
    }

    /** A new table's id was typed: it's used once it reads as an id and no table has it already. */
    private void idChanged(String text) {
        int check = ++idCheck;
        ResourceLocation id = text.contains(":") ? ResourceLocation.tryParse(text.trim()) : null;
        if (id == null) {
            idProblem = Component.translatable("screen.justenoughstructures.editor.bad_id");
            idChecking = false;
            return;
        }
        idProblem = null;
        idChecking = true;
        ClientRequests.table(id).thenAccept(reply -> {
            if (check != idCheck) {
                return;
            }
            idChecking = false;
            LootOverrides.View other = reply.view();
            if (other != null && (other.original() != null || other.current() != null)) {
                idProblem = Component.translatable("screen.justenoughstructures.editor.id_taken", id.toString());
            } else {
                tableId = id;
            }
        });
    }

    private void save(boolean thenReload) {
        Component problem = unsaveable();
        if (problem != null) {
            note(problem, BAD);
            return;
        }
        String json = json();
        ResourceLocation savedAs = tableId;
        ClientRequests.saveTable(savedAs, json).thenAccept(reply -> {
            boolean saved = reply.message() != null && reply.message().getContents() instanceof TranslatableContents t
                    && t.getKey().endsWith("override.saved");
            note(reply.message(), saved ? GOOD : BAD);
            if (saved) {
                dirty = false;
                if (onSaved != null) {
                    onSaved.accept(savedAs);
                }
                if (thenReload && minecraft.player != null) {
                    minecraft.player.connection.sendCommand("reload");
                    note(Component.translatable("screen.justenoughstructures.editor.saved_reloaded"), GOOD);
                }
                refresh();
            }
        });
    }

    private void tableAction(int action) {
        ClientRequests.tableAction(tableId, action).thenAccept(reply -> {
            note(reply.message(), GOOD);
            if (action == JesNetwork.ACTION_REMOVE) {
                draft = null;
                rawText = null;
                raw = false;
            }
            refresh();
        });
    }

    /** Asks the server again how things stand, after a save or an action. */
    private void refresh() {
        boolean keepDraft = draft != null || rawText != null;
        ClientRequests.table(tableId).thenAccept(reply -> {
            if (reply.problem() != null) {
                loadProblem = reply.problem();
                return;
            }
            view = reply.view();
            if (!keepDraft) {
                startFrom(view.current(), false);
                dirty = false;
            }
            if (minecraft.screen == this) {
                rebuildWidgets();
            }
        });
    }

    private void note(Component text, int colour) {
        message = text;
        messageColour = colour;
    }

    @Override
    public void tick() {
        super.tick();
        if (rawBox != null) {
            rawBox.tick();
        }
        if (itemBox != null) {
            itemBox.tick();
        }
        if (idBox != null) {
            idBox.tick();
        }
        if (previewDue > 0 && Util.getMillis() >= previewDue && view != null) {
            previewDue = 0;
            previewing = true;
            int request = ++previewRequest;
            ClientRequests.draftOdds(tableId, json()).thenAccept(reply -> {
                if (request != previewRequest) {
                    return;
                }
                previewing = false;
                preview = reply.odds();
                previewProblem = reply.message();
            });
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && hasControlDown() && view != null) {
            save(false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        if (!dirty) {
            minecraft.setScreen(parent);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(leave -> minecraft.setScreen(leave ? parent : this),
                Component.translatable("screen.justenoughstructures.editor.unsaved.title"),
                Component.translatable("screen.justenoughstructures.editor.unsaved.message")));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (itemBox != null && itemBox.isFocused() && !suggestions.isEmpty()) {
            int index = (int) ((mouseY - suggestionsY) / ROW);
            if (mouseX >= formX && mouseX < formX + formW && mouseY >= suggestionsY && index >= 0 && index < suggestions.size()) {
                itemBox.setValue(BuiltInRegistries.ITEM.getKey(suggestions.get(index)).toString());
                suggestions = List.of();
                return true;
            }
        }
        if (!raw && draft != null && mouseX >= treeX && mouseX < treeX + treeW && mouseY >= contentTop && mouseY < contentBottom - 24) {
            int row = (int) ((mouseY - contentTop - 2 + treeScroll) / ROW);
            int[] picked = rowAt(row);
            if (picked != null) {
                selectedPool = picked[0];
                selectedEntry = picked[1];
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!raw && mouseX >= treeX && mouseX < treeX + treeW) {
            treeScroll = Math.max(0, Math.min(treeScroll - delta * ROW, Math.max(0, treeRows() * ROW - (contentBottom - 24 - contentTop - 4))));
            return true;
        }
        if (previewW > 0 && mouseX >= previewX) {
            previewScroll = Math.max(0, previewScroll - delta * ROW);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** The pool and entry on a row of the tree, the entry -1 for the pool itself. Null past the end. */
    private int[] rowAt(int row) {
        int i = 0;
        List<JsonObject> pools = TableDraft.poolList(draft);
        for (int p = 0; p < pools.size(); p++) {
            if (i++ == row) {
                return new int[]{p, -1};
            }
            int entries = TableDraft.entryList(pools.get(p)).size();
            if (row < i + entries) {
                return new int[]{p, row - i};
            }
            i += entries;
        }
        return null;
    }

    private int treeRows() {
        int rows = 0;
        for (JsonObject pool : TableDraft.poolList(draft)) {
            rows += 1 + TableDraft.entryList(pool).size();
        }
        return rows;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Gui.panel(g, PAD, PAD, width - PAD * 2, height - PAD * 2);
        int headerRight = (view == null ? width - PAD - 6 : statusRight) - 4;
        int textX = PAD + 8;
        g.drawString(font, Gui.clip(font, headerTitle().getString(), headerRight - textX), textX, PAD + 8, Gui.LABEL, false);
        Component status = message != null ? message : statusText();
        int statusColour = message != null ? messageColour : statusColour();
        int statusX = textX;
        if (idBox != null) {
            g.drawString(font, Component.translatable("screen.justenoughstructures.editor.id"), textX, PAD + 21, Gui.LABEL_SOFT, false);
            statusX = idBox.getX() + idBox.getWidth() + 6;
            if (idProblem != null) {
                status = idProblem;
                statusColour = BAD;
            }
        }
        statusLine(g, status, statusX, headerRight, statusColour, mouseX, mouseY);

        if (loadProblem != null) {
            Gui.wrapped(g, font, loadProblem, textX, contentTop + 4, width - PAD * 2 - 16, BAD);
        } else if (view == null) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.loading"), textX, contentTop + 4, width - PAD * 2 - 16, Gui.LABEL_SOFT);
        } else if (!raw && draft != null) {
            renderTree(g, mouseX, mouseY);
            renderForm(g, mouseX, mouseY);
        }
        if (previewW > 0 && view != null) {
            renderPreview(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (itemBox != null && itemBox.isFocused() && !suggestions.isEmpty()) {
            renderSuggestions(g, mouseX, mouseY);
        }
        if (statusTooltip != null) {
            g.renderTooltip(font, font.split(statusTooltip, 260), mouseX, mouseY);
            statusTooltip = null;
        }
    }

    private Component headerTitle() {
        if (isNew()) {
            return Component.translatable("screen.justenoughstructures.editor.new_title");
        }
        return Component.translatable("screen.justenoughstructures.editor.title", tableId.equals(openedAs) ? tableName
                : StructureNames.lootTable(tableId.toString()));
    }

    /**
     * The line under the title. When it's too long for one line it goes over two in smaller text,
     * and anything still left over is in a tooltip, so a message is never just cut off.
     */
    private void statusLine(GuiGraphics g, Component status, int x, int right, int colour, int mouseX, int mouseY) {
        int room = right - x;
        if (room < 20) {
            return;
        }
        if (font.width(status) <= room) {
            g.drawString(font, status, x, PAD + 21, colour, false);
            return;
        }
        float scale = Gui.smallScale();
        List<FormattedCharSequence> lines = font.split(status, (int) (room / scale));
        int line = (int) Math.ceil(font.lineHeight * scale);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            Gui.scaled(g, font, lines.get(i), x, PAD + 18 + i * (line + 1), colour, scale);
        }
        if (lines.size() > 2 && mouseX >= x && mouseX < right && mouseY >= PAD + 17 && mouseY < PAD + 19 + 2 * (line + 1)) {
            statusTooltip = status;
        }
    }

    private Component statusText() {
        if (view == null) {
            return Component.empty();
        }
        if (isNew()) {
            return Component.translatable("screen.justenoughstructures.editor.status.new");
        }
        return Component.translatable("screen.justenoughstructures.editor.status." + view.status().name().toLowerCase(Locale.ROOT));
    }

    private int statusColour() {
        if (view == null) {
            return Gui.LABEL_SOFT;
        }
        return switch (view.status()) {
            case NONE -> Gui.LABEL_SOFT;
            case ACTIVE -> GOOD;
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> WARN;
            case BROKEN -> BAD;
        };
    }

    private void renderTree(GuiGraphics g, int mouseX, int mouseY) {
        int bottom = contentBottom - 24;
        Gui.inset(g, treeX, contentTop, treeW, bottom - contentTop, Gui.PANEL);
        g.enableScissor(treeX + 1, contentTop + 1, treeX + treeW - 1, bottom - 1);
        int y = contentTop + 2 - (int) treeScroll;
        List<JsonObject> pools = TableDraft.poolList(draft);
        for (int p = 0; p < pools.size(); p++) {
            JsonObject pool = pools.get(p);
            boolean selected = p == selectedPool && selectedEntry < 0;
            Gui.card(g, treeX + 2, y, treeW - 4, ROW - 1);
            if (selected || over(mouseX, mouseY, treeX + 2, y, treeW - 4, ROW - 1, bottom)) {
                g.fill(treeX + 3, y + 1, treeX + treeW - 3, y + ROW - 2, selected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
            }
            TableDraft.Range rolls = TableDraft.rolls(pool);
            String rollsText = rolls == null ? Component.translatable("screen.justenoughstructures.editor.rolls_formula").getString()
                    : Component.translatable("screen.justenoughstructures.editor.rolls", range(rolls)).getString();
            int rollsWidth = Gui.smallWidth(font, rollsText);
            Gui.fitted(g, font, Component.translatable("screen.justenoughstructures.editor.pool", p + 1).getString(), treeX + 6, y + 5,
                    treeW - 16 - rollsWidth, Gui.LABEL);
            Gui.small(g, font, rollsText, treeX + treeW - 5 - rollsWidth, y + 6, Gui.LABEL_SOFT);
            y += ROW;
            List<JsonObject> entries = TableDraft.entryList(pool);
            for (int e = 0; e < entries.size(); e++) {
                JsonObject entry = entries.get(e);
                boolean picked = p == selectedPool && e == selectedEntry;
                int rowX = treeX + 10;
                int rowW = treeW - 12;
                if (picked || over(mouseX, mouseY, rowX, y, rowW, ROW - 1, bottom)) {
                    g.fill(rowX, y, rowX + rowW, y + ROW - 1, picked ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
                }
                g.renderItem(icon(entry), rowX + 1, y + 1);
                String weight = Component.translatable("screen.justenoughstructures.editor.weight", TableDraft.weight(entry)).getString();
                int weightWidth = Gui.smallWidth(font, weight);
                Gui.fitted(g, font, label(entry), rowX + 20, y + 5, rowW - 26 - weightWidth, Gui.LABEL);
                Gui.small(g, font, weight, rowX + rowW - 3 - weightWidth, y + 6, Gui.LABEL_SOFT);
                y += ROW;
            }
        }
        g.disableScissor();
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int w, int h, int clipBottom) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY < clipBottom;
    }

    private static ItemStack icon(JsonObject entry) {
        if (TableDraft.isItem(entry)) {
            ResourceLocation id = ResourceLocation.tryParse(TableDraft.name(entry));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(TableDraft.type(entry).equals("empty") ? Items.GLASS_BOTTLE : Items.CHEST);
    }

    private String label(JsonObject entry) {
        if (TableDraft.isItem(entry)) {
            ResourceLocation id = ResourceLocation.tryParse(TableDraft.name(entry));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.get(id).getDescription().getString();
            }
            return TableDraft.name(entry);
        }
        String name = TableDraft.name(entry);
        return entryType(entry).getString() + (name.isEmpty() ? "" : " " + name);
    }

    /** The entry type's name, or its id for a mod's own type. */
    private static Component entryType(JsonObject entry) {
        String type = TableDraft.type(entry);
        return Component.translatableWithFallback("screen.justenoughstructures.editor.entry_type." + type.replace(':', '.'), type);
    }

    private static String range(TableDraft.Range range) {
        return range.min() == range.max() ? String.valueOf(range.min())
                : Component.translatable("screen.justenoughstructures.editor.range", range.min(), range.max()).getString();
    }

    private void renderForm(GuiGraphics g, int mouseX, int mouseY) {
        int x = formX + 2;
        int y = contentTop;
        int w = formW - 4;
        JsonObject pool = pool();
        if (pool == null) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.pick_something"), x, y + 4, w, Gui.LABEL_SOFT);
            return;
        }
        JsonObject entry = entry();
        if (entry == null) {
            Gui.band(g, font, Gui.clip(font, Component.translatable("screen.justenoughstructures.editor.pool", selectedPool + 1).getString(), w - 8), x - 2, y, w + 4, 13);
            Gui.small(g, font, Component.translatable("screen.justenoughstructures.editor.field.rolls").getString(), x, y + LABEL_1, Gui.LABEL_SOFT);
            if (TableDraft.rolls(pool) == null) {
                Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.formula"), x, y + FIELD_1, w, Gui.LABEL_SOFT);
            } else {
                g.drawString(font, Component.translatable("screen.justenoughstructures.editor.to"), x + 45, y + FIELD_1 + 4, Gui.LABEL_SOFT, false);
            }
            return;
        }
        Gui.band(g, font, Gui.clip(font, label(entry), w - 8), x - 2, y, w + 4, 13);
        if (!TableDraft.isItem(entry)) {
            Component what = Component.translatable("screen.justenoughstructures.editor.not_item", entryType(entry));
            Gui.wrapped(g, font, what, x, y + 18, w, Gui.LABEL_SOFT);
            return;
        }
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.editor.field.item").getString(), x, y + LABEL_1, Gui.LABEL_SOFT);
        ResourceLocation id = ResourceLocation.tryParse(itemBox == null ? "" : itemBox.getValue().trim());
        if (itemBox != null && (id == null || !BuiltInRegistries.ITEM.containsKey(id)) && suggestions.isEmpty()) {
            Gui.small(g, font, Gui.clipSmall(font, Component.translatable("screen.justenoughstructures.editor.unknown_item").getString(), w),
                    x, y + FIELD_1 + 18, BAD);
        }
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.editor.field.weight").getString(), x, y + LABEL_2, Gui.LABEL_SOFT);
        Gui.small(g, font, Component.translatable("screen.justenoughstructures.editor.field.count").getString(), x, y + LABEL_3, Gui.LABEL_SOFT);
        int infoY;
        if (TableDraft.count(entry) == null) {
            infoY = Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.formula"), x, y + FIELD_3, w, Gui.LABEL_SOFT);
        } else {
            g.drawString(font, Component.translatable("screen.justenoughstructures.editor.to"), x + 45, y + FIELD_3 + 4, Gui.LABEL_SOFT, false);
            infoY = y + INFO;
        }
        int others = TableDraft.others(entry);
        if (others > 0) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.others", others), x, infoY + 2, w, Gui.LABEL_SOFT);
        }
    }

    private void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        int x = formX + 2;
        int w = formW - 4;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Gui.card(g, x, suggestionsY, w, suggestions.size() * ROW + 2);
        for (int i = 0; i < suggestions.size(); i++) {
            int y = suggestionsY + 1 + i * ROW;
            if (over(mouseX, mouseY, x, y, w, ROW, Integer.MAX_VALUE)) {
                g.fill(x + 1, y, x + w - 1, y + ROW, Gui.ROW_HOVER);
            }
            Item item = suggestions.get(i);
            g.renderItem(new ItemStack(item), x + 2, y + 1);
            Gui.fitted(g, font, item.getDescription().getString(), x + 21, y + 5, w - 24, Gui.LABEL);
        }
        g.pose().popPose();
    }

    private void renderPreview(GuiGraphics g) {
        int x = previewX;
        int y = contentTop;
        int w = previewW;
        Gui.band(g, font, Gui.clip(font, Component.translatable("screen.justenoughstructures.editor.preview").getString(), w - 8), x, y, w, 13);
        int top = y + 16;
        if (previewProblem != null) {
            Gui.wrapped(g, font, previewProblem, x + 2, top, w - 4, BAD);
            return;
        }
        if (preview == null) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.rolling"), x + 2, top, w - 4, Gui.LABEL_SOFT);
            return;
        }
        g.enableScissor(x, top, x + w, contentBottom);
        int rowY = top - (int) previewScroll;
        for (LootOdds.Row row : preview.rows()) {
            float chance = (float) row.hits() / preview.rolls();
            String pct = chance >= 0.1f ? Math.round(chance * 100) + "%" : String.format(Locale.ROOT, "%.1f%%", chance * 100);
            Gui.slot(g, x + 1, rowY);
            g.renderItem(row.example(), x + 2, rowY + 1);
            Gui.fitted(g, font, row.example().getHoverName().getString(), x + 22, rowY + 5, w - 26 - font.width(pct), Gui.LABEL);
            g.drawString(font, pct, x + w - 2 - font.width(pct), rowY + 5, Gui.LABEL, false);
            rowY += ROW + 1;
        }
        if (previewing) {
            Gui.small(g, font, Component.translatable("screen.justenoughstructures.editor.rolling").getString(), x + 2, rowY + 2, Gui.LABEL_SOFT);
        }
        g.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** See changes, for the screenshot harness. */
    public void showChanges() {
        if (view != null) {
            minecraft.setScreen(new DiffScreen(this, tableName, view.original(), json()));
        }
    }

    /** Merge, for the screenshot harness. */
    public void mergeNow() {
        if (view != null) {
            merge();
        }
    }

    /** For the screenshot harness: the pool and entry to pick. */
    public void pick(int pool, int entry) {
        selectedPool = pool;
        selectedEntry = entry;
        rebuildWidgets();
    }

    public boolean loaded() {
        return view != null || loadProblem != null;
    }

    /** For the screenshot harness: the JSON view with this text in it. */
    public void showJson(String text) {
        if (!raw) {
            toggleMode();
        }
        if (rawBox != null) {
            rawBox.setValue(text);
        }
    }

    /** For the screenshot harness: types into the picked item's box. */
    public void typeItem(String text) {
        if (itemBox != null) {
            itemBox.setValue(text);
        }
    }

    /** Save, for the screenshot harness. */
    public void saveNow() {
        if (view != null) {
            save(false);
        }
    }
}
