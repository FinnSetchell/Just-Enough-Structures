package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The player's notes on one structure, edited in a popup over the browser. They're saved locally
 * whenever the popup closes, and show at the bottom of the structure's Info tab. The browser adds
 * the box and Done as widgets, so they get typing and clicks, and has this draw them on top.
 */
final class NotesPopup {
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 240;

    final ResourceLocation id;
    private final Component title;
    /** The box's text, kept for when the window is resized and the box is made again. */
    private String text;
    MultiLineEditBox box;
    Button done;
    private int x, y, w, h;

    NotesPopup(ResourceLocation id) {
        this.id = id;
        this.title = Component.translatable("screen.justenoughstructures.your_notes.title", StructureNames.structure(id));
        this.text = ClientState.note(id);
    }

    /** Makes the box and Done for this screen size, centred in the space under the nav bar, keeping what's typed. */
    void build(Font font, int screenWidth, int screenHeight, Runnable onDone) {
        w = Math.min(screenWidth - 12, MAX_WIDTH);
        h = Math.min(screenHeight - NavBar.TOP - 6, MAX_HEIGHT);
        x = (screenWidth - w) / 2;
        y = NavBar.TOP + (screenHeight - NavBar.TOP - 6 - h) / 2;
        int left = x + 6;
        int right = x + w - 6;
        int top = y + 22;
        // Under the box: its character count on the right, then Done below that.
        int bottom = y + h - 42;
        box = JsonEditBox.create(font, left, top, right - left, bottom - top,
                Component.translatable("screen.justenoughstructures.your_notes.placeholder"), title);
        box.setCharacterLimit(ClientState.NOTE_LIMIT);
        box.setValue(text);
        box.setValueListener(value -> text = value);
        int buttonWidth = Math.max(60, font.width(CommonComponents.GUI_DONE) + 12);
        done = Button.builder(CommonComponents.GUI_DONE, b -> onDone.run()).bounds(right - buttonWidth, y + h - 26, buttonWidth, 20).build();
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        Gui.panel(g, x, y, w, h);
        int left = x + 6;
        Gui.drawClipped(g, font, title.getString(), left, y + 8, w - 16, Gui.label(), false);
        // Beside Done, since a player on a server may wonder who else can see them.
        Gui.drawClipped(g, font, Component.translatable("screen.justenoughstructures.your_notes.private").getString(),
                left, y + h - 20, done.getX() - left - 6, Gui.labelSoft(), false);
        Gui.render(g, box, mouseX, mouseY, partialTick);
        Gui.render(g, done, mouseX, mouseY, partialTick);
    }

    /** Saves what's written. */
    void save() {
        ClientState.setNote(id, text);
    }

    int[] doneCentre() {
        return new int[]{done.getX() + done.getWidth() / 2, done.getY() + done.getHeight() / 2};
    }
}
