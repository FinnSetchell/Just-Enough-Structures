package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * A container's popup, drawn with the vanilla chest texture, or a popup for a loot table by itself
 * that isn't in this layout. It shows one roll of the table, or every item's chance in a container
 * of this kind, switched by the two tabs beside its name.
 */
final class ChestPopup {
    private static final ResourceLocation TEXTURE = Ids.parse("textures/gui/container/generic_54.png");
    static final int WIDTH = 176;
    /** Height of the list of chances, the same for every container. */
    private static final int ODDS_HEIGHT = 108;
    private static final int TAB_HEIGHT = 11;
    /** Width of a tab beside the popup's name: room for its icon. */
    private static final int TAB_WIDTH = Icons.SMALL + 4;

    enum View { ROLL, ODDS }

    /** What the tabs and links on the popup do. */
    enum Action {
        /** Show one roll of the table. */
        ROLL,
        /** Show every item's chance. */
        ODDS,
        /** Open the container in Pack tools. */
        TOOLS_CONTAINER,
        /** Open its loot table in Pack tools. */
        TOOLS_TABLE
    }

    private static final ItemStack PAPER = new ItemStack(net.minecraft.world.item.Items.PAPER);
    private static final int ICON = 14;

    /** The container, or null for a loot table by itself. */
    final StructureSnapshot.Container container;
    /** Its loot table, or null for a container saved with its items. */
    final String table;
    final Component title;
    final int size;
    final int rows;
    /**
     * Index among the {@code count} containers with this table, or -1 when opened from something that
     * stands for all of them (a Loot tab row or a shared marker). Actions on a single container are
     * disabled until one is selected.
     */
    final int index;
    final int count;
    /** What a container like this is called in text: "chest", "suspicious sand", or "container". */
    final String kind;
    View view;
    /** Whether this player sees Pack tools: the marks on edited tables and what a changed container was. */
    final boolean packTools = ClientRequests.showsPackTools();
    long seed;
    List<ItemStack> items;
    LootOdds odds;
    int x;
    int y;
    private final Scroll scroll = new Scroll(OddsList.ROW + 1);
    /** Where the list of chances' bar was last drawn. */
    private int barRight, barTop, barHeight;
    /** The unselected tab under the mouse, set while drawing, or null. */
    Action hoveredAction;
    /** Lines to add to the tooltip of the hovered item, in the list of chances. */
    List<Component> hoveredExtra = List.of();
    /** The tooltip of a link or icon under the mouse, or null. */
    List<Component> hoveredTip;
    /**
     * Set while the player is picking a chest for Pack tools to change. The button that opens the
     * container in Pack tools is then highlighted, so players learn it's the way to change one.
     */
    boolean picking;
    /** The block or entity it is, for the icon that opens it in Pack tools. */
    ItemStack icon = ItemStack.EMPTY;
    private final PopupLinks<Action> links = new PopupLinks<>(Action.class);

    private ChestPopup(StructureSnapshot.Container container, String table, Component title, int size, int index, int count, String kind, View view) {
        this.container = container;
        this.table = table;
        this.title = title;
        this.size = size;
        this.rows = Math.max(1, Math.min(6, (size + 8) / 9));
        this.index = index;
        this.count = count;
        this.kind = kind;
        this.view = view;
    }

    static ChestPopup forContainer(StructureSnapshot.Container container, Component title, int size, int index, int count) {
        return new ChestPopup(container, container.lootTable(), title, size, index, count, title.getString().toLowerCase(Locale.ROOT), View.ROLL);
    }

    /**
     * A popup for a loot table by itself, sized like a chest, showing its chances first. Unless
     * {@code title} is given, the title says the table isn't in this layout.
     */
    static ChestPopup forTable(String table, Component title) {
        return new ChestPopup(null, table, title != null ? title : Component.translatable("screen.justenoughstructures.not_in_layout"), 27, 0, 1,
                Component.translatable("screen.justenoughstructures.container").getString(), View.ODDS);
    }

    /** Whether it shows all the containers with this table instead of one. */
    boolean overview() {
        return container != null && index < 0;
    }

    /** "3 / 17", or "all 17" before one is selected, or null for a container that's the only one like it. */
    private String indexText() {
        if (count <= 1) {
            return null;
        }
        return overview() ? Component.translatable("screen.justenoughstructures.container_all", count).getString()
                : Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString();
    }

    /** Places it at {@code x}, beside the preview, so its containers stay in view. */
    void placeAt(int x, int screenHeight, Font font) {
        this.x = x;
        y = Math.max(4, (screenHeight - placedHeight(font)) / 2);
    }

    /**
     * The height used to place it: its tallest, with the taller view and the most lines of notes, so
     * switching tabs or rolling again only changes how far down it reaches, and its title and tabs
     * stay put.
     */
    private int placedHeight(Font font) {
        int body = hasTabs() ? Math.max(rows * 18, ODDS_HEIGHT) : bodyHeight();
        return headerHeight(font) + body + 7 + infoHeight(font, 2);
    }

    /** Whether there's a table to show the chances of, which is what the tabs switch to. */
    boolean hasTabs() {
        return table != null;
    }

    private int bodyHeight() {
        return view == View.ODDS ? Math.max(rows * 18, ODDS_HEIGHT) : rows * 18;
    }

    /** Whether Pack tools' icons sit beside the label, which then needs room for them. */
    private boolean showsIcons() {
        return packTools;
    }

    private int labelRow(Font font) {
        return showsIcons() ? ICON + 2 : Gui.fineLine(font) + 2;
    }

    private int infoHeight(Font font) {
        return infoHeight(font, noteLines(font).size());
    }

    private int infoHeight(Font font, int notes) {
        int fine = Gui.fineLine(font);
        int height = 5 + labelRow(font) + font.lineHeight + 1 + notes * (fine + 1) + 3 + 20 + 6;
        if (Gui.advanced() && table != null) {
            height += fine + 1;
        }
        return height;
    }

    /**
     * Note under the table name, at most two lines: the original table of a changed container, or,
     * for a group of prefilled containers, that each has its own items.
     */
    private List<FormattedCharSequence> noteLines(Font font) {
        String changedFrom = container == null || container.source() == null ? null : container.source().patchedFrom();
        Component note;
        if (packTools && changedFrom != null && !overview()) {
            note = Component.translatable("screen.justenoughstructures.container.changed_from", StructureNames.lootTable(changedFrom));
        } else if (overview() && table == null) {
            note = Component.translatable("screen.justenoughstructures.popup_pick_saved");
        } else {
            return List.of();
        }
        List<FormattedCharSequence> lines = font.split(note, (int) ((WIDTH - 14) / Gui.fineScale()));
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    /** How much of the title bar the index and tabs leave for the name. */
    private int titleRoom(Font font) {
        int room = WIDTH - 16;
        if (count > 1) {
            // Room for the widest count text, so the popup doesn't change shape as the arrows step.
            int all = font.width(Component.translatable("screen.justenoughstructures.container_all", count).getString());
            int last = font.width(Component.translatable("screen.justenoughstructures.container_index", count, count).getString());
            room -= Math.max(all, last) + 4;
        }
        if (hasTabs()) {
            room -= TAB_WIDTH + 3 + TAB_WIDTH + 4;
        }
        return room;
    }

    /** The title bar: one line, or two when the name doesn't fit beside the tabs. */
    private int headerHeight(Font font) {
        return font.width(title.getString()) > titleRoom(font) ? 17 + font.lineHeight + 1 : 17;
    }

    /** Whether the name goes on a separate line under the tabs, because one of its words won't fit beside them. */
    private boolean titleBelow(Font font) {
        for (String word : title.getString().split(" ")) {
            if (font.width(word) > titleRoom(font)) {
                return true;
            }
        }
        return false;
    }

    int height(Font font) {
        return headerHeight(font) + bodyHeight() + 7 + infoHeight(font);
    }

    void place(int screenWidth, int screenHeight, Font font) {
        x = (screenWidth - WIDTH) / 2;
        y = Math.max(4, (screenHeight - placedHeight(font)) / 2);
    }

    boolean contains(double mouseX, double mouseY, Font font) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height(font);
    }

    /** Where the buttons go, one row along the bottom of the info panel. */
    int buttonRowY(Font font) {
        return y + height(font) - 26;
    }

    void switchTo(View next) {
        view = next;
        scroll.reset();
    }

    /** Scrolls the list of chances, when it's showing and the mouse is over it. */
    boolean scroll(double mouseX, double mouseY, double delta) {
        if (view != View.ODDS || odds == null || mouseX < x || mouseX >= x + WIDTH) {
            return false;
        }
        fitScroll();
        // In whole pixels, as the rows are drawn.
        scroll.to(scroll.top() - (int) (delta * (OddsList.ROW + 1)));
        return true;
    }

    /** Returns the grabbed scroll bar if the press is on the list of chances' bar, or null. */
    HeldBar grabBar(double mouseX, double mouseY) {
        if (view != View.ODDS || odds == null) {
            return null;
        }
        fitScroll();
        return HeldBar.at(scroll, mouseX, mouseY, barRight, barTop, barHeight);
    }

    private void fitScroll() {
        scroll.fit(bodyHeight() - 4, OddsList.height(odds.rows().size()));
    }

    /** A point part of the way down the list of chances' scroll bar, or null without one. Test hook for the scripted scenarios. */
    int[] barPoint(float along) {
        if (view != View.ODDS || odds == null) {
            return null;
        }
        fitScroll();
        return scroll.scrolls() ? new int[]{barRight - 4, barTop + 1 + Math.round((barHeight - 3) * along)} : null;
    }

    double scrolled() {
        return scroll.offset();
    }

    /** Draws the popup and returns the stack under the mouse, or empty. */
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        links.clear();
        hoveredAction = null;
        hoveredExtra = List.of();
        hoveredTip = null;
        int body = bodyHeight();
        int header = headerHeight(font);

        // The chest's frame: its title bar, a plain strip under it if the name takes two lines, then
        // rows of slots or a plain strip for the list, then its bottom edge.
        Gui.blit(g, TEXTURE, x, y, 0, 0, WIDTH, 17);
        for (int filled = 17; filled < header; filled += 12) {
            Gui.blit(g, TEXTURE, x, y + filled, 0, 127, WIDTH, Math.min(12, header - filled));
        }
        if (view == View.ROLL) {
            Gui.blit(g, TEXTURE, x, y + header, 0, 17, WIDTH, body);
        } else {
            for (int filled = 0; filled < body; filled += 12) {
                Gui.blit(g, TEXTURE, x, y + header + filled, 0, 127, WIDTH, Math.min(12, body - filled));
            }
        }
        Gui.blit(g, TEXTURE, x, y + header + body, 0, 215, WIDTH, 7);

        int right = x + WIDTH - 8;
        String of = indexText();
        if (of != null) {
            right -= font.width(of);
            g.drawString(font, of, right, y + 6, Gui.labelSoft(), false);
            right -= 4;
        }
        if (hasTabs()) {
            right = tab(g, Action.ODDS, "popup_odds", Icons.CHANCES, view == View.ODDS, right, mouseX, mouseY);
            right = tab(g, Action.ROLL, "popup_roll", Icons.ONE_ROLL, view == View.ROLL, right - 3, mouseX, mouseY);
        }
        if (header > 17 && titleBelow(font)) {
            Gui.drawClipped(g, font, title.getString(), x + 8, y + 6 + font.lineHeight + 1, WIDTH - 16, Gui.label(), false);
        } else if (header > 17) {
            List<FormattedCharSequence> lines = font.split(title, titleRoom(font));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), x + 8, y + 6 + i * (font.lineHeight + 1), Gui.label(), false);
            }
        } else {
            g.drawString(font, title, x + 8, y + 6, Gui.label(), false);
        }

        ItemStack hovered = view == View.ROLL ? renderSlots(g, font, mouseX, mouseY, y + header) : renderOdds(g, font, mouseX, mouseY, y + header, body);

        int infoTop = y + header + body + 7;
        Gui.panel(g, x, infoTop, WIDTH, infoHeight(font));
        int cy = infoTop + 5;
        int labelY = showsIcons() ? cy + (ICON - Gui.fineLine(font)) / 2 : cy;
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.field.loot_table").getString(), x + 7, labelY, Gui.labelSoft());
        int iconRight = x + WIDTH - 7;
        if (showsIcons()) {
            // Pack tools' shortcuts: the table, and the container itself, each with a wrench on it.
            if (table != null) {
                iconRight = toolsIcon(g, Action.TOOLS_TABLE, PAPER, iconRight, cy, mouseX, mouseY, true, false,
                        List.of(Component.translatable("screen.justenoughstructures.tools.open_table")));
            }
            if (container != null) {
                // The table is the same for the whole group, but the container has to be a particular one.
                toolsIcon(g, Action.TOOLS_CONTAINER, icon.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.CHEST) : icon, iconRight - 2, cy,
                        mouseX, mouseY, !overview(), picking, overview() ? pickOne()
                                : List.of(Component.translatable("screen.justenoughstructures.tools.open_container")));
            }
        }
        cy += labelRow(font);
        String tableName = table == null ? Component.translatable("screen.justenoughstructures.prefilled").getString() : StructureNames.lootTable(table);
        int mark = packTools ? editedMark(g, font, table, x + WIDTH - 7, cy + 1, mouseX, mouseY) : 0;
        Gui.drawClipped(g, font, tableName, x + 7, cy, WIDTH - 14 - mark, Gui.ink(0xFF202020, Gui.VANILLA_LABEL), false);
        cy += font.lineHeight + 1;
        if (Gui.advanced() && table != null) {
            Gui.fineClipped(g, font, table, x + 7, cy, WIDTH - 14, Gui.labelSoftSmall());
            cy += Gui.fineLine(font) + 1;
        }
        for (FormattedCharSequence line : noteLines(font)) {
            Gui.scaled(g, font, line, x + 7, cy, Gui.labelSoft(), Gui.fineScale());
            cy += Gui.fineLine(font) + 1;
        }
        return hovered;
    }

    private ItemStack renderSlots(GuiGraphics g, Font font, int mouseX, int mouseY, int bodyTop) {
        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < rows * 9; slot++) {
            int sx = x + 8 + (slot % 9) * 18;
            int sy = bodyTop + 1 + (slot / 9) * 18;
            if (slot >= size) {
                Gui.tone(g, sx - 1, sy - 1, sx + 17, sy + 17, Gui.Tone.SLOT);
                continue;
            }
            ItemStack stack = items != null && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                g.renderItem(stack, sx, sy);
                g.renderItemDecorations(font, stack, sx, sy);
            }
            if (mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) {
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                hovered = stack;
            }
        }
        if (items == null) {
            // Waiting for the roll, said over the empty slots so the popup keeps its size.
            String rolling = Component.translatable("screen.justenoughstructures.rolling").getString();
            g.drawString(font, rolling, x + (WIDTH - font.width(rolling)) / 2, bodyTop + (rows * 18 - font.lineHeight) / 2, 0xFF404040, false);
        }
        return hovered;
    }

    private ItemStack renderOdds(GuiGraphics g, Font font, int mouseX, int mouseY, int bodyTop, int body) {
        int top = bodyTop + 1;
        int left = x + 7;
        int right = x + WIDTH - 7;
        int bottom = top + body - 2;
        Gui.inset(g, left, top - 1, right - left, body, Gui.Tone.WELL);
        if (odds == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.rolling").getString(), left + 4, top + 4, Gui.labelSoft());
            return ItemStack.EMPTY;
        }
        if (odds.rows().isEmpty()) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.always_empty"), left + 4, top + 4, right - left - 8, Gui.labelSoft());
            return ItemStack.EMPTY;
        }
        ItemStack hovered = ItemStack.EMPTY;
        List<LootOdds.Row> rows = OddsList.sorted(odds, false);
        Map<LootOdds.Row, String> names = OddsList.names(rows);
        fitScroll();
        int rowRight = scroll.scrolls() ? right - 7 : right;
        Gui.scissor(g, left + 1, top, right - 1, bottom);
        int detailRoom = OddsList.detailRoom(font, rows.stream().map(OddsList::counts).toList());
        int cy = top + 1 - scroll.top();
        for (LootOdds.Row row : rows) {
            if (cy + OddsList.ROW > top && cy < bottom) {
                float chance = (float) row.hits() / odds.rolls();
                boolean over = mouseX >= left && mouseX < rowRight && mouseY >= Math.max(cy, top) && mouseY < Math.min(cy + OddsList.ROW, bottom);
                OddsList.drawRow(g, font, row.example(), names.get(row), OddsList.counts(row), chance, left, cy, rowRight - 1, over, detailRoom);
                if (over) {
                    hovered = row.example();
                    hoveredExtra = OddsList.tooltip(row, chance, count, kind);
                }
            }
            cy += OddsList.ROW + 1;
        }
        Gui.endScissor(g);
        Gui.scrollbar(g, right - 5, top, bottom - top, scroll.offset(), scroll.most());
        barRight = right;
        barTop = top;
        barHeight = bottom - top;
        return hovered;
    }

    /** The tooltip for an action that needs a single container selected first. */
    private static List<Component> pickOne() {
        return List.of(Component.translatable("screen.justenoughstructures.tools.pick_one"));
    }

    /** {@link #drawToolsIcon} at the left of {@code right}, as a link with {@code tip}. Returns its left edge. */
    private int toolsIcon(GuiGraphics g, Action action, ItemStack stack, int right, int top, int mouseX, int mouseY, boolean enabled,
                          boolean pointedOut, List<Component> tip) {
        int left = right - ICON;
        if (drawToolsIcon(g, stack, left, top, mouseX, mouseY, enabled, pointedOut)) {
            hoveredTip = tip;
        }
        if (enabled) {
            links.put(action, left, top, ICON, ICON, Narration.button(tip.get(0)));
        }
        return left;
    }

    /**
     * An item with a small wrench on it, that opens something in Pack tools, or greyed out when it
     * can't yet. {@code pointedOut} draws attention to it, as the one to use. Returns whether the
     * mouse is over it.
     */
    static boolean drawToolsIcon(GuiGraphics g, ItemStack stack, int left, int top, int mouseX, int mouseY, boolean enabled, boolean pointedOut) {
        int right = left + ICON;
        boolean over = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ICON;
        if (pointedOut && enabled) {
            pointOut(g, left, top, ICON);
        }
        if (over && enabled) {
            g.fill(left, top, right, top + ICON, 0xFF555555);
            g.fill(left + 1, top + 1, right - 1, top + ICON - 1, 0x90FFFFFF);
        }
        Gui.push(g);
        Gui.translate(g, left + 1, top + 1);
        Gui.scale(g, 0.75f);
        g.renderItem(stack, 0, 0);
        Gui.pop(g);
        Gui.push(g);
        Gui.lift(g, 200);
        Gui.blit(g, PackToolsScreen.WRENCH, right - 8, top + ICON - 8, 0, 0, 8, 8, 8, 8);
        if (!enabled) {
            // The panel's own grey over the item and wrench, so they show through faintly.
            g.fill(left, top, right, top + ICON, 0xA0C6C6C6);
        }
        Gui.pop(g);
        return over;
    }

    /**
     * Draws a gently pulsing yellow frame around a button, to point it out as the next step in what
     * the player is doing, like the button that changes a chest picked for Pack tools.
     */
    static void pointOut(GuiGraphics g, int left, int top, int size) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(net.minecraft.Util.getMillis() / 220.0);
        int alpha = 0x70 + (int) (0x8F * pulse);
        g.fill(left - 2, top - 2, left + size + 2, top + size + 2, alpha << 24 | 0xF5C400);
        g.fill(left - 1, top - 1, left + size + 1, top + size + 1, 0xFFFFF2B0);
    }

    /** Draws a tab beside the popup's name, its icon on it and its label as the tooltip, its right edge at {@code right}. Returns its left edge. */
    private int tab(GuiGraphics g, Action action, String key, ResourceLocation icon, boolean on, int right, int mouseX, int mouseY) {
        int w = TAB_WIDTH;
        int left = right - w;
        int top = y + 4;
        boolean over = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + TAB_HEIGHT;
        Gui.tone(g, left, top, right, top + TAB_HEIGHT, on ? Gui.Tone.SLOT_DARK : Gui.Tone.SLOT);
        Gui.tone(g, left + 1, top + 1, right - 1, top + TAB_HEIGHT - 1, on ? Gui.Tone.LIGHT : Gui.Tone.PANEL);
        if (over && !on) {
            g.fill(left + 1, top + 1, right - 1, top + TAB_HEIGHT - 1, 0x48FFFFFF);
        }
        Gui.icon(g, icon, left + 2, top + 1, Icons.SMALL, Icons.SMALL, on ? Gui.label() : 0xFF404040, false);
        Component label = Component.translatable("screen.justenoughstructures." + key);
        links.put(action, left, top, w, TAB_HEIGHT, Narration.tab(label, on));
        if (over) {
            hoveredTip = List.of(label);
        }
        if (over && !on) {
            hoveredAction = action;
        }
        return left;
    }

    /** Draws the edited-table badge at the right of the line, with its text as the tooltip. Returns the width used. */
    private int editedMark(GuiGraphics g, Font font, String table, int right, int top, int mouseX, int mouseY) {
        LootOverrides.Status status = ClientRequests.overrideStatus(table);
        Component mark = ToolsSection.editedMark(status);
        if (mark == null) {
            return 0;
        }
        int width = ToolsSection.badgeWidth(font, mark);
        ToolsSection.badge(g, font, mark, right - width, top - 1, ToolsSection.markColour(status));
        if (mouseX >= right - width && mouseX < right && mouseY >= top - 1 && mouseY < top + Icons.SMALL - 1) {
            hoveredTip = List.of(mark);
        }
        return width + 4;
    }

    /** The middle of a tab or link, or null if it isn't shown. */
    int[] linkCentre(Action action) {
        return links.centre(action);
    }

    /** The tab or link at a point, or null. */
    Action actionAt(double mouseX, double mouseY) {
        return links.at(mouseX, mouseY);
    }

    /** What the narrator reads for the tab or link at a point, or null. */
    NarrationSupplier narrationAt(double mouseX, double mouseY) {
        return links.narrationAt(mouseX, mouseY);
    }

    /** Whether an action changes what the popup shows, as a tab that's open already doesn't. */
    boolean changes(Action action) {
        return switch (action) {
            case ROLL -> view != View.ROLL;
            case ODDS -> view != View.ODDS;
            default -> true;
        };
    }
}
