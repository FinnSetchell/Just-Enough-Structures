package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.ServerConfig;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.WorldData;

/** What the game tests and scenarios share. */
public final class TestSupport {
    /** A loot table that gives one diamond and nothing else. */
    public static final String DIAMONDS_ONLY = """
            {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:diamond"}]}]}
            """;

    private TestSupport() {
    }

    /**
     * The server's usual settings, with Pack tools open to players of {@code level}: 0 lets everyone
     * use it, 4 only operators, and a test's mock player isn't one.
     */
    public static ServerConfig.Settings packToolsFor(int level) {
        return packToolsFor(level, true);
    }

    /** The same, with changes to containers and spawners used or not. */
    public static ServerConfig.Settings packToolsFor(int level, boolean containerChanges) {
        return new ServerConfig.Settings(Set.of(), Set.of(), 2, 2, true, ServerConfig.PackTools.level(level), containerChanges);
    }

    /** A translated message's key, or what anything else says. */
    public static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }

    public static void expect(GameTestHelper helper, Component reply, String keyEnd) {
        helper.assertTrue(key(reply).endsWith(keyEnd), "expected " + keyEnd + " but got " + reply.getString());
    }

    /**
     * Lists every pack the world isn't using as left out, as each reload does, so a /reload turns
     * none of them on. Forge's game test world doesn't until its first reload, so a test's /reload
     * that's the first would turn on the experimental packs, above JES's loot overrides. The overrides
     * pack is never left out, as a test's first edit makes it.
     */
    public static void leaveOutUnusedPacks(MinecraftServer server) {
        PackRepository packs = server.getPackRepository();
        List<String> used = List.copyOf(packs.getSelectedIds());
        List<String> unused = packs.getAvailableIds().stream().filter(id -> !used.contains(id) && !id.equals(LootOverrides.PACK_ID)).toList();
        WorldData world = server.getWorldData();
        world.setDataConfiguration(new WorldDataConfiguration(new DataPackConfig(used, unused), world.enabledFeatures()));
    }

    /** What /reload does: look for new datapacks, then reload with them. */
    public static CompletableFuture<Void> reload(MinecraftServer server) {
        server.getPackRepository().reload();
        return server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    /** Captures {@code structure} with {@code seed}, failing the test if it doesn't capture. */
    public static StructureSnapshot capture(MinecraftServer server, ResourceLocation structure, long seed) {
        CaptureResult result = StructureCapture.capture(server, structure, seed);
        if (!result.succeeded()) {
            throw new AssertionError(structure + " did not capture: " + result.error());
        }
        return result.snapshot();
    }

    public static StructureTemplate copy(StructureTemplate template) {
        StructureTemplate copy = new StructureTemplate();
        // Saving hands over the template's own block entity tags, so they're copied to keep the two apart.
        copy.load(Regs.getter(BuiltInRegistries.BLOCK), template.save(new CompoundTag()).copy());
        return copy;
    }

    /** The first block in any of a template's palettes that {@code which} picks, or null. */
    public static StructureTemplate.StructureBlockInfo firstBlock(StructureTemplate template, Predicate<StructureTemplate.StructureBlockInfo> which) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (which.test(info)) {
                    return info;
                }
            }
        }
        return null;
    }
}
