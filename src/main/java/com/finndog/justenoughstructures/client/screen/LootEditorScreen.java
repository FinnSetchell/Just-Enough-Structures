package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.JsonMerge;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
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

/**
 * Edits a loot table in game, as a form like misode's generator. On the left are the table, its
 * pools and their entries; in the middle the form for the one picked, with every function and
 * condition, and one possible roll of the edit; on the right every item's chance. All of it is the
 * table's JSON underneath, which can be edited directly too. Saving writes an override, which
 * applies from the next /reload; the server refuses anything the game couldn't load.
 */
public final class LootEditorScreen extends BackdropScreen implements Nav.Page, LootForm.Host {
    private static final int PAD = 6;
    private static final int TOP = NavBar.TOP;
    private static final int TREE_ROW = 18;
    /** The widest the tree, form and chances are together. */
    private static final int MOST_CONTENT = 1100;
    /** The least height the form keeps when one possible roll goes under it; with less, there's no roll. */
    private static final int LEAST_FORM = 170;
    private static final int SLOT = 18;
    private static final int GOOD = 0xFF2E7D1F;
    private static final int BAD = 0xFFB02020;
    private static final int WARN = 0xFF9A6200;
    private static final long PREVIEW_DELAY = 400;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Where a new table starts: a chest table with one pool and nothing in it yet. */
    private static final String BLANK = "{\"type\": \"minecraft:chest\", \"pools\": [{\"rolls\": 1, \"entries\": []}]}";

    private final Screen parent;
    private final NavBar navBar = new NavBar(this);
    private final ToolsUi ui;
    private final LootForm form;
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
    /** Merge in what the mod changed as soon as the table's here, as Pack tools' Merge asks. */
    private boolean mergeWhenLoaded;
    private Component message;
    private int messageColour;

    /** What's picked on the left: -1 for the table itself, then a pool, and -1 for the pool or one of its entries. */
    private int selectedPool = -1;
    private int selectedEntry = -1;
    private final ToolsSection.Scroller treeScroll = new ToolsSection.Scroller();
    private final ToolsSection.Scroller formScroll = new ToolsSection.Scroller();
    private final ToolsSection.Scroller oddsScroll = new ToolsSection.Scroller();

    private LootOdds preview;
    private Component previewProblem;
    private boolean previewing;
    private long previewDue;
    private int previewRequest;
    private List<ItemStack> roll;
    private long rollSeed = ThreadLocalRandom.current().nextLong();
    private int rollRequest;
    private LootOdds.Row hoveredRow;
    private float hoveredChance;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    private MultiLineEditBox rawBox;
    /** The one text box for typing in the form, moved to whichever field is clicked. */
    private EditBox inline;
    private String editingId;
    private Consumer<String> editingCommit;
    /** Where each field was drawn this frame, so the text box can follow its field as the form scrolls. */
    private final Map<String, int[]> fieldRects = new HashMap<>();
    private Choice choice;
    /** What the field being typed in can be set to, listed under it, for fields that have such a list. */
    private Suggestions suggestions;
    private Picker picker;
    private EditBox pickerSearch;

    private int contentTop, contentBottom, treeX, treeW, formX, formW, oddsX, oddsW, rollTop;
    /** How far in from the panel's sides the columns, header and buttons are, on a very big screen. */
    private int inset;
    /** How many times a chest's own size the roll's slots are drawn: twice when there's plenty of room. */
    private int rollScale = 1;

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
        this.ui = new ToolsUi(net.minecraft.client.Minecraft.getInstance().font);
        this.form = new LootForm(net.minecraft.client.Minecraft.getInstance().font, ui, this);
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
                // The first pool, as that's where most of the table is.
                if (draft != null && JsonPaths.object(draft, "pools.0") != null) {
                    selectedPool = 0;
                }
            }
            if (mergeWhenLoaded) {
                mergeWhenLoaded = false;
                merge();
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
            raw = false;
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
            return parsed.isJsonObject() ? LootFormat.forEditing(parsed.getAsJsonObject()) : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /** The edit as it stands, as JSON text. */
    private String json() {
        if (raw) {
            return rawBox != null ? rawBox.getValue() : rawText == null ? "" : rawText;
        }
        return draft == null ? "" : PRETTY.toJson(LootFormat.forGame(draft));
    }

    @Override
    public JsonObject draft() {
        return draft;
    }

    @Override
    public void changed() {
        dirty = true;
        if (message != null && messageColour == BAD) {
            message = null;
        }
        // Whatever was picked may be gone, like a removed pool.
        if (selectedPool >= 0 && JsonPaths.object(draft, JsonPaths.join("pools", selectedPool)) == null) {
            selectedPool = -1;
            selectedEntry = -1;
        } else if (selectedEntry >= 0 && JsonPaths.object(draft, "pools." + selectedPool + ".entries." + selectedEntry) == null) {
            selectedEntry = -1;
        }
        schedulePreview();
    }

    private void schedulePreview() {
        previewDue = Util.getMillis() + PREVIEW_DELAY;
    }

    @Override
    public void select(int pool, int entry) {
        commitEditing();
        selectedPool = pool;
        selectedEntry = entry;
        formScroll.reset();
    }

    // ------------------------------------------------------------------ typing in the form

    @Override
    public void edit(String id, int[] rect, String text, Consumer<String> commit) {
        commitEditing();
        if (inline == null) {
            return;
        }
        editingId = id;
        editingCommit = commit;
        inline.setValue(text);
        inline.moveCursorToEnd();
        inline.setHighlightPos(0);
        place(inline, rect);
        inline.visible = true;
        setFocused(inline);
        inline.setFocused(true);
    }

    @Override
    public void edit(String id, int[] rect, String text, Consumer<String> commit, LootOptions.Source options) {
        edit(id, rect, text, commit);
        if (editingId != null && options != null) {
            suggestions = new Suggestions(rect, options, text);
        }
    }

    /** Puts a listed option in the field being typed in, as if it had been typed. */
    private void pickSuggestion(LootOptions.Option option) {
        if (inline != null) {
            inline.setValue(option.id());
        }
        commitEditing();
    }

    @Override
    public String editing() {
        return editingId;
    }

    @Override
    public void drawn(String id, int[] rect) {
        fieldRects.put(id, rect);
    }

    private static void place(EditBox box, int[] rect) {
        box.setX(rect[0] + 4);
        box.setY(rect[1] + 3);
        box.setWidth(Math.max(10, rect[2] - 8));
    }

    /** Puts what was typed into the table, and puts the text box away. */
    private void commitEditing() {
        if (editingId == null) {
            return;
        }
        Consumer<String> commit = editingCommit;
        String text = inline == null ? null : inline.getValue();
        stopEditing();
        if (text != null) {
            commit.accept(text);
        }
    }

    private void stopEditing() {
        editingId = null;
        editingCommit = null;
        suggestions = null;
        if (inline != null) {
            inline.visible = false;
            inline.setFocused(false);
        }
        if (getFocused() == inline) {
            setFocused(null);
        }
    }

    @Override
    public void choose(int[] rect, List<String> options, Function<String, String> names, String current, Consumer<String> pick) {
        commitEditing();
        choice = new Choice(rect, options, names, current, pick);
    }

    @Override
    public void pickItem(String path) {
        commitEditing();
        picker = new Picker(path);
        if (pickerSearch != null) {
            pickerSearch.setValue("");
            pickerSearch.visible = true;
            setFocused(pickerSearch);
            pickerSearch.setFocused(true);
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        commitEditing();
        // A list of choices hangs under its box where that was, which moves when the screen changes size.
        choice = null;
        // Tags and the server's lists may have changed since the editor was last open.
        LootOptions.forget();
        int left = PAD + 6;
        int right = width - PAD - 6;
        // On a very big screen the columns are kept together in the middle, rather than a form
        // stretching across it with its links far from what they're for.
        inset = Math.max(0, (right - left - MOST_CONTENT) / 2);
        left += inset;
        right -= inset;
        int content = right - left;
        contentTop = TOP + 46;
        contentBottom = height - PAD - 30;
        treeW = Math.max(110, Math.min(Math.max(200, content / 5), content * 27 / 100));
        oddsW = content >= 420 ? Math.max(120, Math.min(Math.max(200, content / 5), content * 25 / 100)) : 0;
        treeX = left;
        oddsX = right - oddsW;
        formX = treeX + treeW + 6;
        formW = (oddsW > 0 ? oddsX - 6 : right) - formX;
        // One possible roll goes under the form when there's room for both, and at twice the size
        // when it fits across and the form still keeps plenty of room.
        int contentH = contentBottom - contentTop;
        rollScale = formW >= 2 * 9 * SLOT + 12 && contentH - (6 * SLOT + 44) >= 240 ? 2 : 1;
        rollTop = contentH - rollHeight() - 6 >= LEAST_FORM ? contentBottom - rollHeight() - 2 : contentBottom;

        addToolbar(left, right);
        inline = addRenderableWidget(new EditBox(font, 0, 0, 40, 9, Component.translatable("screen.justenoughstructures.editor.field")));
        inline.setBordered(false);
        inline.setMaxLength(32767);
        inline.visible = false;
        pickerSearch = addRenderableWidget(new EditBox(font, 0, 0, 100, 14, Component.translatable("screen.justenoughstructures.editor.search_items")));
        pickerSearch.setHint(Component.translatable("screen.justenoughstructures.editor.search_items").withStyle(ChatFormatting.DARK_GRAY));
        pickerSearch.setResponder(text -> {
            if (picker != null) {
                picker.query(text);
            }
        });
        pickerSearch.visible = picker != null;
        if (view == null) {
            return;
        }
        idBox = null;
        if (isNew()) {
            int labelWidth = font.width(Component.translatable("screen.justenoughstructures.editor.id")) + 4;
            int boxX = PAD + 8 + inset + labelWidth;
            idBox = addRenderableWidget(new EditBox(font, boxX, TOP + 17, Math.max(60, Math.min(240, width / 3)), 12,
                    Component.translatable("screen.justenoughstructures.editor.id")));
            idBox.setMaxLength(256);
            idBox.setValue(tableId.toString());
            idBox.setResponder(this::idChanged);
        }
        rawBox = null;
        if (raw) {
            rawBox = addRenderableWidget(JsonEditBox.create(font, treeX, contentTop, formX + formW - treeX, contentBottom - contentTop,
                    Component.empty(), Component.translatable("screen.justenoughstructures.editor.json")));
            rawBox.setCharacterLimit(Integer.MAX_VALUE);
            rawBox.setValue(rawText == null ? "" : rawText);
            rawBox.setValueListener(text -> {
                if (!text.equals(rawText)) {
                    rawText = text;
                    dirty = true;
                    schedulePreview();
                }
            });
        }
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
        if (ClientRequests.canUsePackTools()) {
            x -= buttonWidth("save_reload") + 4;
            Button saveReload = addRenderableWidget(Button.builder(Component.translatable("screen.justenoughstructures.editor.save_reload"),
                    b -> save(true)).bounds(x, y, buttonWidth("save_reload"), 20).build());
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
                commitEditing();
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

    // ------------------------------------------------------------------ actions

    private void toggleMode() {
        commitEditing();
        if (raw) {
            JsonObject parsed = parseObject(json());
            if (parsed == null) {
                note(Component.translatable("screen.justenoughstructures.editor.fix_json"), BAD);
                return;
            }
            draft = parsed;
            raw = false;
            rawText = null;
            if (selectedPool >= 0 && JsonPaths.object(draft, JsonPaths.join("pools", selectedPool)) == null) {
                selectedPool = -1;
                selectedEntry = -1;
            }
        } else {
            rawText = json();
            raw = true;
        }
        rebuildWidgets();
    }

    private void merge() {
        commitEditing();
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
        Component merged = switch (result.conflicts()) {
            case 0 -> Component.translatable("screen.justenoughstructures.editor.merged");
            case 1 -> Component.translatable("screen.justenoughstructures.editor.merged_conflict");
            default -> Component.translatable("screen.justenoughstructures.editor.merged_conflicts", result.conflicts());
        };
        note(merged, result.conflicts() == 0 ? GOOD : WARN);
        rebuildWidgets();
    }

    /** What has to be fixed before saving that the server wouldn't say so plainly: an item that isn't one, or the id. Null if nothing. */
    private Component unsaveable() {
        if (!raw && draft != null) {
            JsonArray pools = JsonPaths.array(draft, "pools");
            for (int p = 0; pools != null && p < pools.size(); p++) {
                JsonArray entries = JsonPaths.array(draft, "pools." + p + ".entries");
                for (int e = 0; entries != null && e < entries.size(); e++) {
                    String path = "pools." + p + ".entries." + e;
                    if (LootTypes.shortId(JsonPaths.string(draft, path + ".type", "")).equals("item")
                            && !LootForm.itemExists(JsonPaths.string(draft, path + ".name", ""))) {
                        selectedPool = p;
                        selectedEntry = e;
                        return Component.translatable("screen.justenoughstructures.editor.fix_item", JsonPaths.string(draft, path + ".name", ""));
                    }
                }
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
        commitEditing();
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
                if (thenReload) {
                    ClientRequests.reloadServer();
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

    private void reroll() {
        rollSeed = ThreadLocalRandom.current().nextLong();
        requestRoll();
    }

    private void requestRoll() {
        int request = ++rollRequest;
        ClientRequests.draftRoll(tableId, json(), rollSeed, 27).thenAccept(items -> {
            if (request == rollRequest) {
                roll = items;
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (rawBox != null) {
            //? if <1.21 {
            rawBox.tick();
            //?}
        }
        if (idBox != null) {
            //? if <1.21 {
            idBox.tick();
            //?}
        }
        if (inline != null) {
            //? if <1.21 {
            inline.tick();
            //?}
        }
        if (pickerSearch != null) {
            //? if <1.21 {
            pickerSearch.tick();
            //?}
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
            requestRoll();
        }
    }

    // ------------------------------------------------------------------ keys and the mouse

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == InputConstants.KEY_ESCAPE) {
            if (picker != null) {
                closePicker();
                return true;
            }
            if (choice != null) {
                choice = null;
                return true;
            }
            if (editingId != null) {
                stopEditing();
                return true;
            }
        }
        if (editingId != null && suggestions != null) {
            if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
                suggestions.move(key == InputConstants.KEY_DOWN ? 1 : -1);
                return true;
            }
            LootOptions.Option highlighted = suggestions.highlighted();
            if (highlighted != null && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_TAB)) {
                pickSuggestion(highlighted);
                return true;
            }
        }
        if (editingId != null && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_TAB)) {
            commitEditing();
            return true;
        }
        if (key == InputConstants.KEY_S && hasControlDown() && view != null) {
            save(false);
            return true;
        }
        if (picker == null && navBar.keyPressed(key, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (picker != null) {
            if (pickerSearch != null && pickerSearch.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            picker.click(mouseX, mouseY);
            return true;
        }
        if (choice != null) {
            choice.click(mouseX, mouseY);
            choice = null;
            return true;
        }
        if (editingId != null && suggestions != null && suggestions.contains(mouseX, mouseY)) {
            LootOptions.Option clicked = suggestions.at(mouseX, mouseY);
            if (clicked != null) {
                pickSuggestion(clicked);
            }
            return true;
        }
        if (editingId != null && !(inline != null && inline.isMouseOver(mouseX, mouseY))) {
            commitEditing();
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && ui.click(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (picker != null) {
            picker.scroll(delta);
            return true;
        }
        if (choice != null) {
            choice.scroll(delta);
            return true;
        }
        if (editingId != null && suggestions != null && suggestions.contains(mouseX, mouseY)) {
            suggestions.scroll(delta);
            return true;
        }
        if (treeScroll.scroll(mouseX, mouseY, delta) || oddsScroll.scroll(mouseX, mouseY, delta)) {
            return true;
        }
        if (formScroll.scroll(mouseX, mouseY, delta)) {
            commitEditing();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        commitEditing();
        if (!dirty) {
            minecraft.setScreen(parent);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(leave -> minecraft.setScreen(leave ? parent : this),
                Component.translatable("screen.justenoughstructures.editor.unsaved.title"), Component.empty()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ back and forward

    /** Where the editor is, for Back and Forward: which table. */
    private record EditorLayer(ResourceLocation id, String name, boolean isNew) implements Nav.Layer {
        @Override
        public Object key() {
            return List.of("editor", id);
        }

        @Override
        public Component label() {
            return isNew ? Component.translatable("screen.justenoughstructures.nav.new_table")
                    : Component.translatable("screen.justenoughstructures.nav.editor", name);
        }

        @Override
        public Screen open(Screen below) {
            return new LootEditorScreen(below, id, name, below instanceof TablePickerScreen picker ? picker::useSaved : null);
        }
    }

    @Override
    public Nav.Layer layer() {
        return new EditorLayer(openedAs, tableName, isNew());
    }

    @Override
    public Screen below() {
        return parent;
    }

    @Override
    public boolean unsaved() {
        return dirty;
    }

    @Override
    public void discard() {
        dirty = false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Gui.beginClipped();
        backdrop(g);
        Gui.panel(g, PAD, TOP, width - PAD * 2, height - TOP - PAD);
        boolean overlay = picker != null || choice != null;
        int mx = overlay ? -1 : mouseX;
        int my = overlay ? -1 : mouseY;
        ui.begin(mx, my);
        fieldRects.clear();
        hoveredRow = null;
        hoveredStack = ItemStack.EMPTY;
        header(g);

        if (loadProblem != null) {
            Gui.wrapped(g, font, loadProblem, treeX, contentTop + 4, width - PAD * 2 - 16, BAD);
        } else if (view == null) {
            Gui.wrapped(g, font, Component.translatable("screen.justenoughstructures.editor.loading"), treeX, contentTop + 4, width - PAD * 2 - 16, Gui.LABEL_SOFT);
        } else if (!raw && draft != null) {
            renderTree(g);
            renderForm(g);
            if (rollTop < contentBottom) {
                renderRoll(g);
            }
        }
        if (oddsW > 0 && view != null) {
            renderOdds(g);
        }
        followField();
        super.render(g, mx, my, partialTick);

        if (picker != null || choice != null) {
            // Only what's cut short in the list over the editor counts now.
            Gui.beginClipped();
            Gui.push(g);
            Gui.lift(g, 400);
            if (picker != null) {
                picker.render(g, mouseX, mouseY);
            } else {
                choice.render(g, mouseX, mouseY);
            }
            Gui.pop(g);
            if (pickerSearch != null && picker != null) {
                Gui.push(g);
                Gui.lift(g, 450);
                Gui.render(g, pickerSearch, mouseX, mouseY, partialTick);
                Gui.pop(g);
            }
        }
        if (suggestions != null && editingId != null) {
            Gui.push(g);
            Gui.lift(g, 400);
            suggestions.render(g, mouseX, mouseY);
            Gui.pop(g);
        }
        navBar.render(g, font, mouseX, mouseY, partialTick);

        Gui.push(g);
        Gui.lift(g, 600);
        if (picker != null && picker.hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(StructureNames.item(picker.hovered));
            if (Gui.advanced()) {
                lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(picker.hovered).toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (!overlay && hoveredRow != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hoveredRow.example()));
            lines.addAll(OddsList.tooltip(hoveredRow, hoveredChance, 1, Component.translatable("screen.justenoughstructures.container").getString()));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (!overlay && !hoveredStack.isEmpty()) {
            g.renderTooltip(font, hoveredStack, mouseX, mouseY);
        } else if (!overlay && ui.tooltip() != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            ui.tooltip().forEach(line -> lines.addAll(font.split(line, 260)));
            g.renderTooltip(font, lines, mouseX, mouseY);
        } else {
            Gui.clippedTooltip(g, font, mouseX, mouseY);
        }
        Gui.pop(g);
    }

    /** The title, the id, and a line saying how the table stands with what can be done about it. */
    private void header(GuiGraphics g) {
        int textX = PAD + 8 + inset;
        int right = width - PAD - 8 - inset;
        String unsaved = dirty && view != null ? Component.translatable("screen.justenoughstructures.editor.unsaved").getString() : null;
        int markRoom = unsaved == null ? 0 : Gui.fineWidth(font, unsaved) + 8;
        String shownTitle = Gui.clip(font, headerTitle().getString(), right - textX - markRoom);
        Gui.drawClipped(g, font, headerTitle().getString(), textX, TOP + 6, right - textX - markRoom, Gui.LABEL, false);
        if (unsaved != null) {
            Gui.fine(g, font, unsaved, textX + font.width(shownTitle) + 8, TOP + 7, WARN);
        }
        if (idBox != null) {
            g.drawString(font, Component.translatable("screen.justenoughstructures.editor.id"), textX, TOP + 19, Gui.LABEL_SOFT, false);
        } else {
            Gui.fine(g, font, tableId.toString(), textX, TOP + 17, Gui.LABEL_SOFT);
        }
        if (view == null) {
            return;
        }
        // The line under it: a message if there's one, otherwise how the table stands.
        int barY = TOP + 30;
        Component status = message != null ? message : statusText();
        int colour = message != null ? messageColour : statusColour();
        if (idProblem != null) {
            status = idProblem;
            colour = BAD;
        }
        int[] fill = switch (view.status()) {
            case ACTIVE -> new int[]{0xFFCFE8C0};
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> new int[]{0xFFF1DCAE};
            case BROKEN -> new int[]{0xFFF0C0C0};
            default -> new int[]{isNew() ? 0xFFC6D4F0 : 0xFFD6D6D6};
        };
        g.fill(PAD + 6 + inset, barY, width - PAD - 6 - inset, barY + 13, fill[0]);
        int actionsLeft = right;
        if (view.status() == LootOverrides.Status.ORIGINAL_CHANGED && message == null) {
            for (String key : List.of("use_mods", "keep", "merge", "see_changes")) {
                Component label = Component.translatable("screen.justenoughstructures.editor." + key);
                actionsLeft -= ui.buttonWidth(label);
                Runnable action = switch (key) {
                    // The same as Remove: the edit's put aside, and the mod's table used.
                    case "use_mods" -> () -> tableAction(JesNetwork.ACTION_REMOVE);
                    case "keep" -> () -> tableAction(JesNetwork.ACTION_KEEP);
                    case "merge" -> this::merge;
                    default -> this::showChanges;
                };
                String hint = "screen.justenoughstructures.editor." + key + "_hint";
                ui.button(g, label, actionsLeft, barY, ui.buttonWidth(label), 13, true, action,
                        net.minecraft.locale.Language.getInstance().has(hint) ? Component.translatable(hint) : null);
                actionsLeft -= 2;
            }
        }
        String text = Gui.clip(font, status.getString(), actionsLeft - textX - 6);
        g.drawString(font, text, textX, barY + 3, colour, false);
        if (!text.equals(status.getString())) {
            ui.tooltip(textX, barY, actionsLeft - textX, 13, status);
        }
    }

    private Component headerTitle() {
        if (isNew()) {
            return Component.translatable("screen.justenoughstructures.editor.new_title");
        }
        return Component.translatable("screen.justenoughstructures.editor.title", tableId.equals(openedAs) ? tableName
                : StructureNames.lootTable(tableId.toString()));
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
        if (view == null || isNew()) {
            return 0xFF1F2F60;
        }
        return switch (view.status()) {
            case NONE -> 0xFF505050;
            case ACTIVE -> 0xFF24451A;
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> 0xFF5A3A00;
            case BROKEN -> 0xFF6A1010;
        };
    }

    /** The table, its pools and their entries, to pick one to change, with ways to add more. */
    private void renderTree(GuiGraphics g) {
        Gui.inset(g, treeX, contentTop, treeW, contentBottom - contentTop, Gui.PANEL);
        int top = treeScroll.begin(g, ui, treeX + 1, contentTop + 1, treeW - 2, contentBottom - contentTop - 2);
        int w = treeScroll.width();
        int x = treeX + 1;
        int y = top;
        String type = JsonPaths.string(draft, "type", "minecraft:chest");
        String typeName = LootTypes.name("table_type", type);
        y = treeRow(g, x, y, w, ItemStack.EMPTY, Component.translatable("screen.justenoughstructures.editor.card.table").getString(),
                typeName, typeName, selectedPool < 0, () -> select(-1, -1), 0);
        JsonArray pools = JsonPaths.array(draft, "pools");
        boolean shortWeights = shortWeights(pools, w);
        for (int p = 0; pools != null && p < pools.size(); p++) {
            int pool = p;
            String poolPath = "pools." + p;
            JsonElement rolls = JsonPaths.get(draft, poolPath + ".rolls");
            y = treeRow(g, x, y, w, ItemStack.EMPTY, Component.translatable("screen.justenoughstructures.editor.pool", p + 1).getString(),
                    rollsText(rolls), rollsAmount(rolls), selectedPool == p && selectedEntry < 0, () -> select(pool, -1), 0);
            JsonArray entries = JsonPaths.array(draft, poolPath + ".entries");
            for (int e = 0; entries != null && e < entries.size(); e++) {
                int entry = e;
                JsonObject object = entries.get(e).isJsonObject() ? entries.get(e).getAsJsonObject() : new JsonObject();
                JsonElement weight = object.get("weight");
                String weightText = weight == null ? "1" : weight.getAsString();
                String weightLabel = shortWeights ? weightText : Component.translatable("screen.justenoughstructures.editor.weight", weightText).getString();
                y = treeRow(g, x, y, w, icon(object), label(object), weightLabel, weightText,
                        selectedPool == p && selectedEntry == e, () -> select(pool, entry), 8);
            }
            Component add = Component.translatable("screen.justenoughstructures.editor.add_item");
            ui.link(g, add, x + 12, y + 2, true, () -> {
                int index = JsonPaths.append(draft, poolPath + ".entries", LootTypes.entry("minecraft:item", null));
                select(pool, index);
                changed();
            });
            y += Gui.fineLine(font) + 6;
        }
        ui.link(g, Component.translatable("screen.justenoughstructures.editor.add_pool"), x + 4, y + 2, true, () -> {
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.add("entries", new JsonArray());
            int index = JsonPaths.append(draft, "pools", pool);
            select(index, -1);
            changed();
        });
        y += Gui.fineLine(font) + 6;
        treeScroll.end(g, ui, y - top);
    }

    /**
     * A row of the tree. {@code shortDetail} stands in for {@code detail} when the name wouldn't fit
     * beside it, like "15" for "weight 15", as the name matters more.
     */
    private int treeRow(GuiGraphics g, int x, int y, int w, ItemStack icon, String name, String detail, String shortDetail, boolean selected,
                        Runnable pick, int indent) {
        if (selected) {
            g.fill(x, y, x + w, y + TREE_ROW, 0xFF9D9D9D);
        } else if (ui.hovered(x, y, w, TREE_ROW)) {
            g.fill(x, y, x + w, y + TREE_ROW, Gui.ROW_HOVER);
        }
        ui.spot(x, y, w, TREE_ROW, pick);
        int textX = x + 3 + indent;
        if (!icon.isEmpty()) {
            g.renderItem(icon, textX, y + 1);
            textX += 19;
        }
        if (font.width(name) > x + w - 6 - Gui.fineWidth(font, detail) - textX) {
            detail = shortDetail;
        }
        int detailW = Gui.fineWidth(font, detail);
        Gui.drawClipped(g, font, name, textX, y + 5, x + w - 6 - detailW - textX, selected ? 0xFFFFFFFF : Gui.LABEL, selected);
        Gui.fine(g, font, detail, x + w - 3 - detailW, y + 6, selected ? 0xFFEEEEEE : Gui.LABEL_SOFT);
        return y + TREE_ROW;
    }

    /**
     * Whether entries show only their weight, "15" rather than "weight 15": when any entry's name
     * wouldn't fit beside the longer one, so they all read the same way down the tree.
     */
    private boolean shortWeights(JsonArray pools, int w) {
        for (int p = 0; pools != null && p < pools.size(); p++) {
            JsonArray entries = JsonPaths.array(draft, "pools." + p + ".entries");
            for (int e = 0; entries != null && e < entries.size(); e++) {
                JsonObject object = entries.get(e).isJsonObject() ? entries.get(e).getAsJsonObject() : new JsonObject();
                JsonElement weight = object.get("weight");
                String full = Component.translatable("screen.justenoughstructures.editor.weight", weight == null ? "1" : weight.getAsString()).getString();
                // Indented, with an icon: the same room treeRow leaves the name.
                if (font.width(label(object)) > w - 36 - Gui.fineWidth(font, full)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many rolls a pool has on its own, like "2-4", for when "rolls 2-4" doesn't fit. */
    private static String rollsAmount(JsonElement rolls) {
        String kind = LootForm.providerKind(rolls);
        if (rolls == null) {
            return "1";
        } else if (kind.equals("constant")) {
            JsonElement value = rolls.isJsonObject() ? rolls.getAsJsonObject().get("value") : rolls;
            return value == null ? "?" : LootForm.shown(value);
        } else if (kind.equals("uniform")) {
            JsonObject range = rolls.getAsJsonObject();
            return Component.translatable("screen.justenoughstructures.editor.range", string(range.get("min")), string(range.get("max"))).getString();
        }
        return rollsText(rolls);
    }

    private static String rollsText(JsonElement rolls) {
        String kind = LootForm.providerKind(rolls);
        String amount;
        if (rolls == null) {
            amount = "1";
        } else if (kind.equals("constant")) {
            JsonElement value = rolls.isJsonObject() ? rolls.getAsJsonObject().get("value") : rolls;
            amount = value == null ? "?" : LootForm.shown(value);
        } else if (kind.equals("uniform")) {
            JsonObject range = rolls.getAsJsonObject();
            amount = Component.translatable("screen.justenoughstructures.editor.range", string(range.get("min")), string(range.get("max"))).getString();
        } else {
            return Component.translatable("screen.justenoughstructures.editor.rolls_formula").getString();
        }
        return Component.translatable("screen.justenoughstructures.editor.rolls", amount).getString();
    }

    private static String string(JsonElement value) {
        return value == null ? "?" : value.isJsonPrimitive() ? LootForm.shown(value) : "?";
    }

    private static ItemStack icon(JsonObject entry) {
        String kind = LootTypes.shortId(entry.has("type") ? entry.get("type").getAsString() : "minecraft:item");
        return switch (kind) {
            case "item" -> {
                String name = entry.has("name") ? entry.get("name").getAsString() : "";
                yield LootForm.itemExists(name) ? new ItemStack(Regs.value(BuiltInRegistries.ITEM, Ids.parse(name.trim()))) : new ItemStack(Items.BARRIER);
            }
            case "tag" -> new ItemStack(Items.NAME_TAG);
            case "empty" -> new ItemStack(Items.GLASS_BOTTLE);
            case "loot_table" -> new ItemStack(Items.CHEST);
            default -> new ItemStack(Items.BOOKSHELF);
        };
    }

    private static String label(JsonObject entry) {
        String type = entry.has("type") ? entry.get("type").getAsString() : "minecraft:item";
        String name = entry.has("name") && entry.get("name").isJsonPrimitive() ? entry.get("name").getAsString() : "";
        if (LootTypes.shortId(type).equals("item")) {
            return LootForm.itemExists(name) ? LootForm.itemName(name).getString() : name;
        }
        String kind = LootTypes.name("entry_type", type);
        return name.isEmpty() ? kind : kind + " " + StructureNames.pretty(name);
    }

    private void renderForm(GuiGraphics g) {
        int bottom = rollTop - 4;
        int top = formScroll.begin(g, ui, formX, contentTop, formW, bottom - contentTop);
        int end = form.render(g, formX, top, formScroll.width(), selectedPool, selectedEntry);
        formScroll.end(g, ui, end - top + 4);
    }

    /** Keeps the text box over its field as the form scrolls, and puts it away once the field's out of view. */
    private void followField() {
        if (editingId == null || inline == null) {
            return;
        }
        int[] rect = fieldRects.get(editingId);
        int bottom = rollTop - 4;
        if (rect == null || rect[1] < contentTop || rect[1] + LootForm.FIELD > bottom) {
            commitEditing();
            return;
        }
        place(inline, rect);
    }

    private int rollHeight() {
        return 3 * SLOT * rollScale + 42;
    }

    /** One possible roll of the edit as it stands, in a chest's slots, with a way to roll again. */
    private void renderRoll(GuiGraphics g) {
        int slot = SLOT * rollScale;
        int gridW = 9 * slot + 8;
        int x = formX + Math.max(0, (formW - gridW) / 2);
        int y = rollTop;
        Gui.panel(g, x - 2, y, gridW + 4, rollHeight());
        Component title = Component.translatable("screen.justenoughstructures.tools.one_roll");
        g.drawString(font, title, x + 4, y + 5, Gui.LABEL, false);
        String changes = Component.translatable("screen.justenoughstructures.editor.with_changes").getString();
        if (dirty && font.width(title) + Gui.fineWidth(font, changes) + 14 <= gridW) {
            Gui.fine(g, font, changes, x + gridW - 4 - Gui.fineWidth(font, changes), y + 6, Gui.LABEL_SOFT);
        }
        int sy = y + 16;
        for (int i = 0; i < 27; i++) {
            int sx = x + 4 + (i % 9) * slot;
            int slotY = sy + (i / 9) * slot;
            ItemStack stack = roll != null && i < roll.size() ? roll.get(i) : ItemStack.EMPTY;
            // Drawn at the chest's own size and scaled up whole, slot, item and count together.
            Gui.push(g);
            Gui.translate(g, sx, slotY);
            Gui.scale(g, rollScale);
            Gui.slot(g, 0, 0);
            if (!stack.isEmpty()) {
                g.renderItem(stack, 1, 1);
                g.renderItemDecorations(font, stack, 1, 1);
            }
            Gui.pop(g);
            if (!stack.isEmpty() && ui.hovered(sx, slotY, slot, slot)) {
                hoveredStack = stack;
            }
        }
        ui.button(g, Component.translatable("screen.justenoughstructures.reroll_loot"), x + 2, sy + 3 * slot + 3, gridW - 4, 18, true, this::reroll);
    }

    /** Every item's chance in a container, from rolling the edit as it stands. */
    private void renderOdds(GuiGraphics g) {
        int x = oddsX;
        Gui.band(g, font, Component.translatable("screen.justenoughstructures.editor.preview").getString(), x, contentTop, oddsW, 13);
        int top = contentTop + 15;
        if (previewProblem != null) {
            Gui.fineWrapped(g, font, previewProblem, x + 2, top, oddsW - 4, BAD);
            return;
        }
        if (preview == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.editor.rolling").getString(), x + 2, top + 2, Gui.LABEL_SOFT);
            return;
        }
        int start = oddsScroll.begin(g, ui, x, top, oddsW, contentBottom - top);
        int w = oddsScroll.width();
        int y = start;
        List<LootOdds.Row> rows = OddsList.sorted(preview, false);
        Map<LootOdds.Row, String> names = OddsList.names(rows);
        int detailRoom = OddsList.detailRoom(font, rows.stream().map(OddsList::counts).toList());
        for (LootOdds.Row row : rows) {
            float chance = (float) row.hits() / Math.max(1, preview.rolls());
            boolean over = ui.hovered(x, y, w, OddsList.ROW);
            if (y + OddsList.ROW >= top && y <= contentBottom) {
                OddsList.drawRow(g, font, row.example(), names.get(row), OddsList.counts(row), chance, x, y, x + w, over, detailRoom);
            }
            if (over) {
                hoveredRow = row;
                hoveredChance = chance;
            }
            y += OddsList.ROW;
        }
        if (rows.isEmpty()) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.always_empty"), x + 2, y + 2, w - 4, Gui.LABEL_SOFT);
            y += 20;
        }
        if (previewing) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.editor.rolling").getString(), x + 2, y + 2, Gui.LABEL_SOFT);
            y += 12;
        }
        oddsScroll.end(g, ui, y - start);
    }

    // ------------------------------------------------------------------ the list to choose from

    /** A list of choices under a box in the form, like a function's kind. */
    private final class Choice {
        private static final int LINE = 12;
        private final int[] rect;
        private final List<String> options;
        private final Function<String, String> names;
        private final String current;
        private final Consumer<String> pick;
        private final int x, y, w, h;
        private double scroll;

        Choice(int[] rect, List<String> options, Function<String, String> names, String current, Consumer<String> pick) {
            this.rect = rect;
            this.options = options;
            this.names = names;
            this.current = current;
            this.pick = pick;
            int widest = rect[2];
            for (String option : options) {
                widest = Math.max(widest, font.width(names.apply(option)) + 12);
            }
            w = Math.min(widest, width - 12);
            h = Math.min(options.size() * LINE + 2, Math.max(60, Math.min(170, height - rect[1] - rect[3] - 8)));
            x = Math.max(4, Math.min(rect[0], width - w - 4));
            int below = rect[1] + rect[3];
            y = below + h <= height - 4 ? below : Math.max(4, rect[1] - h);
            int index = options.indexOf(current);
            if (index >= 0) {
                scroll = Math.max(0, Math.min(index * LINE - h / 2, options.size() * LINE + 2 - h));
            }
        }

        void render(GuiGraphics g, int mouseX, int mouseY) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
            g.fill(x, y, x + w, y + h, 0xFF202020);
            Gui.scissor(g, x, y, x + w, y + h);
            int cy = y + 1 - (int) scroll;
            for (String option : options) {
                if (cy + LINE > y && cy < y + h) {
                    boolean over = mouseX >= x && mouseX < x + w && mouseY >= Math.max(cy, y) && mouseY < Math.min(cy + LINE, y + h);
                    if (over) {
                        g.fill(x, cy, x + w, cy + LINE, 0xFF4B5280);
                    }
                    int colour = option.equals(current) ? 0xFFFFFF55 : 0xFFE0E0E0;
                    Gui.drawClipped(g, font, names.apply(option), x + 4, cy + 2, w - 8, colour, false);
                }
                cy += LINE;
            }
            Gui.endScissor(g);
        }

        void click(double mouseX, double mouseY) {
            if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
                return;
            }
            int index = (int) ((mouseY - y - 1 + scroll) / LINE);
            if (index >= 0 && index < options.size()) {
                pick.accept(options.get(index));
            }
        }

        void scroll(double delta) {
            scroll = Math.max(0, Math.min(scroll - delta * LINE * 2, Math.max(0, options.size() * LINE + 2 - h)));
        }

        int[] centre(String option) {
            int index = options.indexOf(option);
            if (index < 0) {
                return null;
            }
            scroll = Math.max(0, Math.min(index * LINE, Math.max(0, options.size() * LINE + 2 - h)));
            return new int[]{x + w / 2, y + 1 + index * LINE - (int) scroll + LINE / 2};
        }
    }

    // ------------------------------------------------------------------ options while typing

    /**
     * What the field being typed in can be set to, listed under it as misode's generator does: all of
     * them until something's typed, then the ones whose name or id has it in, those starting with it
     * first. Up and down move through them, and Enter, Tab or a click puts one in. Something typed
     * that isn't listed, like a mod's id this game doesn't have, can still be put in with Enter.
     */
    private final class Suggestions {
        private static final int LINE = 12;
        private static final int ROWS = 10;
        private final int[] rect;
        private final LootOptions.Source source;
        /** The text when it opened: until that changes, every option shows, with the one it is picked. */
        private final String opened;
        private String query;
        private List<LootOptions.Option> shown = List.of();
        private int highlight = -1;
        private int scroll;
        private int x, y, w, h;

        Suggestions(int[] rect, LootOptions.Source source, String opened) {
            this.rect = rect;
            this.source = source;
            this.opened = opened;
        }

        private void update() {
            String text = inline == null ? "" : inline.getValue();
            String q = text.equals(opened) ? "" : text.trim().toLowerCase(Locale.ROOT);
            if (q.equals(query)) {
                return;
            }
            query = q;
            List<LootOptions.Option> all = source.list();
            highlight = -1;
            if (q.isEmpty()) {
                shown = all;
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).id().equals(opened.trim()) || source.name(opened.trim()) != null && all.get(i).name().equals(source.name(opened.trim()))) {
                        highlight = i;
                        break;
                    }
                }
            } else {
                List<LootOptions.Option> starting = new ArrayList<>();
                List<LootOptions.Option> containing = new ArrayList<>();
                for (LootOptions.Option option : all) {
                    String id = option.id().toLowerCase(Locale.ROOT);
                    String path = id.substring(id.indexOf(':') + 1);
                    String name = option.name().toLowerCase(Locale.ROOT);
                    if (id.startsWith(q) || path.startsWith(q) || name.startsWith(q)) {
                        starting.add(option);
                    } else if (id.contains(q) || name.contains(q)) {
                        containing.add(option);
                    }
                }
                shown = new ArrayList<>(starting);
                shown.addAll(containing);
                // Only a close match is picked for Enter; otherwise Enter keeps what was typed.
                highlight = starting.isEmpty() ? -1 : 0;
            }
            scroll = highlight < 0 ? 0 : Math.max(0, Math.min(highlight - ROWS / 2, shown.size() - ROWS));
            layout();
        }

        private void layout() {
            int widest = rect[2];
            for (int i = 0; i < Math.min(shown.size(), 300); i++) {
                LootOptions.Option option = shown.get(i);
                widest = Math.max(widest, font.width(option.name()) + (sameAsId(option) ? 0 : Gui.fineWidth(font, option.id()) + 8) + 12);
            }
            w = Math.min(widest, Math.min(Math.max(rect[2], 320), width - 8));
            h = Math.min(shown.size(), ROWS) * LINE + 2;
            x = Math.max(4, Math.min(rect[0], width - w - 4));
            int below = rect[1] + rect[3] + 1;
            y = below + h <= height - 4 ? below : Math.max(4, rect[1] - h - 1);
        }

        private boolean sameAsId(LootOptions.Option option) {
            return option.name().equalsIgnoreCase(option.id());
        }

        void render(GuiGraphics g, int mouseX, int mouseY) {
            update();
            if (shown.isEmpty()) {
                return;
            }
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
            g.fill(x, y, x + w, y + h, 0xFF202020);
            int rows = Math.min(shown.size() - scroll, ROWS);
            for (int i = 0; i < rows; i++) {
                int index = scroll + i;
                LootOptions.Option option = shown.get(index);
                int cy = y + 1 + i * LINE;
                boolean over = mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + LINE;
                if (index == highlight || over) {
                    g.fill(x, cy, x + w, cy + LINE, index == highlight ? 0xFF4B5280 : 0xFF3A3A3A);
                }
                int right = x + w - (shown.size() > ROWS ? 6 : 3);
                int idRoom = sameAsId(option) ? 0 : Math.min(Gui.fineWidth(font, option.id()), (right - x) / 2);
                Gui.drawClipped(g, font, option.name(), x + 4, cy + 2, right - x - 6 - (idRoom > 0 ? idRoom + 6 : 0), 0xFFE0E0E0, false);
                if (idRoom > 0) {
                    Gui.fineClipped(g, font, option.id(), right - idRoom, cy + 3, idRoom, 0xFF8C8C8C);
                }
            }
            if (shown.size() > ROWS) {
                int track = h - 2;
                int thumb = Math.max(6, track * ROWS / shown.size());
                int top = y + 1 + (track - thumb) * scroll / Math.max(1, shown.size() - ROWS);
                g.fill(x + w - 3, top, x + w - 1, top + thumb, 0xFF9A9A9A);
            }
        }

        boolean contains(double mouseX, double mouseY) {
            update();
            return !shown.isEmpty() && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        LootOptions.Option at(double mouseX, double mouseY) {
            int index = (int) ((mouseY - y - 1) / LINE) + scroll;
            return index >= 0 && index < shown.size() ? shown.get(index) : null;
        }

        void move(int direction) {
            update();
            if (shown.isEmpty()) {
                return;
            }
            highlight = Math.max(0, Math.min(shown.size() - 1, highlight + direction));
            if (highlight < scroll) {
                scroll = highlight;
            } else if (highlight >= scroll + ROWS) {
                scroll = highlight - ROWS + 1;
            }
        }

        LootOptions.Option highlighted() {
            update();
            return highlight >= 0 && highlight < shown.size() ? shown.get(highlight) : null;
        }

        void scroll(double delta) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta) * 3, Math.max(0, shown.size() - ROWS)));
        }
    }

    // ------------------------------------------------------------------ the item picker

    private void closePicker() {
        picker = null;
        if (pickerSearch != null) {
            pickerSearch.visible = false;
            pickerSearch.setFocused(false);
        }
        setFocused(null);
    }

    /** Every item in the game, from every mod, to pick one for an entry. */
    private final class Picker {
        private final String path;
        private List<Item> shown;
        private double scroll;
        private int x, y, w, h, gridTop, gridH, columns;
        Item hovered;

        Picker(String path) {
            this.path = path;
            query("");
        }

        void query(String text) {
            String q = text.trim().toLowerCase(Locale.ROOT);
            List<Item> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                if (q.isEmpty() || BuiltInRegistries.ITEM.getKey(item).toString().contains(q)
                        || StructureNames.item(item).getString().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(item);
                }
            }
            if (!q.isEmpty()) {
                out.sort(Comparator.comparing((Item item) -> !StructureNames.item(item).getString().toLowerCase(Locale.ROOT).startsWith(q)));
            }
            shown = out;
            scroll = 0;
        }

        void render(GuiGraphics g, int mouseX, int mouseY) {
            // Bigger on a big screen, so more of every item in the game shows at once.
            w = Math.min(Math.max(330, width * 2 / 5), width - 40);
            h = Math.min(Math.max(300, height * 3 / 5), height - 40);
            x = (width - w) / 2;
            y = (height - h) / 2;
            g.fill(0, 0, width, height, 0x88000000);
            Gui.panel(g, x, y, w, h);
            g.drawString(font, Component.translatable("screen.justenoughstructures.editor.pick_item"), x + 8, y + 8, Gui.LABEL, false);
            Component cancel = Component.translatable("gui.cancel");
            int cancelW = ui.buttonWidth(cancel);
            boolean overCancel = mouseX >= x + w - 8 - cancelW && mouseX < x + w - 8 && mouseY >= y + 5 && mouseY < y + 19;
            Gui.buttonBackground(g, x + w - 8 - cancelW, y + 5, cancelW, 14, overCancel ? 2 : 1);
            g.drawString(font, cancel, x + w - 8 - cancelW + 5, y + 8, 0xFFFFFFFF, true);
            if (pickerSearch != null) {
                pickerSearch.setX(x + 8);
                pickerSearch.setY(y + 24);
                pickerSearch.setWidth(w - 16);
            }
            gridTop = y + 44;
            gridH = h - 44 - 18;
            columns = Math.max(1, (w - 18) / SLOT);
            Gui.inset(g, x + 7, gridTop - 1, columns * SLOT + 2, gridH + 2, Gui.PANEL);
            int rows = (shown.size() + columns - 1) / columns;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, rows * SLOT - gridH)));
            hovered = null;
            Gui.scissor(g, x + 8, gridTop, x + 8 + columns * SLOT, gridTop + gridH);
            int first = (int) (scroll / SLOT);
            for (int i = first * columns; i < shown.size(); i++) {
                int sx = x + 8 + (i % columns) * SLOT;
                int sy = gridTop + (i / columns) * SLOT - (int) scroll;
                if (sy > gridTop + gridH) {
                    break;
                }
                boolean over = mouseX >= sx && mouseX < sx + SLOT && mouseY >= Math.max(sy, gridTop) && mouseY < Math.min(sy + SLOT, gridTop + gridH);
                if (over) {
                    g.fill(sx, sy, sx + SLOT, sy + SLOT, 0x80FFFFFF);
                    hovered = shown.get(i);
                }
                g.renderItem(new ItemStack(shown.get(i)), sx + 1, sy + 1);
            }
            Gui.endScissor(g);
            Gui.scrollbar(g, x + w - 6, gridTop, gridH, scroll, Math.max(0, rows * SLOT - gridH));
            String count = Component.translatable("screen.justenoughstructures.editor.items_count", shown.size()).getString();
            Gui.fine(g, font, count, x + 8, y + h - 13, Gui.LABEL_SOFT);
        }

        void click(double mouseX, double mouseY) {
            Component cancel = Component.translatable("gui.cancel");
            int cancelW = ui.buttonWidth(cancel);
            if (mouseX >= x + w - 8 - cancelW && mouseX < x + w - 8 && mouseY >= y + 5 && mouseY < y + 19
                    || mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
                closePicker();
                return;
            }
            if (hovered != null) {
                JsonPaths.set(draft, path, new com.google.gson.JsonPrimitive(BuiltInRegistries.ITEM.getKey(hovered).toString()));
                changed();
                closePicker();
            }
        }

        void scroll(double delta) {
            scroll = Math.max(0, scroll - delta * SLOT * 2);
        }
    }

    // ------------------------------------------------------------------ for the screenshot harness

    /** The mod's table and this edit side by side. */
    public void showChanges() {
        if (view != null) {
            Nav.remember();
            minecraft.setScreen(new DiffScreen(this, tableName, view.original(), json()));
        }
    }

    /** Merges what the mod changed into the edit once the table's loaded, for Pack tools' Merge. */
    void mergeWhenLoaded() {
        mergeWhenLoaded = true;
    }

    public void mergeNow() {
        if (view != null) {
            merge();
        }
    }

    /** Picks a pool, and an entry in it or -1 for the pool itself. */
    public void pick(int pool, int entry) {
        select(pool, entry);
    }

    public boolean loaded() {
        return view != null || loadProblem != null;
    }

    /** The JSON view with this text in it. */
    public void showJson(String text) {
        if (!raw) {
            toggleMode();
        }
        if (rawBox != null) {
            rawBox.setValue(text);
        }
    }

    /** Types into the picked entry's item, as if typed in its field. */
    public void typeItem(String text) {
        if (draft != null && selectedPool >= 0 && selectedEntry >= 0) {
            JsonPaths.set(draft, "pools." + selectedPool + ".entries." + selectedEntry + ".name", new com.google.gson.JsonPrimitive(text));
            changed();
        }
    }

    /** Opens the item picker on the picked entry. */
    public void openItemPicker() {
        if (selectedPool >= 0 && selectedEntry >= 0) {
            pickItem("pools." + selectedPool + ".entries." + selectedEntry + ".name");
        }
    }

    /** Where a button or link with this label was last drawn, or null. */
    public int[] buttonAt(String label) {
        return ui.centre(label);
    }

    /** Where the field or list box for a path in the table was last drawn, or null. */
    public int[] fieldAt(String path) {
        int[] rect = fieldRects.get(path);
        return rect == null ? null : new int[]{rect[0] + rect[2] / 2, rect[1] + rect[3] / 2};
    }

    /** Where an option is in the open list of choices, or null. */
    public int[] optionAt(String option) {
        return choice == null ? null : choice.centre(option);
    }

    public void saveNow() {
        if (view != null) {
            save(false);
        }
    }
}
