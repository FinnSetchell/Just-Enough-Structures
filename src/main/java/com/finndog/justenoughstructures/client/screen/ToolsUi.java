package com.finndog.justenoughstructures.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Pack tools' controls, drawn as they're laid out each frame: buttons, links, switches, check boxes
 * and chips that can sit in rows of a scrolling list. Each one drawn leaves a spot to click, and the
 * last drawn on top wins, so a button on a row is clicked rather than the row under it.
 */
final class ToolsUi {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    static final int BUTTON = 14;
    static final int TEXT = 0xFF404040;
    static final int GOOD = 0xFF2E5B1D;
    static final int CHANGED = 0xFF9A6200;
    static final int BAD = 0xFFB02020;
    static final int NEW = 0xFF3A55A0;
    static final int LINK = 0xFF3A55A0;
    static final int SELECTED = 0xFF4B5280;

    private final Font font;
    private final List<Spot> spots = new ArrayList<>();
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

    private void spot(int x, int y, int w, int h, Runnable action, String label) {
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

    boolean click(double mx, double my) {
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
        g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 46 + state * 20);
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
        g.blitNineSliced(WIDGETS, x, y, w, BUTTON, 20, 4, 200, 20, 0, 46 + (!active ? 0 : over ? 2 : 1) * 20);
        int colour = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        NavBar.chevron(g, x + 5, y + 3, colour, true);
        g.drawString(font, label, x + 14, y + 3, colour, true);
        if (active) {
            spot(x, y, w, BUTTON, action);
        }
        tooltip(x, y, w, BUTTON, tip);
        return w;
    }

    /** A button lit up while it's the one chosen, like the section on show. */
    void choice(GuiGraphics g, Component label, String count, int x, int y, int w, int h, boolean chosen, Runnable action) {
        if (chosen) {
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SELECTED);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF8F96C8);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF8F96C8);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF2B3060);
        } else {
            boolean over = hovered(x, y, w, h);
            g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 46 + (over ? 2 : 1) * 20);
        }
        int countWidth = count.isEmpty() ? 0 : Gui.fineWidth(font, count) + 4;
        Gui.drawClipped(g, font, label.getString(), x + 5, y + (h - 8) / 2, w - 10 - countWidth, 0xFFFFFFFF, true);
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
            Gui.fine(g, font, text, x, y, over ? 0xFF2040C0 : LINK);
        } else {
            g.drawString(font, text, x, y, over ? 0xFF2040C0 : LINK, false);
        }
        if (over) {
            g.fill(x, y + h, x + w, y + h + 1, 0xFF2040C0);
        }
        spot(x, y - 1, w, h + 2, action, text);
        tooltip(x, y - 1, w, h + 2, tip);
        return w;
    }

    /** A sliding switch, 16 by 9, green when on. */
    void toggle(GuiGraphics g, int x, int y, boolean on, Runnable action, Component... tip) {
        g.fill(x, y, x + 17, y + 10, 0xFF000000);
        g.fill(x + 1, y + 1, x + 16, y + 9, on ? 0xFF4F7A3A : 0xFF555555);
        int knob = on ? x + 9 : x + 1;
        g.fill(knob, y + 1, knob + 7, y + 9, Gui.PANEL);
        g.fill(knob, y + 1, knob + 6, y + 2, 0xFFFFFFFF);
        g.fill(knob, y + 1, knob + 1, y + 8, 0xFFFFFFFF);
        g.fill(knob + 6, y + 1, knob + 7, y + 9, 0xFF555555);
        g.fill(knob, y + 8, knob + 7, y + 9, 0xFF555555);
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
            for (int i = 0; i < 5; i++) {
                g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFFFFFFFF);
                g.fill(x + 6 - i, y + 2 + i, x + 7 - i, y + 3 + i, 0xFFFFFFFF);
            }
        }
    }

    /** A small tag, lit up when on. Returns its width. */
    int chip(GuiGraphics g, String label, int x, int y, boolean on, Runnable action, Component... tip) {
        int w = Gui.fineWidth(font, label) + 6;
        int h = Gui.fineLine(font) + 4;
        boolean over = hovered(x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? 0xFF2B3060 : 0xFF777777);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? SELECTED : over ? 0xFFC0C0C0 : 0xFFA8A8A8);
        Gui.fine(g, font, label, x + 3, y + 2, on ? 0xFFFFFFFF : 0xFF303030);
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

    /** A coloured bar with a line of text, for how something stands. Returns its height. */
    int status(GuiGraphics g, Component text, int x, int y, int w, int kind) {
        int[] colours = switch (kind) {
            case 1 -> new int[]{0xFFCFE8C0, 0xFF24451A};
            case 2 -> new int[]{0xFFF1DCAE, 0xFF5A3A00};
            case 3 -> new int[]{0xFFF0C0C0, 0xFF6A1010};
            case 4 -> new int[]{0xFFC6D4F0, 0xFF1F2F60};
            default -> new int[]{0xFFD6D6D6, 0xFF505050};
        };
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
