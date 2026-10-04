package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class JesClient {
    public static final KeyMapping OPEN = new KeyMapping("key.justenoughstructures.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.justenoughstructures");

    private JesClient() {
    }

    /** Opens the browser for /jes open, on {@code structure} if it's given, over whatever screen is showing. */
    public static void openBrowser(Minecraft minecraft, ResourceLocation structure) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.screen instanceof JesScreen browser) {
            if (structure != null) {
                browser.select(structure);
            }
            return;
        }
        if (structure != null) {
            JesScreen.startOn(structure);
        }
        minecraft.setScreen(new JesScreen());
    }

    /** Called every client tick by the loader. */
    public static void tick(Minecraft minecraft) {
        // Keeps asking for the loot index while it's being built, even with the browser shut, as
        // the JEI plugin waits on it too.
        ClientRequests.tick();
        while (OPEN.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new JesScreen());
            }
        }
    }
}
