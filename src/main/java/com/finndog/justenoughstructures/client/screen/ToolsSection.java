package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One part of Pack tools, drawn in the space right of its menu. */
abstract class ToolsSection {
    protected final PackToolsScreen screen;
    protected final Font font;

    ToolsSection(PackToolsScreen screen) {
        this.screen = screen;
        this.font = Minecraft.getInstance().font;
    }

    /** Adds any text boxes, each time the screen is laid out. */
    void init(int x, int y, int w, int h) {
    }

    abstract void render(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h, int mouseX, int mouseY);

    /** Drawn over everything else, like a list of suggestions under a text box. */
    void renderOver(GuiGraphics g, ToolsUi ui, int mouseX, int mouseY) {
    }

    /** A click before the screen's widgets get it. */
    boolean clickFirst(double mouseX, double mouseY) {
        return false;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        return false;
    }

    boolean keyPressed(int key, int modifiers) {
        return false;
    }

    void tick() {
    }

    /** What's picked, for Back and Forward. Must be a value that equals itself when picked again. */
    Object selection() {
        return null;
    }

    void select(Object selection) {
    }

    /** A selection written as text, for the screenshot harness. */
    Object parse(String text) {
        return null;
    }

    /** The server said how things stand. */
    void stateChanged() {
    }

    /** About to go elsewhere: anything typed and not saved yet is saved. */
    void leaving() {
    }

    /** What the menu shows beside this section's name, like how many tables are edited. */
    String count() {
        return "";
    }

    static final int ROW = 22;

    /** The least width a list and what's picked in it share. Narrower, they're shown one at a time. */
    static final int TWO_COLUMNS = 300;

    /** The widest text, forms and lists of odds get, so they stay easy to read on a wide screen. */
    static final int READABLE = 460;

    /** Whether a section {@code w} wide shows its list and what's picked in it one at a time. */
    static boolean oneAtATime(int w) {
        return w < TWO_COLUMNS;
    }

    /** Above what's picked when it's shown on its own: a way back to the list. Returns the y below it. */
    int backToList(GuiGraphics g, ToolsUi ui, int x, int y) {
        ui.backButton(g, screen.section().label(), x, y, true, () -> screen.pick(null));
        return y + ToolsUi.BUTTON + 4;
    }

    /** What's drawn at the left of a row. */
    interface Icon {
        void draw(GuiGraphics g, int x, int y);

        static Icon item(ItemStack stack) {
            return (g, x, y) -> g.renderItem(stack, x, y);
        }

        /** A structure's picture from the browser's list, or a map until it has one. */
        static Icon structure(ResourceLocation id) {
            return (g, x, y) -> {
                if (!Thumbnails.draw(g, id, x, y, 16)) {
                    g.renderItem(MAP, x, y);
                }
            };
        }
    }

    private static final ItemStack MAP = new ItemStack(Items.FILLED_MAP);
    static final ItemStack CHEST = new ItemStack(Items.CHEST);

    /** A button on a row: what it says, what it does, and a tooltip, or null. A null action greys it out. */
    record RowButton(Component label, Runnable action, Component tip) {
        RowButton(Component label, Runnable action) {
            this(label, action, null);
        }

        static RowButton of(String key, Runnable action) {
            return new RowButton(Component.translatable("screen.justenoughstructures." + key), action);
        }
    }

    /**
     * A row in a list: an icon, a name with a mark after it, a line under it, and buttons on the
     * right, or under the name when beside it they'd leave it too little room. Clicking the rest of
     * the row does {@code click}, if there is one. Returns its height.
     */
    int row(GuiGraphics g, ToolsUi ui, int x, int y, int w, Icon icon, String name, Component mark, int markColour,
            String detail, List<RowButton> buttons, Runnable click, int background, boolean selected) {
        int textX = x + 22;
        String markText = mark == null ? "" : mark.getString();
        int markW = markText.isEmpty() ? 0 : Gui.fineWidth(font, markText) + 4;
        int buttonsW = 0;
        for (RowButton b : buttons) {
            buttonsW += ui.buttonWidth(b.label()) + 2;
        }
        boolean below = !buttons.isEmpty() && x + w - buttonsW - 2 - textX < Math.min(70, font.width(name) + markW);
        int h = ROW;
        if (below) {
            int lines = 1;
            int bx = textX;
            for (RowButton b : buttons) {
                int bw = ui.buttonWidth(b.label());
                if (bx > textX && bx + bw > x + w - 2) {
                    lines++;
                    bx = textX;
                }
                bx += bw + 2;
            }
            h = ROW + lines * (ToolsUi.BUTTON + 2) + 1;
        }
        if (selected) {
            g.fill(x, y, x + w, y + h - 1, 0xFF9D9D9D);
        } else if (background != 0) {
            g.fill(x, y, x + w, y + h - 1, background);
        } else if (click != null && ui.hovered(x, y, w, h - 1)) {
            g.fill(x, y, x + w, y + h - 1, 0x50FFFFFF);
        }
        ui.spot(x, y, w, h - 1, click);
        int right = x + w - 2;
        if (below) {
            int bx = textX;
            int by = y + ROW;
            for (RowButton b : buttons) {
                int bw = ui.buttonWidth(b.label());
                if (bx > textX && bx + bw > x + w - 2) {
                    bx = textX;
                    by += ToolsUi.BUTTON + 2;
                }
                ui.button(g, b.label(), bx, by, Math.min(bw, x + w - 2 - bx), ToolsUi.BUTTON, b.action() != null, b.action(), b.tip());
                bx += bw + 2;
            }
        } else {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                RowButton b = buttons.get(i);
                int bw = ui.buttonWidth(b.label());
                right -= bw;
                ui.button(g, b.label(), right, y + 4, b.action() != null, b.action(), b.tip());
                right -= 2;
            }
        }
        int room = right - 2 - textX;
        if (icon != null) {
            icon.draw(g, x + 3, y + 3);
        }
        String shown = Gui.clip(font, name, Math.max(0, room - markW));
        Gui.drawClipped(g, font, name, textX, y + 2, Math.max(0, room - markW), selected ? 0xFFFFFFFF : ToolsUi.TEXT, selected);
        if (!markText.isEmpty()) {
            int markX = textX + font.width(shown) + 4;
            Gui.fineClipped(g, font, markText, markX, y + 3, x + w - markX, selected ? 0xFFFFFFFF : markColour);
        }
        if (detail != null) {
            Gui.fineClipped(g, font, detail, textX, y + 12, Math.max(0, room), selected ? 0xFFDDDDDD : Gui.LABEL_SOFT);
        }
        g.fill(x, y + h - 1, x + w, y + h, 0xFFB0B0B0);
        return h;
    }

    /** Where a container's template came from, readably: its last part, and the one before if that's a single word. */
    static String templateName(ResourceLocation template) {
        String[] parts = template.getPath().split("/");
        String last = StructureNames.pretty(parts[parts.length - 1]);
        if (parts.length > 1 && !last.contains(" ")) {
            return StructureNames.pretty(parts[parts.length - 2]) + " " + last;
        }
        return last;
    }

    /** The structures using a loot table, as "used by A, B, C and 3 more". */
    static String usedBy(ResourceLocation table) {
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : FoundIn.structuresUsing(table)) {
            names.add(StructureNames.structure(id));
        }
        if (names.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.tools.used_by_none").getString();
        }
        names.sort(null);
        String shown = String.join(", ", names.subList(0, Math.min(3, names.size())));
        return names.size() > 3
                ? Component.translatable("screen.justenoughstructures.tools.used_by_more", shown, names.size() - 3).getString()
                : Component.translatable("screen.justenoughstructures.tools.used_by", shown).getString();
    }

    /** The mark on an edited table, as the browser shows it, or null. */
    static Component editedMark(LootOverrides.Status status) {
        if (status == null || status == LootOverrides.Status.NONE) {
            return null;
        }
        String key = switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> "edited_changed";
            case BROKEN -> "edited_broken";
            default -> "edited";
        };
        return Component.translatable("screen.justenoughstructures.loot." + key);
    }

    static int markColour(LootOverrides.Status status) {
        return switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> ToolsUi.CHANGED;
            case BROKEN -> ToolsUi.BAD;
            default -> ToolsUi.GOOD;
        };
    }

    /** A part of the section that scrolls on its own: drawn between {@link #begin} and {@link #end}. */
    static final class Scroller {
        private double offset;
        private int content;
        private int x, y, w, h;

        /** Starts drawing; returns where the content's top is now. */
        int begin(GuiGraphics g, ToolsUi ui, int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            clamp();
            Gui.scissor(g, x, y, x + w, y + h);
            ui.clip(y, y + h);
            return y - (int) offset;
        }

        /** How wide the content can be, leaving room for the scroll bar when it needs one. */
        int width() {
            return content > h ? w - 8 : w;
        }

        void end(GuiGraphics g, ToolsUi ui, int contentHeight) {
            content = contentHeight;
            Gui.endScissor(g);
            ui.unclip();
            clamp();
            if (content > h) {
                Gui.scrollbar(g, x + w - 4, y, h, offset, content - h);
            }
        }

        boolean scroll(double mouseX, double mouseY, double delta) {
            if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
                return false;
            }
            offset -= delta * 20;
            clamp();
            return true;
        }

        void reset() {
            offset = 0;
        }

        /**
         * Scrolls so a row from {@code top} to {@code bottom}, in content terms, is in view: just far
         * enough when part of it shows, and near the top, with the row before it, when none of it does.
         */
        void reveal(int top, int bottom) {
            if (bottom <= offset || top >= offset + h) {
                offset = Math.max(0, top - (bottom - top));
            } else if (top < offset) {
                offset = top;
            } else if (bottom > offset + h) {
                offset = bottom - h;
            }
        }

        private void clamp() {
            offset = Math.max(0, Math.min(offset, Math.max(0, content - h)));
        }
    }
}
