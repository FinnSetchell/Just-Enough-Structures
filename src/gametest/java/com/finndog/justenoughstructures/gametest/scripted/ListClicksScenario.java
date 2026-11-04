package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.dragBy;
import static com.finndog.justenoughstructures.gametest.scripted.Director.dragTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.moveTo;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Director.wheel;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Clicks on the structure list's rows land on the row drawn under the mouse: the top and bottom pixel
 * of rows near each end of the list, and of the mod's header, at the top of the list, one turn of the
 * wheel down, and part of the way through a pixel after dragging its bar. Between rows there's no pixel
 * that belongs to the row above or below. A click that folds a header is undone before going on.
 */
final class ListClicksScenario {
    private ListClicksScenario() {
    }

    static Director build(Minecraft mc) {
        JesScreen.startOn(Ids.parse("igloo"));
        List<String> wrong = new ArrayList<>();
        Director d = new Director(mc);
        d.then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                .then(until(() -> !browser(mc).listRowsDrawn().isEmpty(), 100))
                .then(moveTo(() -> middle(mc), 10))
                .then(wheel(20))
                .then(pause(5))
                .then(run(() -> expect(browser(mc).scrolled(false) == 0, "The list didn't go back to its top: " + browser(mc).scrolled(false))))
                .then(new Probes(mc, "at the top", true, wrong))
                .then(moveTo(() -> middle(mc), 10))
                .then(wheel(-1))
                .then(pause(5))
                .then(run(() -> expect(browser(mc).scrolled(false) > 0, "One turn of the wheel didn't scroll the list")))
                .then(new Probes(mc, "one turn down", false, wrong))
                .then(moveTo(() -> orZero(browser(mc).scrollBar(false, 0.02f)), 10))
                .then(dragTo(() -> orZero(browser(mc).scrollBar(false, 0.37f)), 20, InputConstants.MOUSE_BUTTON_LEFT))
                .then(pause(5))
                .then(partWayThroughAPixel(mc))
                .then(new Probes(mc, "dragged by its bar", false, wrong))
                .then(run(() -> {
                    JustEnoughStructures.LOGGER.info("LIST_CLICKS {} clicks landed on another row than the one drawn under them", wrong.size());
                    expect(wrong.isEmpty(), wrong.size() + " clicks on the structure list landed on another row: " + String.join("; ", wrong));
                }));
        return d;
    }

    /**
     * Leaves the list scrolled part of the way through a pixel, nudging its bar a pixel at a time, so
     * a click near a row's edge shows whether it's measured from where the row is drawn.
     */
    private static Director.Action partWayThroughAPixel(Minecraft mc) {
        return new Director.Action() {
            int tries;
            Director.Action nudge;

            @Override
            public boolean step(Director d, int frame) {
                if (nudge != null) {
                    if (nudge.step(d, frame)) {
                        nudge = null;
                    }
                    return false;
                }
                double offset = browser(mc).scrolled(false);
                double part = offset - Math.floor(offset);
                if (part >= 0.15 && part <= 0.85) {
                    JustEnoughStructures.LOGGER.info("LIST_CLICKS dragged by its bar to {}", offset);
                    return true;
                }
                expect(tries++ < 6, "Dragging the bar always left the list on a whole pixel, at " + offset);
                nudge = new Director.Action() {
                    Director.Action press;
                    int start = -1;

                    @Override
                    public boolean step(Director d, int frame) {
                        if (press == null) {
                            press = dragBy(0, 1, 2);
                            start = frame;
                        }
                        return press.step(d, frame - start);
                    }
                };
                return false;
            }
        };
    }

    /**
     * Clicks the top and bottom pixel of the first three and last two structure rows wholly in view,
     * and with {@code headers} of the headers wholly in view, and notes each one that lands elsewhere.
     * Scrolled part of the way through a pixel, it also clicks just above each row's bottom edge.
     */
    private static final class Probes implements Director.Action {
        private final Minecraft mc;
        private final String where;
        private final boolean headers;
        private final List<String> wrong;
        private final List<double[]> points = new ArrayList<>();
        private final List<String> expected = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private Map<String, int[]> before;
        private double offset;
        private boolean built;
        private int at;
        private int since;
        /** A header a click folded, to open again before the next click. */
        private String folded;

        Probes(Minecraft mc, String where, boolean headers, List<String> wrong) {
            this.mc = mc;
            this.where = where;
            this.headers = headers;
            this.wrong = wrong;
        }

        @Override
        public boolean step(Director d, int frame) {
            JesScreen s = browser(mc);
            if (!built) {
                build(s);
                built = true;
                since = frame + 1;
                return false;
            }
            int t = frame - since;
            if (folded != null) {
                // Open the header again by its middle, then check every row is back where it was.
                int[] r = s.listRowsDrawn().get(folded);
                if (t == 0) {
                    expect(r != null, "The header " + folded + " went out of view once folded");
                    d.moveMouse((r[0] + r[2]) / 2.0, (r[1] + r[3]) / 2.0);
                } else if (t == 2) {
                    d.press(InputConstants.MOUSE_BUTTON_LEFT);
                } else if (t == 4) {
                    d.release(InputConstants.MOUSE_BUTTON_LEFT);
                } else if (t >= 7) {
                    for (String key : expected) {
                        int[] now = s.listRowsDrawn().get(key);
                        expect(now != null && now[1] == before.get(key)[1], "Opening " + folded + " again left " + key + " somewhere else");
                    }
                    folded = null;
                    since = frame + 1;
                }
                return false;
            }
            if (at >= points.size()) {
                return true;
            }
            double[] p = points.get(at);
            if (t == 0) {
                d.moveMouse(p[0], p[1]);
            } else if (t == 2) {
                d.press(InputConstants.MOUSE_BUTTON_LEFT);
            } else if (t == 4) {
                d.release(InputConstants.MOUSE_BUTTON_LEFT);
            } else if (t >= 5) {
                String landed = s.listClicked();
                String want = expected.get(at);
                boolean right = want.equals(landed);
                JustEnoughStructures.LOGGER.info("LIST_CLICKS {}: {} of {} at y {} landed on {}{}", where, labels.get(at), want, p[1], landed,
                        right ? "" : " WRONG");
                if (!right) {
                    wrong.add(where + ", " + labels.get(at) + " of " + want + " (y " + p[1] + ") landed on " + landed);
                }
                if (landed != null && landed.startsWith("header:")) {
                    folded = landed;
                }
                at++;
                since = frame + 1;
                if (s.scrolled(false) != offset) {
                    // A click that picked a row partly out of view brings it in, and the points left are stale.
                    wrong.add(where + ": that click scrolled the list from " + offset + " to " + s.scrolled(false) + ", so the rest of this stage was left out");
                    at = points.size();
                }
            }
            return false;
        }

        private void build(JesScreen s) {
            int[] bounds = s.listBounds();
            offset = s.scrolled(false);
            double part = offset - Math.floor(offset);
            before = s.listRowsDrawn();
            // Wholly in view, so picking one doesn't scroll the list: a row drawn at the very top is partly
            // above the view when the list is scrolled part of the way through a pixel.
            int first = bounds[0] + (part > 0 ? 1 : 0);
            List<Map.Entry<String, int[]>> whole = before.entrySet().stream()
                    .filter(e -> e.getValue()[1] >= first && e.getValue()[3] <= bounds[1] - 2)
                    .sorted(Comparator.comparingInt(e -> e.getValue()[1]))
                    .toList();
            List<Map.Entry<String, int[]>> rows = whole.stream().filter(e -> isStructure(e.getKey())).toList();
            List<Map.Entry<String, int[]>> picked = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (i < 3 || i >= rows.size() - 2) {
                    picked.add(rows.get(i));
                }
            }
            expect(picked.size() >= 4, where + ": only " + picked.size() + " structure rows were wholly in view");
            JustEnoughStructures.LOGGER.info("LIST_CLICKS {}: scrolled {}, list from y {} to {}, {} rows wholly in view", where, offset, bounds[0], bounds[1],
                    whole.size());
            for (Map.Entry<String, int[]> row : picked) {
                add(row, "top pixel", row.getValue()[1]);
                add(row, "bottom pixel", row.getValue()[3] - 1);
                if (part >= 0.15) {
                    add(row, "just above the bottom edge", row.getValue()[3] - part / 2);
                }
            }
            if (headers) {
                List<Map.Entry<String, int[]>> heads = whole.stream().filter(e -> e.getKey().startsWith("header:")).toList();
                expect(!heads.isEmpty(), where + ": no header was wholly in view");
                for (Map.Entry<String, int[]> head : heads) {
                    add(head, "top pixel", head.getValue()[1]);
                    add(head, "bottom pixel", head.getValue()[3] - 1);
                }
            }
        }

        private void add(Map.Entry<String, int[]> row, String label, double y) {
            int[] r = row.getValue();
            points.add(new double[]{(r[0] + r[2]) / 2.0, y});
            expected.add(row.getKey());
            labels.add(label);
        }

        /** Structures' ids have a namespace and a colon; the hooks' other rows start with "header:" or "item:". */
        private static boolean isStructure(String key) {
            return !key.startsWith("header:") && !key.startsWith("item:");
        }
    }

    private static int[] middle(Minecraft mc) {
        int[] bounds = browser(mc).listBounds();
        int left = browser(mc).listRowsDrawn().values().stream().mapToInt(r -> r[0]).min().orElse(0);
        return new int[]{left + 40, (bounds[0] + bounds[1]) / 2};
    }

    private static int[] orZero(int[] point) {
        return point == null ? new int[]{0, 0} : point;
    }

    private static void expect(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
