package com.finndog.justenoughstructures.gametest.fabric;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.gametest.CaptureTests;
import com.finndog.justenoughstructures.gametest.CompassTests;
import com.finndog.justenoughstructures.gametest.ContainerTests;
import com.finndog.justenoughstructures.gametest.OverrideTests;
import com.finndog.justenoughstructures.gametest.PerfTests;
import com.finndog.justenoughstructures.gametest.ServiceTests;
import com.finndog.justenoughstructures.gametest.SettingsTests;
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
    public void foundInRecipesComeFromTheIndex(GameTestHelper helper) {
        ServiceTests.foundInRecipesComeFromTheIndex(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void savedLootIndexReadsBack(GameTestHelper helper) {
        ServiceTests.savedLootIndexReadsBack(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lootIndexFingerprintIsStable(GameTestHelper helper) {
        ServiceTests.lootIndexFingerprintIsStable(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hiddenStructuresLeaveTheLootIndex(GameTestHelper helper) {
        ServiceTests.hiddenStructuresLeaveTheLootIndex(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void compassOnlySearchesWhereItCould(GameTestHelper helper) {
        CompassTests.compassOnlySearchesWhereItCould(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void editsSaveAndReadBack(GameTestHelper helper) {
        OverrideTests.editsSaveAndReadBack(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void brokenEditsAreRefused(GameTestHelper helper) {
        OverrideTests.brokenEditsAreRefused(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void editsNoticeTheirOriginalChanging(GameTestHelper helper) {
        OverrideTests.editsNoticeTheirOriginalChanging(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void formEditsKeepTheRest(GameTestHelper helper) {
        OverrideTests.formEditsKeepTheRest(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mergesKeepBothSidesChanges(GameTestHelper helper) {
        OverrideTests.mergesKeepBothSidesChanges(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void removedEditsAreKept(GameTestHelper helper) {
        OverrideTests.removedEditsAreKept(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void brokenOverridesAreLeftOut(GameTestHelper helper) {
        OverrideTests.brokenOverridesAreLeftOut(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void draftsRollBeforeSaving(GameTestHelper helper) {
        OverrideTests.draftsRollBeforeSaving(helper);
    }

    // Reloads the server's datapacks twice, so it runs on its own rather than alongside other tests.
    @GameTest(template = EMPTY_STRUCTURE, batch = "loot_override_reload", timeoutTicks = 1200)
    public void editsApplyOnReload(GameTestHelper helper) {
        OverrideTests.editsApplyOnReload(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void impossibleLocateIsQuick(GameTestHelper helper) {
        ServiceTests.impossibleLocateIsQuick(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void teleportLandsSomewhereSafe(GameTestHelper helper) {
        ServiceTests.teleportLandsSomewhereSafe(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void onlyOperatorsCanTeleport(GameTestHelper helper) {
        ServiceTests.onlyOperatorsCanTeleport(helper);
    }

    // Captures everything installed and times it; does nothing without -Pperf. Long timeout for
    // the batch it holds up, although the body runs in one go on the server thread.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 72000)
    public void captureEveryStructure(GameTestHelper helper) {
        PerfTests.captureEveryStructure(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unknownLootTableIsEmpty(GameTestHelper helper) {
        ServiceTests.unknownLootTableIsEmpty(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void browserStateSurvivesARestart(GameTestHelper helper) {
        SettingsTests.browserStateSurvivesARestart(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void serverSettingsReadTheirFile(GameTestHelper helper) {
        SettingsTests.serverSettingsReadTheirFile(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void serverSettingsWriteBack(GameTestHelper helper) {
        SettingsTests.serverSettingsWriteBack(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void oldSettingsFilesGainNewSettings(GameTestHelper helper) {
        SettingsTests.oldSettingsFilesGainNewSettings(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hiddenStructuresStayHidden(GameTestHelper helper) {
        SettingsTests.hiddenStructuresStayHidden(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void locateLevelsComeFromTheSettings(GameTestHelper helper) {
        SettingsTests.locateLevelsComeFromTheSettings(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void structureInfoFilesAreRead(GameTestHelper helper) {
        SettingsTests.structureInfoFilesAreRead(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hiddenLootLeavesThePreview(GameTestHelper helper) {
        SettingsTests.hiddenLootLeavesThePreview(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void patchesFollowTheirContainer(GameTestHelper helper) {
        ContainerTests.patchesFollowTheirContainer(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void patchesAreChecked(GameTestHelper helper) {
        ContainerTests.patchesAreChecked(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void patchesCanBeTurnedOff(GameTestHelper helper) {
        ContainerTests.patchesCanBeTurnedOff(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void containersKnowTheirTemplate(GameTestHelper helper) {
        ContainerTests.containersKnowTheirTemplate(helper);
    }

    // Reloads the server's datapacks twice, so it runs on its own rather than alongside other tests.
    @GameTest(template = EMPTY_STRUCTURE, batch = "container_patch_reload", timeoutTicks = 1200)
    public void patchesApplyOnReload(GameTestHelper helper) {
        ContainerTests.patchesApplyOnReload(helper);
    }
}
