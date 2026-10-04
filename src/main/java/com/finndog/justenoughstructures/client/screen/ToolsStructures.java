package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Every structure, by mod, with a switch to show or hide each one or a whole mod, and for the one
 * picked, what players are told about it and everything its definition says.
 */
final class ToolsStructures extends ToolsSection {
    private static final int LIST_ROW = 20;
    private static final int HEADER = 14;

    private final Scroller list = new Scroller();
    private final Scroller detail = new Scroller();
    private EditBox search;
    private String query = "";
    private ResourceLocation selected;
    /**
     * Whether the list still scrolls to the structure shown. It keeps doing so, as structures arrive
     * and the screen changes size, until the list is scrolled or searched by hand.
     */
    private boolean revealSelected;
    /** Whether the list and the structure were last shown one at a time, rather than side by side. */
    private boolean single;
    private MultiLineEditBox notesBox;
    /** How tall the notes box is: taller on a taller screen, shorter where room is short. */
    private int notesHeight = 46;
    /** The notes as typed, the structure they're for, and whether they differ from what's saved. */
    private String notes = "";
    private ResourceLocation notesFor;
    private boolean notesChanged;

    ToolsStructures(PackToolsScreen screen) {
        super(screen);
    }

    @Override
    String count() {
        PackToolsState state = screen.state();
        if (state == null) {
            return "";
        }
        int hidden = state.settings().hiddenStructures().size() + state.settings().hiddenMods().size();
        return hidden == 0 ? "" : Component.translatable("screen.justenoughstructures.tools.count_hidden", hidden).getString();
    }

    @Override
    Object selection() {
        return selected;
    }

    @Override
    void select(Object selection) {
        selected = selection instanceof ResourceLocation id ? id : null;
        // Even with none picked, the browser's structure is shown, and found in the list.
        revealSelected = true;
        detail.reset();
    }

    @Override
    Object parse(String text) {
        return ResourceLocation.tryParse(text);
    }

    @Override
    void stateChanged() {
        // What's saved now, unless something different is being typed.
        if (!notesChanged) {
            notesFor = null;
        }
    }

    @Override
    void leaving() {
        saveNotes();
    }

    /** The picked structure, or the browser's if none is picked yet. */
    private ResourceLocation shownStructure() {
        if (selected != null) {
            return selected;
        }
        JesScreen browser = screen.browser();
        return browser == null ? null : browser.selectedStructure();
    }

    @Override
    void init(int x, int y, int w, int h) {
        single = oneAtATime(w);
        revealSelected = true;
        search = null;
        if (!single || selected == null) {
            search = screen.add(new EditBox(font, x + 1, y + 1, listWidth(w) - 2, 14,
                    Component.translatable("screen.justenoughstructures.tools.search_structures")));
            search.setMaxLength(256);
            search.setHint(Component.translatable("screen.justenoughstructures.tools.search_structures").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(query);
            search.setResponder(text -> {
                query = text;
                revealSelected = false;
                list.reset();
            });
        }
        // Shown on its own, a structure is only shown once it's picked.
        ResourceLocation shown = single ? selected : shownStructure();
        notesBox = null;
        notesHeight = Math.max(28, Math.min(64, h / 5));
        if (shown != null && screen.state() != null) {
            // Put in place, and shown, as it's drawn.
            notesBox = screen.add(new MultiLineEditBox(font, x, y, detailWidth(w) - 8 - 10, notesHeight,
                    Component.translatable("screen.justenoughstructures.tools.notes_hint"), Component.translatable("screen.justenoughstructures.tools.notes")));
            notesBox.visible = false;
            loadNotes(shown);
            notesBox.setValue(notes);
            notesBox.setValueListener(text -> {
                if (!text.equals(notes)) {
                    notes = text;
                    notesChanged = true;
                }
            });
        }
    }

    private void loadNotes(ResourceLocation structure) {
        if (structure.equals(notesFor)) {
            return;
        }
        StructureInfo info = screen.state().info(structure);
        notes = info.notes() == null ? "" : info.notes().getString();
        notesFor = structure;
        notesChanged = false;
    }

    private void saveNotes() {
        if (notesChanged && notesFor != null && screen.state() != null) {
            ResourceLocation structure = notesFor;
            notesChanged = false;
            ClientRequests.saveStructure(structure, notes, screen.state().info(structure).hideLootLocations())
                    .thenAccept(reply -> screen.replied(reply, "tools.structure_saved"));
        }
    }

    /** Types notes as if into the box, for the screenshot harness. */
    void typeNotes(String text) {
        if (notesBox != null) {
            notesBox.setValue(text);
        }
    }

    @Override
    void tick() {
        if (search != null) {
            search.tick();
        }
        if (notesBox != null) {
            notesBox.tick();
        }
        ResourceLocation shown = shownStructure();
        if (shown != null && !shown.equals(notesFor) && screen.state() != null && notesBox != null) {
            saveNotes();
            loadNotes(shown);
            notesBox.setValue(notes);
        }
    }

    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        // Shown one at a time, only the one on show scrolls.
        if ((!single || selected == null) && list.scroll(mouseX, mouseY, delta)) {
            revealSelected = false;
            return true;
        }
        return (!single || selected != null) && detail.scroll(mouseX, mouseY, delta);
    }

    /** The list's width: all of it when it's shown on its own. */
    private static int listWidth(int w) {
        return oneAtATime(w) ? w : Math.max(140, Math.min(260, w * 40 / 100));
    }

    /** The picked structure's width: all of it when it's shown on its own, and no wider than reads well. */
    private static int detailWidth(int w) {
        return oneAtATime(w) ? w : Math.min(w - listWidth(w) - 6, READABLE);
    }

    /** Every structure the server has, players' and hidden ones, by id. */
    private Map<ResourceLocation, StructureCatalog.Entry> all() {
        Map<ResourceLocation, StructureCatalog.Entry> out = new LinkedHashMap<>();
        screen.catalog().forEach(e -> out.put(e.id(), e));
        screen.state().hidden().forEach(e -> out.put(e.id(), e));
        return out;
    }

    @Override
    void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY) {
        PackToolsState state = screen.state();
        ServerConfig.Settings settings = state.settings();
        Map<ResourceLocation, StructureCatalog.Entry> all = all();
        single = oneAtATime(w);
        if (single && selected != null) {
            int cy = backToList(g, ui, x, y);
            showDetail(g, ui, x, cy, w, y + h - cy, all.get(selected), settings, state);
            return;
        }
        int leftW = listWidth(w);
        int listTop = y + 19;
        Gui.inset(g, x, listTop, leftW, y + h - listTop, Gui.PANEL);

        // Grouped by mod, each mod's name with a switch for all of it.
        TreeMap<String, List<StructureCatalog.Entry>> byMod = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (StructureCatalog.Entry e : all.values()) {
            String name = StructureNames.structure(e.id());
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || e.id().toString().contains(q)) {
                byMod.computeIfAbsent(StructureNames.mod(e.id().getNamespace()), m -> new ArrayList<>()).add(e);
            }
        }
        ResourceLocation shown = shownStructure();
        int top = list.begin(g, ui, x + 1, listTop + 1, leftW - 2, y + h - listTop - 2);
        int rw = list.width();
        int cy = top;
        for (Map.Entry<String, List<StructureCatalog.Entry>> mod : byMod.entrySet()) {
            String namespace = mod.getValue().get(0).id().getNamespace();
            boolean modHidden = settings.hiddenMods().contains(namespace);
            g.fill(x + 1, cy, x + 1 + rw, cy + HEADER - 1, Gui.BAND);
            Gui.drawClipped(g, font, mod.getKey(), x + 4, cy + 3, rw - 30, 0xFFFFFFFF, true);
            ui.toggle(g, x + rw - 19, cy + 2, !modHidden, () -> save(ToolsRules.hideMod(settings, namespace, !modHidden)),
                    Component.translatable(modHidden ? "screen.justenoughstructures.tools.show_mod" : "screen.justenoughstructures.tools.hide_mod"));
            cy += HEADER;
            mod.getValue().sort(Comparator.comparing(e -> StructureNames.structure(e.id())));
            for (StructureCatalog.Entry e : mod.getValue()) {
                if (revealSelected && e.id().equals(shown)) {
                    list.reveal(cy - top, cy - top + LIST_ROW);
                }
                if (cy + LIST_ROW >= listTop && cy <= y + h) {
                    structureRow(g, ui, x + 1, cy, rw, e, settings, state, e.id().equals(shown));
                }
                cy += LIST_ROW;
            }
        }
        if (byMod.isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.no_match").getString(), x + 4, cy + 4, Gui.LABEL_SOFT);
            cy += 16;
        }
        list.end(g, ui, cy - top);
        if (single) {
            return;
        }

        showDetail(g, ui, x + leftW + 6, y, detailWidth(w), h, shown == null ? null : all.get(shown), settings, state);
    }

    private void showDetail(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, StructureCatalog.Entry entry, ServerConfig.Settings settings,
                            PackToolsState state) {
        if (entry == null) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.pick_structure"), x, y + 4, w, Gui.LABEL_SOFT);
            if (notesBox != null) {
                notesBox.visible = false;
            }
            return;
        }
        detail(g, ui, x, y, w, h, entry, settings, state);
    }

    private void structureRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, StructureCatalog.Entry e, ServerConfig.Settings settings,
                              PackToolsState state, boolean isSelected) {
        boolean hidden = settings.hides(e.id());
        if (isSelected) {
            g.fill(x, y, x + w, y + LIST_ROW - 1, 0xFF9D9D9D);
        } else if (ui.hovered(x, y, w, LIST_ROW - 1)) {
            g.fill(x, y, x + w, y + LIST_ROW - 1, Gui.ROW_HOVER);
        }
        ui.spot(x, y, w, LIST_ROW - 1, () -> screen.pick(e.id()));
        Icon.structure(e.id()).draw(g, x + 2, y + 1);
        StructureInfo info = state.info(e.id());
        List<String> marks = new ArrayList<>();
        if (info.notes() != null) {
            marks.add("notes");
        }
        if (info.hideLootLocations()) {
            marks.add("secret");
        }
        int right = x + w - 22;
        for (int i = marks.size() - 1; i >= 0; i--) {
            String mark = Component.translatable("screen.justenoughstructures.tools.mark." + marks.get(i)).getString();
            right -= Gui.fineWidth(font, mark);
            Gui.fine(g, font, mark, right, y + 7, isSelected ? 0xFFFFFFFF : marks.get(i).equals("notes") ? ToolsUi.GOOD : ToolsUi.CHANGED);
            right -= 4;
        }
        String name = StructureNames.structure(e.id());
        int colour = isSelected ? 0xFFFFFFFF : hidden ? 0xFF8A8A8A : ToolsUi.TEXT;
        Gui.drawClipped(g, font, hidden ? "§o" + name : name, x + 21, y + 6, right - x - 22, colour, isSelected);
        boolean modHidden = settings.hiddenMods().contains(e.id().getNamespace());
        ui.toggle(g, x + w - 19, y + 5, !hidden, modHidden ? null : () -> save(ToolsRules.hideStructure(settings, e.id(), !hidden)),
                Component.translatable(modHidden ? "screen.justenoughstructures.tools.hidden_by_mod"
                        : hidden ? "screen.justenoughstructures.tools.shown_off" : "screen.justenoughstructures.tools.shown_on"));
        g.fill(x, y + LIST_ROW - 1, x + w, y + LIST_ROW, 0xFFB8B8B8);
    }

    /** The picked structure: a way to see it, what players are told, and its definition. */
    private void detail(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, StructureCatalog.Entry entry, ServerConfig.Settings settings,
                        PackToolsState state) {
        ResourceLocation id = entry.id();
        boolean hidden = settings.hides(id);
        int cy = y + row(g, ui, x, y, w, Icon.structure(id), StructureNames.structure(id), null, 0, StructureNames.mod(id.getNamespace()),
                List.of(new RowButton(Component.translatable("screen.justenoughstructures.tools.open_in_browser"),
                        hidden || screen.browser() == null ? null : () -> screen.showInBrowser(id, null),
                        hidden ? Component.translatable("screen.justenoughstructures.tools.open_hidden") : null)), null, 0, false) + 4;

        // What players are told, then what its definition says, scrolling together. There's always room
        // kept for the scroll bar, as the notes box can't change how it wraps once it's made.
        int top = detail.begin(g, ui, x, cy, w, y + h - cy);
        int cw = w - 8;
        int end = forPlayers(g, ui, x, top, cw, id, settings, state, cy, y + h);
        end = ui.heading(g, Component.translatable("screen.justenoughstructures.tools.advanced"), x, end + 6, cw);
        end = advanced(g, x, end, cw, entry);
        detail.end(g, ui, end - top);
    }

    /** What players are told and shown, in a box of its own. Returns the y below it. */
    private int forPlayers(GuiGraphics g, ToolsUi ui, int x, int y, int w, ResourceLocation id, ServerConfig.Settings settings, PackToolsState state,
                           int viewTop, int viewBottom) {
        boolean hidden = settings.hides(id);
        boolean modHidden = settings.hiddenMods().contains(id.getNamespace());
        StructureInfo info = state.info(id);
        Component shownLabel = Component.translatable("screen.justenoughstructures.tools.shown");
        Component secretLabel = Component.translatable("screen.justenoughstructures.tools.secret");
        Component save = Component.translatable("screen.justenoughstructures.tools.save_notes");
        int saveW = ui.buttonWidth(save);
        String status = notesChanged ? Component.translatable("screen.justenoughstructures.tools.notes_unsaved").getString()
                : info.notes() == null ? ""
                : Component.translatable(state.structures().getOrDefault(id, new PackToolsState.Written(info, false)).fromPack()
                ? "screen.justenoughstructures.tools.notes_shown" : "screen.justenoughstructures.tools.notes_from_mod").getString();
        List<FormattedCharSequence> statusLines = status.isEmpty() ? List.of()
                : font.split(Component.literal(status), (int) (Math.max(20, w - saveW - 16) / Gui.fineScale()));
        int lineH = Gui.fineLine(font) + 1;

        // Where everything goes, worked out first so the box can be drawn behind it.
        int checkW = w - 10;
        int shownY = y + 16;
        int secretY = shownY + ui.checkHeight(shownLabel, checkW) + 2;
        int labelY = secretY + ui.checkHeight(secretLabel, checkW) + 3;
        int notesY = labelY + 9;
        int saveY = notesY + notesHeight + 2;
        int boxH = saveY + Math.max(ToolsUi.BUTTON, statusLines.size() * lineH) + 4 - y;

        g.fill(x, y, x + w, y + boxH, 0xFF9A9A9A);
        g.fill(x + 1, y + 1, x + w - 1, y + boxH - 1, 0xFFE8E8E8);
        Gui.drawClipped(g, font, Component.translatable("screen.justenoughstructures.tools.for_players").getString(), x + 5, y + 4, w - 10, ToolsUi.TEXT, false);
        ui.wrappedCheck(g, shownLabel, x + 5, shownY, checkW, !hidden,
                modHidden ? null : () -> save(ToolsRules.hideStructure(settings, id, !hidden)),
                Component.translatable(modHidden ? "screen.justenoughstructures.tools.hidden_by_mod" : "screen.justenoughstructures.tools.shown_hint"));
        ui.wrappedCheck(g, secretLabel, x + 5, secretY, checkW, info.hideLootLocations(), () -> {
            String text = id.equals(notesFor) ? notes : info.notes() == null ? "" : info.notes().getString();
            if (id.equals(notesFor)) {
                notesChanged = false;
            }
            ClientRequests.saveStructure(id, text, !info.hideLootLocations()).thenAccept(reply -> screen.replied(reply, "tools.structure_saved"));
        }, Component.translatable("screen.justenoughstructures.tools.secret_hint"));
        Gui.fineClipped(g, font, Component.translatable("screen.justenoughstructures.tools.notes").getString(), x + 5, labelY, w - 10, Gui.LABEL_SOFT);
        placeNotes(g, x + 5, notesY, w - 10, viewTop, viewBottom);
        ui.button(g, save, x + w - 5 - saveW, saveY, notesChanged, this::saveNotes);
        int sy = saveY + 3;
        for (FormattedCharSequence line : statusLines) {
            Gui.scaled(g, font, line, x + 5, sy, notesChanged ? ToolsUi.CHANGED : Gui.LABEL_SOFT, Gui.fineScale());
            sy += lineH;
        }
        return y + boxH;
    }

    /**
     * Puts the notes box where it's drawn as the detail scrolls. It's a text box only while all of it
     * is in view; while only some is, a picture of it stands in, cut off at the edge like the rest.
     */
    private void placeNotes(GuiGraphics g, int x, int y, int w, int viewTop, int viewBottom) {
        if (notesBox == null) {
            return;
        }
        notesBox.setX(x);
        notesBox.setY(y);
        notesBox.setWidth(w);
        notesBox.visible = y >= viewTop && y + notesHeight <= viewBottom;
        if (notesBox.visible || y + notesHeight <= viewTop || y >= viewBottom) {
            return;
        }
        g.fill(x, y, x + w, y + notesHeight, 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + notesHeight - 1, 0xFF000000);
        Gui.scissor(g, x + 1, y + 1, x + w - 1, y + notesHeight - 1);
        Component text = notes.isEmpty() ? Component.translatable("screen.justenoughstructures.tools.notes_hint") : Component.literal(notes);
        int ly = y + 4;
        for (FormattedCharSequence line : font.split(text, w - 8)) {
            g.drawString(font, line, x + 4, ly, notes.isEmpty() ? 0xCCE0E0E0 : 0xFFE0E0E0, false);
            ly += font.lineHeight;
        }
        Gui.endScissor(g);
    }

    /** What the structure's definition and its sets say, as the browser's details do. Returns the y below. */
    private int advanced(GuiGraphics g, int x, int y, int w, StructureCatalog.Entry entry) {
        List<String[]> rows = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        titles.add("structure");
        rows.add(null);
        rows.add(detail("id", entry.id().toString()));
        rows.add(detail("type", entry.type() == null ? "?" : entry.type().toString()));
        JsonObject def = entry.definition();
        if (def != null) {
            rows.add(detail("step", string(def.get("step"))));
            rows.add(detail("biomes", string(def.get("biomes"))));
            if (def.has("start_pool")) {
                rows.add(detail("start_pool", string(def.get("start_pool"))));
            }
            if (def.has("size")) {
                rows.add(detail("depth", string(def.get("size"))));
            }
            rows.add(detail("terrain", def.has("terrain_adaptation") ? string(def.get("terrain_adaptation")) : "none"));
        }
        for (StructureCatalog.SetInfo set : entry.sets()) {
            titles.add("placement");
            rows.add(null);
            rows.add(detail("set", set.setId().toString()));
            JsonObject p = set.placement();
            if (p != null) {
                rows.add(detail("type", string(p.get("type"))));
                for (String key : List.of("spacing", "separation", "salt", "frequency", "distance", "count")) {
                    if (p.has(key)) {
                        rows.add(detail(key, string(p.get(key))));
                    }
                }
            }
        }
        int labelW = 0;
        for (String[] r : rows) {
            if (r != null) {
                labelW = Math.max(labelW, Gui.fineWidth(font, r[0]));
            }
        }
        int line = Gui.fineLine(font) + 2;
        int title = 0;
        for (String[] r : rows) {
            if (r == null) {
                String heading = Component.translatable("screen.justenoughstructures.detail." + titles.get(title++)).getString();
                g.drawString(font, heading, x, y + 2, ToolsUi.TEXT, false);
                g.fill(x, y + 11, x + w, y + 12, 0x30000000);
                y += 14;
                continue;
            }
            Gui.fine(g, font, r[0], x + 2, y, Gui.LABEL_SOFT);
            Gui.fineClipped(g, font, r[1], x + labelW + 8, y, w - labelW - 10, ToolsUi.TEXT);
            y += line;
        }
        return y + 4;
    }

    private static String[] detail(String key, String value) {
        return new String[]{Component.translatable("screen.justenoughstructures.detail." + key).getString(), value};
    }

    private static String string(JsonElement e) {
        if (e == null) {
            return "?";
        }
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private void save(ServerConfig.Settings settings) {
        ClientRequests.saveRules(settings).thenAccept(reply -> screen.replied(reply, "tools.rules_saved"));
    }
}
