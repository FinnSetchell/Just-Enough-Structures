package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.server.ServerConfig;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

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

    /** What /reload does: look for new datapacks, then reload with them. */
    public static CompletableFuture<Void> reload(MinecraftServer server) {
        server.getPackRepository().reload();
        return server.reloadResources(server.getPackRepository().getSelectedIds());
    }

    /** A structure as {@code seed} lays it out, which has to capture. */
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
