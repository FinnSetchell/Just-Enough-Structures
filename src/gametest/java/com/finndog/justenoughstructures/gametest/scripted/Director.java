package com.finndog.justenoughstructures.gametest.scripted;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.JesClient;
import com.finndog.justenoughstructures.gametest.Gallery;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
//? if fabric && <26.1 {
import net.fabricmc.loader.api.FabricLoader;
//?}
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import com.finndog.justenoughstructures.client.screen.Gui;
import net.minecraft.client.gui.GuiGraphics;
//? if >=26.1 {
/*import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
*///?}

/**
 * Plays a script of mouse and keyboard actions one frame at a time, through Minecraft's own input
 * handlers, and draws a cursor so it shows up in screenshots. When recording, every frame is saved
 * and the script only moves on once a new frame has been drawn, so recordings play back smoothly
 * however slow the capture is.
 */
public final class Director {
    public interface Action {
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
    private static Method keyPress;

    public Director(Minecraft mc) {
        this.mc = mc;
        this.cursorX = mc.getWindow().getGuiScaledWidth() / 2.0;
        this.cursorY = mc.getWindow().getGuiScaledHeight() / 2.0;
    }

    public Director then(Action action) {
        actions.add(action);
        return this;
    }

    boolean done() {
        return current == null && next >= actions.size();
    }

    /** Another script this one is playing through, for a suite of them in one game, and how it's going. */
    private Director inner;
    private String innerName;
    private int innerTicks;
    private RuntimeException innerFailure;
    // How long each script of a suite may take. A big pack's first loot index can take longer.
    private static final int INNER_MINUTES = Integer.getInteger("jes.autoshot.suite.minutes", 15);
    private static final int INNER_TICKS = 20 * 60 * INNER_MINUTES;

    /**
     * Plays another script to its end before going on, drawing and recording as it would on its own.
     * One that throws, or that's still going after {@link #INNER_MINUTES}, is logged and left, so the rest
     * of the suite still runs. One that can't run here throws UnsupportedOperationException as it's
     * built, and is logged as skipped.
     */
    public static Action play(String name, Supplier<Director> script) {
        return (d, frame) -> {
            if (frame == 0) {
                // Each starts in the world with nothing open, as it would on its own, whatever the
                // one before was in the middle of when it was left.
                if (d.pressed) {
                    d.release(0);
                }
                d.mc.setScreen(null);
                d.innerName = name;
                d.innerTicks = 0;
                d.innerFailure = null;
                try {
                    d.inner = script.get();
                } catch (RuntimeException e) {
                    d.innerFailure = e;
                }
                return false;
            }
            String result = d.innerFailure instanceof UnsupportedOperationException skip ? "skipped: " + skip.getMessage()
                    : d.innerFailure != null ? "failed: " + d.innerFailure
                    : d.inner.done() ? "finished" : "gave up after " + INNER_MINUTES + " minutes";
            JustEnoughStructures.LOGGER.info("SUITE {} {}", name, result);
            if (d.innerFailure != null && !(d.innerFailure instanceof UnsupportedOperationException)) {
                JustEnoughStructures.LOGGER.error("SUITE {} failed", name, d.innerFailure);
            }
            d.inner = null;
            return true;
        };
    }

    private boolean innerRunning() {
        return inner != null && innerFailure == null && !inner.done() && innerTicks < INNER_TICKS;
    }

    int recordedFrames() {
        return recorded;
    }

    /** Called at the end of every client tick. */
    void tick() {
        if (innerRunning()) {
            innerTicks++;
            try {
                inner.tick();
            } catch (RuntimeException e) {
                innerFailure = e;
            }
            return;
        }
        if (recordTo != null) {
            if (renders == consumedRenders) {
                return;
            }
            consumedRenders = renders;
            grab(String.format("%s/f_%04d.png", recordTo, recorded++));
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
        if (inner != null) {
            inner.onRender(g);
            return;
        }
        renders++;
        if (!showCursor) {
            return;
        }
        Gui.push(g);
        Gui.lift(g, 800);
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
        Gui.pop(g);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        g.fill(cx - r, cy - r, cx + r, cy - r + 1, color);
        g.fill(cx - r, cy + r - 1, cx + r, cy + r, color);
        g.fill(cx - r, cy - r + 1, cx - r + 1, cy + r - 1, color);
        g.fill(cx + r - 1, cy - r + 1, cx + r, cy + r - 1, color);
    }

    // ------------------------------------------------------------------ raw input

    void moveMouse(double x, double y) {
        //? if >=26.3 {
        /*// From 26.3 the game's told how far the mouse moved as well, which turns the player.
        double dx = (x - cursorX) * scaleX();
        double dy = (y - cursorY) * scaleY();
        cursorX = x;
        cursorY = y;
        call("onMove", mc.mouseHandler, window(), x * scaleX(), y * scaleY(), dx, dy);
        *///?} else {
        cursorX = x;
        cursorY = y;
        call("onMove", mc.mouseHandler, window(), x * scaleX(), y * scaleY());
        //?}
    }

    void press(int button) {
        pressed = true;
        clickX = (int) Math.round(cursorX);
        clickY = (int) Math.round(cursorY);
        clickAge = 0;
        button(button, InputConstants.PRESS);
    }

    void release(int button) {
        pressed = false;
        button(button, InputConstants.RELEASE);
    }

    private void button(int button, int action) {
        //? if >=26.1 {
        /*call("onPress", mc.mouseHandler, window(), new MouseButtonInfo(button, 0), action);
        *///?} else {
        call("onPress", mc.mouseHandler, window(), button, action, 0);
        //?}
    }

    void scroll(double amount) {
        call("onScroll", mc.mouseHandler, window(), 0.0, amount);
    }

    void key(int key) {
        //? if >=26.1 {
        /*call("keyPress", mc.keyboardHandler, window(), InputConstants.PRESS, new KeyEvent(key, 0, 0));
        call("keyPress", mc.keyboardHandler, window(), InputConstants.RELEASE, new KeyEvent(key, 0, 0));
        *///?} else {
        mc.keyboardHandler.keyPress(window(), key, 0, InputConstants.PRESS, 0);
        mc.keyboardHandler.keyPress(window(), key, 0, InputConstants.RELEASE, 0);
        //?}
    }

    void typeChar(char c) {
        //? if >=26.1 {
        /*call("charTyped", mc.keyboardHandler, window(), new CharacterEvent(c));
        *///?} else {
        call("charTyped", mc.keyboardHandler, window(), (int) c, 0);
        //?}
    }

    void screenshot(String name) {
        new File(mc.gameDirectory, "screenshots/review").mkdirs();
        grab("review/" + name + ".png");
    }

    private void grab(String file) {
        Gallery.grab(mc, file);
    }

    private long window() {
        //? if >=26.1 {
        /*return mc.getWindow().handle();
        *///?} else {
        return mc.getWindow().getWindow();
        //?}
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
                //? if >=26.3 {
                /*onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class, double.class, double.class);
                *///?} else if >=26.1 {
                /*onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
                *///?} else if fabric {
                // A real game's names are obfuscated, so the ones it has are looked up from their intermediary names.
                onMove = gameMethod(MouseHandler.class, "net.minecraft.class_312", "method_1600", "(JDD)V", long.class, double.class, double.class);
                //?} else {
                /*onMove = named(MouseHandler.class, "onMove", "m_91561_", long.class, double.class, double.class);
                *///?}
                //? if >=26.1 {
                /*// 26.1 hands the game each button and key as an event, and keeps the key handler to itself.
                onScroll = MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
                onPress = MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class);
                charTyped = KeyboardHandler.class.getDeclaredMethod("charTyped", long.class, CharacterEvent.class);
                keyPress = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
                *///?} else if fabric {
                onScroll = gameMethod(MouseHandler.class, "net.minecraft.class_312", "method_1598", "(JDD)V", long.class, double.class, double.class);
                onPress = gameMethod(MouseHandler.class, "net.minecraft.class_312", "method_1601", "(JIII)V", long.class, int.class, int.class, int.class);
                charTyped = gameMethod(KeyboardHandler.class, "net.minecraft.class_309", "method_1457", "(JII)V", long.class, int.class, int.class);
                keyPress = onPress;
                //?} else {
                /*onScroll = named(MouseHandler.class, "onScroll", "m_91526_", long.class, double.class, double.class);
                onPress = named(MouseHandler.class, "onPress", "m_91530_", long.class, int.class, int.class, int.class);
                charTyped = named(KeyboardHandler.class, "charTyped", "m_90889_", long.class, int.class, int.class);
                keyPress = onPress;
                *///?}
                for (Method m : new Method[]{onMove, onPress, onScroll, charTyped, keyPress}) {
                    m.setAccessible(true);
                }
            }
            Method method = switch (name) {
                case "onMove" -> onMove;
                case "onPress" -> onPress;
                case "onScroll" -> onScroll;
                case "charTyped" -> charTyped;
                case "keyPress" -> keyPress;
                default -> throw new IllegalArgumentException(name);
            };
            method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Couldn't fake input", e);
        }
    }

    /**
     * One of the game's own methods by name, or by its SRG name in a real Forge 1.20.1 game, which
     * keeps those rather than the names its code was written with.
     */
    private static Method named(Class<?> owner, String name, String srg, Class<?>... params) throws NoSuchMethodException {
        try {
            return owner.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            return owner.getDeclaredMethod(srg, params);
        }
    }

    //? if fabric && <26.1 {
    /**
     * One of the game's own methods, by its intermediary name: in a dev game that's turned into the
     * name it has there, and a real game uses intermediary names already.
     */
    private static Method gameMethod(Class<?> owner, String intermediaryOwner, String intermediary, String descriptor, Class<?>... params)
            throws NoSuchMethodException {
        String name = FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", intermediaryOwner, intermediary, descriptor);
        return owner.getDeclaredMethod(name, params);
    }
    //?}

    // ------------------------------------------------------------------ actions

    /** Smootherstep: starts and ends gently, like a hand moving a mouse. */
    static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    public static Action moveTo(Supplier<int[]> target, int frames) {
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
    public static Action dragTo(Supplier<int[]> target, int frames, int button) {
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

    public static Action dragBy(int dx, int dy, int frames) {
        return new Action() {
            Action inner;

            @Override
            public boolean step(Director d, int frame) {
                if (inner == null) {
                    int[] to = {(int) Math.round(d.cursorX) + dx, (int) Math.round(d.cursorY) + dy};
                    inner = dragTo(() -> to, frames, InputConstants.MOUSE_BUTTON_LEFT);
                }
                return inner.step(d, frame);
            }
        };
    }

    public static Action click() {
        return (d, frame) -> {
            if (frame == 0) {
                d.press(InputConstants.MOUSE_BUTTON_LEFT);
            } else if (frame == 2) {
                d.release(InputConstants.MOUSE_BUTTON_LEFT);
            }
            return frame >= 3;
        };
    }

    public static Action pause(int frames) {
        return (d, frame) -> frame + 1 >= frames;
    }

    /**
     * How many times longer than the scripts ask to wait for something before going on, for big
     * modpacks, where a reload or a busy server can take far longer than in the dev game.
     */
    private static final int PATIENCE = Integer.getInteger("jes.autoshot.patience", 1);

    public static Action until(BooleanSupplier condition, int maxFrames) {
        return (d, frame) -> condition.getAsBoolean() || frame >= maxFrames * PATIENCE;
    }

    public static Action run(Runnable runnable) {
        return (d, frame) -> {
            runnable.run();
            return true;
        };
    }

    /**
     * Presses whichever key opens the browser, as a pack can move it off K when another mod has that.
     * Anything else on the same key is logged, since only one of them gets the press.
     */
    public static Action pressBrowserKey() {
        return (d, frame) -> {
            InputConstants.Key key = InputConstants.getKey(JesClient.OPEN.saveString());
            for (KeyMapping other : d.mc.options.keyMappings) {
                if (other != JesClient.OPEN && other.same(JesClient.OPEN)) {
                    JustEnoughStructures.LOGGER.warn("The browser's key {} is also {}", key.getName(), other.getName());
                }
            }
            d.key(key.getValue());
            return true;
        };
    }

    public static Action pressKey(int key) {
        return (d, frame) -> {
            d.key(key);
            return true;
        };
    }

    public static Action type(String text, int framesPerChar) {
        return (d, frame) -> {
            int index = frame / framesPerChar;
            if (frame % framesPerChar == 0 && index < text.length()) {
                d.typeChar(text.charAt(index));
            }
            return index >= text.length();
        };
    }

    /** Backspace {@code count} times, one every {@code framesPer} frames. */
    public static Action erase(int count, int framesPer) {
        return (d, frame) -> {
            if (frame % framesPer == 0 && frame / framesPer < count) {
                d.key(InputConstants.KEY_BACKSPACE);
            }
            return frame / framesPer >= count;
        };
    }

    /** Plays actions one after another as one. */
    public static Action chain(Action... actions) {
        int[] at = {0};
        int[] start = {0};
        return (d, frame) -> {
            if (frame == 0) {
                at[0] = 0;
                start[0] = 0;
            }
            if (at[0] < actions.length && actions[at[0]].step(d, frame - start[0])) {
                at[0]++;
                start[0] = frame + 1;
            }
            return at[0] >= actions.length;
        };
    }

    /** Skips {@code action} if {@code when} holds as it starts. */
    public static Action skipIf(BooleanSupplier when, Action action) {
        boolean[] skip = new boolean[1];
        return (d, frame) -> {
            if (frame == 0) {
                skip[0] = when.getAsBoolean();
            }
            return skip[0] || action.step(d, frame);
        };
    }

    public static Action wheel(double amount) {
        return (d, frame) -> {
            d.scroll(amount);
            return true;
        };
    }

    /** Starts saving every frame to screenshots/{@code name}. */
    public static Action record(String name) {
        return (d, frame) -> {
            d.recordTo = name;
            new File(d.mc.gameDirectory, "screenshots/" + name).mkdirs();
            return true;
        };
    }

    public static Action shoot(String name) {
        return (d, frame) -> {
            if (frame == 1) {
                d.screenshot(name);
            }
            return frame >= 2;
        };
    }
}
