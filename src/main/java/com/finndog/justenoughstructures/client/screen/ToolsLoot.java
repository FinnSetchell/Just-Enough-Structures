package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Every loot table: those the structures use, those edited, or all of them, and for the one picked,
 * how it stands, where it's used and what it gives.
 */
final class ToolsLoot extends ToolsSection {
    private static final int LIST_ROW = 20;

    private enum Filter { USED, EDITED, ALL }

    private final Scroller list = new Scroller();
    private final Scroller detail = new Scroller();
    private EditBox search;
    private String query = "";
    private Filter filter = Filter.USED;
    private ResourceLocation selected;
    private List<ResourceLocation> shown = List.of();
    private String shownFor;
    private LootOdds odds;
    private ResourceLocation oddsFor;
    private LootOdds.Row hoveredRow;
    private float hoveredChance;

    ToolsLoot(PackToolsScreen screen) {
        super(screen);
    }

    @Override
    String count() {
        PackToolsState state = screen.state();
        return state == null || state.overrides().isEmpty() ? "" : String.valueOf(state.overrides().size());
    }

    @Override
    Object selection() {
        return selected;
    }

    @Override
    void select(Object selection) {
        selected = selection instanceof ResourceLocation id ? id : null;
        detail.reset();
        if (selected != null) {
            // Opened on a table: listed whichever filter it would need.
            if (!matches(selected, filter)) {
                filter = Filter.ALL;
            }
            shownFor = null;
            revealSelected = true;
        }
    }

    private boolean revealSelected;

    @Override
    Object parse(String text) {
        return ResourceLocation.tryParse(text);
    }

    @Override
    void stateChanged() {
        shownFor = null;
    }

    @Override
    void init(int x, int y, int w, int h) {
        search = screen.add(new EditBox(font, x + 1, y + 1, leftWidth(w) - 2, 14, Component.translatable("screen.justenoughstructures.tools.search_tables")));
        search.setMaxLength(256);
        search.setHint(Component.translatable("screen.justenoughstructures.tools.search_tables").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            if (!text.equals(query)) {
                query = text;
                shownFor = null;
                list.reset();
            }
        });
    }

    @Override
    void tick() {
        if (search != null) {
            search.tick();
        }
    }

    @Override
    boolean scroll(double mouseX, double mouseY, double delta) {
        return list.scroll(mouseX, mouseY, delta) || detail.scroll(mouseX, mouseY, delta);
    }

    private static int leftWidth(int w) {
        return Math.max(130, Math.min(220, w * 38 / 100));
    }

    private boolean matches(ResourceLocation table, Filter f) {
        PackToolsState state = screen.state();
        return switch (f) {
            case ALL -> true;
            case EDITED -> state != null && state.overrides().containsKey(table);
            case USED -> FoundIn.allTables().contains(table) || state != null && state.overrides().containsKey(table) && !state.tables().contains(table);
        };
    }

    /** The tables the filter and search leave, worked out again only when either changes. */
    private List<ResourceLocation> shown() {
        String key = filter + "|" + query + "|" + FoundIn.ready();
        if (key.equals(shownFor)) {
            return shown;
        }
        PackToolsState state = screen.state();
        Set<ResourceLocation> all = new LinkedHashSet<>(state.tables());
        all.addAll(state.overrides().keySet());
        all.addAll(FoundIn.allTables());
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation table : all) {
            if (matches(table, filter) && (q.isEmpty() || table.toString().contains(q)
                    || StructureNames.lootTable(table.toString()).toLowerCase(Locale.ROOT).contains(q))) {
                out.add(table);
            }
        }
        out.sort(Comparator.comparing((ResourceLocation t) -> StructureNames.lootTable(t.toString())).thenComparing(ResourceLocation::toString));
        shown = out;
        shownFor = key;
        return out;
    }

    @Override
    void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY) {
        int leftW = leftWidth(w);
        // The filters, and a new table, under the search box.
        int fy = y + 19;
        int fx = x;
        for (Filter f : Filter.values()) {
            Filter which = f;
            fx += ui.chip(g, Component.translatable("screen.justenoughstructures.tools.filter." + f.name().toLowerCase(Locale.ROOT)).getString(),
                    fx, fy, filter == f, () -> {
                        filter = which;
                        shownFor = null;
                        list.reset();
                    }) + 2;
        }
        Component newLink = Component.translatable("screen.justenoughstructures.tools.new_table");
        ui.link(g, newLink, x + leftW - 2 - Gui.fineWidth(font, newLink.getString()), fy + 2, true, screen::newTable);

        int listTop = fy + ui.chipHeight() + 4;
        Gui.inset(g, x, listTop, leftW, y + h - listTop, Gui.PANEL);
        List<ResourceLocation> tables = shown();
        int top = list.begin(g, ui, x + 1, listTop + 1, leftW - 2, y + h - listTop - 2);
        int rw = list.width();
        int ry = top;
        PackToolsState state = screen.state();
        for (ResourceLocation table : tables) {
            if (revealSelected && table.equals(selected)) {
                list.reveal(ry - top, ry - top + LIST_ROW);
                revealSelected = false;
            }
            if (ry + LIST_ROW >= listTop && ry <= y + h) {
                listRow(g, ui, x + 1, ry, rw, table, state.overrides().get(table));
            }
            ry += LIST_ROW;
        }
        if (tables.isEmpty()) {
            Component none = Component.translatable(FoundIn.ready() || filter != Filter.USED ? "screen.justenoughstructures.tools.no_match"
                    : "screen.justenoughstructures.indexing");
            Gui.fineWrapped(g, font, none, x + 4, ry + 3, rw - 8, Gui.LABEL_SOFT);
            ry += 20;
        }
        list.end(g, ui, ry - top);

        int dx = x + leftW + 6;
        int dw = w - leftW - 6;
        hoveredRow = null;
        if (selected == null) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.tools.pick_table"), dx, y + 4, dw, Gui.LABEL_SOFT);
            return;
        }
        detail(g, ui, dx, y, dw, h, state);
    }

    private void listRow(GuiGraphics g, ToolsUi ui, int x, int y, int w, ResourceLocation table, LootOverrides.Status status) {
        boolean isSelected = table.equals(selected);
        if (isSelected) {
            g.fill(x, y, x + w, y + LIST_ROW - 1, 0xFF9D9D9D);
        } else if (ui.hovered(x, y, w, LIST_ROW - 1)) {
            g.fill(x, y, x + w, y + LIST_ROW - 1, Gui.ROW_HOVER);
        }
        ui.spot(x, y, w, LIST_ROW - 1, () -> screen.pick(table));
        Component mark = editedMark(status);
        String markText = mark == null ? "" : mark.getString();
        int markW = markText.isEmpty() ? 0 : Gui.fineWidth(font, markText) + 4;
        g.drawString(font, Gui.clip(font, StructureNames.lootTable(table.toString()), w - 6 - markW), x + 3, y + 1,
                isSelected ? 0xFFFFFFFF : ToolsUi.TEXT, isSelected);
        if (!markText.isEmpty()) {
            Gui.fine(g, font, markText, x + w - 3 - Gui.fineWidth(font, markText), y + 2, isSelected ? 0xFFFFFFFF : markColour(status));
        }
        Gui.fine(g, font, Gui.fineClip(font, table.toString(), w - 6), x + 3, y + 11, isSelected ? 0xFFDDDDDD : Gui.LABEL_SOFT);
        g.fill(x, y + LIST_ROW - 1, x + w, y + LIST_ROW, 0xFFB0B0B0);
    }

    /** The picked table: what it is, how it stands, where it's used, and every item's chance. */
    private void detail(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, PackToolsState state) {
        ResourceLocation table = selected;
        LootOverrides.Status status = state.overrides().getOrDefault(table, LootOverrides.Status.NONE);
        List<RowButton> buttons = new ArrayList<>();
        buttons.add(RowButton.of("tools.edit", () -> screen.openEditor(table, false)));
        if (status != LootOverrides.Status.NONE) {
            buttons.add(new RowButton(Component.translatable("screen.justenoughstructures.tools.remove_edit"), () -> screen.removeEdit(table),
                    Component.translatable("screen.justenoughstructures.editor.remove_hint")));
        }
        int cy = y + row(g, ui, x, y, w, Icon.item(CHEST), StructureNames.lootTable(table.toString()), null, 0, table.toString(), buttons, null, 0, false) + 2;

        boolean waiting = screen.waiting(PackToolsState.tableKey(table));
        Component text = Component.translatable("screen.justenoughstructures.editor.status." + status.name().toLowerCase(Locale.ROOT));
        if (waiting) {
            text = text.copy().append(" ").append(Component.translatable("screen.justenoughstructures.tools.waiting_this"));
        }
        int kind = switch (status) {
            case NONE -> 0;
            case ACTIVE -> 1;
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> 2;
            case BROKEN -> 3;
        };
        cy += ui.status(g, text, x, cy, w, kind) + 2;
        if (status == LootOverrides.Status.ORIGINAL_CHANGED) {
            int lx = x + 2;
            lx += ui.link(g, Component.translatable("screen.justenoughstructures.editor.see_changes"), lx, cy, false, () -> screen.showChanges(table)) + 8;
            lx += ui.link(g, Component.translatable("screen.justenoughstructures.editor.merge"), lx, cy, false, () -> screen.openEditor(table, true),
                    Component.translatable("screen.justenoughstructures.editor.merge_hint")) + 8;
            ui.link(g, Component.translatable("screen.justenoughstructures.editor.keep"), lx, cy, false, () -> screen.keep(table),
                    Component.translatable("screen.justenoughstructures.editor.keep_hint"));
            cy += font.lineHeight + 4;
        }

        // Where it's used, each opening that structure in the browser on this table.
        Set<ResourceLocation> used = FoundIn.structuresUsing(table);
        Gui.fine(g, font, Component.translatable(used.isEmpty() ? "screen.justenoughstructures.tools.used_in_none"
                : "screen.justenoughstructures.tools.used_in").getString(), x, cy, Gui.LABEL_SOFT);
        cy += Gui.fineLine(font) + 2;
        int cx = x;
        int count = 0;
        List<ResourceLocation> sorted = new ArrayList<>(used);
        sorted.sort(Comparator.comparing(StructureNames::structure));
        for (ResourceLocation structure : sorted) {
            if (count == 14) {
                Gui.fine(g, font, Component.translatable("screen.justenoughstructures.and_more", sorted.size() - 14).getString(), cx, cy + 2, Gui.LABEL_SOFT);
                break;
            }
            String name = StructureNames.structure(structure);
            int chipW = Gui.fineWidth(font, name) + 6;
            if (cx > x && cx + chipW > x + w) {
                cx = x;
                cy += ui.chipHeight() + 2;
            }
            cx += ui.chip(g, name, cx, cy, false, () -> screen.showInBrowser(structure, table.toString()),
                    Component.literal(name), Component.translatable("screen.justenoughstructures.tools.open_in_browser").withStyle(ChatFormatting.YELLOW)) + 2;
            count++;
        }
        cy += (used.isEmpty() ? 0 : ui.chipHeight()) + 6;

        Gui.band(g, font, Component.translatable("screen.justenoughstructures.tools.chance_each").getString(), x, cy, w, 13);
        cy += 15;
        if (!table.equals(oddsFor)) {
            oddsFor = table;
            odds = null;
            ClientRequests.odds(table).thenAccept(o -> {
                if (table.equals(oddsFor)) {
                    odds = o;
                }
            });
        }
        int top = detail.begin(g, ui, x, cy, w, y + h - cy);
        int ow = detail.width();
        int oy = top;
        if (odds == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.rolling").getString(), x + 2, oy + 2, Gui.LABEL_SOFT);
            oy += 14;
        } else if (odds.rows().isEmpty()) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.tools.gives_nothing").getString(), x + 2, oy + 2, Gui.LABEL_SOFT);
            oy += 14;
        } else {
            List<LootOdds.Row> rows = OddsList.sorted(odds, false);
            Map<LootOdds.Row, String> names = OddsList.names(rows);
            for (LootOdds.Row r : rows) {
                float chance = (float) r.hits() / Math.max(1, odds.rolls());
                boolean over = ui.hovered(x, oy, ow, OddsList.ROW);
                if (oy + OddsList.ROW >= cy && oy <= y + h) {
                    OddsList.drawRow(g, font, r.example(), names.get(r), OddsList.counts(r), chance, x, oy, x + ow, over);
                }
                if (over) {
                    hoveredRow = r;
                    hoveredChance = chance;
                }
                oy += OddsList.ROW;
            }
        }
        detail.end(g, ui, oy - top);
    }

    @Override
    void renderOver(GuiGraphics g, ToolsUi ui, int mouseX, int mouseY) {
        if (hoveredRow != null) {
            List<Component> lines = new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), hoveredRow.example()));
            lines.addAll(OddsList.tooltip(hoveredRow, hoveredChance, 1, Component.translatable("screen.justenoughstructures.container").getString()));
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            g.pose().popPose();
        }
    }
}
