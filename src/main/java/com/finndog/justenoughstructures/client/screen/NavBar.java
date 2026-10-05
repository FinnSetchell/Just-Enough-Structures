package com.finndog.justenoughstructures.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

/**
 * The slim bar along the top of every JES screen: Back and where it goes on the left, Forward and
 * where it goes on the right. Backspace and Shift+Backspace do the same, and so do the mouse's side
 * buttons.
 */
final class NavBar {
    /** Where a screen's own panels start, below the bar. */
    static final int TOP = 20;
    private static final int Y = 3;
    private static final int HEIGHT = 14;
    private static final int SIDE = 8;

    private final Screen screen;
    private final Arrow back = new Arrow(true, Component.translatable("screen.justenoughstructures.nav.back"), b -> Nav.back());
    private final Arrow forward = new Arrow(false, Component.translatable("screen.justenoughstructures.nav.forward"), b -> Nav.forward());
    private Component backTo;
    private Component forwardTo;
    /** Whether where they go is written beside the buttons, so their tooltips needn't say it. */
    private boolean written;

    NavBar(Screen screen) {
        this.screen = screen;
    }

    /** Drawn after everything else on the screen, so it stays usable over a popup. */
    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        Nav.Place backPlace = Nav.backTarget(screen);
        Nav.Place forwardPlace = Nav.forwardTarget(screen);
        Component newBack = backPlace == null ? null : backPlace.label();
        Component newForward = forwardPlace == null ? null : forwardPlace.label();

        back.setWidth(back.contentWidth(font) + 10);
        forward.setWidth(forward.contentWidth(font) + 10);
        back.setPosition(SIDE, Y);
        forward.setPosition(screen.width - SIDE - forward.getWidth(), Y);
        // Where each goes, beside it, each given half of what's left between them, or all of it
        // when the other has nowhere to go.
        int between = forward.getX() - back.getX() - back.getWidth() - 16;
        int room = newBack != null && newForward != null ? between / 2 : between;
        boolean nowWritten = room > 20;
        if (!same(newBack, backTo) || nowWritten != written) {
            back.setTooltip(tooltip(newBack, true, nowWritten));
        }
        if (!same(newForward, forwardTo) || nowWritten != written) {
            forward.setTooltip(tooltip(newForward, false, nowWritten));
        }
        backTo = newBack;
        forwardTo = newForward;
        written = nowWritten;
        back.active = backTo != null;
        forward.active = forwardTo != null;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        back.render(g, mouseX, mouseY, partialTick);
        forward.render(g, mouseX, mouseY, partialTick);
        int textY = Y + (HEIGHT - 8) / 2;
        if (backTo != null && written) {
            Gui.drawClipped(g, font, Component.translatable("screen.justenoughstructures.nav.to", backTo).getString(),
                    back.getX() + back.getWidth() + 4, textY, room, 0xFFDDDDDD, true);
        }
        if (forwardTo != null && written) {
            String full = Component.translatable("screen.justenoughstructures.nav.to", forwardTo).getString();
            String text = Gui.clip(font, full, room);
            Gui.drawClipped(g, font, full, forward.getX() - 4 - font.width(text), textY, room, 0xFFDDDDDD, true);
        }
        g.pose().popPose();
    }

    private static boolean same(Component a, Component b) {
        return a == null ? b == null : b != null && a.getString().equals(b.getString());
    }

    /** Its keys, and where it goes when that isn't written beside it, or none when there's nowhere to go. */
    private static Tooltip tooltip(Component to, boolean isBack, boolean written) {
        if (to == null) {
            return null;
        }
        String which = isBack ? "back" : "forward";
        MutableComponent keys = Component.translatable("screen.justenoughstructures.nav." + which + "_keys");
        if (written) {
            return Tooltip.create(keys);
        }
        return Tooltip.create(Component.translatable("screen.justenoughstructures.nav." + which + "_to", to)
                .append("\n").append(keys.withStyle(ChatFormatting.GRAY)));
    }

    /** Clicks on the bar, and the mouse's back and forward buttons anywhere. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            Nav.back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            Nav.forward();
            return true;
        }
        return back.mouseClicked(mouseX, mouseY, button) || forward.mouseClicked(mouseX, mouseY, button);
    }

    /** Backspace goes back and Shift+Backspace forward, unless a text box has the keyboard. */
    boolean keyPressed(int key, int modifiers) {
        if (key != GLFW.GLFW_KEY_BACKSPACE || typing(screen)) {
            return false;
        }
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            Nav.forward();
        } else {
            Nav.back();
        }
        return true;
    }

    private static boolean typing(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        return focused instanceof EditBox box && box.canConsumeInput()
                || focused instanceof MultiLineEditBox text && text.isFocused();
    }

    /** Where the buttons are, for the screenshot harness. */
    int[] backCentre() {
        return new int[]{back.getX() + back.getWidth() / 2, Y + HEIGHT / 2};
    }

    int[] forwardCentre() {
        return new int[]{forward.getX() + forward.getWidth() / 2, Y + HEIGHT / 2};
    }

    /** A short button with a chevron before or after its label. */
    private static final class Arrow extends Button {
        private static final int CHEVRON = 5;
        private final boolean pointsBack;

        Arrow(boolean pointsBack, Component label, OnPress onPress) {
            super(0, Y, 40, HEIGHT, label, onPress, DEFAULT_NARRATION);
            this.pointsBack = pointsBack;
        }

        int contentWidth(Font font) {
            return CHEVRON + 4 + font.width(getMessage());
        }

        @Override
        public void renderString(GuiGraphics g, Font font, int colour) {
            int x = getX() + (getWidth() - contentWidth(font)) / 2;
            int y = getY() + (getHeight() - 8) / 2;
            if (pointsBack) {
                chevron(g, x, y, colour);
                g.drawString(font, getMessage(), x + CHEVRON + 4, y, colour, true);
            } else {
                g.drawString(font, getMessage(), x, y, colour, true);
                chevron(g, x + font.width(getMessage()) + 4, y, colour);
            }
        }

        private void chevron(GuiGraphics g, int x, int y, int colour) {
            NavBar.chevron(g, x, y, colour, pointsBack);
        }
    }

    /** An arrow head five wide, two pixels thick and seven tall, like a letter, with a shadow like one. */
    static void chevron(GuiGraphics g, int x, int y, int colour, boolean pointsBack) {
        int shadow = (colour & 0xFF000000) | ((colour & 0xFCFCFC) >> 2);
        for (int pass = 0; pass < 2; pass++) {
            int offset = pass == 0 ? 1 : 0;
            int c = pass == 0 ? shadow : colour;
            for (int row = 0; row < 7; row++) {
                int dx = Math.abs(3 - row);
                int px = pointsBack ? x + dx : x + 3 - dx;
                g.fill(px + offset, y + row + offset, px + 2 + offset, y + row + 1 + offset, c);
            }
        }
    }
}
