package com.finndog.justenoughstructures.gametest.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** The test mod on NeoForge, which needs a class of its own. Its tests are found by their annotation. */
@Mod("justenoughstructures_gametest")
public final class JesGameTestMod {
    public JesGameTestMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgeAutoshot.install();
        }
    }
}
