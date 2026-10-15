package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.type;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.editor;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;

/**
 * The loot editor's JSON view scrolls like the other lists: opened as the form first, as a player does,
 * then switched to JSON, the wheel over it moves it three lines a notch, its bar right of it drags it
 * to the bottom, a resize keeps it where it was, and once the cursor is out of view, typing brings its
 * line back into view. It has to open at its top, where filling the box would leave it at the end: that
 * is checked last, so on older code the bar first takes it to the top, dragged above the box, which
 * vanilla's bar does too, and the wheel is checked first.
 */
final class JsonScrollScenario {
    private static final ResourceLocation TABLE = Ids.parse("chests/desert_pyramid");
    /** Three of the box's 9 pixel lines, for three notches of the wheel. */
    private static final double THREE_NOTCHES = 3 * 27;

    private JsonScrollScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        double[] bottom = new double[1];
        double[] opened = new double[1];
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 400))
                .then(run(() -> mc.setScreen(new LootEditorScreen(mc.screen, TABLE, "Desert Pyramid"))))
                .then(until(() -> editor(mc) != null && editor(mc).loaded(), 100))
                .then(pause(30))
                // The JSON/Form toggle is the toolbar's first button, at the bottom left.
                .then(moveTo(() -> new int[]{24, mc.getWindow().getGuiScaledHeight() - 22}, 8))
                .then(click())
                .then(pause(20))
                .then(run(() -> opened[0] = offset(mc)))
                .then(moveTo(() -> bar(mc, 0.5f), 8))
                .then(dragTo(() -> new int[]{bar(mc, 0f)[0], box(mc).getY() - 6}, 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(10))
                .then(run(() -> {
                    if (offset(mc) > 1) {
                        throw new IllegalStateException("Dragging the JSON view's bar above it left it at " + offset(mc) + ", not the top");
                    }
                }))
                .then(moveTo(() -> new int[]{box(mc).getX() + box(mc).getWidth() / 2, box(mc).getY() + box(mc).getHeight() / 2}, 8))
                .then(wheel(-3))
                .then(pause(10))
                .then(shoot("j01_wheel"))
                .then(run(() -> {
                    double now = offset(mc);
                    if (Math.abs(now - THREE_NOTCHES) > 1) {
                        throw new IllegalStateException("Three notches of the wheel over the JSON view moved it " + now + " pixels, not three lines a notch ("
                                + THREE_NOTCHES + ")");
                    }
                }))
                // As when the window changes size: the editor is laid out again, the JSON box made anew.
                .then(run(() -> {
                    //? if >=26.1 {
                    /*mc.resizeGui();
                    *///?} else {
                    mc.resizeDisplay();
                    //?}
                }))
                .then(pause(10))
                .then(run(() -> {
                    double now = offset(mc);
                    if (Math.abs(now - THREE_NOTCHES) > 1) {
                        throw new IllegalStateException("Laying the editor out again moved the JSON view from " + THREE_NOTCHES + " to " + now);
                    }
                }))
                .then(moveTo(() -> bar(mc, 0f), 8))
                .then(dragTo(() -> bar(mc, 1f), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(10))
                .then(shoot("j02_bar_at_bottom"))
                .then(run(() -> bottom[0] = offset(mc)))
                // Already at the bottom, more of the wheel can't take it further.
                .then(wheel(-20))
                .then(pause(5))
                .then(run(() -> {
                    double now = offset(mc);
                    if (bottom[0] <= THREE_NOTCHES || Math.abs(now - bottom[0]) > 1) {
                        throw new IllegalStateException("Dragging the JSON view's bar to its bottom left it at " + bottom[0] + ", and the wheel took it on to " + now);
                    }
                }))
                // A click near the bottom puts the cursor there; with the bar dragged back to the top it's out
                // of view, and typing has to bring it back.
                .then(moveTo(() -> new int[]{box(mc).getX() + 40, box(mc).getY() + box(mc).getHeight() - 12}, 8))
                .then(click())
                .then(moveTo(() -> bar(mc, 1f), 8))
                .then(dragTo(() -> bar(mc, 0f), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(10))
                .then(run(() -> {
                    if (offset(mc) > 1) {
                        throw new IllegalStateException("Dragging the JSON view's bar to its top left it at " + offset(mc));
                    }
                }))
                .then(type(" ", 2))
                .then(pause(10))
                .then(shoot("j03_typed_out_of_view"))
                .then(run(() -> {
                    double now = offset(mc);
                    if (now < bottom[0] - box(mc).getHeight()) {
                        throw new IllegalStateException("Typing with the cursor out of view left the JSON view at " + now + ", not near " + bottom[0]);
                    }
                    if (opened[0] > 1) {
                        throw new IllegalStateException("The JSON view opened " + opened[0] + " pixels down, not at its top");
                    }
                }));
        return d;
    }

    private static AbstractWidget box(Minecraft mc) {
        Object box = Reflect.get(editor(mc), "rawBox");
        if (box == null) {
            throw new IllegalStateException("The editor isn't showing the JSON view");
        }
        return (AbstractWidget) box;
    }

    /** How far the box is scrolled, by the field vanilla keeps it in. */
    private static double offset(Minecraft mc) {
        return ((Number) Reflect.get(box(mc), "scrollAmount")).doubleValue();
    }

    /** A point part of the way down the strip right of the box, where its scroll bar is. */
    private static int[] bar(Minecraft mc, float along) {
        AbstractWidget box = box(mc);
        return new int[]{box.getX() + box.getWidth() + 4, box.getY() + 1 + Math.round((box.getHeight() - 3) * along)};
    }
}
