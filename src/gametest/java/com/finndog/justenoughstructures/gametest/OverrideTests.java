package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.OverridePack;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.Items;

/** Loot tables edited in the browser, saved as overrides. Every test works in a folder of its own. */
public final class OverrideTests {
    private static final ResourceLocation IGLOO = new ResourceLocation("chests/igloo_chest");
    private static final String DIAMONDS_ONLY = """
            {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:diamond"}]}]}
            """;

    private OverrideTests() {
    }

    private static Path freshFolder() {
        try {
            Path dir = Files.createTempDirectory("jes-overrides");
            LootOverrides.setFolder(dir);
            return dir;
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
    }

    /** A saved edit is what the editor gets back, next to the table it replaced. */
    public static void editsSaveAndReadBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.View before = LootOverrides.view(server.getResourceManager(), IGLOO);
            helper.assertTrue(before.status() == LootOverrides.Status.NONE && before.original() != null && before.original().equals(before.current()),
                    "an untouched table should read as the vanilla one, got " + before.status());

            Component saved = LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            helper.assertTrue(key(saved).endsWith("override.saved"), "saving got " + saved.getString());
            helper.assertTrue(Files.exists(dir.resolve("pack.mcmeta")), "the folder wasn't made a datapack");

            LootOverrides.View after = LootOverrides.view(server.getResourceManager(), IGLOO);
            helper.assertTrue(after.status() == LootOverrides.Status.ACTIVE, "a fresh edit should be active, got " + after.status());
            helper.assertTrue(after.current().contains("minecraft:diamond"), "the edit didn't read back");
            helper.assertTrue(after.original().equals(before.original()), "the original changed when the edit was saved");
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** Edits the game couldn't load are never saved. */
    public static void brokenEditsAreRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            helper.assertTrue(key(LootOverrides.save(server.getResourceManager(), IGLOO, "{ not json")).endsWith("invalid_json"),
                    "broken JSON wasn't refused");
            String unknownEntry = "{\"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:nonsense\"}]}]}";
            helper.assertTrue(key(LootOverrides.save(server.getResourceManager(), IGLOO, unknownEntry)).endsWith("invalid_table"),
                    "a table with an unknown entry type wasn't refused");
            helper.assertFalse(Files.exists(dir.resolve("data")), "something was saved anyway");
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** An edit is flagged when the table it was made from changes or goes, and keeping it clears the flag. */
    public static void editsNoticeTheirOriginalChanging(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            // As if the mod had updated the table since: the remembered original no longer matches.
            Path meta = dir.resolve("overrides.json");
            JsonObject json = JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
            json.getAsJsonObject(IGLOO.toString()).addProperty("base", "not what it was");
            Files.writeString(meta, json.toString());
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), IGLOO).status() == LootOverrides.Status.ORIGINAL_CHANGED,
                    "a changed original wasn't noticed");

            helper.assertTrue(key(LootOverrides.keep(server.getResourceManager(), IGLOO)).endsWith("override.kept"), "keeping the edit failed");
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), IGLOO).status() == LootOverrides.Status.ACTIVE,
                    "keeping the edit didn't clear the flag");

            ResourceLocation gone = new ResourceLocation("justenoughstructures", "chests/nothing_here");
            LootOverrides.save(server.getResourceManager(), gone, DIAMONDS_ONLY);
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), gone).status() == LootOverrides.Status.ORIGINAL_MISSING,
                    "an edit with no original wasn't noticed");
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** Removing an edit keeps a copy of it. A dev's work is never deleted. */
    public static void removedEditsAreKept(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            helper.assertTrue(key(LootOverrides.remove(IGLOO)).endsWith("override.removed"), "removing failed");
            helper.assertTrue(LootOverrides.view(server.getResourceManager(), IGLOO).status() == LootOverrides.Status.NONE,
                    "the table is still overridden");
            List<Path> kept = new ArrayList<>();
            try (Stream<Path> files = Files.walk(dir.resolve("removed"))) {
                files.filter(p -> p.toString().endsWith("igloo_chest.json")).forEach(kept::add);
            }
            helper.assertTrue(kept.size() == 1 && Files.readString(kept.get(0)).contains("minecraft:diamond"), "no copy of the removed edit was kept");
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** An override broken by hand is left out of the pack, so the mod's own table is used instead. */
    public static void brokenOverridesAreLeftOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path dir = freshFolder();
        try {
            LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
            Path broken = dir.resolve("data/minecraft/loot_tables/chests/shipwreck_map.json");
            Files.createDirectories(broken.getParent());
            Files.writeString(broken, "{ this was edited by hand and broke");
            List<Pack> packs = new ArrayList<>();
            new OverridePack().loadPacks(packs::add);
            helper.assertTrue(packs.size() == 1, "the override folder wasn't offered as a datapack");
            try (PackResources resources = packs.get(0).open()) {
                helper.assertTrue(resources.getResource(PackType.SERVER_DATA, new ResourceLocation("loot_tables/chests/igloo_chest.json")) != null,
                        "a good override was left out");
                helper.assertTrue(resources.getResource(PackType.SERVER_DATA, new ResourceLocation("loot_tables/chests/shipwreck_map.json")) == null,
                        "a broken override was let through");
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            LootOverrides.setFolder(null);
        }
        helper.succeed();
    }

    /** The editor's preview rolls the edit itself, and only for players allowed to edit. */
    public static void draftsRollBeforeSaving(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, 4));
            JesServer.DraftOdds refused = JesServer.draftOdds(player, IGLOO, DIAMONDS_ONLY);
            helper.assertTrue(refused.odds() == null && key(refused.problem()).endsWith("no_permission"), "a player who can't edit got odds");

            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, 0));
            JesServer.DraftOdds rolled = JesServer.draftOdds(player, IGLOO, DIAMONDS_ONLY);
            LootOdds odds = rolled.odds();
            helper.assertTrue(odds != null && odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.DIAMOND)
                    && odds.rows().get(0).hits() == odds.rolls(), "the draft didn't roll a diamond every time: " + rolled.problem());
            JesServer.DraftOdds broken = JesServer.draftOdds(player, IGLOO, "{ not json");
            helper.assertTrue(broken.odds() == null && key(broken.problem()).endsWith("invalid_json"), "a broken draft was rolled");
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    /** After /reload the game really uses the edit, and after removing it and another /reload, the original again. */
    public static void editsApplyOnReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        freshFolder();
        LootOverrides.save(server.getResourceManager(), IGLOO, DIAMONDS_ONLY);
        CompletableFuture<Void> first = reload(server);
        AtomicReference<CompletableFuture<Void>> second = new AtomicReference<>();
        boolean[] applied = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isDone(), "still reloading");
            if (second.get() == null) {
                applied[0] = onlyDiamonds(LootRolls.odds(helper.getLevel(), IGLOO, 50, 1L));
                LootOverrides.remove(IGLOO);
                second.set(reload(server));
            }
            helper.assertTrue(second.get().isDone(), "still reloading");
            LootOverrides.setFolder(null);
            helper.assertTrue(applied[0], "the edited table wasn't used after /reload");
            helper.assertFalse(onlyDiamonds(LootRolls.odds(helper.getLevel(), IGLOO, 50, 1L)), "the original wasn't back after removing the edit");
        });
    }

    /** What /reload does: look for new datapacks, then reload with them. */
    private static CompletableFuture<Void> reload(MinecraftServer server) {
        server.getPackRepository().reload();
        return server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    private static boolean onlyDiamonds(LootOdds odds) {
        return odds.rows().size() == 1 && odds.rows().get(0).example().is(Items.DIAMOND);
    }

    private static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }
}
