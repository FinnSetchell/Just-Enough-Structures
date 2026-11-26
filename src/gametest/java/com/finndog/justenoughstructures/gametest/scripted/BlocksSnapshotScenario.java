package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.Exports;
import com.finndog.justenoughstructures.client.render.Highlight;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * Closing the browser while a Blocks row's tint is still being worked out lets go of the structure it
 * showed once that work has ended. The build ran on with the closed highlight, which held the structure
 * until a later highlight came along: about 1.3 MB for the mansion. Checked when Esc comes the frame
 * after the row is hovered, and when the mouse leaves the row first: the closed highlight the build
 * keeps must no longer hold what it was searching, and where the closed browser itself is let go, so
 * must the structure. On some builds something else keeps the closed browser a while, nothing hovered
 * or not (1.21.1 Fabric, 26 November 2026), and there only the first can be checked.
 */
final class BlocksSnapshotScenario {
    private static final String STRUCTURE = "mansion";
    private final Minecraft mc;
    private final Director d;
    /** Weak, so the check never keeps the structure itself. */
    private WeakReference<StructureSnapshot> watched = new WeakReference<>(null);
    private WeakReference<Object> screen = new WeakReference<>(null);
    private WeakReference<Object> highlight = new WeakReference<>(null);
    private String row;
    /** Whether the build was still going when its highlight closed, without which the round shows nothing. */
    private boolean caught;

    private BlocksSnapshotScenario(Minecraft mc) {
        this.mc = mc;
        this.d = new Director(mc);
    }

    static Director build(Minecraft mc) {
        BlocksSnapshotScenario s = new BlocksSnapshotScenario(mc);
        s.round("Esc on the next frame", false);
        s.round("the mouse leaving the row on the next frame, then Esc", true);
        return s.d;
    }

    private void round(String how, boolean leaveFirst) {
        d.then(run(() -> JesScreen.startOn(Ids.parse(STRUCTURE))))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 100))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(until(() -> browser(mc).idle() && browser(mc).result() != null, 2400))
                .then(moveTo(() -> browser(mc).tab("blocks"), 4))
                .then(click())
                .then(moveTo(() -> browser(mc).searchBox(), 1))
                // An earlier build still going would keep this row's from starting.
                .then(until(BlocksSnapshotScenario::buildOver, 600))
                .then(pause(5))
                .then(run(() -> {
                    row = biggestTintedRow();
                    watched = new WeakReference<>(browser(mc).result().snapshot());
                    screen = new WeakReference<>(browser(mc));
                    int[] at = browser(mc).highlightRow(row).orElseThrow(() -> new IllegalStateException(row + " has no row on screen"));
                    d.moveMouse(at[0], at[1]);
                }))
                .then(until(this::buildStarted, 200))
                .then(run(() -> {
                    Object building = staticField("building");
                    if (building == null || call(building, "owner") != Reflect.get(browser(mc), "highlight")) {
                        throw new IllegalStateException("hovering " + row + " in the " + STRUCTURE + " started no tint");
                    }
                    caught = !((CompletableFuture<?>) call(building, "result")).isDone();
                    highlight = new WeakReference<>(call(building, "owner"));
                    if (leaveFirst) {
                        int[] away = browser(mc).searchBox();
                        d.moveMouse(away[0], away[1]);
                    } else {
                        d.key(InputConstants.KEY_ESCAPE);
                    }
                }));
        if (leaveFirst) {
            d.then(until(() -> Reflect.get(browser(mc), "highlight") == null, 20))
                    .then(run(() -> caught &= !buildOver()))
                    .then(pressKey(InputConstants.KEY_ESCAPE));
        }
        d.then(until(() -> mc.screen == null, 40))
                .then(until(BlocksSnapshotScenario::buildOver, 600))
                .then(pause(5))
                .then(run(() -> {
                    if (mc.screen != null) {
                        throw new IllegalStateException("Esc left " + mc.screen.getClass().getSimpleName() + " open");
                    }
                    if (!buildOver()) {
                        throw new IllegalStateException("the tint of " + row + " was still being built 30 s after the browser closed");
                    }
                    Object building = staticField("building");
                    Object kept = building == null ? null : call(building, "owner");
                    boolean searching = kept != null && kept == highlight.get() && Reflect.get(kept, "find") != null;
                    for (int i = 0; i < 4; i++) {
                        System.gc();
                    }
                    boolean held = watched.get() != null;
                    boolean screenHeld = screen.get() != null;
                    JustEnoughStructures.LOGGER.info("In the check of the {}'s structure after {}: the tint of {} was {} when its highlight closed; once its build ended, the"
                                    + " highlight it keeps {} what it was searching, the closed browser was {}, and the structure {}", STRUCTURE, how, row,
                            caught ? "still being built" : "already built", searching ? "still held" : "no longer held", screenHeld ? "still held" : "let go",
                            held ? "still held" : "let go");
                    if (!caught) {
                        // The build finished in time to be taken in, as on a fast machine it can: nothing to check this round.
                        return;
                    }
                    if (searching) {
                        throw new IllegalStateException("after " + how + " while the tint of " + row + " was being built, the closed highlight its build keeps still"
                                + " holds what it was searching, the " + STRUCTURE + "'s structure");
                    }
                    if (held && !screenHeld) {
                        throw new IllegalStateException("after " + how + " while the tint of " + row + " was being built, the " + STRUCTURE
                                + "'s structure was still held once the build had ended");
                    }
                }));
    }

    /** Whether a tint's build has started for the highlight shown now. */
    private boolean buildStarted() {
        Object building = staticField("building");
        return building != null && call(building, "owner") == Reflect.get(browser(mc), "highlight");
    }

    /** Whether no tint is being built. One that has ended but not been taken in yet counts as over. */
    private static boolean buildOver() {
        Object building = staticField("building");
        return building == null || ((CompletableFuture<?>) call(building, "result")).isDone();
    }

    /** The Blocks row about the most blocks that is still tinted, so its build takes longest. */
    private String biggestTintedRow() {
        JesScreen s = browser(mc);
        for (Map.Entry<Block, Integer> e : Exports.blockCounts(s.result().snapshot())) {
            String key = "block:" + BuiltInRegistries.BLOCK.getKey(e.getKey());
            if (e.getValue() <= Highlight.MAX_BLOCKS && s.highlightRow(key).isPresent()) {
                return key;
            }
        }
        throw new IllegalStateException("the " + STRUCTURE + " has no tinted Blocks row on screen");
    }

    private static Object staticField(String name) {
        try {
            Field f = Highlight.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object call(Object record, String name) {
        try {
            Method m = record.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
