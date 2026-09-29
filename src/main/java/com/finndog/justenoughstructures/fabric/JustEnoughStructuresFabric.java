package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class JustEnoughStructuresFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JustEnoughStructures.setModNames(namespace -> FabricLoader.getInstance().getModContainer(namespace)
                .map(mod -> mod.getMetadata().getName())
                .orElse(namespace));
        JustEnoughStructures.init();
        FabricNetworking.registerServer();
    }
}
