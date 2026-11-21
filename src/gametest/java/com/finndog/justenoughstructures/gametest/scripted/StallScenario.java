package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.click;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
//? if >=1.21.2 {
/*import net.minecraft.client.InactivityFpsLimit;
*///?}

/**
 * Times frames while the Blocks tab's rows about the most blocks it tints are first hovered, while
 * the layer slider cuts through the layers with the most blocks, and while it's dragged. By default
 * on a woodland mansion, whose Blocks tab has rows about thousands of blocks, and an ancient city,
 * whose layers are big; -Pstructures names others. Frames aren't limited while it runs, so a frame
 * that holds things up shows as a long one instead of hiding in the wait for the next. Each try logs
 * a "STALLS" line: the median frame before it, the slowest frame until the tint or the cut showed
 * and over the second after, and how long until it showed. The last lines give the median and range
 * of the slowest frames until shown over every round, and say LOOK where they held things up.
 */
final class StallScenario {
    private static final int ROUNDS = Integer.getInteger("jes.autoshot.stalls.rounds", 6);
    /** How many places the slider is set to in each round: the layers with the most blocks, where a cut costs most. */
    private static final int CUTS = 4;
    /** Ticks of frames noted before each try, and after it. */
    private static final int BEFORE = 8;
    private static final int AFTER = 20;
    /**
     * How much longer than the frames before it the slowest frame until a hover or a cut showed may
     * take, in the median over the rounds, before the summary says LOOK: about a frame missed at 60
     * frames a second. Drags aren't judged, as their slowest frame is over a whole second and five
     * cuts.
     */
    private static final double HELD_UP_MS = 15;

    private StallScenario() {
    }

    static Director build(Minecraft mc) {
        String wanted = System.getProperty("jes.autoshot.structures", "");
        List<String> ids = new ArrayList<>();
        for (String id : (wanted.isBlank() ? "mansion,ancient_city" : wanted).split(",")) {
            if (!id.isBlank()) {
                ids.add(id.trim());
            }
        }
        JesScreen.startOn(Ids.parse(ids.get(0)));
        Director d = new Director(mc);
        State state = new State(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(run(() -> browser(mc).setSpin(false)))
                .then(run(state::unlimitFrames));
        for (int i = 0; i < ids.size(); i++) {
            String name = Ids.parse(ids.get(i)).getPath();
            if (i > 0) {
                String id = ids.get(i);
                d.then(run(() -> browser(mc).select(Ids.parse(id))))
                        .then(pause(10));
            }
            d.then(until(() -> browser(mc).idle(), 2400))
                    .then(moveTo(() -> browser(mc).tab("blocks"), 8))
                    .then(click())
                    .then(until(() -> browser(mc).result() != null && !state.pickRows().isEmpty(), 100))
                    .then(moveTo(() -> browser(mc).searchBox(), 4))
                    .then(pause(20));
            // A first round that isn't counted, as the code it times is still being compiled.
            state.round(d, name + " warm-up");
            for (int round = 0; round < ROUNDS; round++) {
                state.round(d, name);
            }
        }
        d.then(run(state::summarise))
                .then(run(state::restoreFrames));
        return d;
    }

    /** What the rounds share: the rows to hover, and the slowest frames seen. */
    private static final class State {
        private final Minecraft mc;
        private List<String> rows = List.of();
        /** The layers to cut through, with the most blocks, from the top down. */
        private List<Integer> cuts = List.of();
        private final Map<String, List<Double>> slowest = new LinkedHashMap<>();
        private final Map<String, List<Double>> before = new LinkedHashMap<>();
        private int keepLimit;
        private boolean keepVsync;
        //? if >=1.21.2 {
        /*private InactivityFpsLimit keepInactive;
        *///?}

        State(Minecraft mc) {
            this.mc = mc;
        }

        void unlimitFrames() {
            keepLimit = mc.options.framerateLimit().get();
            keepVsync = mc.options.enableVsync().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            //? if >=1.21.2 {
            /*// Otherwise a game with no real input for a minute is held to 30 frames a second.
            keepInactive = mc.options.inactivityFpsLimit().get();
            mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            *///?}
        }

        void restoreFrames() {
            mc.options.framerateLimit().set(keepLimit);
            mc.options.enableVsync().set(keepVsync);
            //? if >=1.21.2 {
            /*mc.options.inactivityFpsLimit().set(keepInactive);
            *///?}
        }

        /** The two rows on screen about the most blocks that are still tinted. */
        List<String> pickRows() {
            JesScreen s = browser(mc);
            List<String> picked = new ArrayList<>();
            for (Map.Entry<Block, Integer> e : Exports.blockCounts(s.result().snapshot())) {
                String key = "block:" + BuiltInRegistries.BLOCK.getKey(e.getKey());
                if (e.getValue() <= Highlight.MAX_BLOCKS && s.highlightRow(key).isPresent()) {
                    picked.add(key);
                    JustEnoughStructures.LOGGER.info("STALLS row {} has {} blocks", key, e.getValue());
                    if (picked.size() == 2) {
                        break;
                    }
                }
            }
            rows = picked;
            cuts = densestLayers(s.result().snapshot());
            return picked;
        }

        /** The {@link #CUTS} layers with the most blocks, below the top one, from the top down. */
        private static List<Integer> densestLayers(StructureSnapshot snapshot) {
            int[] perLayer = new int[snapshot.size().getY()];
            for (int i = 0; i < snapshot.blockCount(); i++) {
                perLayer[StructureSnapshot.unpackY(snapshot.packedPosition(i))]++;
            }
            List<Integer> layers = new ArrayList<>();
            for (int y = 0; y < perLayer.length - 1; y++) {
                layers.add(y);
            }
            layers.sort((a, b) -> Integer.compare(perLayer[b], perLayer[a]));
            List<Integer> picked = new ArrayList<>(layers.subList(0, Math.min(CUTS, layers.size())));
            picked.sort((a, b) -> Integer.compare(b, a));
            for (int y : picked) {
                JustEnoughStructures.LOGGER.info("STALLS layer {} has {} blocks", y, perLayer[y]);
            }
            return picked;
        }

        /** Hovers each row, cuts through each of the densest layers, then drags the slider, timing frames each time. */
        void round(Director d, String label) {
            for (int i = 0; i < 2; i++) {
                int which = i;
                Try hover = new Try(label, "hover");
                d.then(pause(10))
                        .then(run(() -> hover.begin(row(which).substring("block:".length()), () -> browser(mc).highlightReady(row(which)))))
                        .then(pause(BEFORE))
                        // Straight onto the row, in the same tick as the timing starts, so no row on
                        // the way is hovered.
                        .then((director, frame) -> {
                            hover.act();
                            int[] at = browser(mc).highlightRow(row(which)).orElseThrow();
                            director.moveMouse(at[0], at[1]);
                            return true;
                        })
                        .then(pause(AFTER))
                        .then(run(hover::end))
                        .then(moveTo(() -> browser(mc).searchBox(), 1));
            }
            for (int i = 0; i < CUTS; i++) {
                int which = i;
                Try slider = new Try(label, "slider");
                d.then(pause(4))
                        .then(run(() -> slider.begin("layer_" + cut(which), () -> browser(mc).idle())))
                        .then(pause(BEFORE))
                        .then(run(() -> {
                            slider.act();
                            // Showing the layers up to this one, so it's the one cut through.
                            browser(mc).setLayers(cut(which) + 1);
                        }))
                        .then(pause(AFTER))
                        .then(run(slider::end));
            }
            Try drag = new Try(label, "drag");
            d.then(run(() -> browser(mc).setLayers(10_000)))
                    .then(pause(10))
                    .then(moveTo(() -> browser(mc).sliderAt(1f), 4))
                    .then(run(() -> drag.begin("to_30%", () -> false)))
                    .then(pause(BEFORE))
                    .then(run(drag::act))
                    .then(dragTo(() -> browser(mc).sliderAt(0.3f), 30, InputConstants.MOUSE_BUTTON_LEFT))
                    .then(run(drag::end))
                    .then(run(() -> browser(mc).setLayers(10_000)))
                    .then(moveTo(() -> browser(mc).searchBox(), 4))
                    .then(pause(10));
        }

        /** The first or second row picked, or the first again if there's only one. */
        String row(int which) {
            return rows.get(Math.min(which, rows.size() - 1));
        }

        /** The layer of the {@code which}th cut, or the last one if the structure has fewer layers. */
        int cut(int which) {
            return cuts.get(Math.min(which, cuts.size() - 1));
        }

        void summarise() {
            for (Map.Entry<String, List<Double>> e : slowest.entrySet()) {
                if (e.getKey().contains("warm-up")) {
                    continue;
                }
                double[] v = sorted(e.getValue());
                double usual = median(sorted(before.get(e.getKey())));
                String verdict = e.getKey().contains(" drag") ? "not judged" : median(v) - usual > HELD_UP_MS ? "LOOK" : "ok";
                JustEnoughStructures.LOGGER.info("STALLS summary {}: slowest frame until shown, median {} ms, range {} to {} ms, {} tries, frames before {} ms, {}",
                        e.getKey(), ms(median(v)), ms(v[0]), ms(v[v.length - 1]), v.length, ms(usual), verdict);
            }
        }

        /** One try: frames noted from {@link #begin}, the change made at {@link #act}, logged at {@link #end}. */
        private final class Try {
            private final String label;
            private final String kind;
            private String what;
            private BooleanSupplier shown;
            private int mark;
            private long actedAt;

            Try(String label, String kind) {
                this.label = label;
                this.kind = kind;
            }

            /** Starts noting frames for a try at {@code what}, which shows once {@code shown} holds. */
            void begin(String what, BooleanSupplier shown) {
                FrameClock.start();
                this.what = what;
                this.shown = shown;
            }

            void act() {
                mark = FrameClock.count();
                actedAt = System.nanoTime();
                FrameClock.watch(shown);
            }

            void end() {
                int count = FrameClock.count();
                FrameClock.stop();
                if (mark < 2 || count <= mark + 1) {
                    JustEnoughStructures.LOGGER.warn("STALLS {} {} {}: too few frames to judge ({} before, {} after)", label, kind, what, mark, count - mark);
                    return;
                }
                double[] before = new double[mark - 1];
                for (int i = 1; i < mark; i++) {
                    before[i - 1] = (FrameClock.at(i) - FrameClock.at(i - 1)) / 1e6;
                }
                Arrays.sort(before);
                int met = FrameClock.metAt();
                // The slowest frame until the change showed, and a frame after, where the work for it
                // was done; the slowest over the whole second after also has the odd unrelated one.
                int madeBy = met < 0 ? count : Math.min(count, met + 2);
                double making = 0;
                double after = 0;
                for (int i = mark; i < count; i++) {
                    double frame = (FrameClock.at(i) - FrameClock.at(i - 1)) / 1e6;
                    after = Math.max(after, frame);
                    if (i < madeBy) {
                        making = Math.max(making, frame);
                    }
                }
                String shownAfter = met < 0 ? "never" : ms((FrameClock.at(met) - actedAt) / 1e6) + " ms (" + (met - mark + 1) + " frames)";
                JustEnoughStructures.LOGGER.info("STALLS {} {} {}: frames before {} ms, slowest until shown {} ms, slowest after {} ms, {} frames after, shown after {}",
                        label, kind, what, ms(median(before)), ms(making), ms(after), count - mark, shownAfter);
                String key = label + " " + kind + (kind.equals("hover") ? " " + what : "");
                slowest.computeIfAbsent(key, k -> new ArrayList<>()).add(making);
                State.this.before.computeIfAbsent(key, k -> new ArrayList<>()).add(median(before));
            }
        }
    }

    private static double[] sorted(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }

    private static double median(double[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    private static String ms(double ms) {
        return String.format(Locale.ROOT, "%.1f", ms);
    }
}
