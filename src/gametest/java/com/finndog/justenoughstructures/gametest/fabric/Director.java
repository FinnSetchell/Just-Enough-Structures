package com.finndog.justenoughstructures.gametest.fabric;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Plays a script of mouse and keyboard actions one frame at a time, through Minecraft's own input
 * handlers, and draws a cursor so it shows up in screenshots. When recording, every frame is saved
 * and the script only moves on once a new frame has been drawn, so recordings play back smoothly
 * however slow the capture is.
 */
final class Director {
    interface Action {
        /** Called once per frame; {@code frame} counts up from 0. Returns true when finished. */
        boolean step(Director d, int frame);
    }

    private static final String[] CURSOR = {
            "X",
            "XX",
            "X.X",
            "X..X",
            "X...X",
            "X....X",
            "X.....X",
            "X......X",
            "X.......X",
            "X........X",
            "X.....XXXXX",
            "X..X..X",
            "X.X X..X",
            "XX  X..X",
            "X    X..X",
            "     X..X",
            "      XX",
    };

    private final Minecraft mc;
    private final List<Action> actions = new ArrayList<>();
    private int next;
    private Action current;
    private int frame;

    private String recordTo;
    private int recorded;
    private int renders;
    private int consumedRenders = -1;

    double cursorX;
    double cursorY;
    private boolean pressed;
    private int clickX;
    private int clickY;
    private int clickAge = 100;
    boolean showCursor = true;

    private static Method onMove;
    private static Method onPress;
    private static Method onScroll;
    private static Method charTyped;

    Director(Minecraft mc, String recordTo) {
        this.mc = mc;
        this.recordTo = recordTo;
        this.cursorX = mc.getWindow().getGuiScaledWidth() / 2.0;
        this.cursorY = mc.getWindow().getGuiScaledHeight() / 2.0;
        if (recordTo != null) {
            new File(mc.gameDirectory, "screenshots/" + recordTo).mkdirs();
        }
    }

    Director then(Action action) {
        actions.add(action);
        return this;
    }

    boolean done() {
        return current == null && next >= actions.size();
    }

    int recordedFrames() {
        return recorded;
    }

    /** Called at the end of every client tick. */
    void tick() {
        if (recordTo != null) {
            if (renders == consumedRenders) {
                return;
            }
            consumedRenders = renders;
            Screenshot.grab(mc.gameDirectory, String.format("%s/f_%04d.png", recordTo, recorded++), mc.getMainRenderTarget(), m -> {
            });
        }
        clickAge++;
        if (current == null) {
            if (next >= actions.size()) {
                return;
            }
            current = actions.get(next++);
            frame = 0;
        }
        if (current.step(this, frame++)) {
            current = null;
        }
    }

    /** Called after a screen (or the HUD) is drawn: counts the frame and draws the cursor over it. */
    void onRender(GuiGraphics g) {
        renders++;
        if (!showCursor) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 800);
        if (clickAge < 8) {
            int r = 3 + clickAge * 2;
            int alpha = (int) (160 * (1f - clickAge / 8f)) << 24;
            ring(g, clickX, clickY, r, alpha | 0xFFFFFF);
        }
        int x = (int) Math.round(cursorX);
        int y = (int) Math.round(cursorY);
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == 'X') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, 0xFF000000);
                } else if (c == '.') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, pressed ? 0xFFD0D0D0 : 0xFFFFFFFF);
                }
            }
        }
        g.pose().popPose();
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        g.fill(cx - r, cy - r, cx + r, cy - r + 1, color);
        g.fill(cx - r, cy + r - 1, cx + r, cy + r, color);
        g.fill(cx - r, cy - r + 1, cx - r + 1, cy + r - 1, color);
        g.fill(cx + r - 1, cy - r + 1, cx + r, cy + r - 1, color);
    }

    // ------------------------------------------------------------------ raw input

    void moveMouse(double x, double y) {
        cursorX = x;
        cursorY = y;
        call("onMove", mc.mouseHandler, window(), x * scaleX(), y * scaleY());
    }

    void press(int button) {
        pressed = true;
        clickX = (int) Math.round(cursorX);
        clickY = (int) Math.round(cursorY);
        clickAge = 0;
        call("onPress", mc.mouseHandler, window(), button, GLFW.GLFW_PRESS, 0);
    }

    void release(int button) {
        pressed = false;
        call("onPress", mc.mouseHandler, window(), button, GLFW.GLFW_RELEASE, 0);
    }

    void scroll(double amount) {
        call("onScroll", mc.mouseHandler, window(), 0.0, amount);
    }

    void key(int key) {
        mc.keyboardHandler.keyPress(window(), key, 0, GLFW.GLFW_PRESS, 0);
        mc.keyboardHandler.keyPress(window(), key, 0, GLFW.GLFW_RELEASE, 0);
    }

    void typeChar(char c) {
        call("charTyped", mc.keyboardHandler, window(), (int) c, 0);
    }

    void screenshot(String name) {
        new File(mc.gameDirectory, "screenshots/review").mkdirs();
        Screenshot.grab(mc.gameDirectory, "review/" + name + ".png", mc.getMainRenderTarget(), m -> {
        });
    }

    private long window() {
        return mc.getWindow().getWindow();
    }

    private double scaleX() {
        return (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
    }

    private double scaleY() {
        return (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
    }

    private static void call(String name, Object target, Object... args) {
        try {
            if (onMove == null) {
                onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                onPress = MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
                onScroll = MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
                charTyped = KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, int.class, int.class);
                for (Method m : new Method[]{onMove, onPress, onScroll, charTyped}) {
                    m.setAccessible(true);
                }
            }
            Method method = switch (name) {
                case "onMove" -> onMove;
                case "onPress" -> onPress;
                case "onScroll" -> onScroll;
                case "charTyped" -> charTyped;
                default -> throw new IllegalArgumentException(name);
            };
            method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Couldn't fake input", e);
        }
    }

    // ------------------------------------------------------------------ actions

    /** Smootherstep: starts and ends gently, like a hand moving a mouse. */
    static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    static Action moveTo(Supplier<int[]> target, int frames) {
        return new Action() {
            double fromX, fromY;
            int[] to;

            @Override
            public boolean step(Director d, int frame) {
                if (frame == 0) {
                    fromX = d.cursorX;
                    fromY = d.cursorY;
                    to = target.get();
                }
                double t = ease((double) (frame + 1) / frames);
                d.moveMouse(fromX + (to[0] - fromX) * t, fromY + (to[1] - fromY) * t);
                return frame + 1 >= frames;
            }
        };
    }

    /** Holds the button while moving to {@code target}, then lets go. */
    static Action dragTo(Supplier<int[]> target, int frames, int button) {
        Action move = moveTo(target, frames);
        return (d, frame) -> {
            if (frame == 0) {
                d.press(button);
                return false;
            }
            if (move.step(d, frame - 1)) {
                d.release(button);
                return true;
            }
            return false;
        };
    }

    static Action dragBy(int dx, int dy, int frames) {
        return new Action() {
            Action inner;

            @Override
            public boolean step(Director d, int frame) {
                if (inner == null) {
                    int[] to = {(int) Math.round(d.cursorX) + dx, (int) Math.round(d.cursorY) + dy};
                    inner = dragTo(() -> to, frames, 0);
                }
                return inner.step(d, frame);
            }
        };
    }

    static Action click() {
        return (d, frame) -> {
            if (frame == 0) {
                d.press(0);
            } else if (frame == 2) {
                d.release(0);
            }
            return frame >= 3;
        };
    }

    static Action pause(int frames) {
        return (d, frame) -> frame + 1 >= frames;
    }

    static Action until(BooleanSupplier condition, int maxFrames) {
        return (d, frame) -> condition.getAsBoolean() || frame >= maxFrames;
    }

    static Action run(Runnable runnable) {
        return (d, frame) -> {
            runnable.run();
            return true;
        };
    }

    static Action pressKey(int key) {
        return (d, frame) -> {
            d.key(key);
            return true;
        };
    }

    static Action type(String text, int framesPerChar) {
        return (d, frame) -> {
            int index = frame / framesPerChar;
            if (frame % framesPerChar == 0 && index < text.length()) {
                d.typeChar(text.charAt(index));
            }
            return index >= text.length();
        };
    }

    static Action wheel(double amount) {
        return (d, frame) -> {
            d.scroll(amount);
            return true;
        };
    }

    /** Starts saving every frame to screenshots/{@code name}. */
    static Action record(String name) {
        return (d, frame) -> {
            d.recordTo = name;
            new File(d.mc.gameDirectory, "screenshots/" + name).mkdirs();
            return true;
        };
    }

    static Action shoot(String name) {
        return (d, frame) -> {
            if (frame == 1) {
                d.screenshot(name);
            }
            return frame >= 2;
        };
    }
}
