package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.compat.cloth.JesConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Mod Menu's Config button, which opens the settings screen when Cloth Config is installed to draw it. */
public final class JesModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ModMenuApi.super.getModConfigScreenFactory();
        }
        return JesConfigScreen::create;
    }
}
