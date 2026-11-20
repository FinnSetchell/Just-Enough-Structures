package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.client.ConfigScreens;
import com.terraformersmc.modmenu.ModMenu;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;
//? if <1.21 {
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
//?}

/** Other mods' settings screens, from Mod Menu, which keeps every mod's in one list. Only loaded with Mod Menu installed. */
final class FabricConfigScreens implements ConfigScreens {
    //? if <1.21 {
    private Set<String> withScreens;
    //?}

    static void register() {
        ConfigScreens.set(new FabricConfigScreens());
    }

    @Override
    public boolean has(String modId) {
        //? if >=1.21 {
        /*return ModMenu.hasConfigScreen(modId);
        *///?} else {
        return withScreens().contains(modId);
        //?}
    }

    @Override
    public Screen open(String modId, Screen parent) {
        return ModMenu.getConfigScreen(modId, parent);
    }

    /** Looks through every mod's own files, nested jars included, in the loader's order. */
    @Override
    public String modWithFile(String path) {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            try {
                if (mod.findPath(path).isPresent()) {
                    return mod.getMetadata().getId();
                }
            } catch (Throwable t) {
                JesLog.debug("Couldn't look for {} in {}", path, mod.getMetadata().getId(), t);
            }
        }
        return null;
    }

    //? if <1.21 {
    /**
     * Mod Menu 7 can only say whether a mod has a screen by building it, so this reads its entrypoints
     * the way it does at start-up: one screen per mod that has one, plus those mods provide for others.
     */
    private Set<String> withScreens() {
        if (withScreens == null) {
            Set<String> ids = new HashSet<>();
            try {
                for (EntrypointContainer<ModMenuApi> container : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
                    ids.add(container.getProvider().getMetadata().getId());
                    try {
                        ids.addAll(container.getEntrypoint().getProvidedConfigScreenFactories().keySet());
                    } catch (Throwable t) {
                        JesLog.debug("Couldn't read the screens {} provides", container.getProvider().getMetadata().getId(), t);
                    }
                }
            } catch (Throwable t) {
                JesLog.warnOnce("modmenu_entrypoints", "Couldn't read Mod Menu's entrypoints", t);
            }
            withScreens = ids;
        }
        return withScreens;
    }
    //?}
}
