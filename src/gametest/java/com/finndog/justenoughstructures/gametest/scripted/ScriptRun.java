package com.finndog.justenoughstructures.gametest.scripted;

import com.finndog.justenoughstructures.gametest.Gallery;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A scripted screenshot run, the same on every loader: makes a superflat world, or joins the server
 * the game was started with, plays one of the {@link Scenarios} in it, writes a summary and quits.
 * The loader's test mod calls {@link #tick} every client tick, and {@link #afterScreen} and
 * {@link #afterHud} once those are drawn, as the scripts draw a cursor and count frames there.
 */
public final class ScriptRun {
    private enum Step { START, WAIT_WORLD, SCRIPT, DONE }

    private static final Map<Class<?>, Method> GRAPHICS = new HashMap<>();

    private Step step = Step.START;
    private int stepTicks;
    /** How long another screen has kept the world or the script from starting. */
    private int blockedTicks;
    private final Path out;
    private final boolean hide;
    private final String mode;
    /** Joins the server the game was started with (-Pjoin) instead of making a world. */
    private final boolean joins = Boolean.getBoolean("jes.autoshot.join");
    private Director director;
    private final JsonArray summary = new JsonArray();

    private ScriptRun(Path out, boolean hide, String mode) {
        this.out = out;
        this.hide = hide;
        this.mode = mode;
    }

    /** The run the settings ask for, or null when this isn't a scripted one ({@code -Djes.autoshot.mode} other than gallery). */
    public static ScriptRun fromProperties() {
        String folder = System.getProperty("jes.autoshot");
        String mode = System.getProperty("jes.autoshot.mode", "gallery");
        if (folder == null || folder.isBlank() || mode.equals("gallery")) {
            return null;
        }
        return new ScriptRun(Path.of(folder), Boolean.parseBoolean(System.getProperty("jes.autoshot.hidden", "true")), mode);
    }

    public void tick(Minecraft mc) {
        stepTicks++;
        switch (step) {
            case START -> {
                if (hide && stepTicks == 1) {
                    Gallery.hideWindow(mc);
                }
                if (joins) {
                    go(Step.WAIT_WORLD);
                    return;
                }
                blockedTicks = Gallery.menuWait(mc, blockedTicks);
                if (Gallery.canStart(mc, blockedTicks)) {
                    blockedTicks = 0;
                    Gallery.startWorld(mc, hide, "teleport".equals(mode));
                    go(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                blockedTicks = Gallery.closeJoinScreen(mc, blockedTicks);
                // On a server, long enough after joining for the notices that pop up then to go.
                if (mc.player != null && mc.level != null && mc.screen == null && stepTicks > (joins ? 200 : 40)) {
                    director = Scenarios.build(mode, mc);
                    go(Step.SCRIPT);
                }
            }
            case SCRIPT -> {
                director.tick();
                if (director.done() || stepTicks > 20 * 60 * Integer.getInteger("jes.autoshot.minutes", 10)) {
                    JsonObject row = new JsonObject();
                    row.addProperty("mode", mode);
                    row.addProperty("finished", director.done());
                    row.addProperty("frames", director.recordedFrames());
                    summary.add(row);
                    finish(mc);
                }
            }
            case DONE -> {
            }
        }
    }

    /** After a screen is drawn. */
    public void afterScreen(GuiGraphics graphics) {
        if (director != null) {
            director.onRender(graphics);
        }
    }

    /** After the HUD is drawn, which counts only with no screen open. */
    public void afterHud(GuiGraphics graphics) {
        if (director != null && Minecraft.getInstance().screen == null) {
            director.onRender(graphics);
        }
    }

    /**
     * What a loader's drawing event draws with, found by what its getter returns rather than by its
     * name, so the same code works whatever a loader's version calls it. 26.1's new name for that
     * class is put in by a text replacement everywhere, which a getter still called by the old name
     * wouldn't survive.
     */
    public static GuiGraphics graphics(Object event) {
        Method getter = GRAPHICS.computeIfAbsent(event.getClass(), type -> {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == GuiGraphics.class) {
                    return method;
                }
            }
            throw new IllegalStateException("Nothing to draw with on " + type);
        });
        try {
            return (GuiGraphics) getter.invoke(event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void go(Step next) {
        step = next;
        stepTicks = 0;
    }

    private void finish(Minecraft mc) {
        go(Step.DONE);
        Gallery.writeSummary(out, summary);
        mc.stop();
    }
}
