package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A container opened from the preview, drawn with the vanilla chest texture. */
final class ChestPopup {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    static final int WIDTH = 176;
    static final int INFO_HEIGHT = 60;

    final StructureSnapshot.Container container;
    final Component title;
    final int size;
    final int rows;
    final int index;
    final int count;
    long seed;
    List<ItemStack> items;
    int x;
    int y;
    /** A link on the popup under the mouse, and what it does, set as it's drawn. */
    Action hoveredAction;
    Component hoveredHint;
    private final java.util.Map<Action, int[]> links = new java.util.EnumMap<>(Action.class);

    /** What the links on the popup do, for players who can edit loot. */
    enum Action {
        /** Point this one container at a different table. */
        CHANGE,
        /** Edit the table itself, for a container placed by code that can't be changed on its own. */
        EDIT,
        /** Put a changed container back on its own table. */
        UNDO
    }

    ChestPopup(StructureSnapshot.Container container, Component title, int size, int index, int count) {
        this.container = container;
        this.title = title;
        this.size = size;
        this.rows = Math.max(1, Math.min(6, (size + 8) / 9));
        this.index = index;
        this.count = count;
    }

    int height() {
        return rows * 18 + 24 + INFO_HEIGHT;
    }

    void place(int screenWidth, int screenHeight) {
        x = (screenWidth - WIDTH) / 2;
        y = Math.max(4, (screenHeight - height()) / 2);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height();
    }

    /** Draws the popup and returns the stack under the mouse, or empty. */
    ItemStack render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int chestHeight = rows * 18 + 17;
        g.blit(TEXTURE, x, y, 0, 0, WIDTH, chestHeight);
        g.blit(TEXTURE, x, y + chestHeight, 0, 215, WIDTH, 7);
        g.drawString(font, title, x + 8, y + 6, Gui.LABEL, false);
        if (count > 1) {
            String of = Component.translatable("screen.justenoughstructures.container_index", index + 1, count).getString();
            g.drawString(font, of, x + WIDTH - 8 - font.width(of), y + 6, Gui.LABEL_SOFT, false);
        }

        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < rows * 9; slot++) {
            int sx = x + 8 + (slot % 9) * 18;
            int sy = y + 18 + (slot / 9) * 18;
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

        int infoTop = y + chestHeight + 7;
        Gui.panel(g, x, infoTop, WIDTH, INFO_HEIGHT);
        String table = container.lootTable() == null
                ? Component.translatable("screen.justenoughstructures.prefilled").getString()
                : StructureNames.lootTable(container.lootTable());
        String label = Component.translatable("screen.justenoughstructures.field.loot_table").getString();
        Gui.small(g, font, label, x + 7, infoTop + 5, Gui.LABEL_SOFT);
        // Whether the table's been edited, for players who can edit loot, next to its label.
        LootOverrides.Status edited = ClientRequests.overrideStatus(container.lootTable());
        if (edited != null && edited != LootOverrides.Status.NONE) {
            boolean used = edited == LootOverrides.Status.ACTIVE;
            Gui.small(g, font, Component.translatable(edited == LootOverrides.Status.BROKEN ? "screen.justenoughstructures.loot.edited_broken"
                            : used ? "screen.justenoughstructures.loot.edited" : "screen.justenoughstructures.loot.edited_changed").getString(),
                    x + 7 + Gui.smallWidth(font, label) + 4, infoTop + 5,
                    edited == LootOverrides.Status.BROKEN ? 0xFFB02020 : used ? 0xFF2E7D1F : 0xFF9A6200);
        }
        g.drawString(font, Gui.clip(font, table, WIDTH - 14), x + 7, infoTop + 12, 0xFF202020, false);

        links.clear();
        hoveredAction = null;
        hoveredHint = null;
        boolean canEdit = ClientRequests.showsPackTools();
        if (canEdit && container.source() != null) {
            link(g, font, Action.CHANGE, "container.change", "container.change_hint", infoTop + 5, mouseX, mouseY);
        } else if (canEdit && container.lootTable() != null) {
            link(g, font, Action.EDIT, "container.edit_table", "container.edit_table_hint", infoTop + 5, mouseX, mouseY);
        }
        String changedFrom = container.source() == null ? null : container.source().patchedFrom();
        if (changedFrom != null) {
            int undoRoom = canEdit ? link(g, font, Action.UNDO, "container.undo", "container.undo_hint", infoTop + 23, mouseX, mouseY) + 4 : 0;
            String was = Component.translatable("screen.justenoughstructures.container.changed_from", StructureNames.lootTable(changedFrom)).getString();
            Gui.small(g, font, Gui.clipSmall(font, was, WIDTH - 14 - undoRoom), x + 7, infoTop + 23, Gui.LABEL_SOFT);
        } else if (items == null) {
            Gui.small(g, font, Component.translatable("screen.justenoughstructures.rolling").getString(), x + 7, infoTop + 23, Gui.LABEL_SOFT);
        } else if (container.lootTable() != null) {
            Gui.small(g, font, Component.translatable("screen.justenoughstructures.roll_hint").getString(), x + 7, infoTop + 23, Gui.LABEL_SOFT);
        }
        return hovered;
    }

    /** Draws a small link at the right of a line and returns how wide it is. */
    private int link(GuiGraphics g, Font font, Action action, String key, String hint, int lineY, int mouseX, int mouseY) {
        String text = Component.translatable("screen.justenoughstructures." + key).getString();
        int w = Gui.smallWidth(font, text) + 2;
        int lx = x + WIDTH - 7 - w;
        boolean over = mouseX >= lx && mouseX < lx + w && mouseY >= lineY - 1 && mouseY < lineY + 8;
        Gui.small(g, font, text, lx, lineY, over ? 0xFF1F3F8F : 0xFF3A55A0);
        links.put(action, new int[]{lx, lineY - 1, w, 9});
        if (over) {
            hoveredAction = action;
            hoveredHint = Component.translatable("screen.justenoughstructures." + hint);
        }
        return w;
    }

    /** The middle of a link, or null if it isn't shown. */
    int[] linkCentre(Action action) {
        int[] r = links.get(action);
        return r == null ? null : new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    /** The link at a point, or null. */
    Action actionAt(double mouseX, double mouseY) {
        for (java.util.Map.Entry<Action, int[]> e : links.entrySet()) {
            int[] r = e.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Where the popup's buttons go, one row along the bottom of the info panel. */
    int buttonRowY() {
        return y + rows * 18 + 24 + INFO_HEIGHT - 24;
    }
}
