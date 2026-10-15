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

    // The browser's toolbar and popups. A die for a new layout.
    static final ResourceLocation DIE = icon("die");
    static final ResourceLocation HOUSE = icon("house");
    static final ResourceLocation LAYERS = icon("layers");
    static final ResourceLocation STEP_LEFT = icon("step_left");
    static final ResourceLocation STEP_RIGHT = icon("step_right");
    static final ResourceLocation ONE_ROLL = icon("one_roll");
    static final ResourceLocation CHANCES = icon("chances");

    // The list, the details panel and Back and Forward. A chevron at the end of a row says it opens something.
    static final ResourceLocation CHEVRON_LEFT = icon("chevron_left");
    static final ResourceLocation CHEVRON_RIGHT = icon("chevron_right");
    static final ResourceLocation FOLD_CLOSED = icon("fold_closed");
    static final ResourceLocation FOLD_OPEN = icon("fold_open");

    // Pack tools: a check box's tick, its rows' buttons, and the badges after names that say how they stand.
    static final ResourceLocation TICK = icon("tick");
    static final ResourceLocation EDIT = icon("edit");
    static final ResourceLocation OPEN = icon("open");
    static final ResourceLocation UNDO_SMALL = icon("undo_small");
    static final ResourceLocation BROWSER = icon("browser");
    static final ResourceLocation EDITED = icon("edited");
    static final ResourceLocation EDITED_CHANGED = icon("edited_changed");
    static final ResourceLocation EDITED_BROKEN = icon("edited_broken");
    static final ResourceLocation AFTER_RELOAD = icon("after_reload");
    static final ResourceLocation NOTES = icon("notes");
    static final ResourceLocation SECRET = icon("secret");

    private Icons() {
    }

    private static ResourceLocation icon(String name) {
        return JustEnoughStructures.id("textures/gui/" + name + ".png");
    }
}
