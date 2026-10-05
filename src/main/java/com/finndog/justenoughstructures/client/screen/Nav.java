package com.finndog.justenoughstructures.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Back and Forward across JES's screens, as in a web browser. Each move from one place to another,
 * like picking a structure, switching tabs, opening a popup or opening another screen, remembers
 * where it was made from. Closing a popup or a screen isn't a move, and any new move clears Forward.
 * A place is the browser as it was, plus whatever screens were open over it.
 */
public final class Nav {
    private static final int MAX = 60;

    /** Where each move was made from, newest last. */
    private static final List<Place> BACK = new ArrayList<>();
    /** Where Back was pressed from, newest last. */
    private static final List<Place> FORWARD = new ArrayList<>();

    private Nav() {
    }

    /** A JES screen that history can come back to. */
    interface Page {
        /** Where this screen is now, or null when it isn't anywhere yet, like the browser before its list arrives. */
        Layer layer();

        /** The screen this one was opened over. The browser has none. */
        Screen below();

        /** Goes back to {@code layer}, which is a state of this same screen. */
        default void restore(Layer layer) {
        }

        /** True when leaving would lose work, like an editor with changes not saved. */
        default boolean unsaved() {
            return false;
        }

        /** Lets the unsaved work go, once the player has said to leave without saving. */
        default void discard() {
        }
    }

    /** One screen of a place: the browser at the bottom, then any screen opened over it. */
    interface Layer {
        /** What tells this place apart from others. Two places with equal keys are the same place. */
        Object key();

        /** What the place is called on the bar, like "Igloo, Loot". */
        Component label();

        /** True when a screen showing {@code other} can be kept and taken to this layer, rather than opened afresh. */
        default boolean sameScreen(Layer other) {
            return key().equals(other.key());
        }

        /** Opens a screen for this layer over {@code below}. */
        Screen open(Screen below);
    }

    /** The browser's layer and the screens over it, bottom first. */
    record Place(List<Layer> layers) {
        Component label() {
            return layers.get(layers.size() - 1).label();
        }

        /**
         * Over another screen, the browser behind it doesn't change which place this is, as it can't
         * be seen.
         */
        List<Object> key() {
            List<Object> key = new ArrayList<>();
            if (layers.size() == 1) {
                key.add(layers.get(0).key());
            } else {
                for (int i = 1; i < layers.size(); i++) {
                    key.add(layers.get(i).key());
                }
            }
            return key;
        }

        boolean sameAs(Place other) {
            return other != null && key().equals(other.key());
        }
    }

    /** The JES screens open now, bottom first, and the place they show. */
    private record Here(List<Screen> screens, Place place) {
        JesScreen browser() {
            return (JesScreen) screens.get(0);
        }
    }

    /** Where the player is, if on a JES screen with the browser under it. */
    private static Here here() {
        return here(Minecraft.getInstance().screen);
    }

    private static Here here(Screen top) {
        List<Screen> screens = new ArrayList<>();
        List<Layer> layers = new ArrayList<>();
        Screen s = top;
        while (s instanceof Page page) {
            Layer layer = page.layer();
            if (layer == null) {
                return null;
            }
            screens.add(0, s);
            layers.add(0, layer);
            if (s instanceof JesScreen) {
                return new Here(screens, new Place(List.copyOf(layers)));
            }
            s = page.below();
        }
        return null;
    }

    /** Remembers where the player is, as a move away from it is about to be made. */
    static void remember() {
        Here here = here();
        if (here == null) {
            return;
        }
        FORWARD.clear();
        if (!BACK.isEmpty() && BACK.get(BACK.size() - 1).sameAs(here.place())) {
            return;
        }
        BACK.add(here.place());
        while (BACK.size() > MAX) {
            BACK.remove(0);
        }
    }

    /** Where Back goes from {@code screen}, or null. */
    static Place backTarget(Screen screen) {
        Here here = here(screen);
        int i = here == null ? -1 : target(BACK, here);
        return i < 0 ? null : BACK.get(i);
    }

    /** Where Forward goes from {@code screen}, or null. */
    static Place forwardTarget(Screen screen) {
        Here here = here(screen);
        int i = here == null ? -1 : target(FORWARD, here);
        return i < 0 ? null : FORWARD.get(i);
    }

    /** The newest place in {@code list} that's somewhere else and can still be gone to. */
    private static int target(List<Place> list, Here here) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Place place = list.get(i);
            if (!place.sameAs(here.place()) && here.browser().canRestore(place.layers().get(0))) {
                return i;
            }
        }
        return -1;
    }

    static void back() {
        Here here = here();
        int i = here == null ? -1 : target(BACK, here);
        if (i < 0) {
            return;
        }
        Place to = BACK.get(i);
        leave(here, to, () -> {
            FORWARD.add(here.place());
            BACK.subList(i, BACK.size()).clear();
            go(here, to);
        });
    }

    static void forward() {
        Here here = here();
        int i = here == null ? -1 : target(FORWARD, here);
        if (i < 0) {
            return;
        }
        Place to = FORWARD.get(i);
        leave(here, to, () -> {
            if (BACK.isEmpty() || !BACK.get(BACK.size() - 1).sameAs(here.place())) {
                BACK.add(here.place());
            }
            FORWARD.subList(i, FORWARD.size()).clear();
            go(here, to);
        });
    }

    /** How many of the open screens, from the browser up, stay open on the way to {@code to}. */
    private static int kept(Here here, Place to) {
        int kept = 1;
        while (kept < here.screens().size() && kept < to.layers().size()
                && ((Page) here.screens().get(kept)).layer().sameScreen(to.layers().get(kept))) {
            kept++;
        }
        return kept;
    }

    /** Asks first when a screen that would close has work not saved, and goes on if the player says to. */
    private static void leave(Here here, Place to, Runnable then) {
        Minecraft mc = Minecraft.getInstance();
        for (int i = here.screens().size() - 1; i >= kept(here, to); i--) {
            if (here.screens().get(i) instanceof Page page && page.unsaved()) {
                Screen top = mc.screen;
                mc.setScreen(new ConfirmScreen(yes -> {
                    if (yes) {
                        page.discard();
                        then.run();
                    } else {
                        mc.setScreen(top);
                    }
                }, Component.translatable("screen.justenoughstructures.editor.unsaved.title"), Component.empty()));
                return;
            }
        }
        then.run();
    }

    /** Takes the screens to {@code to}: keeping those it still has, opening the rest and setting the browser behind them. */
    private static void go(Here here, Place to) {
        int kept = kept(here, to);
        Screen top = here.browser();
        for (int i = 1; i < to.layers().size(); i++) {
            Layer layer = to.layers().get(i);
            if (i < kept) {
                top = here.screens().get(i);
                ((Page) top).restore(layer);
            } else {
                top = layer.open(top);
            }
        }
        here.browser().restorePlace(to.layers().get(0), top == here.browser());
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != top) {
            mc.setScreen(top);
        }
    }

    /** Forgets every place, as when leaving a world, whose structures the next one may not have. */
    public static void forget() {
        BACK.clear();
        FORWARD.clear();
    }

    /** For the screenshot harness. */
    public static boolean canGoBack() {
        return backTarget(Minecraft.getInstance().screen) != null;
    }

    public static boolean canGoForward() {
        return forwardTarget(Minecraft.getInstance().screen) != null;
    }
}
