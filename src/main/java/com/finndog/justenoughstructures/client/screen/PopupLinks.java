package com.finndog.justenoughstructures.client.screen;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.narration.NarrationSupplier;

/** Where a popup's links and tabs were last drawn, to find the one under the mouse, and what the narrator reads for each. */
final class PopupLinks<A extends Enum<A>> {
    private record Link(int x, int y, int w, int h, Narration narration) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private final Map<A, Link> drawn;

    PopupLinks(Class<A> actions) {
        drawn = new EnumMap<>(actions);
    }

    void clear() {
        drawn.clear();
    }

    void put(A action, int x, int y, int w, int h, Narration narration) {
        drawn.put(action, new Link(x, y, w, h, narration));
    }

    /** The link at a point, or null. */
    A at(double mouseX, double mouseY) {
        for (Map.Entry<A, Link> e : drawn.entrySet()) {
            if (e.getValue().contains(mouseX, mouseY)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** What the narrator reads for the link at a point, or null. */
    NarrationSupplier narrationAt(double mouseX, double mouseY) {
        for (Link link : drawn.values()) {
            if (link.contains(mouseX, mouseY)) {
                return link.narration();
            }
        }
        return null;
    }

    /** The middle of a link, or null if it isn't shown. Test hook for the scripted scenarios. */
    int[] centre(A action) {
        Link link = drawn.get(action);
        return link == null ? null : new int[]{link.x() + link.w() / 2, link.y() + link.h() / 2};
    }
}
