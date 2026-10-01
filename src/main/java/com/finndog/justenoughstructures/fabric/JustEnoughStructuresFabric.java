package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.compat.explorerscompass.ExplorersCompassSearch;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import net.fabricmc.api.ModInitializer;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class JustEnoughStructuresFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JustEnoughStructures.setModNames(namespace -> FabricLoader.getInstance().getModContainer(namespace)
                .map(mod -> mod.getMetadata().getName())
                .orElse(namespace));
        JustEnoughStructures.setConfigDir(FabricLoader.getInstance().getConfigDir());
        JustEnoughStructures.setGameDir(FabricLoader.getInstance().getGameDir());
        JustEnoughStructures.setModVersions(() -> FabricLoader.getInstance().getAllMods().stream()
                .collect(Collectors.toMap(mod -> mod.getMetadata().getId(), mod -> mod.getMetadata().getVersion().getFriendlyString(), (a, b) -> a)));
        JustEnoughStructures.init();
        FabricNetworking.registerServer();
        if (FabricLoader.getInstance().isModLoaded("explorerscompass")) {
            ExplorersCompassSearch.install();
        }
        FabricPermissions.install();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PackToolsAccess.joined(handler.getPlayer()));
    }
}
