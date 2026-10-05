package com.finndog.justenoughstructures.gametest.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
//? if >=26.1 {
/*import net.neoforged.bus.api.IEventBus;
*///?}

/**
 * The test mod on NeoForge, which needs a class of its own. Its tests are found by their annotation,
 * by NeoForge itself before 26.1 and from then by {@link NeoForgeGameTests}.
 */
@Mod("justenoughstructures_gametest")
public final class JesGameTestMod {
    //? if >=26.1 {
    /*public JesGameTestMod(IEventBus modBus) {
        NeoForgeGameTests.register(modBus);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeAutoshot.install();
        }
    }
    *///?} else {
    public JesGameTestMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeAutoshot.install();
        }
    }
    //?}
}
