package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * A container opened from the preview, drawn with the vanilla chest texture, or a loot table on its
 * own that this layout doesn't have. It shows one roll of the table, or every item's chance in a
 * container like it, switched by the two tabs by its name.
 */
final class ChestPopup {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    static final int WIDTH = 176;
    /** How tall the list of chances is, so it's the same size whatever the container. */
    private static final int ODDS_HEIGHT = 108;
    private static final int TAB_HEIGHT = 11;

    enum View { ROLL, ODDS }

    /** What the tabs on the popup do. */
    enum Action {
        /** Show one roll of the table. */
        ROLL,
        /** Show every item's chance. */
        ODDS
    }

    /** The container, or null for a loot table on its own. */
    final StructureSnapshot.Container container;
    /** Its loot table, or null for a container saved with its items. */
    final String table;
    final Component title;
    final int size;
    final int rows;
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
    private int scroll;
    /** A tab under the mouse, set as it's drawn. */
    Action hoveredAction;
    /** Lines to add to the tooltip of the hovered item, in the list of chances. */
    List<Component> hoveredExtra = List.of();
    private final Map<Action, int[]> links = new EnumMap<>(Action.class);

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

    /** A loot table this layout doesn't have, in a container as big as a chest, showing its chances first. */
    static ChestPopup forTable(String table) {
        return new ChestPopup(null, table, Component.translatable("screen.justenoughstructures.not_in_layout"), 27, 0, 1,
                Component.translatable("screen.justenoughstructures.container").getString(), View.ODDS);
    }

    /** Whether there's a table to show the chances of, which is what the tabs switch to. */
    boolean hasTabs() {
        return table != null;
    }

    private int bodyHeight() {
        return view == View.ODDS ? Math.max(rows * 18, ODDS_HEIGHT) : rows * 18;
    }

    private int infoHeight(Font font) {
        int fine = Gui.fineLine(font);
        int height = 5 + fine + 2 + font.lineHeight + 1 + noteLines(font).size() * (fine + 1) + 3 + 20 + 6;
        if (Gui.advanced() && table != null) {
            height += fine + 1;
        }
        return height;
    }

    /** The line under the table's name: what's showing, or what a changed container was. Up to two lines. */
    private List<FormattedCharSequence> noteLines(Font font) {
        String changedFrom = container == null || container.source() == null ? null : container.source().patchedFrom();
        Component note;
        if (packTools && changedFrom != null) {
            note = Component.translatable("screen.justenoughstructures.container.changed_from", StructureNames.lootTable(changedFrom));
        } else if (table == null) {
            note = Component.translatable("screen.justenoughstructures.popup_saved_items");
        } else if (view == View.ODDS) {
            note = Component.translatable("screen.justenoughstructures.popup_odds_hint", kind);
        } else if (items == null) {
            note = Component.translatable("screen.justenoughstructures.rolling");
        } else {
            note = container == null ? Component.translatable("screen.justenoughstructures.popup_table_roll_hint")
                    : Component.translatable("screen.justenoughstructures.popup_roll_hint", kind);
        }
        List<FormattedCharSequence> lines = font.split(note, (int) ((WIDTH - 14) / Gui.fineScale()));
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    /** How much of the title bar the index and tabs leave for the name. */
    private int titleRoom(Font font) {
        int room = WIDTH - 16;
        if (count > 1) {
            room -= font.width(Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString()) + 4;
        }
        if (hasTabs()) {
            room -= tabWidth(font, "popup_roll") + 2 + tabWidth(font, "popup_odds") + 4;
        }
        return room;
    }

    /** The title bar: one line, or two when the name doesn't fit beside the tabs. */
    private int headerHeight(Font font) {
        return font.width(title.getString()) > titleRoom(font) ? 17 + font.lineHeight + 1 : 17;
    }

    int height(Font font) {
        return headerHeight(font) + bodyHeight() + 7 + infoHeight(font);
    }

    void place(int screenWidth, int screenHeight, Font font) {
        x = (screenWidth - WIDTH) / 2;
        y = Math.max(4, (screenHeight - height(font)) / 2);
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
        scroll = 0;
    }

    /** Scrolls the list of chances, when it's showing and the mouse is over it. */
    boolean scroll(double mouseX, double mouseY, double delta) {
        if (view != View.ODDS || odds == null || mouseX < x || mouseX >= x + WIDTH) {
            return false;
        }
        int most = Math.max(0, odds.rows().size() * (OddsList.ROW + 1) - (bodyHeight() - 4));
        scroll = Math.max(0, Math.min(most, scroll - (int) (delta * (OddsList.ROW + 1))));
        return true;
    }

    /** Draws the popup and returns the stack under the mouse, or empty. */
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        links.clear();
        hoveredAction = null;
        hoveredExtra = List.of();
        int body = bodyHeight();
        int header = headerHeight(font);

        // The chest's frame: its title bar, a plain strip under it if the name takes two lines, then
        // rows of slots or a plain strip for the list, then its bottom edge.
        g.blit(TEXTURE, x, y, 0, 0, WIDTH, 17);
        for (int filled = 17; filled < header; filled += 12) {
            g.blit(TEXTURE, x, y + filled, 0, 127, WIDTH, Math.min(12, header - filled));
        }
        if (view == View.ROLL) {
            g.blit(TEXTURE, x, y + header, 0, 17, WIDTH, body);
        } else {
            for (int filled = 0; filled < body; filled += 12) {
                g.blit(TEXTURE, x, y + header + filled, 0, 127, WIDTH, Math.min(12, body - filled));
            }
        }
        g.blit(TEXTURE, x, y + header + body, 0, 215, WIDTH, 7);

        int right = x + WIDTH - 8;
        if (count > 1) {
            String of = Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString();
            right -= font.width(of);
            g.drawString(font, of, right, y + 6, Gui.LABEL_SOFT, false);
            right -= 4;
        }
        if (hasTabs()) {
            right = tab(g, font, Action.ODDS, "popup_odds", view == View.ODDS, right, mouseX, mouseY);
            right = tab(g, font, Action.ROLL, "popup_roll", view == View.ROLL, right - 2, mouseX, mouseY);
        }
        if (header > 17) {
            List<FormattedCharSequence> lines = font.split(title, titleRoom(font));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), x + 8, y + 6 + i * (font.lineHeight + 1), Gui.LABEL, false);
            }
        } else {
            g.drawString(font, title, x + 8, y + 6, Gui.LABEL, false);
        }

        ItemStack hovered = view == View.ROLL ? renderSlots(g, font, mouseX, mouseY, y + header) : renderOdds(g, font, mouseX, mouseY, y + header, body);

        int infoTop = y + header + body + 7;
        Gui.panel(g, x, infoTop, WIDTH, infoHeight(font));
        int cy = infoTop + 5;
        Gui.fine(g, font, Component.translatable("screen.justenoughstructures.field.loot_table").getString(), x + 7, cy, Gui.LABEL_SOFT);
        cy += Gui.fineLine(font) + 2;
        String tableName = table == null ? Component.translatable("screen.justenoughstructures.prefilled").getString() : StructureNames.lootTable(table);
        int mark = packTools ? editedMark(g, font, table, x + WIDTH - 7, cy + 1) : 0;
        g.drawString(font, Gui.clip(font, tableName, WIDTH - 14 - mark), x + 7, cy, 0xFF202020, false);
        cy += font.lineHeight + 1;
        if (Gui.advanced() && table != null) {
            Gui.fine(g, font, Gui.fineClip(font, table, WIDTH - 14), x + 7, cy, 0xFF555555);
            cy += Gui.fineLine(font) + 1;
        }
        for (FormattedCharSequence line : noteLines(font)) {
            Gui.scaled(g, font, line, x + 7, cy, Gui.LABEL_SOFT, Gui.fineScale());
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
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
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
        return hovered;
    }

    private ItemStack renderOdds(GuiGraphics g, Font font, int mouseX, int mouseY, int bodyTop, int body) {
        int top = bodyTop + 1;
        int left = x + 7;
        int right = x + WIDTH - 7;
        int bottom = top + body - 2;
        Gui.inset(g, left, top - 1, right - left, body, 0xFFB9B9B9);
        if (odds == null) {
            Gui.fine(g, font, Component.translatable("screen.justenoughstructures.rolling").getString(), left + 4, top + 4, Gui.LABEL_SOFT);
            return ItemStack.EMPTY;
        }
        if (odds.rows().isEmpty()) {
            Gui.fineWrapped(g, font, Component.translatable("screen.justenoughstructures.always_empty"), left + 4, top + 4, right - left - 8, Gui.LABEL_SOFT);
            return ItemStack.EMPTY;
        }
        ItemStack hovered = ItemStack.EMPTY;
        List<LootOdds.Row> rows = OddsList.sorted(odds, false);
        Map<LootOdds.Row, String> names = OddsList.names(rows);
        g.enableScissor(left + 1, top, right - 1, bottom);
        int cy = top + 1 - scroll;
        for (LootOdds.Row row : rows) {
            if (cy + OddsList.ROW > top && cy < bottom) {
                float chance = (float) row.hits() / odds.rolls();
                boolean over = mouseX >= left && mouseX < right && mouseY >= Math.max(cy, top) && mouseY < Math.min(cy + OddsList.ROW, bottom);
                OddsList.drawRow(g, font, row.example(), names.get(row), OddsList.counts(row), chance, left, cy, right - 1, over);
                if (over) {
                    hovered = row.example();
                    hoveredExtra = OddsList.tooltip(row, chance, count, kind);
                }
            }
            cy += OddsList.ROW + 1;
        }
        g.disableScissor();
        return hovered;
    }

    /** A tab by the popup's name, its right edge at {@code right}. Returns its left edge. */
    private static int tabWidth(Font font, String key) {
        return Gui.fineWidth(font, Component.translatable("screen.justenoughstructures." + key).getString()) + 6;
    }

    private int tab(GuiGraphics g, Font font, Action action, String key, boolean on, int right, int mouseX, int mouseY) {
        String text = Component.translatable("screen.justenoughstructures." + key).getString();
        int w = tabWidth(font, key);
        int left = right - w;
        int top = y + 4;
        boolean over = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + TAB_HEIGHT;
        g.fill(left, top, right, top + TAB_HEIGHT, on ? 0xFF373737 : 0xFF8B8B8B);
        g.fill(left + 1, top + 1, right - 1, top + TAB_HEIGHT - 1, on ? 0xFFFFFFFF : over ? 0xFFD6D6D6 : 0xFFC6C6C6);
        Gui.fine(g, font, text, left + 3, top + (TAB_HEIGHT - Gui.fineLine(font)) / 2 + 1, on ? Gui.LABEL : 0xFF404040);
        links.put(action, new int[]{left, top, w, TAB_HEIGHT});
        if (over && !on) {
            hoveredAction = action;
        }
        return left;
    }

    /** The note on an edited table at the right of its line, for players who see Pack tools. Returns the room it took. */
    private static int editedMark(GuiGraphics g, Font font, String table, int right, int top) {
        LootOverrides.Status status = ClientRequests.overrideStatus(table);
        if (status == null || status == LootOverrides.Status.NONE) {
            return 0;
        }
        String key = switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> "edited_changed";
            case BROKEN -> "edited_broken";
            default -> "edited";
        };
        int colour = switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> 0xFF9A6200;
            case BROKEN -> 0xFFB02020;
            default -> 0xFF2E7D1F;
        };
        String text = Component.translatable("screen.justenoughstructures.loot." + key).getString();
        int width = Gui.fineWidth(font, text);
        Gui.fine(g, font, text, right - width, top, colour);
        return width + 4;
    }

    /** The middle of a tab, or null if it isn't shown. */
    int[] linkCentre(Action action) {
        int[] r = links.get(action);
        return r == null ? null : new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    /** The tab at a point, or null. */
    Action actionAt(double mouseX, double mouseY) {
        for (Map.Entry<Action, int[]> e : links.entrySet()) {
            int[] r = e.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return e.getKey();
            }
        }
        return null;
    }
}
