package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.FoundIn;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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

    /** The current selection, for Back and Forward. Must be a value that equals itself when selected again. */
    Object selection() {
        return null;
    }

    void select(Object selection) {
    }

    /** Parses a selection written as text. Test hook for the scripted scenarios. */
    Object parse(String text) {
        return null;
    }

    /** Called when new state arrives from the server. */
    void stateChanged() {
    }

    /** Called before going elsewhere: saves anything typed but not saved yet. */
    void leaving() {
    }

    /** What the menu shows beside this section's name, like how many tables are edited. */
    String count() {
        return "";
    }

    static final int ROW = 22;

    /** The minimum width for a list and its selected item side by side. Narrower, they're shown one at a time. */
    static final int TWO_COLUMNS = 300;

    /** The maximum width for text, forms and lists of odds, so they stay easy to read on a wide screen. */
    static final int READABLE = 460;

    /** Whether a section {@code w} wide shows its list and its selected item one at a time. Then only the visible one scrolls. */
    static boolean oneAtATime(int w) {
        return w < TWO_COLUMNS;
    }

    /** Draws a button back to the list, above the selected item when it's shown alone. Returns the y below it. */
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

    /** Row buttons drawn as an icon only, keyed by their label's lang key. The label becomes the tooltip. */
    private static final Map<String, ResourceLocation> ROW_ICONS = Map.of(
            "screen.justenoughstructures.tools.edit", Icons.EDIT,
            "screen.justenoughstructures.tools.open", Icons.OPEN,
            "screen.justenoughstructures.container.undo", Icons.UNDO_SMALL,
            "screen.justenoughstructures.editor.remove", Icons.REMOVE,
            "screen.justenoughstructures.tools.remove_edit", Icons.REMOVE,
            "screen.justenoughstructures.tools.copy_table", Icons.COPY_SMALL);

    /** A button on a row: what it says, what it does, and a tooltip, or null. A null action greys it out. */
    record RowButton(Component label, Runnable action, Component tip) {
        RowButton(Component label, Runnable action) {
            this(label, action, null);
        }

        static RowButton of(String key, Runnable action) {
            return new RowButton(Component.translatable("screen.justenoughstructures." + key), action);
        }

        /** The icon it's drawn as, or null to draw its label. */
        ResourceLocation icon() {
            return label.getContents() instanceof TranslatableContents words ? ROW_ICONS.get(words.getKey()) : null;
        }

        int width(ToolsUi ui) {
            return icon() != null ? ToolsUi.ICON_BUTTON : ui.buttonWidth(label);
        }

        /** Draws it with its top left at x, y, at most {@code w} wide. */
        void draw(GuiGraphics g, ToolsUi ui, int x, int y, int w) {
            ResourceLocation icon = icon();
            if (icon != null) {
                ui.iconOnlyButton(g, icon, label, x, y, action != null, action, tip);
            } else {
                ui.button(g, label, x, y, w, ToolsUi.BUTTON, action != null, action, tip);
            }
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
        int markW = mark == null ? 0 : badgeWidth(font, mark) + 4;
        int buttonsW = 0;
        for (RowButton b : buttons) {
            buttonsW += b.width(ui) + 2;
        }
        boolean below = !buttons.isEmpty() && x + w - buttonsW - 2 - textX < Math.min(70, font.width(name) + markW);
        int h = ROW;
        if (below) {
            int lines = 1;
            int bx = textX;
            for (RowButton b : buttons) {
                int bw = b.width(ui);
                if (bx > textX && bx + bw > x + w - 2) {
                    lines++;
                    bx = textX;
                }
                bx += bw + 2;
            }
            h = ROW + lines * (ToolsUi.BUTTON + 2) + 1;
        }
        if (selected) {
            g.fill(x, y, x + w, y + h - 1, Gui.ink(0xFF9D9D9D, ToolsUi.PICKED_ON_DARK));
        } else if (background != 0) {
            g.fill(x, y, x + w, y + h - 1, background);
        } else if (click != null && ui.hovered(x, y, w, h - 1)) {
            g.fill(x, y, x + w, y + h - 1, 0x50FFFFFF);
        }
        ui.row(x, y, w, h - 1, click, Component.literal(name));
        int right = x + w - 2;
        if (below) {
            int bx = textX;
            int by = y + ROW;
            for (RowButton b : buttons) {
                int bw = b.width(ui);
                if (bx > textX && bx + bw > x + w - 2) {
                    bx = textX;
                    by += ToolsUi.BUTTON + 2;
                }
                b.draw(g, ui, bx, by, Math.min(bw, x + w - 2 - bx));
                bx += bw + 2;
            }
        } else {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                RowButton b = buttons.get(i);
                int bw = b.width(ui);
                right -= bw;
                b.draw(g, ui, right, y + 4, bw);
                right -= 2;
            }
        }
        int room = right - 2 - textX;
        if (icon != null) {
            icon.draw(g, x + 3, y + 3);
        }
        String shown = Gui.clip(font, name, Math.max(0, room - markW));
        Gui.drawClipped(g, font, name, textX, y + 2, Math.max(0, room - markW), selected ? 0xFFFFFFFF : ToolsUi.TEXT, selected);
        if (mark != null) {
            int markX = textX + font.width(shown) + 4;
            badge(g, font, mark, markX, y + 2, selected ? 0xFFFFFFFF : markColour);
            ui.tooltip(markX, y + 2, badgeWidth(font, mark), Icons.SMALL, mark);
        }
        if (detail != null) {
            Gui.fineClipped(g, font, detail, textX, y + 12, Math.max(0, room), selected ? 0xFFDDDDDD : Gui.labelSoft());
        }
        g.fill(x, y + h - 1, x + w, y + h, Gui.ink(0xFFB0B0B0, ToolsUi.LINE_ON_DARK));
        return h;
    }

    /** A readable template name: the last path part, with the one before it when the last is a single word. */
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

    /** The badge for a mark after a name, by what it says, or null for one that has none. */
    static ResourceLocation markIcon(Component mark) {
        if (!(mark.getContents() instanceof TranslatableContents words)) {
            return null;
        }
        return switch (words.getKey()) {
            case "screen.justenoughstructures.loot.edited" -> Icons.EDITED;
            case "screen.justenoughstructures.loot.edited_changed" -> Icons.EDITED_CHANGED;
            case "screen.justenoughstructures.loot.edited_broken" -> Icons.EDITED_BROKEN;
            case "screen.justenoughstructures.tools.after_reload" -> Icons.AFTER_RELOAD;
            case "screen.justenoughstructures.tools.mark.notes" -> Icons.NOTES;
            case "screen.justenoughstructures.tools.mark.secret" -> Icons.SECRET;
            default -> null;
        };
    }

    static int badgeWidth(Font font, Component mark) {
        return markIcon(mark) != null ? Icons.SMALL : Gui.fineWidth(font, mark.getString());
    }

    /** Draws a mark after a name: its badge in {@code colour}, top left at x, y, or its text if it has no badge. Returns its width. */
    static int badge(GuiGraphics g, Font font, Component mark, int x, int y, int colour) {
        ResourceLocation icon = markIcon(mark);
        if (icon == null) {
            Gui.fine(g, font, mark.getString(), x, y + 1, colour);
        } else {
            Gui.icon(g, icon, x, y, Icons.SMALL, Icons.SMALL, colour, false);
        }
        return badgeWidth(font, mark);
    }

    static int markColour(LootOverrides.Status status) {
        return switch (status) {
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> ToolsUi.changed();
            case BROKEN -> ToolsUi.bad();
            default -> ToolsUi.good();
        };
    }

    /** The status bar text for a table's edit. */
    static Component statusText(LootOverrides.Status status) {
        return Component.translatable("screen.justenoughstructures.editor.status." + status.name().toLowerCase(Locale.ROOT));
    }

    /** The colours of a table's status bar: see {@link ToolsUi#status}. */
    static int statusKind(LootOverrides.Status status) {
        return switch (status) {
            case NONE -> ToolsUi.STATUS_PLAIN;
            case ACTIVE -> ToolsUi.STATUS_GOOD;
            case ORIGINAL_CHANGED, ORIGINAL_MISSING -> ToolsUi.STATUS_CHANGED;
            case BROKEN -> ToolsUi.STATUS_BAD;
        };
    }
}
