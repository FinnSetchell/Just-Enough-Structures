package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
//? if <26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
//?}
//? if >=26.1 {
/*import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
*///?}

/**
 * Drawing helpers in the look JEI uses, which is the vanilla container look: the same frames,
 * slots and buttons, with title rows of white text on a dark band.
 */
public final class Gui {

    //? if >=1.21 {
    /*private static final ResourceLocation BUTTON = Ids.parse("widget/button");
    private static final ResourceLocation BUTTON_DISABLED = Ids.parse("widget/button_disabled");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = Ids.parse("widget/button_highlighted");
    private static final ResourceLocation SLIDER = Ids.parse("widget/slider");
    *///?} else {
    private static final ResourceLocation WIDGETS = Ids.parse("textures/gui/widgets.png");
    private static final ResourceLocation SLIDER = Ids.parse("textures/gui/slider.png");
    //?}

    /** A vanilla button's background stretched to size: {@code state} 0 for off, 1 normal, 2 under the mouse. */
    public static void buttonBackground(GuiGraphics g, int x, int y, int w, int h, int state) {
        //? if >=26.1 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, state == 0 ? BUTTON_DISABLED : state == 2 ? BUTTON_HIGHLIGHTED : BUTTON, x, y, w, h);
        *///?} else if >=1.21 {
        /*g.blitSprite(state == 0 ? BUTTON_DISABLED : state == 2 ? BUTTON_HIGHLIGHTED : BUTTON, x, y, w, h);
        *///?} else {
        g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, 46 + state * 20);
        //?}
    }

    /** A vanilla slider's dark track stretched to size, with no handle on it. */
    static void sliderTrack(GuiGraphics g, int x, int y, int w, int h) {
        //? if >=26.1 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, SLIDER, x, y, w, h);
        *///?} else if >=1.21 {
        /*g.blitSprite(SLIDER, x, y, w, h);
        *///?} else {
        g.blitNineSliced(SLIDER, x, y, w, h, 20, 4, 200, 20, 0, 0);
        //?}
    }
    /**
     * The vanilla chest's texture. Frames here are cut from it rather than filled in, so a resource
     * pack that restyles the chest restyles them with it, and without one they're pixel for pixel
     * the vanilla container frame.
     */
    private static final ResourceLocation CHEST = Ids.parse("textures/gui/container/generic_54.png");
    /** The part of that texture the chest takes up. */
    private static final int CHEST_W = 176;
    private static final int CHEST_H = 222;
    /** How much of the chest's frame each corner of a panel takes: all there is of it before the slots begin. */
    private static final int CORNER = 7;
    /** A row of the plain strip between the chest's slots and the inventory's, where its sides are frame and nothing else. */
    private static final int PLAIN = 133;
    /** Where the chest's first slot is. */
    private static final int SLOT_U = 7;
    private static final int SLOT_V = 17;

    /**
     * The colours frames are made of. Each is a pixel of the chest's texture rather than a colour of
     * its own, so it's whatever a resource pack has made of the chest.
     */
    enum Tone {
        /** The frame's outline. */
        EDGE(0, PLAIN, 0),
        /** The lit side of the frame, and of a slot. */
        LIGHT(1, PLAIN, 0),
        PANEL(4, PLAIN, 0),
        /** The frame's shaded side. */
        DARK(CHEST_W - 3, PLAIN, 0),
        SLOT(SLOT_U + 9, SLOT_V + 9, 0),
        /** A slot's shaded side. */
        SLOT_DARK(SLOT_U, SLOT_V, 0),
        /** The well behind a list: the panel, a little darker. */
        WELL(4, PLAIN, 0x11000000);

        private final int u;
        private final int v;
        /** See-through black that goes over it, or 0. */
        private final int over;

        Tone(int u, int v, int over) {
            this.u = u;
            this.v = v;
            this.over = over;
        }
    }

    // See-through black and white that make the greys of a card and of a thumb's grip out of the
    // panel's own, whatever that is. Over the vanilla panel they come to the greys JEI uses.
    private static final int CARD_OUTLINE = 0x3A000000;
    private static final int CARD_LIGHT = 0x50FFFFFF;
    private static final int CARD_DARK = 0x18000000;
    private static final int GRIP = 0x4C000000;

    static final int PANEL = 0xFFC6C6C6;
    static final int EDGE = 0xFF000000;
    /**
     * Vanilla's own label colour. A resource pack with dark panels changes text of exactly this colour
     * to one that shows on them, so it's what body text is drawn in over a dark panel.
     */
    static final int VANILLA_LABEL = 0xFF404040;
    /** A panel whose red, green and blue come to less than this on average is a dark one. */
    private static final int DARK_BELOW = 100;
    /** Whether the chest's texture was a dark one when it was last looked at. */
    private static boolean dark;
    /** The game's list of the resource packs in use as it was then. The game makes a new list each time resources load. */
    private static Object packsThen;
    private static long lookedAt;

    /**
     * Whether panels are dark, as a resource pack with a dark look makes them, going by the chest
     * texture's own panel colour. Text and row highlights made for a light panel can't be seen on one.
     *
     * <p>The texture is looked at again when the game's list of packs is a new one, which it is after
     * every reload, rather than by listening for reloads: NeoForge closes the game's list of reload
     * listeners once it has started, and on 26.2 adding one after that throws.
     */
    static boolean darkPanel() {
        Minecraft mc = Minecraft.getInstance();
        Object packs = mc.getResourcePackRepository().getSelectedPacks();
        // Not while packs are still loading, when the texture could be the old one, and not more than
        // once a second whatever the list does.
        if (packs != packsThen && mc.getOverlay() == null && System.currentTimeMillis() - lookedAt > 1000) {
            packsThen = packs;
            lookedAt = System.currentTimeMillis();
            dark = isDark(mc.getResourceManager());
        }
        return dark;
    }

    private static boolean isDark(ResourceManager resources) {
        try (InputStream in = resources.open(CHEST); NativeImage chest = NativeImage.read(in)) {
            // A pack's texture may be finer than vanilla's 256 pixels.
            int pixel = Thumbnails.pixel(chest, Tone.PANEL.u * chest.getWidth() / 256, Tone.PANEL.v * chest.getHeight() / 256);
            // The colours come one to a byte, whichever way round they are.
            return ((pixel & 0xFF) + (pixel >> 8 & 0xFF) + (pixel >> 16 & 0xFF)) / 3 < DARK_BELOW;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Body text: black as in JEI's recipe layouts, or over a dark panel vanilla's label colour. */
    public static int label() {
        return darkPanel() ? VANILLA_LABEL : 0xFF000000;
    }

    /** Secondary text, as JEI's cook times and other recipe details. */
    public static int labelSoft() {
        return darkPanel() ? 0xFFA8A8A8 : 0xFF808080;
    }

    /** The same, drawn smaller, as vanilla's dark grey. */
    static int labelSoftSmall() {
        return darkPanel() ? 0xFFA8A8A8 : 0xFF555555;
    }

    // What coloured text is drawn in over a dark panel, where the colours made for a light one can't be read.
    static final int GOOD_ON_DARK = 0xFF7FD46A;
    static final int BAD_ON_DARK = 0xFFFF6B6B;
    static final int WARN_ON_DARK = 0xFFE6A93C;
    static final int LINK_ON_DARK = 0xFF8FB4FF;
    static final int LINK_HOVER_ON_DARK = 0xFFC4D7FF;

    /** A colour for a light panel, or the one that stands in for it over a dark panel. */
    static int ink(int onLight, int onDark) {
        return darkPanel() ? onDark : onLight;
    }

    /** The highlight of a row under the mouse: JEI's slot highlight, fainter over a dark panel. */
    static int rowHover() {
        return darkPanel() ? 0x30FFFFFF : 0x80FFFFFF;
    }

    /** The darker highlight of the row that's picked, darker still over a dark panel. */
    static int rowSelected() {
        return darkPanel() ? 0x70000000 : 0x40000000;
    }

    /** The translucent strip behind white titles in JEI's title and page rows. */
    static final int BAND = 0x44000000;
    static final int VIEW_TOP = 0xFF6E7C90;
    static final int VIEW_BOTTOM = 0xFF343B48;
    static final int BAR = 0xFF3E8A2A;
    static final int BAR_BACK = 0xFF8A8A8A;

    private Gui() {
    }

    /** Fills an area with a {@link Tone}. */
    static void tone(GuiGraphics g, int x1, int y1, int x2, int y2, Tone tone) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        stretch(g, x1, y1, x2 - x1, y2 - y1, tone.u, tone.v, 1, 1);
        if (tone.over != 0) {
            g.fill(x1, y1, x2, y2, tone.over);
        }
    }

    /** Part of the chest's texture, the same size on screen as in the texture. */
    private static void piece(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        blit(g, CHEST, x, y, u, v, w, h);
    }

    /** Part of the chest's texture, stretched over an area. */
    private static void stretch(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0) {
            return;
        }
        //? if >=26.1 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, CHEST, x, y, (float) u, (float) v, w, h, uw, vh, 256, 256);
        *///?} else {
        g.blit(CHEST, x, y, w, h, (float) u, (float) v, uw, vh, 256, 256);
        //?}
    }

    /**
     * The vanilla container frame, which is also JEI's panel: the chest's own corners, with a line of
     * each of its sides drawn out between them.
     */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        int c = Math.min(CORNER, Math.min(w, h) / 2);
        int r = x + w;
        int b = y + h;
        tone(g, x + c, y + c, r - c, b - c, Tone.PANEL);
        stretch(g, x + c, y, w - 2 * c, c, CORNER, 0, 1, c);
        stretch(g, x + c, b - c, w - 2 * c, c, CORNER, CHEST_H - c, 1, c);
        stretch(g, x, y + c, c, h - 2 * c, 0, PLAIN, c, 1);
        stretch(g, r - c, y + c, c, h - 2 * c, CHEST_W - c, PLAIN, c, 1);
        piece(g, x, y, 0, 0, c, c);
        piece(g, r - c, y, CHEST_W - c, 0, c, c);
        piece(g, x, b - c, 0, CHEST_H - c, c, c);
        piece(g, r - c, b - c, CHEST_W - c, CHEST_H - c, c, c);
    }

    /**
     * A category tab sitting on top of a panel whose top edge is at {@code y + 21}, like JEI's. The
     * selected one is panel coloured and joins the panel; the others are darker and sit behind it.
     */
    static void tab(GuiGraphics g, int x, int y, boolean selected) {
        int r = x + 24;
        Tone fill = selected ? Tone.PANEL : Tone.SLOT;
        int bottom = y + 21;
        tone(g, x + 3, y + 3, r - 3, bottom, fill);
        tone(g, x + 2, y, r - 3, y + 1, Tone.EDGE);
        tone(g, x + 1, y + 1, x + 2, y + 2, Tone.EDGE);
        tone(g, x + 2, y + 1, r - 3, y + 2, Tone.LIGHT);
        tone(g, r - 3, y + 1, r - 2, y + 2, Tone.EDGE);
        tone(g, x, y + 2, x + 1, y + 3, Tone.EDGE);
        tone(g, x + 1, y + 2, r - 3, y + 3, Tone.LIGHT);
        tone(g, r - 3, y + 2, r - 2, y + 3, fill);
        tone(g, r - 2, y + 2, r - 1, y + 3, Tone.EDGE);
        tone(g, x, y + 3, x + 1, bottom, Tone.EDGE);
        tone(g, x + 1, y + 3, x + 3, bottom, Tone.LIGHT);
        tone(g, x + 3, y + 3, x + 4, y + 4, Tone.LIGHT);
        tone(g, r - 1, y + 3, r, bottom, Tone.EDGE);
        tone(g, r - 3, y + 3, r - 1, bottom, Tone.DARK);
        if (selected) {
            // Run on into the panel, over its top edge, so the two read as one piece.
            tone(g, x, bottom, x + 1, bottom + 1, Tone.EDGE);
            tone(g, x + 1, bottom, x + 3, bottom + 3, Tone.LIGHT);
            tone(g, x + 3, bottom, r - 1, bottom + 3, Tone.PANEL);
            tone(g, r - 1, bottom, r, bottom + 1, Tone.EDGE);
            tone(g, r - 1, bottom + 1, r, bottom + 3, Tone.LIGHT);
        } else {
            tone(g, x, bottom, r, bottom + 1, Tone.EDGE);
        }
    }

    /** JEI's lighter "card" that each recipe sits on: a thin grey outline with a soft bevel. */
    static void card(GuiGraphics g, int x, int y, int w, int h) {
        int r = x + w;
        int b = y + h;
        tone(g, x + 1, y, r - 1, y + 1, Tone.PANEL);
        tone(g, x, y + 1, r, b - 1, Tone.PANEL);
        tone(g, x + 1, b - 1, r - 1, b, Tone.PANEL);
        g.fill(x + 1, y, r - 1, y + 1, CARD_OUTLINE);
        g.fill(x + 1, b - 1, r - 1, b, CARD_OUTLINE);
        g.fill(x, y + 1, x + 1, b - 1, CARD_OUTLINE);
        g.fill(r - 1, y + 1, r, b - 1, CARD_OUTLINE);
        g.fill(x + 1, y + 1, r - 2, y + 2, CARD_LIGHT);
        g.fill(x + 1, y + 2, x + 2, b - 2, CARD_LIGHT);
        g.fill(x + 2, b - 2, r - 1, b - 1, CARD_DARK);
        g.fill(r - 2, y + 2, r - 1, b - 2, CARD_DARK);
    }

    /**
     * A sunken area: a vanilla slot's edges drawn out to any size around a {@link Tone}, used for one
     * well behind a group of slots.
     */
    static void inset(GuiGraphics g, int x, int y, int w, int h, Tone fill) {
        int r = x + w - 1;
        int b = y + h - 1;
        tone(g, x + 1, y + 1, r, b, fill);
        stretch(g, x + 1, y, w - 2, 1, SLOT_U + 1, SLOT_V, 1, 1);
        stretch(g, x + 1, b, w - 2, 1, SLOT_U + 1, SLOT_V + 17, 1, 1);
        stretch(g, x, y + 1, 1, h - 2, SLOT_U, SLOT_V + 1, 1, 1);
        stretch(g, r, y + 1, 1, h - 2, SLOT_U + 17, SLOT_V + 1, 1, 1);
        piece(g, x, y, SLOT_U, SLOT_V, 1, 1);
        piece(g, r, y, SLOT_U + 17, SLOT_V, 1, 1);
        piece(g, x, b, SLOT_U, SLOT_V + 17, 1, 1);
        piece(g, r, b, SLOT_U + 17, SLOT_V + 17, 1, 1);
    }

    /** A vanilla slot: the chest's first. */
    static void slot(GuiGraphics g, int x, int y) {
        piece(g, x, y, SLOT_U, SLOT_V, 18, 18);
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
        blit(g, STAR, x, y, hovered ? 8 : 0, favourite ? 0 : 8, 8, 8, 16, 16);
    }

    //? if >=1.21 {
    /*private static final ResourceLocation CHECKMARK = Ids.parse("icon/checkmark");
    *///?} else {
    private static final ResourceLocation CHECKMARK = Ids.parse("textures/gui/checkmark.png");
    //?}

    /** Vanilla's green tick, 9 by 8, its top left at x, y. It's the game's own, so a pack restyles it. */
    static void checkmark(GuiGraphics g, int x, int y) {
        //? if >=26.1 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, CHECKMARK, x, y, 9, 8);
        *///?} else if >=1.21 {
        /*g.blitSprite(CHECKMARK, x, y, 9, 8);
        *///?} else {
        blit(g, CHECKMARK, x, y, 0, 0, 9, 8, 9, 8);
        //?}
    }

    /** Part of a texture 256 pixels square, as GUI textures used to be. */
    public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        blit(g, texture, x, y, u, v, w, h, 256, 256);
    }

    /** Part of a texture, the same size on screen as in the texture. */
    public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h, int textureW, int textureH) {
        //? if >=26.1 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, textureW, textureH);
        *///?} else {
        g.blit(texture, x, y, u, v, w, h, textureW, textureH);
        //?}
    }

    /** A whole icon file, {@code size} pixels square, white with its shadow, as words on a button are. */
    static void icon(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        icon(g, texture, x, y, size, size, 0xFFFFFFFF, true);
    }

    /**
     * A whole icon file, {@code w} by {@code h}, its white drawn in {@code colour} over a shadow one pixel
     * down and right. The files hold only the shape, so the shadow is drawn here. On a button it's the
     * colour a quarter as bright, as vanilla gives the words there. On a light panel, where the icon is
     * dark, it's a soft see-through black, so the icon still stands apart from it on any panel a pack draws.
     */
    static void icon(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int colour, boolean onButton) {
        int shadow = onButton ? (colour & 0xFCFCFC) >> 2 | colour & 0xFF000000
                : (int) (SOFT_SHADOW_ALPHA * ((colour >>> 24) / 255f)) << 24;
        tinted(g, texture, x + 1, y + 1, w, h, shadow);
        tinted(g, texture, x, y, w, h, colour);
    }

    /** How dark the shadow under an icon on a light panel is: black at 55%. */
    private static final int SOFT_SHADOW_ALPHA = 0x8C;

    private static void tinted(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int colour) {
        //? if >=26.1 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w, h, colour);
        *///?} else {
        // A see-through colour, like the soft shadow, needs blending on. Vanilla's widgets leave it on too.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor((colour >> 16 & 0xFF) / 255f, (colour >> 8 & 0xFF) / 255f, (colour & 0xFF) / 255f, (colour >>> 24) / 255f);
        g.blit(texture, x, y, 0, 0, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        //?}
    }

    /** Draws a widget that a screen draws itself, rather than through its list of widgets. */
    public static void render(GuiGraphics g, Renderable widget, int mouseX, int mouseY, float partialTick) {
        //? if >=26.1 {
        /*widget.extractRenderState(g, mouseX, mouseY, partialTick);
        *///?} else {
        widget.render(g, mouseX, mouseY, partialTick);
        //?}
    }

    /** Passes a click on to a widget that a screen handles itself. */
    public static boolean click(GuiEventListener widget, double mouseX, double mouseY, int button) {
        //? if >=26.1 {
        /*return widget.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
        *///?} else {
        return widget.mouseClicked(mouseX, mouseY, button);
        //?}
    }

    // ------------------------------------------------------------------ moving what's drawn
    // 26.1 lays screens out flat: what's drawn later goes over what it overlaps, so there's no depth
    // to move things along any more, and moving them about takes two numbers rather than three.

    public static void push(GuiGraphics g) {
        //? if >=26.1 {
        /*g.pose().pushMatrix();
        *///?} else {
        g.pose().pushPose();
        //?}
    }

    public static void pop(GuiGraphics g) {
        //? if >=26.1 {
        /*g.pose().popMatrix();
        *///?} else {
        g.pose().popPose();
        //?}
    }

    public static void translate(GuiGraphics g, float x, float y) {
        //? if >=26.1 {
        /*g.pose().translate(x, y);
        *///?} else {
        g.pose().translate(x, y, 0);
        //?}
    }

    public static void scale(GuiGraphics g, float scale) {
        //? if >=26.1 {
        /*g.pose().scale(scale, scale);
        *///?} else {
        g.pose().scale(scale, scale, 1);
        //?}
    }

    /** Puts what's drawn next in front of what's there, by {@code z}. From 26.1 later things go in front anyway. */
    public static void lift(GuiGraphics g, float z) {
        //? if <26.1 {
        g.pose().translate(0, 0, z);
        //?}
    }

    /** JEI's search box: black, with a thin bevel just inside a black edge, a dim one over a dark panel. */
    static void searchBox(GuiGraphics g, int x, int y, int w, int h) {
        int lit = ink(0xFFACACAC, 0xFF3C3C3C);
        int shade = ink(0xFF565655, 0xFF1E1E1E);
        int corner = ink(0xFF676767, 0xFF2C2C2C);
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, lit);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, lit);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, shade);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, shade);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, corner);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, corner);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, EDGE);
    }

    /** A box with a one-pixel outline, each drawn once, so see-through colours come out as they should. */
    static void outlined(GuiGraphics g, int x, int y, int w, int h, int outline, int fill) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x, y, x + w, y + 1, outline);
        g.fill(x, y + h - 1, x + w, y + h, outline);
        g.fill(x, y + 1, x + 1, y + h - 1, outline);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, outline);
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
        push(g);
        translate(g, x, y);
        scale(g, scale);
        g.drawString(font, text, 0, 0, readable(color, scale), false);
        pop(g);
    }

    static void scaled(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        push(g);
        translate(g, x, y);
        scale(g, scale);
        g.drawString(font, text, 0, 0, readable(color, scale), false);
        pop(g);
    }

    /** The grey of secondary text is too faint once it's drawn smaller, so it's darker there. */
    private static int readable(int color, float scale) {
        return color == labelSoft() && scale < 1f ? labelSoftSmall() : color;
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

    /** Whether small text is sharp at this GUI scale. Only GUI scale 2 isn't. */
    static boolean smallIsSharp() {
        return smallScale() != 0.75f || Math.round(Minecraft.getInstance().getWindow().getGuiScale()) % 4 == 0;
    }

    /**
     * The size of secondary text, like labels and details under a row: small where small text is
     * sharp, otherwise full size. That makes it full size at GUI scales 1 and 2.
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
        inset(g, x - 2, y, w, height, Tone.SLOT);
        int thumb = Scroll.thumb(height, maxScroll);
        int top = y + Scroll.thumbTop(height, maxScroll, scroll);
        int tx = x - 1;
        tone(g, tx, top, tx + w - 2, top + thumb, Tone.PANEL);
        tone(g, tx, top, tx + w - 3, top + 1, Tone.LIGHT);
        tone(g, tx, top, tx + 1, top + thumb - 1, Tone.LIGHT);
        tone(g, tx + 1, top + thumb - 1, tx + w - 2, top + thumb, Tone.DARK);
        tone(g, tx + w - 3, top + 1, tx + w - 2, top + thumb, Tone.DARK);
        for (int line = top + 3; line < top + thumb - 3; line += 2) {
            g.fill(tx + 1, line, tx + w - 3, line + 1, GRIP);
        }
    }

    /**
     * The bar of a list that drops down over a screen: a thin thumb and no track, on the list's own
     * dark ground. {@code right} is the list's right edge and the bar is as tall as the list.
     */
    static void slimBar(GuiGraphics g, int right, int top, int height, Scroll scroll) {
        if (!scroll.scrolls()) {
            return;
        }
        int from = top + Scroll.thumbTop(height, scroll.most(), scroll.offset());
        g.fill(right - 5, from, right - 1, from + Scroll.thumb(height, scroll.most()), 0xFF9A9A9A);
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
}
