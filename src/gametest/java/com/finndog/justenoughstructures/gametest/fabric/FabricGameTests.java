package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class FabricGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded(JustEnoughStructures.MOD_ID), "mod not loaded");
        helper.succeed();
    }
}
