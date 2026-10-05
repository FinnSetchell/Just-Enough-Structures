package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.StructureScan;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Containers in structure templates pointed at other loot tables from the browser. Every test works in a folder of its own. */
public final class ContainerTests {
    private static final ResourceLocation TOWER = Ids.parse("pillager_outpost/watchtower");
    private static final ResourceLocation IGLOO = Ids.parse("chests/igloo_chest");
    private static final ResourceLocation OUTPOST = Ids.parse("pillager_outpost");
    private static final ResourceLocation VILLAGE = Ids.parse("village_plains");
    private static final ResourceLocation MARKER = Ids.of("justenoughstructures", "test/marker");
    private static final String DIAMONDS_ONLY = """
            {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:diamond"}]}]}
            """;

    private ContainerTests() {
    }

    private static Path freshFolder() {
        try {
            Path dir = Files.createTempDirectory("jes-containers");
            LootOverrides.setFolder(dir);
            ContainerPatches.load();
            return dir;
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
    }

    private static void leaveFolder() {
        LootOverrides.setFolder(null);
        ContainerPatches.load();
    }

    /** A file a newer version saved is neither read nor written over. */
    public static void newerFilesAreLeftAlone(GameTestHelper helper) {
        Path dir = freshFolder();
        try {
            Path file = dir.resolve("containers.json");
            String newer = """
                    {"format": 99, "patches": [{"template": "minecraft:pillager_outpost/feature_cage1", "pos": [1, 2, 3],
                      "block": "minecraft:chest", "original": "", "table": "minecraft:chests/igloo_chest"}], "removed": []}
                    """;
            Files.writeString(file, newer);
            ContainerPatches.load();
            helper.assertTrue(ContainerPatches.all().isEmpty(), "read a file a newer version saved");
            Component saved = ContainerPatches.save(new ContainerPatches.Patch(Ids.parse("pillager_outpost/feature_cage1"),
                    new BlockPos(1, 2, 3), Ids.parse("chest"), "", IGLOO));
            helper.assertTrue(key(saved).endsWith("override.save_failed"), "saved over a file a newer version saved: " + saved.getString());
            helper.assertTrue(newer.equals(Files.readString(file)), "a file a newer version saved was changed");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("couldn't write the test file: " + e);
        } finally {
            leaveFolder();
        }
    }

    /** A patch changes its container as the template loads, and one that no longer fits, or a broken file, changes nothing. */
    public static void patchesFollowTheirContainer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // Loaded before any patch exists, so the copy the game shares is never patched by this test.
        StructureTemplate tower = server.getStructureManager().get(TOWER).orElse(null);
        helper.assertTrue(tower != null, "the watchtower template didn't load");
        StructureTemplate.StructureBlockInfo chest = firstContainer(tower);
        helper.assertTrue(chest != null, "the watchtower has no container with a loot table");
        String original = chest.nbt().getString("LootTable");
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(chest.state().getBlock());
        Path dir = freshFolder();
        try {
            Component saved = ContainerPatches.save(new ContainerPatches.Patch(TOWER, chest.pos(), block, original, IGLOO));
            helper.assertTrue(key(saved).endsWith("container.saved"), "saving got " + saved.getString());
            StructureTemplate patched = copy(tower);
            ContainerPatches.apply(TOWER, patched);
            helper.assertTrue(IGLOO.toString().equals(tableAt(patched, chest.pos())), "the patch didn't change the container");
            helper.assertTrue(original.equals(tableAt(tower, chest.pos())), "patching a copy changed the loaded template");

            // The container's block changed, the spot is empty, and one entry is nonsense: none of them apply, and nothing throws.
            Path file = dir.resolve("containers.json");
            Files.writeString(file, """
                    {"patches": [
                      {"template": "%1$s", "pos": [%2$d, %3$d, %4$d], "block": "minecraft:dispenser", "original": "%5$s", "table": "%6$s"},
                      {"template": "%1$s", "pos": [0, -40, 0], "block": "%7$s", "original": "%5$s", "table": "%6$s"},
                      {"template": "%1$s", "pos": "here"},
                      7
                    ]}
                    """.formatted(TOWER, chest.pos().getX(), chest.pos().getY(), chest.pos().getZ(), original, IGLOO, block));
            ContainerPatches.load();
            StructureTemplate mismatched = copy(tower);
            ContainerPatches.apply(TOWER, mismatched);
            helper.assertTrue(original.equals(tableAt(mismatched, chest.pos())), "a patch for a block that isn't there was applied");

            // A file broken by hand patches nothing, and saving refuses rather than writing over it.
            Files.writeString(file, "{ not json");
            ContainerPatches.load();
            helper.assertTrue(ContainerPatches.byTemplate().isEmpty(), "a broken file still had patches");
            StructureTemplate untouched = copy(tower);
            ContainerPatches.apply(TOWER, untouched);
            helper.assertTrue(original.equals(tableAt(untouched, chest.pos())), "a broken file changed a container");
            Component refused = ContainerPatches.save(new ContainerPatches.Patch(TOWER, chest.pos(), block, original, IGLOO));
            helper.assertTrue(key(refused).endsWith("override.save_failed"), "saving over a broken file got " + refused.getString());
            helper.assertTrue(Files.readString(file).equals("{ not json"), "saving wrote over a file it couldn't read");

            // Undoing keeps the patch in the file's list of removed ones.
            Files.delete(file);
            ContainerPatches.save(new ContainerPatches.Patch(TOWER, chest.pos(), block, original, IGLOO));
            Component removed = ContainerPatches.remove(TOWER, chest.pos());
            helper.assertTrue(key(removed).endsWith("container.removed"), "undoing got " + removed.getString());
            helper.assertTrue(ContainerPatches.find(TOWER, chest.pos()) == null, "an undone patch is still used");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            helper.assertTrue(json.getAsJsonArray("removed").size() == 1 && json.getAsJsonArray("patches").isEmpty(),
                    "the undone patch wasn't kept aside: " + json);
            Component again = ContainerPatches.remove(TOWER, chest.pos());
            helper.assertTrue(key(again).endsWith("container.none"), "undoing twice got " + again.getString());
        } catch (IOException e) {
            throw new AssertionError("couldn't write the test's files", e);
        } finally {
            leaveFolder();
        }
        helper.succeed();
    }

    /** The server only saves a patch for a container that's there and a table that exists, from a player allowed to edit. */
    public static void patchesAreChecked(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplate tower = server.getStructureManager().get(TOWER).orElse(null);
        helper.assertTrue(tower != null, "the watchtower template didn't load");
        StructureTemplate.StructureBlockInfo chest = firstContainer(tower);
        helper.assertTrue(chest != null, "the watchtower has no container with a loot table");
        String original = chest.nbt().getString("LootTable");
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        freshFolder();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(4)));
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos(), IGLOO), "no_permission");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
            expect(helper, JesServer.patchContainer(player, Ids.of("justenoughstructures", "no/such/template"), chest.pos(), IGLOO), "no_template");
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos().above(60), IGLOO), "not_there");
            ResourceLocation fresh = Ids.of("justenoughstructures", "chests/made_in_a_test");
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos(), fresh), "no_table");
            helper.assertTrue(ContainerPatches.find(TOWER, chest.pos()) == null, "a refused patch was saved");

            // A table just made in the editor can be picked before the /reload that loads it.
            Component made = LootOverrides.save(server.getResourceManager(), fresh, DIAMONDS_ONLY);
            helper.assertTrue(key(made).endsWith("override.saved"), "saving a new table got " + made.getString());
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos(), fresh), "container.saved");

            // Changing it again still remembers the table the container had first.
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos(), IGLOO), "container.saved");
            ContainerPatches.Patch patch = ContainerPatches.find(TOWER, chest.pos());
            helper.assertTrue(patch != null && patch.table().equals(IGLOO) && patch.original().equals(original),
                    "the second change lost the first table, got " + patch);
            expect(helper, JesServer.unpatchContainer(player, TOWER, chest.pos()), "container.removed");
        } finally {
            ServerConfig.set(before);
            leaveFolder();
        }
        helper.succeed();
    }

    /** Turning container changes off in the settings leaves templates alone, keeps the saved changes, and uses them again once it's back on. */
    public static void patchesCanBeTurnedOff(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplate tower = server.getStructureManager().get(TOWER).orElse(null);
        helper.assertTrue(tower != null, "the watchtower template didn't load");
        StructureTemplate.StructureBlockInfo chest = firstContainer(tower);
        helper.assertTrue(chest != null, "the watchtower has no container with a loot table");
        String original = chest.nbt().getString("LootTable");
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(chest.state().getBlock());
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        freshFolder();
        try {
            ContainerPatches.save(new ContainerPatches.Patch(TOWER, chest.pos(), block, original, IGLOO));
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0), false));
            StructureTemplate off = copy(tower);
            ContainerPatches.apply(TOWER, off);
            helper.assertTrue(original.equals(tableAt(off, chest.pos())), "a change was used with changes turned off");
            helper.assertTrue(ContainerPatches.byTemplate().isEmpty(), "the loot index still counts changes that are turned off");
            expect(helper, JesServer.patchContainer(player, TOWER, chest.pos(), IGLOO), "container.turned_off");
            ServerConfig.Settings reread = ServerConfig.parse(ServerConfig.render(ServerConfig.get()), "the test");
            helper.assertFalse(reread.containerChanges(), "the switch didn't survive being written to the file and read back");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0), true));
            StructureTemplate on = copy(tower);
            ContainerPatches.apply(TOWER, on);
            helper.assertTrue(IGLOO.toString().equals(tableAt(on, chest.pos())), "the kept change wasn't used once turned back on");
        } finally {
            ServerConfig.set(before);
            leaveFolder();
        }
        helper.succeed();
    }

    /**
     * Changing a container only sends the structures that place its template through the loot index
     * again. A table only a fresh scan would drop, added to every structure, shows which ones were.
     */
    public static void lootIndexUpdatesOnlyWhatChanged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplate tower = server.getStructureManager().get(TOWER).orElse(null);
        helper.assertTrue(tower != null, "the watchtower template didn't load");
        StructureTemplate.StructureBlockInfo chest = firstContainer(tower);
        helper.assertTrue(chest != null, "the watchtower has no container with a loot table");
        freshFolder();
        try {
            StructureScan full = LootIndex.scan(server, List.of(OUTPOST, VILLAGE), done -> {
            }, () -> false, new AtomicInteger());
            helper.assertTrue(full.templates().getOrDefault(OUTPOST, Set.of()).contains(TOWER),
                    "the outpost's templates don't include the watchtower: " + full.templates().get(OUTPOST));
            StructureScan marked = withMarker(full);
            helper.assertTrue(LootIndex.update(server, marked, () -> false) == marked, "nothing changed, but the scan did");

            ContainerPatches.save(new ContainerPatches.Patch(TOWER, chest.pos(), BuiltInRegistries.BLOCK.getKey(chest.state().getBlock()),
                    chest.nbt().getString("LootTable"), IGLOO));
            StructureScan updated = LootIndex.update(server, marked, () -> false);
            helper.assertFalse(updated.tables().getOrDefault(OUTPOST, Set.of()).contains(MARKER), "the outpost wasn't generated again for its changed container");
            helper.assertTrue(updated.tables().getOrDefault(VILLAGE, Set.of()).contains(MARKER), "the village was generated again, though nothing in it changed");
            helper.assertTrue(updated.patches().equals(ContainerPatches.byTemplate()), "the scan doesn't remember the changes it was made with");
        } finally {
            leaveFolder();
        }
        helper.succeed();
    }

    /** After /reload, bringing the loot index up to date finds the table a container was changed to. */
    public static void lootIndexFollowsContainerChanges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshFolder();
        StructureScan before = withMarker(LootIndex.scan(server, List.of(OUTPOST, VILLAGE), done -> {
        }, () -> false, new AtomicInteger()));
        StructureTemplate tower = server.getStructureManager().get(TOWER).orElse(null);
        StructureTemplate.StructureBlockInfo chest = tower == null ? null : firstContainer(tower);
        if (chest == null) {
            leaveFolder();
            helper.fail("the watchtower has no container with a loot table");
            return;
        }
        ContainerPatches.save(new ContainerPatches.Patch(TOWER, chest.pos(), BuiltInRegistries.BLOCK.getKey(chest.state().getBlock()),
                chest.nbt().getString("LootTable"), IGLOO));
        CompletableFuture<Void> first = reload(server);
        AtomicReference<CompletableFuture<Void>> second = new AtomicReference<>();
        StructureScan[] after = new StructureScan[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isDone(), "still reloading");
            if (second.get() == null) {
                after[0] = LootIndex.update(server, before, () -> false);
                ContainerPatches.remove(TOWER, chest.pos());
                second.set(reload(server));
            }
            helper.assertTrue(second.get().isDone(), "still reloading");
            leaveFolder();
            helper.assertTrue(after[0].tables().getOrDefault(OUTPOST, Set.of()).contains(IGLOO),
                    "the outpost's tables don't include the one its chest was changed to: " + after[0].tables().get(OUTPOST));
            helper.assertTrue(after[0].tables().getOrDefault(VILLAGE, Set.of()).contains(MARKER), "the village was generated again, though nothing in it changed");
        });
    }

    /** The scan with a table no structure has added to every structure, which generating one again drops. */
    private static StructureScan withMarker(StructureScan scan) {
        Map<ResourceLocation, Set<ResourceLocation>> tables = new TreeMap<>();
        scan.tables().forEach((id, found) -> {
            Set<ResourceLocation> marked = new TreeSet<>(found);
            marked.add(MARKER);
            tables.put(id, marked);
        });
        return new StructureScan(tables, scan.templates(), scan.patches());
    }

    /** Containers placed from a template know where in it they came from. Ones structure code places don't. */
    public static void containersKnowTheirTemplate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<StructureSnapshot.Container> sourced = capture(server, "pillager_outpost").containers().stream()
                .filter(c -> c.source() != null).toList();
        helper.assertFalse(sourced.isEmpty(), "no container in the pillager outpost knew its template");
        for (StructureSnapshot.Container container : sourced) {
            StructureSnapshot.Source source = container.source();
            StructureTemplate template = server.getStructureManager().get(source.template()).orElse(null);
            helper.assertTrue(template != null, "the container's template " + source.template() + " doesn't load");
            StructureTemplate.StructureBlockInfo info = containerAt(template, source.pos());
            helper.assertTrue(info != null, "there's no container at " + source.pos() + " in " + source.template());
            helper.assertTrue(info.nbt().getString("LootTable").equals(container.lootTable())
                            && BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).equals(source.block()),
                    "the container at " + container.pos() + " doesn't match its spot in " + source.template());
            helper.assertTrue(source.patchedFrom() == null, "a container nobody changed says it was changed");
        }
        boolean codePlaced = capture(server, "jungle_pyramid").containers().stream()
                .filter(c -> c.lootTable() != null).allMatch(c -> c.source() == null);
        helper.assertTrue(codePlaced, "a jungle pyramid chest, placed by structure code, claimed a template");
        helper.succeed();
    }

    /** After /reload the structure really has the changed container, and after undoing it and another /reload, its own table again. */
    public static void patchesApplyOnReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshFolder();
        StructureSnapshot.Container before = capture(server, "pillager_outpost").containers().stream()
                .filter(c -> c.source() != null).findFirst().orElse(null);
        if (before == null) {
            leaveFolder();
            helper.fail("no container in the pillager outpost knew its template");
            return;
        }
        StructureSnapshot.Source source = before.source();
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings settings = ServerConfig.get();
        ServerConfig.Settings open = new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0));
        ServerConfig.set(open);
        Component saved = JesServer.patchContainer(player, source.template(), source.pos(), IGLOO);
        ServerConfig.set(settings);
        if (!key(saved).endsWith("container.saved")) {
            leaveFolder();
            helper.fail("saving the patch got " + saved.getString());
            return;
        }
        CompletableFuture<Void> first = reload(server);
        AtomicReference<CompletableFuture<Void>> second = new AtomicReference<>();
        StructureSnapshot.Container[] seen = new StructureSnapshot.Container[2];
        String[] rechanged = new String[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isDone(), "still reloading");
            if (second.get() == null) {
                seen[0] = at(capture(server, "pillager_outpost"), source);
                ContainerPatches.remove(source.template(), source.pos());
                // Changed again before the next /reload, the loaded template still has the undone
                // table, and the new patch has to remember the container's own one.
                ServerConfig.set(open);
                JesServer.patchContainer(player, source.template(), source.pos(), Ids.parse("chests/desert_pyramid"));
                ServerConfig.set(settings);
                rechanged[0] = ContainerPatches.all().stream()
                        .filter(p -> p.template().equals(source.template()) && p.pos().equals(source.pos()))
                        .map(ContainerPatches.Patch::original).findFirst().orElse(null);
                ContainerPatches.remove(source.template(), source.pos());
                second.set(reload(server));
            }
            helper.assertTrue(second.get().isDone(), "still reloading");
            if (seen[1] == null) {
                seen[1] = at(capture(server, "pillager_outpost"), source);
                leaveFolder();
            }
            helper.assertTrue(seen[0] != null && IGLOO.toString().equals(seen[0].lootTable())
                    && before.lootTable().equals(seen[0].source().patchedFrom()), "the changed container wasn't used after /reload");
            helper.assertTrue(seen[1] != null && before.lootTable().equals(seen[1].lootTable()) && seen[1].source().patchedFrom() == null,
                    "the container's own table wasn't back after undoing the change");
            helper.assertTrue(before.lootTable().equals(rechanged[0]),
                    "changing it again before /reload remembered " + rechanged[0] + " as its own table, not " + before.lootTable());
        });
    }

    private static StructureSnapshot capture(MinecraftServer server, String structure) {
        CaptureResult result = StructureCapture.capture(server, Ids.parse(structure), CaptureTests.SEED);
        if (!result.succeeded()) {
            throw new AssertionError(structure + " did not capture: " + result.error());
        }
        return result.snapshot();
    }

    private static StructureSnapshot.Container at(StructureSnapshot snapshot, StructureSnapshot.Source source) {
        return snapshot.containers().stream()
                .filter(c -> c.source() != null && c.source().template().equals(source.template()) && c.source().pos().equals(source.pos()))
                .findFirst().orElse(null);
    }

    /** What /reload does: look for new datapacks, then reload with them. */
    private static CompletableFuture<Void> reload(MinecraftServer server) {
        server.getPackRepository().reload();
        return server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    private static StructureTemplate copy(StructureTemplate template) {
        StructureTemplate copy = new StructureTemplate();
        // Saving hands over the template's own block entity tags, so they're copied to keep the two apart.
        copy.load(BuiltInRegistries.BLOCK.asLookup(), template.save(new CompoundTag()).copy());
        return copy;
    }

    private static StructureTemplate.StructureBlockInfo firstContainer(StructureTemplate template) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.nbt() != null && info.nbt().contains("LootTable", Tag.TAG_STRING)) {
                    return info;
                }
            }
        }
        return null;
    }

    private static StructureTemplate.StructureBlockInfo containerAt(StructureTemplate template, BlockPos pos) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.pos().equals(pos) && info.nbt() != null && info.nbt().contains("LootTable", Tag.TAG_STRING)) {
                    return info;
                }
            }
        }
        return null;
    }

    private static String tableAt(StructureTemplate template, BlockPos pos) {
        StructureTemplate.StructureBlockInfo info = containerAt(template, pos);
        return info == null ? null : info.nbt().getString("LootTable");
    }

    private static void expect(GameTestHelper helper, Component reply, String keyEnd) {
        helper.assertTrue(key(reply).endsWith(keyEnd), "expected " + keyEnd + " but got " + reply.getString());
    }

    private static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }
}
