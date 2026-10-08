package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.JustEnoughStructures;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
//? if <26.1 {
import java.util.Collection;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
//?}

/**
 * Every game test, which each loader's test mod registers: Fabric through its fabric-gametest
 * entrypoint, Forge and NeoForge by the annotations here, and from 26.1 Forge through ForgeGameTests
 * and NeoForge through NeoForgeGameTests. Each test runs in an empty 8x8x8 structure.
 */
//? if forge && <1.21 {
/*@net.minecraftforge.gametest.GameTestHolder("justenoughstructures_gametest")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
*///?} else if forge && <26.1 {
/*@net.minecraftforge.gametest.GameTestHolder("justenoughstructures_gametest")
*///?} else if forge {
/*// Forge from 26.1 names each test after its method, in the test mod's namespace.
@net.minecraftforge.gametest.GameTestNamespace("justenoughstructures_gametest")
@net.minecraftforge.gametest.GameTestDontPrefix
*///?} else if neoforge && <26.1 {
/*@net.neoforged.neoforge.gametest.GameTestHolder("justenoughstructures_gametest")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
*///?}
public final class JesGameTests {
    //? if fabric {
    private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
    private static final String EMPTY_STRUCTURE_ID = EMPTY_STRUCTURE;
    //?} else if forge && >=1.21 {
    /*// Forge 1.21 puts the holder's name in front of a template, unless it's given with its namespace.
    private static final String EMPTY_STRUCTURE = "justenoughstructures_gametest:empty";
    private static final String EMPTY_STRUCTURE_ID = EMPTY_STRUCTURE;
    *///?} else if neoforge && >=26.1 {
    /*// NeoForge from 26.1 takes it as it is.
    private static final String EMPTY_STRUCTURE = "justenoughstructures_gametest:empty";
    private static final String EMPTY_STRUCTURE_ID = EMPTY_STRUCTURE;
    *///?} else {
    /*// Forge and NeoForge put the holder's namespace in front of a test's template, but not a generated test's.
    private static final String EMPTY_STRUCTURE = "empty";
    private static final String EMPTY_STRUCTURE_ID = "justenoughstructures_gametest:empty";
    *///?}

    @GameTest(template = EMPTY_STRUCTURE)
    public void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(JustEnoughStructures.modVersions().containsKey(JustEnoughStructures.MOD_ID), "mod not loaded");
        helper.succeed();
    }

    //? if >=26.1 {
    /*// From 26.1 each test is a method of its own, so the vanilla structures are captured in one test, one a tick.
    @GameTest(structure = EMPTY_STRUCTURE, environment = "justenoughstructures_gametest:capture", maxTicks = 200)
    public void captureEveryVanillaStructure(GameTestHelper helper) {
        CaptureTests.capturesEveryVanillaStructure(helper);
    }
    *///?} else {
    @GameTestGenerator
    public Collection<TestFunction> captureEveryVanillaStructure() {
        return CaptureTests.VANILLA.stream()
                .map(name -> new TestFunction("capture", "capture_" + name, EMPTY_STRUCTURE_ID, 200, 0L, true,
                        helper -> CaptureTests.capturesVanillaStructure(helper, name)))
                .toList();
    }
    //?}

    @GameTest(template = EMPTY_STRUCTURE)
    public void capturesReachPastTheMiddleChunks(GameTestHelper helper) {
        CaptureTests.capturesReachPastTheMiddleChunks(helper);
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
    public void backgroundCapturesStopForPreviews(GameTestHelper helper) {
        CaptureTests.backgroundCapturesStopForPreviews(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void outOfMemoryTheGameOnlyLogsIsNoticed(GameTestHelper helper) {
        CaptureTests.outOfMemoryTheGameOnlyLogsIsNoticed(helper);
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
    public void structuresSayIfTheyGenerate(GameTestHelper helper) {
        ServiceTests.structuresSayIfTheyGenerate(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void jesOpenCommand(GameTestHelper helper) {
        ServiceTests.jesOpenCommand(helper);
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
    public void tableIdsStayInTheFolder(GameTestHelper helper) {
        OverrideTests.tableIdsStayInTheFolder(helper);
    }

    //? if >=1.21 {
    /*@GameTest(template = EMPTY_STRUCTURE)
    public void overridesWithModdedEntriesLoad(GameTestHelper helper) {
        OverrideTests.overridesWithModdedEntriesLoad(helper);
    }
    *///?}

    @GameTest(template = EMPTY_STRUCTURE)
    public void editsNoticeTheirOriginalChanging(GameTestHelper helper) {
        OverrideTests.editsNoticeTheirOriginalChanging(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void formEditsKeepTheRest(GameTestHelper helper) {
        OverrideTests.formEditsKeepTheRest(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void newFunctionsAndConditionsLoad(GameTestHelper helper) {
        OverrideTests.newFunctionsAndConditionsLoad(helper);
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void editorShapeRoundTrips(GameTestHelper helper) {
        OverrideTests.editorShapeRoundTrips(helper);
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

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void locateLooksInOtherDimensions(GameTestHelper helper) {
        ServiceTests.locateLooksInOtherDimensions(helper);
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
    public void packToolsFollowTheRules(GameTestHelper helper) {
        SettingsTests.packToolsFollowTheRules(helper);
    }

    // These change the server's settings and what's said about the igloo, so they run on their own.
    @GameTest(template = EMPTY_STRUCTURE, batch = "pack_tools")
    public void packToolsRulesSaveAndApply(GameTestHelper helper) {
        PackToolsTests.rulesSaveAndApply(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "pack_tools")
    public void packToolsNotesGoInThePack(GameTestHelper helper) {
        PackToolsTests.structureNotesGoInThePack(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "pack_tools")
    public void packToolsChangesWaitForReload(GameTestHelper helper) {
        PackToolsTests.changesWaitForReload(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "pack_tools")
    public void draftsRollIntoAChest(GameTestHelper helper) {
        PackToolsTests.draftsRollIntoAChest(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void uploadsAreCapped(GameTestHelper helper) {
        PackToolsTests.uploadsAreCapped(helper);
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

    //? if >=1.21 {
    /*@GameTest(template = EMPTY_STRUCTURE, batch = "capture", timeoutTicks = 400)
    public void hiddenLootLeavesTrialSpawners(GameTestHelper helper) {
        SettingsTests.hiddenLootLeavesTrialSpawners(helper);
    }
    *///?}

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

    @GameTest(template = EMPTY_STRUCTURE)
    public void newerPatchFilesAreLeftAlone(GameTestHelper helper) {
        ContainerTests.newerFilesAreLeftAlone(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void lootIndexUpdatesOnlyWhatChanged(GameTestHelper helper) {
        ContainerTests.lootIndexUpdatesOnlyWhatChanged(helper);
    }

    // Reloads the server's datapacks twice, so it runs on its own rather than alongside other tests.
    @GameTest(template = EMPTY_STRUCTURE, batch = "loot_index_update", timeoutTicks = 1200)
    public void lootIndexFollowsContainerChanges(GameTestHelper helper) {
        ContainerTests.lootIndexFollowsContainerChanges(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bigSparseStructuresStayCheap(GameTestHelper helper) {
        GridTests.bigSparseStructuresStayCheap(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "capture", timeoutTicks = 400)
    public void gridMatchesACapturedVillage(GameTestHelper helper) {
        GridTests.gridMatchesACapturedVillage(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void previewsLeaveTheRealWorldAlone(GameTestHelper helper) {
        RealWorldTests.previewsLeaveTheRealWorldAlone(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "capture", timeoutTicks = 400)
    public void spawnerPoolsAreRecorded(GameTestHelper helper) {
        CaptureTests.spawnerPoolsAreRecorded(helper);
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnerPatchesFollowTheirSpawner(GameTestHelper helper) {
        SpawnerTests.patchesFollowTheirSpawner(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void patchedSpawnersKeepTheirMob(GameTestHelper helper) {
        SpawnerTests.patchedSpawnersKeepTheirMob(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnerPatchesAreChecked(GameTestHelper helper) {
        SpawnerTests.patchesAreChecked(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnerPatchesSurviveTheWire(GameTestHelper helper) {
        SpawnerTests.spawnerPatchesSurviveTheWire(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnerSwitchesAreChecked(GameTestHelper helper) {
        SpawnerTests.switchesAreChecked(helper);
    }

    //? if >=1.21 {
    /*@GameTest(template = EMPTY_STRUCTURE)
    public void trialSpawnerPatchesKeepTheirSettings(GameTestHelper helper) {
        SpawnerTests.trialPatchesKeepTheirSettings(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnersSwitchKind(GameTestHelper helper) {
        SpawnerTests.spawnersSwitchKind(helper);
    }

    // Loads the lava basin again with a patch, so it runs on its own rather than alongside other tests.
    @GameTest(template = EMPTY_STRUCTURE, batch = "spawner_switch", timeoutTicks = 1200)
    public void switchedSpawnersAreCaptured(GameTestHelper helper) {
        SpawnerTests.switchedSpawnersAreCaptured(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "capture", timeoutTicks = 400)
    public void trialSpawnersKnowTheirTemplate(GameTestHelper helper) {
        SpawnerTests.trialSpawnersKnowTheirTemplate(helper);
    }
    *///?}

    @GameTest(template = EMPTY_STRUCTURE, batch = "capture", timeoutTicks = 400)
    public void spawnersKnowTheirTemplate(GameTestHelper helper) {
        SpawnerTests.spawnersKnowTheirTemplate(helper);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "capture", timeoutTicks = 400)
    public void spawnersGroupByWhatTheyMake(GameTestHelper helper) {
        SpawnerTests.spawnersGroupByWhatTheyMake(helper);
    }

    // Reloads the server's datapacks twice, so it runs on its own rather than alongside other tests.
    @GameTest(template = EMPTY_STRUCTURE, batch = "spawner_patch_reload", timeoutTicks = 1200)
    public void spawnerPatchesApplyOnReload(GameTestHelper helper) {
        SpawnerTests.patchesApplyOnReload(helper);
    }
}
