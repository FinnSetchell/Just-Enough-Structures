package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class JesClient {
    public static final KeyMapping OPEN = new KeyMapping("key.justenoughstructures.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.justenoughstructures");

    private JesClient() {
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
