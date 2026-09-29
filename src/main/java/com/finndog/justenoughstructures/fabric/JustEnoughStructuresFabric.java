package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import net.fabricmc.api.ModInitializer;

public final class JustEnoughStructuresFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JustEnoughStructures.init();
        FabricNetworking.registerServer();
    }
}
