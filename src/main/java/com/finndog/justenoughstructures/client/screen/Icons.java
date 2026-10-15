package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.JustEnoughStructures;
import net.minecraft.resources.ResourceLocation;

/**
 * The browser's own small pictures, each a file of its own in textures/gui so a resource pack can
 * draw it again. They're white with a dark grey shadow; on a light panel they're drawn tinted with
 * the colour the words there have. The 9 pixel ones sit level with a line of text.
 */
final class Icons {
    /** 9 pixels square. */
    static final int SMALL = 9;
    /** 16 pixels square, in a 20 pixel button. */
    static final int LARGE = 16;

    // Rows of the loot editor's list, and the heads of its cards.
    static final ResourceLocation COPY_SMALL = icon("row_copy");
    static final ResourceLocation REMOVE = icon("row_remove");
    static final ResourceLocation GRIP = icon("row_grip");
    static final ResourceLocation MOVE_UP = icon("move_up");
    static final ResourceLocation MOVE_DOWN = icon("move_down");
    static final ResourceLocation ADD = icon("add");
    static final ResourceLocation BOLD = icon("bold");
    static final ResourceLocation ITALIC = icon("italic");
    /** 7 by 4: the arrow of a dropdown. */
    static final ResourceLocation DROPDOWN = icon("dropdown");
    static final int DROPDOWN_W = 7;
    static final int DROPDOWN_H = 4;

    // The loot editor's toolbar.
    static final ResourceLocation UNDO = icon("undo");
    static final ResourceLocation REDO = icon("redo");
    static final ResourceLocation COPY = icon("copy");
    static final ResourceLocation PASTE = icon("paste");

    // The browser's toolbar and popups. A die for a new layout or a new roll of a chest's loot.
    static final ResourceLocation DIE = icon("die");
    static final ResourceLocation DIE_SMALL = icon("die_small");
    static final ResourceLocation HOUSE = icon("house");
    static final ResourceLocation LAYERS = icon("layers");
    static final ResourceLocation STEP_LEFT = icon("step_left");
    static final ResourceLocation STEP_RIGHT = icon("step_right");
    static final ResourceLocation ONE_ROLL = icon("one_roll");
    static final ResourceLocation CHANCES = icon("chances");

    private Icons() {
    }

    private static ResourceLocation icon(String name) {
        return JustEnoughStructures.id("textures/gui/" + name + ".png");
    }
}
