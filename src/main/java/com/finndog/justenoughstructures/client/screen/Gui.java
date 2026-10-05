package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing helpers in the look JEI uses, which is the vanilla container look: the same frames,
 * slots and buttons, with title rows of white text on a dark band.
 */
public final class Gui {

    //? if >=1.21 {
    /*private static final ResourceLocation BUTTON = Ids.parse("widget/button");
    private static final ResourceLocation BUTTON_DISABLED = Ids.parse("widget/button_disabled");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = Ids.parse("widget/button_highlighted");
    *///?} else {
    private static final ResourceLocation WIDGETS = Ids.parse("textures/gui/widgets.png");
    //?}

    /** A vanilla button's background stretched to size: {@code state} 0 for off, 1 normal, 2 under the mouse. */
    public static void buttonBackground(GuiGraphics g, int x, int y, int w, int h, int state) {
        //? if >=1.21 {
        /*g.blitSprite(state == 0 ? BUTTON_DISABLED : state == 2 ? BUTTON_HIGHLIGHTED : BUTTON, x, y, w, h);
        *///?} else {
        g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 46 + state * 20);
        //?}
    }
    static final int PANEL = 0xFFC6C6C6;
    static final int PANEL_LIGHT = 0xFFFFFFFF;
    static final int PANEL_DARK = 0xFF555555;
    static final int EDGE = 0xFF000000;
    static final int SLOT = 0xFF8B8B8B;
    static final int SLOT_DARK = 0xFF373737;
    /** Body text, as in JEI's recipe layouts. */
    public static final int LABEL = 0xFF000000;
    /** Secondary text, as JEI's cook times and other recipe details. */
    public static final int LABEL_SOFT = 0xFF808080;
    /** Row highlights: JEI's slot highlight, and a darker one for what's picked. */
    static final int ROW_HOVER = 0x80FFFFFF;
    static final int ROW_SELECTED = 0x40000000;
    /** The translucent strip behind white titles in JEI's title and page rows. */
    static final int BAND = 0x44000000;
    static final int VIEW_TOP = 0xFF6E7C90;
    static final int VIEW_BOTTOM = 0xFF343B48;
    static final int BAR = 0xFF3E8A2A;
    static final int BAR_BACK = 0xFF8A8A8A;

    private Gui() {
    }

    /** The vanilla container frame, corner pixels and all, which is also JEI's panel. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;
        g.fill(x + 3, y + 3, r - 3, b - 3, PANEL);
        // Top edge and its corners.
        g.fill(x + 2, y, r - 3, y + 1, EDGE);
        g.fill(x + 1, y + 1, x + 2, y + 2, EDGE);
        g.fill(x + 2, y + 1, r - 3, y + 2, PANEL_LIGHT);
        g.fill(r - 3, y + 1, r - 2, y + 2, EDGE);
        g.fill(x, y + 2, x + 1, y + 3, EDGE);
        g.fill(x + 1, y + 2, r - 3, y + 3, PANEL_LIGHT);
        g.fill(r - 3, y + 2, r - 2, y + 3, PANEL);
        g.fill(r - 2, y + 2, r - 1, y + 3, EDGE);
        // Sides.
        g.fill(x, y + 3, x + 1, b - 3, EDGE);
        g.fill(x + 1, y + 3, x + 3, b - 3, PANEL_LIGHT);
        g.fill(x + 3, y + 3, x + 4, y + 4, PANEL_LIGHT);
        g.fill(r - 1, y + 3, r, b - 2, EDGE);
        g.fill(r - 3, y + 3, r - 1, b - 3, PANEL_DARK);
        g.fill(r - 4, b - 4, r - 3, b - 3, PANEL_DARK);
        // Bottom edge and its corners.
        g.fill(x + 1, b - 3, x + 2, b - 2, EDGE);
        g.fill(x + 2, b - 3, x + 3, b - 2, PANEL);
        g.fill(x + 3, b - 3, r - 1, b - 2, PANEL_DARK);
        g.fill(x + 2, b - 2, x + 3, b - 1, EDGE);
        g.fill(x + 3, b - 2, r - 2, b - 1, PANEL_DARK);
        g.fill(r - 2, b - 2, r - 1, b - 1, EDGE);
        g.fill(x + 3, b - 1, r - 2, b, EDGE);
    }

    /**
     * A category tab sitting on top of a panel whose top edge is at {@code y + 21}, like JEI's. The
     * selected one is panel coloured and joins the panel; the others are darker and sit behind it.
     */
    static void tab(GuiGraphics g, int x, int y, boolean selected) {
        int r = x + 24;
        int fill = selected ? PANEL : SLOT;
        int bottom = y + 21;
        g.fill(x + 3, y + 3, r - 3, bottom, fill);
        g.fill(x + 2, y, r - 3, y + 1, EDGE);
        g.fill(x + 1, y + 1, x + 2, y + 2, EDGE);
        g.fill(x + 2, y + 1, r - 3, y + 2, PANEL_LIGHT);
        g.fill(r - 3, y + 1, r - 2, y + 2, EDGE);
        g.fill(x, y + 2, x + 1, y + 3, EDGE);
        g.fill(x + 1, y + 2, r - 3, y + 3, PANEL_LIGHT);
        g.fill(r - 3, y + 2, r - 2, y + 3, fill);
        g.fill(r - 2, y + 2, r - 1, y + 3, EDGE);
        g.fill(x, y + 3, x + 1, bottom, EDGE);
        g.fill(x + 1, y + 3, x + 3, bottom, PANEL_LIGHT);
        g.fill(x + 3, y + 3, x + 4, y + 4, PANEL_LIGHT);
        g.fill(r - 1, y + 3, r, bottom, EDGE);
        g.fill(r - 3, y + 3, r - 1, bottom, PANEL_DARK);
        if (selected) {
            // Run on into the panel, over its top edge, so the two read as one piece.
            g.fill(x, bottom, x + 1, bottom + 1, EDGE);
            g.fill(x + 1, bottom, x + 3, bottom + 3, PANEL_LIGHT);
            g.fill(x + 3, bottom, r - 1, bottom + 3, PANEL);
            g.fill(r - 1, bottom, r, bottom + 1, EDGE);
            g.fill(r - 1, bottom + 1, r, bottom + 3, PANEL_LIGHT);
        } else {
            g.fill(x, bottom, r, bottom + 1, EDGE);
        }
    }

    /** JEI's lighter "card" that each recipe sits on: a thin grey outline with a soft bevel. */
    static void card(GuiGraphics g, int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;
        g.fill(x + 1, y, r - 1, y + 1, 0xFF999999);
        g.fill(x + 1, b - 1, r - 1, b, 0xFF999999);
        g.fill(x, y + 1, x + 1, b - 1, 0xFF999999);
        g.fill(r - 1, y + 1, r, b - 1, 0xFF999999);
        g.fill(x + 1, y + 1, r - 1, b - 1, PANEL);
        g.fill(x + 1, y + 1, r - 2, y + 2, 0xFFD8D8D8);
        g.fill(x + 1, y + 1, x + 2, b - 2, 0xFFD8D8D8);
        g.fill(x + 2, b - 2, r - 1, b - 1, 0xFFB3B3B3);
        g.fill(r - 2, y + 2, r - 1, b - 1, 0xFFB3B3B3);
    }

    /** A sunken area: a vanilla slot stretched to any size, used for one well behind a group of slots. */
    static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + w - 1, y, x + w, y + 1, SLOT);
        g.fill(x, y + h - 1, x + 1, y + h, SLOT);
    }

    static void slot(GuiGraphics g, int x, int y) {
        inset(g, x, y, 18, 18, SLOT);
    }

    /** JEI's title and page rows: white text with a shadow, centred on a translucent dark band. */
    static void band(GuiGraphics g, Font font, String text, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BAND);
        String shown = clip(font, text, w - 4);
        g.drawString(font, shown, x + (w - font.width(shown) + 1) / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);
        if (!shown.equals(text)) {
            noteClipped(x, y, w, h, text);
        }
    }

    // Four 8x8 stars: a favourite and one that isn't, each also as it looks under the mouse.
    private static final ResourceLocation STAR = JustEnoughStructures.id("textures/gui/favourite.png");

    /** The favourite star, 8 pixels square, its top left at x, y. */
    static void star(GuiGraphics g, int x, int y, boolean favourite, boolean hovered) {
        g.blit(STAR, x, y, hovered ? 8 : 0, favourite ? 0 : 8, 8, 8, 16, 16);
    }

    /** A small white arrow with a dark shadow, as on JEI's page buttons, pointing left or right. */
    static void arrow(GuiGraphics g, int x, int y, boolean left) {
        arrowShape(g, x + 1, y + 1, left, 0xFF383838);
        arrowShape(g, x, y, left, 0xFFFFFFFF);
    }

    private static void arrowShape(GuiGraphics g, int x, int y, boolean left, int color) {
        for (int i = 0; i < 4; i++) {
            int column = left ? x + i : x + 3 - i;
            g.fill(column, y + 3 - i, column + 1, y + 4 + i, color);
        }
    }

    /** JEI's search box: black, with a thin bevel just inside a black edge. */
    static void searchBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFACACAC);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFACACAC);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF565655);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF565655);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, 0xFF676767);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, 0xFF676767);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, EDGE);
    }

    /** A button drawn pressed in, JEI's look for a toggle that's on. */
    static void pressedButton(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? 0xFFFFFFFF : EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF6D6D6D);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF555555);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF555555);
    }

    /** Text at {@link #smallScale()}, for labels and other secondary lines. */
    public static void small(GuiGraphics g, Font font, String text, int x, int y, int color) {
        scaled(g, font, text, x, y, color, smallScale());
    }

    /** Text drawn at any size, with its top left at x, y. */
    static void scaled(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    static void scaled(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * The size small text is drawn at: about three quarters, picked at each GUI scale so every pixel
     * of the font covers a whole number of screen pixels. A plain 0.75 does that only at GUI scale 4
     * and 8, and at the others the letters come out uneven and hard to read. GUI scale 2 has no
     * whole-pixel size near it, so it keeps 0.75; at GUI scale 1 that would be under a screen pixel
     * for each of the font's, which can't be read at all, so it's full size there.
     */
    public static float smallScale() {
        int gui = (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale());
        if (gui <= 1) {
            return 1f;
        }
        float best = 0.75f;
        float bestOff = Float.MAX_VALUE;
        for (int pixels = 1; pixels <= gui; pixels++) {
            float scale = (float) pixels / gui;
            float off = Math.abs(scale - 0.75f);
            if (scale >= 0.6f && scale <= 0.8f && off < bestOff - 1e-4f) {
                best = scale;
                bestOff = off;
            }
        }
        return best;
    }

    /** Whether small text is sharp at this GUI scale, which it isn't at 1 and 2. */
    static boolean smallIsSharp() {
        return smallScale() != 0.75f || Math.round(Minecraft.getInstance().getWindow().getGuiScale()) % 4 == 0;
    }

    /**
     * The size of secondary text, like labels and details under a row: small where small text is
     * sharp, and full size at GUI scales 1 and 2, where it isn't.
     */
    static float fineScale() {
        return smallIsSharp() ? smallScale() : 1f;
    }

    /** Secondary text, at {@link #fineScale()}. */
    static void fine(GuiGraphics g, Font font, String text, int x, int y, int color) {
        scaled(g, font, text, x, y, color, fineScale());
    }

    static int fineWidth(Font font, String text) {
        return (int) Math.ceil(font.width(text) * fineScale());
    }

    static int fineLine(Font font) {
        return (int) Math.ceil(font.lineHeight * fineScale());
    }

    /** Cuts text short to fit {@code width} at the secondary size. */
    static String fineClip(Font font, String text, int width) {
        return clip(font, text, (int) (width / fineScale()));
    }

    /** Wrapped secondary text. Returns the y below it. */
    static int fineWrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        float scale = fineScale();
        for (FormattedCharSequence line : font.split(text, (int) (width / scale))) {
            scaled(g, font, line, x, y, color, scale);
            y += fineLine(font) + 1;
        }
        return y;
    }

    /** Whether advanced tooltips are on (F3+H), which brings ids and details back, as vanilla does for items. */
    static boolean advanced() {
        return Minecraft.getInstance().options.advancedItemTooltips;
    }

    /** How wide text is when drawn small. */
    public static int smallWidth(Font font, String text) {
        return (int) Math.ceil(font.width(text) * smallScale());
    }

    /** Cuts text short to fit {@code width} once it's drawn small. */
    public static String clipSmall(Font font, String text, int width) {
        return clip(font, text, (int) (width / smallScale()));
    }

    static void fitted(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        // Always the normal size, cut short if need be, so rows in a list line up.
        drawClipped(g, font, text, x, y, maxWidth, color, false);
    }

    // ------------------------------------------------------------------ text cut short

    /** Text cut short to fit, where it was drawn and what it says in full. */
    private record Clipped(int x1, int y1, int x2, int y2, String text) {
    }

    private static final List<Clipped> CLIPPED = new ArrayList<>();
    /** The areas text is being drawn into, each within the one before, so text scrolled out of sight isn't counted. */
    private static final Deque<int[]> SCISSORS = new ArrayDeque<>();

    /**
     * Forgets the text cut short last frame. Called as a screen starts drawing, and again before a
     * popup that covers it, so only what can be seen counts.
     */
    static void beginClipped() {
        CLIPPED.clear();
        SCISSORS.clear();
    }

    /** Notes text that had to be cut short, so hovering where it was drawn shows all of it. */
    static void noteClipped(int x, int y, int w, int h, String full) {
        int x1 = x;
        int y1 = y;
        int x2 = x + w;
        int y2 = y + h;
        int[] area = SCISSORS.peek();
        if (area != null) {
            x1 = Math.max(x1, area[0]);
            y1 = Math.max(y1, area[1]);
            x2 = Math.min(x2, area[2]);
            y2 = Math.min(y2, area[3]);
        }
        if (x2 > x1 && y2 > y1) {
            CLIPPED.add(new Clipped(x1, y1, x2, y2, full));
        }
    }

    /** The whole of whatever text under the mouse was cut short, the last drawn first, or null. */
    static String clippedAt(double mouseX, double mouseY) {
        for (int i = CLIPPED.size() - 1; i >= 0; i--) {
            Clipped c = CLIPPED.get(i);
            if (mouseX >= c.x1() && mouseX < c.x2() && mouseY >= c.y1() && mouseY < c.y2()) {
                return c.text();
            }
        }
        return null;
    }

    /** Shows the whole of the cut-short text under the mouse as a tooltip. Returns whether there was any. */
    static boolean clippedTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        String full = clippedAt(mouseX, mouseY);
        if (full == null) {
            return false;
        }
        g.renderTooltip(font, font.split(Component.literal(full), 260), mouseX, mouseY);
        return true;
    }

    /** Text cut short to fit {@code maxWidth}, shown in full when hovered. */
    static void drawClipped(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color, boolean shadow) {
        String shown = clip(font, text, maxWidth);
        g.drawString(font, shown, x, y, color, shadow);
        if (!shown.equals(text)) {
            noteClipped(x, y - 1, Math.max(6, font.width(shown)), font.lineHeight + 1, text);
        }
    }

    /** Secondary text cut short to fit {@code maxWidth}, shown in full when hovered. */
    static void fineClipped(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        String shown = fineClip(font, text, maxWidth);
        fine(g, font, shown, x, y, color);
        if (!shown.equals(text)) {
            noteClipped(x, y - 1, Math.max(6, fineWidth(font, shown)), fineLine(font) + 1, text);
        }
    }

    /** Small text cut short to fit {@code maxWidth}, shown in full when hovered. */
    public static void smallClipped(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        String shown = clipSmall(font, text, maxWidth);
        small(g, font, shown, x, y, color);
        if (!shown.equals(text)) {
            noteClipped(x, y - 1, Math.max(6, smallWidth(font, shown)), (int) Math.ceil(font.lineHeight * smallScale()) + 1, text);
        }
    }

    /** {@link GuiGraphics#enableScissor}, keeping track of the area so text cut short outside it isn't counted. */
    static void scissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
        int[] area = {x1, y1, x2, y2};
        int[] outer = SCISSORS.peek();
        if (outer != null) {
            area = new int[]{Math.max(x1, outer[0]), Math.max(y1, outer[1]), Math.min(x2, outer[2]), Math.min(y2, outer[3])};
        }
        SCISSORS.push(area);
    }

    static void endScissor(GuiGraphics g) {
        g.disableScissor();
        SCISSORS.poll();
    }

    /** JEI's scrollbar: a raised, gripped thumb in a sunken track. */
    static void scrollbar(GuiGraphics g, int x, int y, int height, double scroll, double maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int w = 6;
        inset(g, x - 2, y, w, height, SLOT);
        int thumb = Math.max(12, (int) ((height - 2) * (height - 2) / (height - 2 + maxScroll)));
        int top = y + 1 + (int) ((height - 2 - thumb) * Math.min(1, scroll / maxScroll));
        int tx = x - 1;
        g.fill(tx, top, tx + w - 2, top + thumb, PANEL);
        g.fill(tx, top, tx + w - 3, top + 1, PANEL_LIGHT);
        g.fill(tx, top, tx + 1, top + thumb - 1, PANEL_LIGHT);
        g.fill(tx + 1, top + thumb - 1, tx + w - 2, top + thumb, PANEL_DARK);
        g.fill(tx + w - 3, top + 1, tx + w - 2, top + thumb, PANEL_DARK);
        for (int line = top + 3; line < top + thumb - 3; line += 2) {
            g.fill(tx + 1, line, tx + w - 3, line + 1, SLOT);
        }
    }

    public static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        // Not even room for the dots: nothing, rather than spilling over whatever is next to it.
        if (width < font.width("...")) {
            return "";
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
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
