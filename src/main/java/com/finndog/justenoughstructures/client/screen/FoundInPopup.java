package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Lists the structures whose loot can give an item, like pressing U on an item in JEI. */
final class FoundInPopup {
    static final int WIDTH = 230;
    private static final int ROW = 22;
    private static final int VISIBLE_ROWS = 7;
    private static final ItemStack STRUCTURE_ICON = new ItemStack(Items.FILLED_MAP);
    /** How long the list waits for every chance before showing anyway. */
    private static final long SORT_WAIT_MILLIS = 3000;

    record Row(ResourceLocation structure, Set<ResourceLocation> tables) {
    }

    final ItemStack item;
    private List<Row> rows;
    private boolean sorted;
    private long listedAt;
    /** The index the rows were listed from, as {@link FoundIn#changes()} had it. */
    private int listedFrom;
    // One row a notch. Stored in pixels like any list, and read back as whole rows.
    private final Scroll scroll = new Scroll(ROW);
    int x;
    int y;

    FoundInPopup(ItemStack item) {
        this.item = item.copyWithCount(1);
    }

    int height() {
        return 30 + VISIBLE_ROWS * ROW + 14;
    }

    void place(int screenWidth, int screenHeight) {
        x = (screenWidth - WIDTH) / 2;
        y = Math.max(4, (screenHeight - height()) / 2);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height();
    }

    /** The rows, best chance first, or null until they're ready to show. */
    private List<Row> rows() {
        if (rows != null && listedFrom != FoundIn.changes()) {
            // The loot index changed (for example the full index replaced the early one), so rebuild the list.
            rows = null;
            sorted = false;
        }
        if (rows == null && FoundIn.ready()) {
            listedFrom = FoundIn.changes();
            rows = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : FoundIn.structuresFor(item.getItem()).entrySet()) {
                rows.add(new Row(e.getKey(), e.getValue()));
                e.getValue().forEach(ClientRequests::odds);
            }
            rows.sort(Comparator.comparing(r -> StructureNames.structure(r.structure())));
            listedAt = Util.getMillis();
        }
        // The list only shows once it's sorted, so it never moves under the cursor. A long one shows
        // after a moment anyway, with what's known so far first; the rest fill in where they are.
        if (rows != null && !sorted && (known() == rows.size() || Util.getMillis() - listedAt > SORT_WAIT_MILLIS)) {
            rows.sort(Comparator.comparingDouble((Row r) -> -chance(r)).thenComparing(r -> StructureNames.structure(r.structure())));
            sorted = true;
        }
        return sorted ? rows : null;
    }

    /** How many rows' chances are known. */
    private int known() {
        return (int) rows.stream().filter(r -> chance(r) >= 0).count();
    }

    private double chance(Row row) {
        return FoundIn.chance(item.getItem(), row.tables());
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Gui.panel(g, x, y, WIDTH, height());
        Gui.slot(g, x + 7, y + 6);
        g.renderItem(item, x + 8, y + 7);
        Gui.drawClipped(g, font, item.getHoverName().getString(), x + 30, y + 7, WIDTH - 40, Gui.label(), false);

        List<Row> list = rows();
        int top = y + 30;
        if (list == null) {
            Component text = rows != null ? Component.translatable("screen.justenoughstructures.found_in_working", known(), rows.size())
                    : ClientRequests.indexWaitText();
            Gui.wrapped(g, font, text, x + 8, top + 4, WIDTH - 16, Gui.labelSoft());
            return;
        }
        // With an early index the list can still grow, so it says how far the server has got with the
        // rest, or that the rest couldn't be found.
        boolean filling = ClientRequests.indexFilling();
        boolean cutShort = ClientRequests.indexCutShort();
        int percent = filling ? Math.round(ClientRequests.indexProgress() * 100) : 0;
        Component under = filling ? Component.translatable("screen.justenoughstructures.found_in_so_far", list.size(), percent)
                : cutShort ? Component.translatable("screen.justenoughstructures.found_in_cut_short", list.size())
                : Component.translatable("screen.justenoughstructures.found_in_chance", list.size());
        Gui.smallClipped(g, font, under.getString(), x + 30, y + 17, WIDTH - 38, Gui.labelSoft());
        Gui.inset(g, x + 6, top - 1, WIDTH - 12, VISIBLE_ROWS * ROW + 2, Gui.Tone.WELL);
        if (list.isEmpty()) {
            Component nowhere = filling ? Component.translatable("screen.justenoughstructures.found_nowhere_yet", percent)
                    : cutShort ? Component.translatable("screen.justenoughstructures.found_nowhere_cut_short")
                    : Component.translatable("screen.justenoughstructures.found_nowhere");
            Gui.wrapped(g, font, nowhere, x + 10, top + 4, WIDTH - 20, Gui.labelSoft());
        }
        scroll.fit(VISIBLE_ROWS * ROW, list.size() * ROW);
        int first = first();
        int room = scroll.scrolls() ? 7 : 0;
        for (int i = 0; i < VISIBLE_ROWS && first + i < list.size(); i++) {
            Row row = list.get(first + i);
            int ry = top + i * ROW;
            boolean hovered = mouseX >= x + 7 && mouseX < x + WIDTH - 7 - room && mouseY >= ry && mouseY < ry + ROW;
            if (hovered) {
                g.fill(x + 7, ry, x + WIDTH - 7 - room, ry + ROW, Gui.rowHover());
            }
            if (!Thumbnails.draw(g, row.structure(), x + 9, ry + 2, 18)) {
                g.renderItem(STRUCTURE_ICON, x + 10, ry + 3);
            }
            double chance = chance(row);
            String pct = FoundIn.percent(chance);
            Gui.fitted(g, font, StructureNames.structure(row.structure()), x + 30, ry + 3, WIDTH - 50 - room - font.width(pct), Gui.ink(0xFF202020, Gui.VANILLA_LABEL));
            g.drawString(font, pct, x + WIDTH - 10 - room - font.width(pct), ry + 3, Gui.ink(0xFF202020, Gui.VANILLA_LABEL), false);
            // Which of its loot tables, unless that only repeats the structure's name.
            String name = StructureNames.structure(row.structure());
            String tables = row.tables().stream().map(t -> StructureNames.lootTable(t.toString())).distinct()
                    .filter(t -> !t.equals(name)).collect(Collectors.joining(", "));
            Gui.smallClipped(g, font, tables, x + 30, ry + 13, WIDTH - 40 - room, Gui.labelSoft());
        }
        Gui.scrollbar(g, x + WIDTH - 11, top, VISIBLE_ROWS * ROW, scroll.offset(), scroll.most());
        if (list.size() > VISIBLE_ROWS) {
            String more = Component.translatable("screen.justenoughstructures.found_in_range", first + 1, Math.min(list.size(), first + VISIBLE_ROWS), list.size()).getString();
            Gui.small(g, font, more, x + WIDTH - 8 - Gui.smallWidth(font, more), y + height() - 11, Gui.labelSoft());
        }
    }

    /** The first visible row. */
    private int first() {
        return (int) Math.round(scroll.offset() / ROW);
    }

    /** Returns the grabbed scroll bar if the press is on it, or null. */
    HeldBar grabBar(double mouseX, double mouseY) {
        return rows() == null ? null : HeldBar.at(scroll, mouseX, mouseY, x + WIDTH - 7, y + 30, VISIBLE_ROWS * ROW);
    }

    /** A point part of the way down the list's scroll bar, or null without one. Test hook for the scripted scenarios. */
    int[] barPoint(float along) {
        return rows() != null && scroll.scrolls() ? new int[]{x + WIDTH - 10, y + 31 + Math.round((VISIBLE_ROWS * ROW - 3) * along)} : null;
    }

    double scrolled() {
        return scroll.offset();
    }

    Optional<Row> click(double mouseX, double mouseY) {
        List<Row> list = rows();
        if (list == null) {
            return Optional.empty();
        }
        int top = y + 30;
        int first = first();
        for (int i = 0; i < VISIBLE_ROWS && first + i < list.size(); i++) {
            int ry = top + i * ROW;
            if (mouseX >= x + 7 && mouseX < x + WIDTH - 7 && mouseY >= ry && mouseY < ry + ROW) {
                return Optional.of(list.get(first + i));
            }
        }
        return Optional.empty();
    }

    /** Where a row showing now is, or nothing if there's no such row yet. */
    Optional<int[]> rowCentre(int index) {
        List<Row> list = rows();
        if (list == null || index < 0 || index >= Math.min(VISIBLE_ROWS, list.size() - first())) {
            return Optional.empty();
        }
        return Optional.of(new int[]{x + WIDTH / 2, y + 30 + index * ROW + ROW / 2});
    }

    /** The structures showing right now, to make pictures for. */
    List<ResourceLocation> visibleStructures() {
        List<Row> list = rows();
        if (list == null) {
            return List.of();
        }
        return list.subList(Math.min(first(), list.size()), Math.min(list.size(), first() + VISIBLE_ROWS)).stream().map(Row::structure).toList();
    }

    void scroll(double delta) {
        List<Row> list = rows();
        if (list != null) {
            scroll.fit(VISIBLE_ROWS * ROW, list.size() * ROW);
            // A whole row, however little the wheel says it turned.
            scroll.to((first() - (int) Math.signum(delta)) * ROW);
        }
    }
}
