package com.finndog.justenoughstructures.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Pack tools' controls, drawn as they're laid out each frame: buttons, links, switches, check boxes
 * and chips that can sit in rows of a scrolling list. Each one drawn leaves a spot to click, and the
 * last drawn on top wins, so a button on a row is clicked rather than the row under it.
 */
final class ToolsUi {
    static final int BUTTON = 14;
    static final int TEXT = 0xFF404040;
    static int good() {
        return Gui.ink(0xFF2E5B1D, Gui.GOOD_ON_DARK);
    }

    static int changed() {
        return Gui.ink(0xFF9A6200, Gui.WARN_ON_DARK);
    }

    static int bad() {
        return Gui.ink(0xFFB02020, Gui.BAD_ON_DARK);
    }

    static int link() {
        return Gui.ink(0xFF3A55A0, Gui.LINK_ON_DARK);
    }

    private static int linkHovered() {
        return Gui.ink(0xFF2040C0, Gui.LINK_HOVER_ON_DARK);
    }
    static final int SELECTED = 0xFF4B5280;
    /**
     * Over a dark panel, see-through shades of whatever the panel is: a list's picked row a little
     * lighter, and the lines between rows a little darker.
     */
    static final int PICKED_ON_DARK = 0x40FFFFFF;
    static final int LINE_ON_DARK = 0x40000000;
    /** The kinds of {@link #status} bar, by how what it says stands. */
    static final int STATUS_PLAIN = 0;
    static final int STATUS_GOOD = 1;
    static final int STATUS_CHANGED = 2;
    static final int STATUS_BAD = 3;
    static final int STATUS_NEW = 4;

    private final Font font;
    private final List<Spot> spots = new ArrayList<>();
    /** The scroll bars drawn this frame, and the one a press took hold of. */
    private final List<Scroller> bars = new ArrayList<>();
    private HeldBar held;
    private int mouseX;
    private int mouseY;
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;
    private List<Component> tooltip;

    private record Spot(int x, int y, int w, int h, int clipTop, int clipBottom, Runnable action, String label) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBottom;
        }
    }

    ToolsUi(Font font) {
        this.font = font;
    }

    /** Starts a frame. {@code mouseX} and {@code mouseY} are -1 when something else has the mouse, like a popup. */
    void begin(int mouseX, int mouseY) {
        spots.clear();
        bars.clear();
        tooltip = null;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        unclip();
    }

    /** Keeps what's drawn next from being clicked or hovered outside these rows, as in a scrolled list. */
    void clip(int top, int bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    void unclip() {
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
    }

    boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
    }

    /** A spot to click with nothing drawn, like a whole row. Draw what's on the row after it. */
    void spot(int x, int y, int w, int h, Runnable action) {
        spot(x, y, w, h, action, null);
    }

    /** The same with a name, for a button that's a picture: the name finds it for the screenshot harness. */
    void spot(int x, int y, int w, int h, Runnable action, String label) {
        if (action != null) {
            spots.add(new Spot(x, y, w, h, clipTop, clipBottom, action, label));
        }
    }

    /** The middle of the last button or link drawn with this label, for the screenshot harness, or null. */
    int[] centre(String label) {
        for (int i = spots.size() - 1; i >= 0; i--) {
            Spot spot = spots.get(i);
            if (label.equals(spot.label())) {
                return new int[]{spot.x() + spot.w() / 2, spot.y() + spot.h() / 2};
            }
        }
        return null;
    }

    void tooltip(int x, int y, int w, int h, Component... lines) {
        if (lines.length > 0 && lines[0] != null && hovered(x, y, w, h)) {
            tooltip = List.of(lines);
        }
    }

    List<Component> tooltip() {
        return tooltip;
    }

    /** A scroll bar drawn this frame, for a press to take hold of. */
    void bar(Scroller scroller) {
        bars.add(scroller);
    }

    /** A point part of the way down one of this frame's scroll bars, the first being 0, for the screenshot harness, or null. */
    int[] barPoint(int index, float along) {
        return index < bars.size() ? bars.get(index).barPoint(along) : null;
    }

    /** How far the list with that scroll bar is scrolled, or -1 without one. */
    double scrolled(int index) {
        return index < bars.size() ? bars.get(index).offset() : -1;
    }

    /** Moves the list whose scroll bar is held, if one is. */
    boolean drag(double my) {
        if (held == null) {
            return false;
        }
        held.dragTo(my);
        return true;
    }

    /** The mouse button came up: lets go of a scroll bar. */
    void release() {
        if (held != null) {
            held.letGo();
            held = null;
        }
    }

    boolean click(double mx, double my) {
        for (int i = bars.size() - 1; i >= 0; i--) {
            held = bars.get(i).grab(mx, my);
            if (held != null) {
                return true;
            }
        }
        for (int i = spots.size() - 1; i >= 0; i--) {
            Spot spot = spots.get(i);
            if (spot.contains(mx, my)) {
                spot.action().run();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ controls

    int buttonWidth(Component label) {
        return font.width(label) + 10;
    }

    /** A vanilla button, {@link #BUTTON} high unless said otherwise. Returns its width. */
    int button(GuiGraphics g, Component label, int x, int y, boolean active, Runnable action, Component... tip) {
        return button(g, label, x, y, buttonWidth(label), BUTTON, active, action, tip);
    }

    int button(GuiGraphics g, Component label, int x, int y, int w, int h, boolean active, Runnable action, Component... tip) {
        boolean over = hovered(x, y, w, h);
        int state = !active ? 0 : over ? 2 : 1;
        Gui.buttonBackground(g, x, y, w, h, state);
        String text = Gui.clip(font, label.getString(), w - 6);
        Gui.drawClipped(g, font, label.getString(), x + (w - font.width(text) + 1) / 2, y + (h - 8) / 2, w - 6, active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
        if (active) {
            spot(x, y, w, h, action, label.getString());
        }
        tooltip(x, y, w, h, tip);
        return w;
    }

    int backButtonWidth(Component label) {
        return font.width(label) + 19;
    }

    /** A button with an arrow pointing back before its label. */
    int backButton(GuiGraphics g, Component label, int x, int y, boolean active, Runnable action, Component... tip) {
        int w = backButtonWidth(label);
        boolean over = hovered(x, y, w, BUTTON);
        Gui.buttonBackground(g, x, y, w, BUTTON, !active ? 0 : over ? 2 : 1);
        int colour = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        NavBar.chevron(g, x + 5, y + 3, colour, true);
        g.drawString(font, label, x + 14, y + 3, colour, true);
        if (active) {
            spot(x, y, w, BUTTON, action);
        }
        tooltip(x, y, w, BUTTON, tip);
        return w;
    }

    /** A button lit up while it's the one chosen, like the section on show, with an item drawn small before its label. */
    void choice(GuiGraphics g, ItemStack icon, Component label, String count, int x, int y, int w, int h, boolean chosen, Runnable action) {
        if (chosen) {
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SELECTED);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF8F96C8);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF8F96C8);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF2B3060);
        } else {
            boolean over = hovered(x, y, w, h);
            Gui.buttonBackground(g, x, y, w, h, over ? 2 : 1);
        }
        int countWidth = count.isEmpty() ? 0 : Gui.fineWidth(font, count) + 4;
        Gui.push(g);
        Gui.translate(g, x + 3, y + (h - 12) / 2f);
        Gui.scale(g, 0.75f);
        g.renderItem(icon, 0, 0);
        Gui.pop(g);
        Gui.drawClipped(g, font, label.getString(), x + 18, y + (h - 8) / 2, w - 23 - countWidth, 0xFFFFFFFF, true);
        if (!count.isEmpty()) {
            Gui.fine(g, font, count, x + w - 4 - Gui.fineWidth(font, count), y + (h - Gui.fineLine(font)) / 2 + 1, 0xFFFFD27A);
        }
        spot(x, y, w, h, action);
    }

    /** Text that can be clicked, underlined under the mouse. Returns its width. */
    int link(GuiGraphics g, Component label, int x, int y, boolean fine, Runnable action, Component... tip) {
        String text = label.getString();
        int w = fine ? Gui.fineWidth(font, text) : font.width(text);
        int h = fine ? Gui.fineLine(font) : font.lineHeight;
        boolean over = hovered(x, y - 1, w, h + 2);
        if (fine) {
            Gui.fine(g, font, text, x, y, over ? linkHovered() : link());
        } else {
            g.drawString(font, text, x, y, over ? linkHovered() : link(), false);
        }
        if (over) {
            g.fill(x, y + h, x + w, y + h + 1, linkHovered());
        }
        spot(x, y - 1, w, h + 2, action, text);
        tooltip(x, y - 1, w, h + 2, tip);
        return w;
    }

    /**
     * A 9 pixel icon that's a button, its top left at x, y. The box behind it lights up in {@code lit}
     * under the mouse. {@code name} is its tooltip, and {@code label}, when there is one, finds it for
     * the screenshot harness.
     */
    void iconButton(GuiGraphics g, int x, int y, ResourceLocation icon, int lit, Component name, String label, Runnable action) {
        iconButton(g, x, y, icon, lit, 0xFFFFFFFF, true, name, label, action);
    }

    /** The same, drawn in {@code ink} with the soft shadow until the mouse is over it, for one on a light panel. */
    void iconButton(GuiGraphics g, int x, int y, ResourceLocation icon, int lit, int ink, Component name, String label, Runnable action) {
        iconButton(g, x, y, icon, lit, ink, false, name, label, action);
    }

    private void iconButton(GuiGraphics g, int x, int y, ResourceLocation icon, int lit, int ink, boolean onButton, Component name, String label, Runnable action) {
        if (hovered(x - 1, y - 2, 11, 12)) {
            g.fill(x - 1, y - 2, x + 10, y + 10, lit);
            Gui.icon(g, icon, x, y, Icons.SMALL);
        } else {
            Gui.icon(g, icon, x, y, Icons.SMALL, Icons.SMALL, ink, onButton);
        }
        spot(x - 1, y - 2, 11, 12, action, label);
        tooltip(x - 1, y - 2, 11, 12, name);
    }

    /** A sliding switch, 16 by 9, green when on. */
    void toggle(GuiGraphics g, int x, int y, boolean on, Runnable action, Component... tip) {
        g.fill(x, y, x + 17, y + 10, 0xFF000000);
        g.fill(x + 1, y + 1, x + 16, y + 9, on ? 0xFF4F7A3A : 0xFF555555);
        // The knob is a little piece of the panel, in the chest texture's own colours, so a pack's.
        int knob = on ? x + 9 : x + 1;
        Gui.tone(g, knob, y + 1, knob + 7, y + 9, Gui.Tone.PANEL);
        Gui.tone(g, knob, y + 1, knob + 6, y + 2, Gui.Tone.LIGHT);
        Gui.tone(g, knob, y + 1, knob + 1, y + 8, Gui.Tone.LIGHT);
        Gui.tone(g, knob + 6, y + 1, knob + 7, y + 9, Gui.Tone.DARK);
        Gui.tone(g, knob, y + 8, knob + 7, y + 9, Gui.Tone.DARK);
        if (hovered(x - 1, y - 1, 19, 12)) {
            g.fill(x - 1, y - 1, x + 18, y, 0xFFFFFFFF);
            g.fill(x - 1, y + 10, x + 18, y + 11, 0xFFFFFFFF);
        }
        spot(x - 1, y - 1, 19, 12, action);
        tooltip(x - 1, y - 1, 19, 12, tip);
    }

    /** A box ticked when on, and its label beside it, both clickable. Returns the width. */
    int check(GuiGraphics g, Component label, int x, int y, boolean on, Runnable action, Component... tip) {
        int w = 12 + font.width(label);
        checkBox(g, x, y, on, hovered(x, y - 1, w, 11));
        g.drawString(font, label, x + 12, y + 1, TEXT, false);
        spot(x, y - 1, w, 11, action, label.getString());
        tooltip(x, y - 1, w, 11, tip);
        return w;
    }

    /** A check box whose label wraps onto more lines when it's wider than {@code w} allows. Returns its height. */
    int wrappedCheck(GuiGraphics g, Component label, int x, int y, int w, boolean on, Runnable action, Component... tip) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(label, Math.max(10, w - 12));
        int h = checkHeight(label, w);
        int textW = 0;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int spotW = Math.min(w, 12 + textW);
        checkBox(g, x, y, on, hovered(x, y - 1, spotW, h + 1));
        int ly = y + 1;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 12, ly, TEXT, false);
            ly += font.lineHeight + 1;
        }
        spot(x, y - 1, spotW, h + 1, action, label.getString());
        tooltip(x, y - 1, spotW, h + 1, tip);
        return h;
    }

    /** How tall {@link #wrappedCheck} is with this label in this width. */
    int checkHeight(Component label, int w) {
        return Math.max(1, font.split(label, Math.max(10, w - 12)).size()) * (font.lineHeight + 1);
    }

    private void checkBox(GuiGraphics g, int x, int y, boolean on, boolean over) {
        g.fill(x, y, x + 9, y + 9, over ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + 8, y + 8, 0xFF000000);
        if (on) {
            Gui.checkmark(g, x, y);
        }
    }

    /** How wide a button that's an icon alone is: room for a 9 pixel icon. */
    static final int ICON_BUTTON = Icons.SMALL + 6;

    /**
     * A {@link #BUTTON} high button that's an icon alone: its words are its tooltip, over {@code tip}
     * when there is one, and its name for the screenshot harness. Returns its width.
     */
    int iconOnlyButton(GuiGraphics g, ResourceLocation icon, Component label, int x, int y, boolean active, Runnable action, Component tip) {
        boolean over = hovered(x, y, ICON_BUTTON, BUTTON);
        Gui.buttonBackground(g, x, y, ICON_BUTTON, BUTTON, !active ? 0 : over ? 2 : 1);
        Gui.icon(g, icon, x + 3, y + (BUTTON - Icons.SMALL) / 2, Icons.SMALL, Icons.SMALL, active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
        if (active) {
            spot(x, y, ICON_BUTTON, BUTTON, action, label.getString());
        }
        if (tip == null) {
            tooltip(x, y, ICON_BUTTON, BUTTON, label);
        } else {
            tooltip(x, y, ICON_BUTTON, BUTTON, label, tip);
        }
        return ICON_BUTTON;
    }

    /** How wide {@link #removableChip} is with this label. */
    int removableChipWidth(String label) {
        return Gui.fineWidth(font, label) + 6 + Icons.SMALL + 1;
    }

    /** A small tag with the remove icon after its label, which takes it away. Returns its width. */
    int removableChip(GuiGraphics g, String label, int x, int y, Runnable action, Component... tip) {
        int w = removableChipWidth(label);
        int h = Gui.fineLine(font) + 4;
        boolean over = hovered(x, y, w, h);
        Gui.outlined(g, x, y, w, h, Gui.ink(0xFF777777, 0x80000000), over ? Gui.ink(0xFFC0C0C0, 0x48FFFFFF) : Gui.ink(0xFFA8A8A8, 0x28FFFFFF));
        int ink = Gui.ink(0xFF303030, 0xFFE0E0E0);
        Gui.fine(g, font, label, x + 3, y + 2, ink);
        Gui.icon(g, Icons.REMOVE, x + w - Icons.SMALL - 2, y + (h - Icons.SMALL) / 2, Icons.SMALL, Icons.SMALL, over ? bad() : ink, false);
        spot(x, y, w, h, action);
        tooltip(x, y, w, h, tip);
        return w;
    }

    /** A small tag, lit up when on. Returns its width. */
    int chip(GuiGraphics g, String label, int x, int y, boolean on, Runnable action, Component... tip) {
        int w = Gui.fineWidth(font, label) + 6;
        int h = Gui.fineLine(font) + 4;
        boolean over = hovered(x, y, w, h);
        Gui.outlined(g, x, y, w, h, on ? 0xFF2B3060 : Gui.ink(0xFF777777, 0x80000000),
                on ? SELECTED : over ? Gui.ink(0xFFC0C0C0, 0x48FFFFFF) : Gui.ink(0xFFA8A8A8, 0x28FFFFFF));
        Gui.fine(g, font, label, x + 3, y + 2, on ? 0xFFFFFFFF : Gui.ink(0xFF303030, 0xFFE0E0E0));
        spot(x, y, w, h, action);
        tooltip(x, y, w, h, tip);
        return w;
    }

    int chipHeight() {
        return Gui.fineLine(font) + 4;
    }

    /** A section's heading, with a line under it. Returns the y below it. */
    int heading(GuiGraphics g, Component title, int x, int y, int w) {
        g.drawString(font, title, x, y, TEXT, false);
        g.fill(x, y + 10, x + w, y + 11, 0xFF8B8B8B);
        return y + 14;
    }

    /** A status bar's fill and text colours, by its kind. Over a dark panel they swap round: a dark bar with light text. */
    static int[] statusColours(int kind) {
        int[] colours = switch (kind) {
            case STATUS_GOOD -> new int[]{0xFFCFE8C0, 0xFF24451A};
            case STATUS_CHANGED -> new int[]{0xFFF1DCAE, 0xFF5A3A00};
            case STATUS_BAD -> new int[]{0xFFF0C0C0, 0xFF6A1010};
            case STATUS_NEW -> new int[]{0xFFC6D4F0, 0xFF1F2F60};
            default -> new int[]{0xFFD6D6D6, 0xFF505050};
        };
        return Gui.darkPanel() ? new int[]{colours[1], colours[0]} : colours;
    }

    /** A coloured bar with a line of text, for how something stands. Returns its height. */
    int status(GuiGraphics g, Component text, int x, int y, int w, int kind) {
        int[] colours = statusColours(kind);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, w - 8);
        int h = Math.max(1, lines.size()) * (font.lineHeight + 1) + 3;
        g.fill(x, y, x + w, y + h, colours[0]);
        int ly = y + 2;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 4, ly, colours[1], false);
            ly += font.lineHeight + 1;
        }
        return h;
    }
}
