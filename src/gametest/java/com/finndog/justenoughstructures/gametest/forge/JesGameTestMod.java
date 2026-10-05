package com.finndog.justenoughstructures.gametest.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** The test mod on Forge, which needs a class of its own. Its tests are found by their annotation. */
@Mod("justenoughstructures_gametest")
public final class JesGameTestMod {
    public JesGameTestMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeAutoshot.install();
        }
    }
}
