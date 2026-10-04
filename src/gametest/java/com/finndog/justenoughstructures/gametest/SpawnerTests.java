package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.screen.SpawnerKind;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.PackToolsState;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Spawners in structure templates given other mobs from the browser. The bastion's treasure room
 * has the one vanilla spawner every layout of it has, a magma cube spawner in its lava basin. Every
 * test works in a folder of its own.
 */
public final class SpawnerTests {
    private static final ResourceLocation BASIN = new ResourceLocation("bastion/treasure/bases/lava_basin");
    private static final ResourceLocation BASTION = new ResourceLocation("bastion_remnant");
    private static final String MAGMA_CUBE = "minecraft:magma_cube";
    private static final String HUSK = "minecraft:husk";
    /** Bastion layouts to try for one with the treasure room, which about a quarter have. */
    private static final int TRIES = 16;
    private static volatile Long treasureSeed;

    private SpawnerTests() {
    }

    private static Path freshFolder() {
        try {
            Path dir = Files.createTempDirectory("jes-spawners");
            LootOverrides.setFolder(dir);
            ContainerPatches.load();
            SpawnerPatches.load();
            return dir;
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
    }

    private static void leaveFolder() {
        LootOverrides.setFolder(null);
        ContainerPatches.load();
        SpawnerPatches.load();
    }

    /**
     * A patch gives its spawner just the new mob, or none, as the template loads, and drops the list
     * the spawner would go back to the old mob from. One that no longer fits, or a broken file,
     * changes nothing.
     */
    public static void patchesFollowTheirSpawner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // Loaded before any patch exists, so the copy the game shares is never patched by this test.
        StructureTemplate basin = server.getStructureManager().get(BASIN).orElse(null);
        helper.assertTrue(basin != null, "the lava basin template didn't load");
        StructureTemplate.StructureBlockInfo spawner = firstSpawner(basin);
        helper.assertTrue(spawner != null, "the lava basin has no spawner");
        String original = SpawnerPatches.mobOf(spawner.nbt());
        helper.assertTrue(MAGMA_CUBE.equals(original), "the lava basin's spawner makes " + original);
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(spawner.state().getBlock());
        Path dir = freshFolder();
        try {
            expect(helper, SpawnerPatches.save(new SpawnerPatches.Patch(BASIN, spawner.pos(), block, original, 0, HUSK)), "spawner.saved");
            StructureTemplate patched = copy(basin);
            // A rule about light, which belongs to the spot rather than the mob, is kept.
            CompoundTag rules = new CompoundTag();
            rules.putInt("block_light_limit", 7);
            spawnerAt(patched, spawner.pos()).nbt().getCompound("SpawnData").put("custom_spawn_rules", rules);
            SpawnerPatches.apply(BASIN, patched);
            CompoundTag now = spawnerAt(patched, spawner.pos()).nbt();
            CompoundTag entity = now.getCompound("SpawnData").getCompound("entity");
            helper.assertTrue(entity.size() == 1 && HUSK.equals(entity.getString("id")), "the spawner's mob is " + entity + ", not just a husk");
            helper.assertFalse(now.contains("SpawnPotentials"), "the spawner still has a list to go back to the old mob from");
            helper.assertTrue(rules.equals(now.getCompound("SpawnData").getCompound("custom_spawn_rules")), "the spawner's rule about light went");
            helper.assertTrue(MAGMA_CUBE.equals(SpawnerPatches.mobOf(spawnerAt(basin, spawner.pos()).nbt())), "patching a copy changed the loaded template");
            helper.assertTrue(ContainerPatches.byTemplate().isEmpty(), "a changed spawner made the loot index think a container changed");

            // No mob at all: an empty spawner.
            expect(helper, SpawnerPatches.save(new SpawnerPatches.Patch(BASIN, spawner.pos(), block, original, 0, "")), "spawner.saved");
            StructureTemplate emptied = copy(basin);
            SpawnerPatches.apply(BASIN, emptied);
            helper.assertTrue(spawnerAt(emptied, spawner.pos()).nbt().getCompound("SpawnData").getCompound("entity").isEmpty(),
                    "the spawner wasn't emptied");

            // The spawner's block changed, the spot is empty, and one entry is nonsense: none of them apply, and nothing throws.
            Path file = dir.resolve("spawners.json");
            Files.writeString(file, """
                    {"patches": [
                      {"template": "%1$s", "pos": [%2$d, %3$d, %4$d], "block": "minecraft:chest", "original": "%5$s", "mob": "%6$s"},
                      {"template": "%1$s", "pos": [0, -40, 0], "block": "%7$s", "original": "%5$s", "mob": "%6$s"},
                      {"template": "%1$s", "pos": "here"},
                      7
                    ]}
                    """.formatted(BASIN, spawner.pos().getX(), spawner.pos().getY(), spawner.pos().getZ(), original, HUSK, block));
            SpawnerPatches.load();
            StructureTemplate mismatched = copy(basin);
            SpawnerPatches.apply(BASIN, mismatched);
            helper.assertTrue(MAGMA_CUBE.equals(SpawnerPatches.mobOf(spawnerAt(mismatched, spawner.pos()).nbt())), "a patch for a block that isn't there was applied");

            // A file broken by hand patches nothing, and saving refuses rather than writing over it.
            Files.writeString(file, "{ not json");
            SpawnerPatches.load();
            helper.assertTrue(SpawnerPatches.find(BASIN, spawner.pos()) == null, "a broken file still had patches");
            StructureTemplate untouched = copy(basin);
            SpawnerPatches.apply(BASIN, untouched);
            helper.assertTrue(MAGMA_CUBE.equals(SpawnerPatches.mobOf(spawnerAt(untouched, spawner.pos()).nbt())), "a broken file changed a spawner");
            Component refused = SpawnerPatches.save(new SpawnerPatches.Patch(BASIN, spawner.pos(), block, original, 0, HUSK));
            helper.assertTrue(key(refused).endsWith("override.save_failed"), "saving over a broken file got " + refused.getString());
            helper.assertTrue(Files.readString(file).equals("{ not json"), "saving wrote over a file it couldn't read");

            // Undoing keeps the patch in the file's list of removed ones.
            Files.delete(file);
            SpawnerPatches.save(new SpawnerPatches.Patch(BASIN, spawner.pos(), block, original, 0, HUSK));
            expect(helper, SpawnerPatches.remove(BASIN, spawner.pos()), "spawner.removed");
            helper.assertTrue(SpawnerPatches.find(BASIN, spawner.pos()) == null, "an undone patch is still used");
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            helper.assertTrue(json.getAsJsonArray("removed").size() == 1 && json.getAsJsonArray("patches").isEmpty(),
                    "the undone patch wasn't kept aside: " + json);
            expect(helper, SpawnerPatches.remove(BASIN, spawner.pos()), "spawner.none");
        } catch (IOException e) {
            throw new AssertionError("couldn't write the test's files", e);
        } finally {
            leaveFolder();
        }
        helper.succeed();
    }

    /**
     * A spawner made from a patched template keeps the new mob after it spawns: every mob it can go
     * on to is the new one. Changing only its mob and keeping its list would bring the old one back.
     */
    public static void patchedSpawnersKeepTheirMob(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplate basin = server.getStructureManager().get(BASIN).orElse(null);
        StructureTemplate.StructureBlockInfo spawner = basin == null ? null : firstSpawner(basin);
        helper.assertTrue(spawner != null, "the lava basin has no spawner");
        freshFolder();
        StructureTemplate patched = copy(basin);
        try {
            SpawnerPatches.save(new SpawnerPatches.Patch(BASIN, spawner.pos(), BuiltInRegistries.BLOCK.getKey(spawner.state().getBlock()),
                    MAGMA_CUBE, 0, HUSK));
            SpawnerPatches.apply(BASIN, patched);
        } finally {
            leaveFolder();
        }
        List<String> next = nextMobs(spawnerAt(patched, spawner.pos()).nbt());
        helper.assertTrue(!next.isEmpty() && next.stream().allMatch(HUSK::equals), "the patched spawner can go on to " + next);

        CompoundTag onlyMob = spawner.nbt().copy();
        CompoundTag husk = new CompoundTag();
        husk.putString("id", HUSK);
        onlyMob.getCompound("SpawnData").put("entity", husk);
        helper.assertTrue(nextMobs(onlyMob).contains(MAGMA_CUBE), "keeping the list didn't bring the magma cube back, so this test proves nothing");
        helper.succeed();
    }

    /** The server only saves a patch for a spawner that's there and a mob a spawner can make, from a player allowed to edit. */
    public static void patchesAreChecked(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureTemplate basin = server.getStructureManager().get(BASIN).orElse(null);
        StructureTemplate.StructureBlockInfo spawner = basin == null ? null : firstSpawner(basin);
        helper.assertTrue(spawner != null, "the lava basin has no spawner");
        BlockPos pos = spawner.pos();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerConfig.Settings before = ServerConfig.get();
        freshFolder();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(4)));
            expect(helper, JesServer.patchSpawner(player, BASIN, pos, HUSK), "no_permission");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
            expect(helper, JesServer.patchSpawner(player, new ResourceLocation("justenoughstructures", "no/such/template"), pos, HUSK), "no_template");
            expect(helper, JesServer.patchSpawner(player, BASIN, pos.above(60), HUSK), "spawner.not_there");
            for (String notMob : List.of("minecraft:no_such_mob", "minecraft:armor_stand", "minecraft:item", "minecraft:player", "Not An Id!")) {
                expect(helper, JesServer.patchSpawner(player, BASIN, pos, notMob), "spawner.not_a_mob");
            }
            helper.assertTrue(SpawnerPatches.find(BASIN, pos) == null, "a refused patch was saved");

            // No mob at all is allowed, and changing it again still remembers the mob it had first.
            expect(helper, JesServer.patchSpawner(player, BASIN, pos, ""), "spawner.saved");
            expect(helper, JesServer.patchSpawner(player, BASIN, pos, HUSK), "spawner.saved");
            SpawnerPatches.Patch patch = SpawnerPatches.find(BASIN, pos);
            helper.assertTrue(patch != null && HUSK.equals(patch.mob()) && MAGMA_CUBE.equals(patch.original()),
                    "the second change lost the first mob, got " + patch);

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0), false));
            expect(helper, JesServer.patchSpawner(player, BASIN, pos, HUSK), "spawner.turned_off");
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));

            expect(helper, JesServer.unpatchSpawner(player, BASIN, pos), "spawner.removed");
            expect(helper, JesServer.unpatchSpawner(player, BASIN, pos), "spawner.none");
        } finally {
            ServerConfig.set(before);
            leaveFolder();
        }
        helper.succeed();
    }

    /** Pack tools gets every changed spawner as it was saved. */
    public static void spawnerPatchesSurviveTheWire(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 4);
        List<SpawnerPatches.Patch> patches = List.of(
                new SpawnerPatches.Patch(BASIN, pos, new ResourceLocation("spawner"), MAGMA_CUBE, 0, HUSK),
                new SpawnerPatches.Patch(new ResourceLocation("mod", "rooms/crypt"), pos.above(), new ResourceLocation("spawner"), "", 2, ""));
        PackToolsState state = new PackToolsState(ServerConfig.get(), Set.of(PackToolsState.spawnerKey(BASIN, pos)), Map.of(), List.of(), patches,
                Map.of(), List.of(), List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Codecs.writeTools(buf, state);
        PackToolsState read = Codecs.readTools(buf);
        helper.assertTrue(read.spawners().equals(patches), "the spawners came back as " + read.spawners());
        helper.assertTrue(read.pending().equals(state.pending()), "what's waiting came back as " + read.pending());
        helper.succeed();
    }

    /**
     * A spawner placed from a template knows where in it it came from. One structure code places,
     * like the stronghold's silverfish spawner, doesn't.
     */
    public static void spawnersKnowTheirTemplate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureSnapshot bastion = treasureBastion(server);
        helper.assertTrue(bastion != null, "none of " + TRIES + " bastion layouts had the treasure room");
        List<StructureSnapshot.Spawner> sourced = bastion.spawners().stream().filter(s -> s.source() != null).toList();
        helper.assertFalse(sourced.isEmpty(), "no spawner in the bastion knew its template");
        for (StructureSnapshot.Spawner spawner : sourced) {
            StructureSnapshot.Source source = spawner.source();
            StructureTemplate template = server.getStructureManager().get(source.template()).orElse(null);
            helper.assertTrue(template != null, "the spawner's template " + source.template() + " doesn't load");
            StructureTemplate.StructureBlockInfo info = spawnerAt(template, source.pos());
            helper.assertTrue(info != null, "there's no spawner at " + source.pos() + " in " + source.template());
            helper.assertTrue(SpawnerPatches.mobOf(info.nbt()).equals(spawner.mob()) && BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).equals(source.block()),
                    "the spawner at " + spawner.pos() + " doesn't match its spot in " + source.template());
            helper.assertTrue(source.patchedFrom() == null, "a spawner nobody changed says it was changed");
        }
        List<StructureSnapshot.Spawner> stronghold = capture(server, new ResourceLocation("stronghold"), CaptureTests.SEED).spawners();
        helper.assertFalse(stronghold.isEmpty(), "the stronghold had no spawner");
        helper.assertTrue(stronghold.stream().allMatch(s -> s.source() == null), "the stronghold's spawner, placed by structure code, claimed a template");

        // Moog's Structure Lib's processor picks these spawners' mobs as they generate, so the
        // template's mob isn't what players find. Only checked when that mod is installed.
        ResourceLocation arena = new ResourceLocation("mns", "small_arena");
        if (server.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(arena)) {
            int picked = 0;
            for (int i = 0; i < 4; i++) {
                StructureSnapshot snapshot = capture(server, arena, CaptureTests.SEED + i);
                for (CompoundTag tag : snapshot.blockEntities()) {
                    if (tag.getList(SpawnerPools.TAG, Tag.TAG_COMPOUND).isEmpty()) {
                        continue;
                    }
                    picked++;
                    BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                    helper.assertTrue(snapshot.spawners().stream().noneMatch(s -> s.pos().equals(pos) && s.source() != null),
                            "a spawner whose mob a processor picked, at " + pos + " in " + arena + ", claimed its template");
                }
            }
            helper.assertTrue(picked > 0, "no spawner in " + arena + " had its mob picked from a list");
        }
        helper.succeed();
    }

    /**
     * A spawner's popup steps through the spawners that make the same as it: each is in its own
     * group, the groups share nothing, and together they're every spawner.
     */
    public static void spawnersGroupByWhatTheyMake(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StructureSnapshot bastion = treasureBastion(server);
        helper.assertTrue(bastion != null, "none of " + TRIES + " bastion layouts had the treasure room");
        Map<BlockPos, CompoundTag> tags = SpawnerKind.tags(bastion);
        Set<BlockPos> grouped = new HashSet<>();
        int groups = 0;
        for (StructureSnapshot.Spawner spawner : bastion.spawners()) {
            List<StructureSnapshot.Spawner> same = SpawnerKind.same(bastion, spawner);
            helper.assertTrue(same.contains(spawner), "the spawner at " + spawner.pos() + " isn't in its own group");
            SpawnerKind kind = SpawnerKind.of(tags.get(spawner.pos()));
            for (StructureSnapshot.Spawner other : same) {
                helper.assertTrue(SpawnerKind.of(tags.get(other.pos())).equals(kind), "the spawner at " + other.pos()
                        + " is grouped with " + spawner.pos() + " but makes something else");
            }
            if (same.get(0).equals(spawner)) {
                groups++;
                for (StructureSnapshot.Spawner other : same) {
                    helper.assertTrue(grouped.add(other.pos()), "the spawner at " + other.pos() + " is in two groups");
                }
            }
        }
        helper.assertTrue(grouped.size() == bastion.spawners().size(), grouped.size() + " of " + bastion.spawners().size()
                + " spawners ended up in a group");
        helper.assertTrue(groups > 0, "the bastion had no spawners");
        helper.succeed();
    }

    /** After /reload the structure really has the new mob, and after undoing it and another /reload, its own mob again. */
    public static void patchesApplyOnReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshFolder();
        StructureSnapshot bastion = treasureBastion(server);
        StructureSnapshot.Spawner before = bastion == null ? null
                : bastion.spawners().stream().filter(s -> s.source() != null && s.source().template().equals(BASIN)).findFirst().orElse(null);
        if (before == null) {
            leaveFolder();
            helper.fail("no bastion layout had a spawner that knew its template");
            return;
        }
        long seed = treasureSeed;
        StructureSnapshot.Source source = before.source();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerConfig.Settings settings = ServerConfig.get();
        ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
        Component saved = JesServer.patchSpawner(player, source.template(), source.pos(), HUSK);
        ServerConfig.set(settings);
        if (!key(saved).endsWith("spawner.saved")) {
            leaveFolder();
            helper.fail("saving the patch got " + saved.getString());
            return;
        }
        CompletableFuture<Void> first = reload(server);
        AtomicReference<CompletableFuture<Void>> second = new AtomicReference<>();
        StructureSnapshot.Spawner[] seen = new StructureSnapshot.Spawner[2];
        SpawnerPatches.Patch[] again = new SpawnerPatches.Patch[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isDone(), "still reloading");
            if (second.get() == null) {
                seen[0] = at(capture(server, BASTION, seed), source);
                SpawnerPatches.remove(source.template(), source.pos());
                // Changed again before the /reload that undoes it, while the loaded template still has the husk.
                ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(0)));
                JesServer.patchSpawner(player, source.template(), source.pos(), "minecraft:zombie");
                ServerConfig.set(settings);
                again[0] = SpawnerPatches.find(source.template(), source.pos());
                SpawnerPatches.remove(source.template(), source.pos());
                second.set(reload(server));
            }
            helper.assertTrue(second.get().isDone(), "still reloading");
            if (seen[1] == null) {
                seen[1] = at(capture(server, BASTION, seed), source);
                leaveFolder();
            }
            helper.assertTrue(seen[0] != null && HUSK.equals(seen[0].mob()) && seen[0].others() == 0 && MAGMA_CUBE.equals(seen[0].source().patchedFrom()),
                    "the changed spawner wasn't used after /reload: " + seen[0]);
            helper.assertTrue(again[0] != null && MAGMA_CUBE.equals(again[0].original()),
                    "changing it again after undoing took the undone mob as its own: " + again[0]);
            helper.assertTrue(seen[1] != null && MAGMA_CUBE.equals(seen[1].mob()) && seen[1].source().patchedFrom() == null,
                    "the spawner's own mob wasn't back after undoing the change: " + seen[1]);
        });
    }

    /** A bastion layout with the treasure room, found once and then remembered. */
    private static StructureSnapshot treasureBastion(MinecraftServer server) {
        if (treasureSeed != null) {
            return capture(server, BASTION, treasureSeed);
        }
        for (int i = 0; i < TRIES; i++) {
            StructureSnapshot snapshot = capture(server, BASTION, CaptureTests.SEED + i);
            if (snapshot.spawners().stream().anyMatch(s -> s.source() != null && s.source().template().equals(BASIN))) {
                treasureSeed = CaptureTests.SEED + i;
                return snapshot;
            }
        }
        return null;
    }

    /** The mobs a spawner made from this data can go on to after it spawns. */
    private static List<String> nextMobs(CompoundTag data) {
        CompoundTag tag = data.copy();
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());
        spawner.load(tag);
        ListTag potentials = spawner.saveWithoutMetadata().getList("SpawnPotentials", Tag.TAG_COMPOUND);
        return potentials.stream().map(t -> ((CompoundTag) t).getCompound("data").getCompound("entity").getString("id")).toList();
    }

    private static StructureSnapshot capture(MinecraftServer server, ResourceLocation structure, long seed) {
        CaptureResult result = StructureCapture.capture(server, structure, seed);
        if (!result.succeeded()) {
            throw new AssertionError(structure + " did not capture: " + result.error());
        }
        return result.snapshot();
    }

    private static StructureSnapshot.Spawner at(StructureSnapshot snapshot, StructureSnapshot.Source source) {
        return snapshot.spawners().stream()
                .filter(s -> s.source() != null && s.source().template().equals(source.template()) && s.source().pos().equals(source.pos()))
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

    private static StructureTemplate.StructureBlockInfo firstSpawner(StructureTemplate template) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (SpawnerPatches.isSpawner(info)) {
                    return info;
                }
            }
        }
        return null;
    }

    private static StructureTemplate.StructureBlockInfo spawnerAt(StructureTemplate template, BlockPos pos) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.pos().equals(pos) && SpawnerPatches.isSpawner(info)) {
                    return info;
                }
            }
        }
        return null;
    }

    private static void expect(GameTestHelper helper, Component reply, String keyEnd) {
        helper.assertTrue(key(reply).endsWith(keyEnd), "expected " + keyEnd + " but got " + reply.getString());
    }

    private static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }
}
