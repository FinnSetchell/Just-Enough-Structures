package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.gametest.CaptureTests;
import com.finndog.justenoughstructures.gametest.ServiceTests;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

public final class FabricGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded(JustEnoughStructures.MOD_ID), "mod not loaded");
        helper.succeed();
    }

    @GameTestGenerator
    public Collection<TestFunction> captureEveryVanillaStructure() {
        return CaptureTests.VANILLA.stream()
                .map(name -> new TestFunction("capture", "capture_" + name, EMPTY_STRUCTURE, 200, 0L, true,
                        helper -> CaptureTests.capturesVanillaStructure(helper, name)))
                .toList();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lootSetByStructureCodeIsCaptured(GameTestHelper helper) {
        CaptureTests.lootSetByStructureCodeIsCaptured(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sameSeedGivesSameSnapshot(GameTestHelper helper) {
        CaptureTests.sameSeedGivesSameSnapshot(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void captureLeavesTheWorldAlone(GameTestHelper helper) {
        CaptureTests.captureLeavesTheWorldAlone(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void parallelCapturesMatchSerialOnes(GameTestHelper helper) {
        CaptureTests.parallelCapturesMatchSerialOnes(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unknownStructureFailsCleanly(GameTestHelper helper) {
        CaptureTests.unknownStructureFailsCleanly(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void catalogListsEveryVanillaStructure(GameTestHelper helper) {
        ServiceTests.catalogListsEveryVanillaStructure(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void snapshotSurvivesTheWire(GameTestHelper helper) {
        ServiceTests.snapshotSurvivesTheWire(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void failedCaptureSurvivesTheWire(GameTestHelper helper) {
        ServiceTests.failedCaptureSurvivesTheWire(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lootRollsUseTheRealTable(GameTestHelper helper) {
        ServiceTests.lootRollsUseTheRealTable(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void treasureMapRollsAreQuick(GameTestHelper helper) {
        ServiceTests.treasureMapRollsAreQuick(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lootIndexFindsItemsInStructures(GameTestHelper helper) {
        ServiceTests.lootIndexFindsItemsInStructures(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void impossibleLocateIsQuick(GameTestHelper helper) {
        ServiceTests.impossibleLocateIsQuick(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unknownLootTableIsEmpty(GameTestHelper helper) {
        ServiceTests.unknownLootTableIsEmpty(helper);
    }
}
