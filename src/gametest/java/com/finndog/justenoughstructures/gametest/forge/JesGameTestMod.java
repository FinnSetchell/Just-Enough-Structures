package com.finndog.justenoughstructures.gametest.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
//? if >=26.1 {
/*import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
*///?}

/**
 * The test mod on Forge, which needs a class of its own. Its tests are found by their annotation,
 * by Forge itself before 26.1 and from then by {@link ForgeGameTests}.
 */
@Mod("justenoughstructures_gametest")
public final class JesGameTestMod {
    //? if >=26.1 {
    /*public JesGameTestMod(FMLJavaModLoadingContext context) {
        ForgeGameTests.register(context.getModBusGroup());
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeAutoshot.install();
        }
    }
    *///?} else {
    public JesGameTestMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeAutoshot.install();
        }
    }
    //?}
}
