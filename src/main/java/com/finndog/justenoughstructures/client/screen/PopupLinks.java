package com.finndog.justenoughstructures.client.screen;

import java.util.EnumMap;
import java.util.Map;

/** Where a popup's links and tabs were last drawn, to find the one under the mouse. */
final class PopupLinks<A extends Enum<A>> {
    private final Map<A, int[]> drawn;

    PopupLinks(Class<A> actions) {
        drawn = new EnumMap<>(actions);
    }

    void clear() {
        drawn.clear();
    }

    void put(A action, int x, int y, int w, int h) {
        drawn.put(action, new int[]{x, y, w, h});
    }

    /** The link at a point, or null. */
    A at(double mouseX, double mouseY) {
        for (Map.Entry<A, int[]> e : drawn.entrySet()) {
            int[] r = e.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The middle of a link, or null if it isn't shown. For the screenshot harness. */
    int[] centre(A action) {
        int[] r = drawn.get(action);
        return r == null ? null : new int[]{r[0] + r[2] / 2, r[1] + r[3] / 2};
    }
}
