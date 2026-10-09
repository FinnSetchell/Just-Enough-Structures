package com.finndog.justenoughstructures.gametest.scripted;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.finndog.justenoughstructures.client.screen.LootEditorScreen;
import com.finndog.justenoughstructures.client.screen.PackToolsScreen;
import com.finndog.justenoughstructures.client.screen.TablePickerScreen;
import net.minecraft.client.Minecraft;

/** The screens scenarios step through, each null while another is open, and points on them. */
final class Screens {
    private Screens() {
    }

    static JesScreen browser(Minecraft mc) {
        return mc.screen instanceof JesScreen s ? s : null;
    }

    static PackToolsScreen tools(Minecraft mc) {
        return mc.screen instanceof PackToolsScreen s ? s : null;
    }

    static LootEditorScreen editor(Minecraft mc) {
        return mc.screen instanceof LootEditorScreen s ? s : null;
    }

    static TablePickerScreen tablePicker(Minecraft mc) {
        return mc.screen instanceof TablePickerScreen s ? s : null;
    }

    /** Whether the browser is showing a structure, with nothing left to load. */
    static boolean ready(Minecraft mc) {
        JesScreen b = browser(mc);
        return b != null && b.idle() && b.result() != null;
    }

    /** A point, or the top left corner when it isn't on screen. */
    static int[] orZero(int[] at) {
        return at == null ? new int[]{0, 0} : at;
    }

    static int[] offset(int[] p, int dx, int dy) {
        return p == null ? null : new int[]{p[0] + dx, p[1] + dy};
    }
}
