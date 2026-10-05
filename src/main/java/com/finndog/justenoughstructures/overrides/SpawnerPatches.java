package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Spawners in structure templates that a dev gave a different mob, or none. Kept and applied the
 * same way as {@link ContainerPatches}: each names the template and the spawner's spot in it, and
 * changes the template as it loads, never its file.
 *
 * <p>The spawner gets just the new mob's id, so it spawns with its usual gear, and loses its list
 * of mobs to cycle through. A spawner picks its next mob from that list after every spawn, so
 * keeping it would bring the old mob straight back.
 */
public final class SpawnerPatches {
    private static final PatchFile FILE = new PatchFile("spawners.json");

    /**
     * Gives the spawner at {@code pos} in {@code template} the mob {@code mob}, or none when it's
     * empty. {@code original} is the mob it had, and {@code others} how many more it cycled through.
     */
    public record Patch(ResourceLocation template, BlockPos pos, ResourceLocation block, String original, int others, String mob) {
    }

    private static volatile Map<ResourceLocation, List<Patch>> byTemplate;
    /** Patches undone, by template and spot, for {@link #ownMob}. */
    private static final Map<List<Object>, Patch> UNDONE = new ConcurrentHashMap<>();

    private SpawnerPatches() {
    }

    /** Whether a spawner can be given this kind of mob: something living that can be summoned, but not an armor stand. */
    public static boolean spawnable(EntityType<?> type) {
        try {
            return type.canSummon() && type != EntityType.ARMOR_STAND && DefaultAttributes.hasSupplier(type);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether a block in a template is a spawner with a mob of its own. */
    public static boolean isSpawner(StructureTemplate.StructureBlockInfo info) {
        return info.nbt() != null && Nbt.hasCompound(info.nbt(), "SpawnData") && info.state().getBlock() instanceof SpawnerBlock;
    }

    /** The mob a spawner's data gives it, or "" for none. */
    public static String mobOf(CompoundTag spawner) {
        return Nbt.string(Nbt.compound(Nbt.compound(spawner, "SpawnData"), "entity"), "id");
    }

    /** How many other mobs a spawner's data cycles through besides its own. */
    public static int othersOf(CompoundTag spawner) {
        Set<String> mobs = new LinkedHashSet<>();
        for (Tag entry : Nbt.list(spawner, "SpawnPotentials", Tag.TAG_COMPOUND)) {
            String id = Nbt.string(Nbt.compound(Nbt.compound((CompoundTag) entry, "data"), "entity"), "id");
            if (!id.isEmpty()) {
                mobs.add(id);
            }
        }
        mobs.remove(mobOf(spawner));
        return mobs.size();
    }

    private static Map<ResourceLocation, List<Patch>> patches() {
        if (!ServerConfig.get().containerChanges()) {
            return Map.of();
        }
        Map<ResourceLocation, List<Patch>> loaded = byTemplate;
        if (loaded == null) {
            load();
            loaded = byTemplate;
        }
        return loaded;
    }

    /** Reads the patches again. Templates loaded from then on use them; loaded ones are dropped on /reload. */
    public static synchronized void load() {
        Map<ResourceLocation, List<Patch>> out = new HashMap<>();
        try {
            for (JsonElement element : FILE.entries()) {
                Patch patch = parse(element);
                if (patch != null) {
                    out.computeIfAbsent(patch.template(), id -> new ArrayList<>()).add(patch);
                }
            }
        } catch (IOException | RuntimeException e) {
            Path file = FILE.path();
            JesLog.warnOnce("spawner-patches:" + file + "|" + e.getMessage(), "Couldn't read the spawner patches in {}, leaving spawners as they are: {}",
                    file, e.toString());
            JesLog.debug("Couldn't read the spawner patches in {}", file, e);
        }
        byTemplate = out;
    }

    /** Every patch saved, in the order they were saved, whether or not changes are in use. */
    public static synchronized List<Patch> all() {
        List<Patch> out = new ArrayList<>();
        try {
            for (JsonElement element : FILE.entries()) {
                Patch patch = parse(element);
                if (patch != null) {
                    out.add(patch);
                }
            }
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the spawner patches in {}", FILE.path(), e);
        }
        return out;
    }

    public static Patch find(ResourceLocation template, BlockPos pos) {
        for (Patch patch : patches().getOrDefault(template, List.of())) {
            if (patch.pos().equals(pos)) {
                return patch;
            }
        }
        return null;
    }

    /** Changes the mobs of patched spawners in a template that's just been loaded. */
    public static void apply(ResourceLocation id, StructureTemplate template) {
        List<Patch> patches;
        try {
            patches = patches().get(id);
        } catch (RuntimeException e) {
            return;
        }
        if (patches == null) {
            return;
        }
        for (Patch patch : patches) {
            try {
                // Templates load again after every /reload, so each problem is only worth one warning.
                if (!applyOne(template, patch)) {
                    JesLog.warnOnce("spawner-patch:" + id + "@" + patch.pos().toShortString(),
                            "Not changing the spawner at {} in {}: there's no {} with a mob there any more", patch.pos(), id, patch.block());
                }
            } catch (RuntimeException e) {
                JesLog.warnOnce("spawner-patch-error:" + id + "@" + patch.pos().toShortString(), "Couldn't change the spawner at {} in {}", patch.pos(), id, e);
            }
        }
    }

    private static boolean applyOne(StructureTemplate template, Patch patch) {
        boolean applied = false;
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (!info.pos().equals(patch.pos()) || !isSpawner(info) || !BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).equals(patch.block())) {
                    continue;
                }
                String now = mobOf(info.nbt());
                if (!now.equals(patch.original()) && !now.equals(patch.mob())) {
                    JesLog.warnOnce("spawner-patch-changed:" + patch.template() + "@" + patch.pos().toShortString(),
                            "The spawner at {} in {} had its mob changed by its mod, from {} to {}; using {} as set in the browser",
                            patch.pos(), patch.template(), patch.original(), now, patch.mob());
                }
                setMob(info.nbt(), patch.mob());
                applied = true;
            }
        }
        return applied;
    }

    /**
     * Gives a spawner's data just this mob, or none, keeping any rules it has about light. Without
     * its list of mobs, the spawner makes one from the mob it's given.
     */
    static void setMob(CompoundTag spawner, String mob) {
        CompoundTag data = Nbt.compound(spawner, "SpawnData");
        CompoundTag entity = new CompoundTag();
        if (!mob.isEmpty()) {
            entity.putString("id", mob);
        }
        data.put("entity", entity);
        spawner.put("SpawnData", data);
        spawner.remove("SpawnPotentials");
    }

    /** Saves a patch, replacing any for the same spawner. It applies from the next /reload. */
    public static synchronized Component save(Patch patch) {
        try {
            FILE.save(patch.template(), patch.pos(), toJson(patch));
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save the spawner patch for {} in {}: {}", patch.pos(), patch.template(), e.toString());
            JesLog.debug("Couldn't save the spawner patch for {} in {}", patch.pos(), patch.template(), e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        load();
        return Component.translatable("screen.justenoughstructures.spawner.saved");
    }

    /** Stops patching a spawner. The patch moves to the file's list of removed ones rather than going. */
    public static synchronized Component remove(ResourceLocation template, BlockPos pos) {
        Patch removing = null;
        for (Patch patch : all()) {
            if (patch.template().equals(template) && patch.pos().equals(pos)) {
                removing = patch;
            }
        }
        try {
            if (!FILE.remove(template, pos)) {
                return Component.translatable("screen.justenoughstructures.spawner.none");
            }
        } catch (IOException | RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        if (removing != null) {
            UNDONE.put(List.of(template, pos), removing);
        }
        load();
        return Component.translatable("screen.justenoughstructures.spawner.removed");
    }

    /**
     * The mob a spawner had before any patch, given the template as it's loaded now. Until the next
     * /reload, a template keeps a patch that's been undone, so its mob then is the patch's, not its own.
     */
    public static String ownMob(ResourceLocation template, BlockPos pos, CompoundTag loaded) {
        Patch undone = UNDONE.get(List.of(template, pos));
        String now = mobOf(loaded);
        return undone != null && now.equals(undone.mob()) ? undone.original() : now;
    }

    /** How many other mobs a spawner cycled through before any patch, in the same way. */
    public static int ownOthers(ResourceLocation template, BlockPos pos, CompoundTag loaded) {
        Patch undone = UNDONE.get(List.of(template, pos));
        return undone != null && mobOf(loaded).equals(undone.mob()) ? undone.others() : othersOf(loaded);
    }

    private static JsonObject toJson(Patch patch) {
        JsonObject entry = new JsonObject();
        entry.addProperty("template", patch.template().toString());
        entry.add("pos", PatchFile.pos(patch.pos()));
        entry.addProperty("block", patch.block().toString());
        entry.addProperty("original", patch.original());
        if (patch.others() > 0) {
            entry.addProperty("others", patch.others());
        }
        entry.addProperty("mob", patch.mob());
        return entry;
    }

    private static Patch parse(JsonElement element) {
        try {
            JsonObject entry = element.getAsJsonObject();
            BlockPos pos = PatchFile.pos(entry);
            ResourceLocation template = ResourceLocation.tryParse(entry.get("template").getAsString());
            ResourceLocation block = ResourceLocation.tryParse(entry.get("block").getAsString());
            String mob = entry.get("mob").getAsString();
            if (template == null || block == null || pos == null || !mob.isEmpty() && ResourceLocation.tryParse(mob) == null) {
                return null;
            }
            return new Patch(template, pos, block, entry.has("original") ? entry.get("original").getAsString() : "",
                    entry.has("others") ? entry.get("others").getAsInt() : 0, mob);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
