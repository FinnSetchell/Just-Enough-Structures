package com.finndog.justenoughstructures.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Drawing helpers in the look of vanilla container screens. */
final class Gui {
    static final int PANEL = 0xFFC6C6C6;
    static final int PANEL_LIGHT = 0xFFFFFFFF;
    static final int PANEL_DARK = 0xFF555555;
    static final int EDGE = 0xFF000000;
    static final int SLOT = 0xFF8B8B8B;
    static final int SLOT_DARK = 0xFF373737;
    static final int LABEL = 0xFF404040;
    static final int LABEL_SOFT = 0xFF6A6A6A;
    static final int ROW_HOVER = 0xFFB0B0B0;
    static final int ROW_SELECTED = 0xFF9A9A9A;
    static final int VIEW_TOP = 0xFF6E7C90;
    static final int VIEW_BOTTOM = 0xFF343B48;
    static final int BAR = 0xFF3E8A2A;
    static final int BAR_BACK = 0xFF8A8A8A;

    private Gui() {
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, PANEL_LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, PANEL_DARK);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, PANEL_DARK);
    }

    static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    static void small(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Draws text in {@code maxWidth}: shrunk a little if that's enough to fit, cut short with "..."
     * if it still isn't, so similar names ("Ruined Portal Mountain", "Ruined Portal Swamp") stay
     * tellable apart for as long as possible.
     */
    static void fitted(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = Math.max(0.75f, (float) maxWidth / width);
        String shown = width * scale <= maxWidth ? text : clip(font, text, (int) (maxWidth / scale));
        g.pose().pushPose();
        g.pose().translate(x, y + font.lineHeight * (1f - scale) / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, shown, 0, 0, color, false);
        g.pose().popPose();
    }

    static void scrollbar(GuiGraphics g, int x, int y, int height, double scroll, double maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        g.fill(x, y, x + 3, y + height, 0x30000000);
        int thumb = Math.max(10, (int) (height * height / (height + maxScroll)));
        int top = y + (int) ((height - thumb) * Math.min(1, scroll / maxScroll));
        g.fill(x, top, x + 3, top + thumb, 0xFF6F6F6F);
    }

    static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    /** Draws wrapped text and returns the y below it. */
    static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    static int wrappedHeight(Font font, Component text, int width) {
        return font.split(text, width).size() * (font.lineHeight + 1);
    }
}
