package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Spawners in structure templates that a dev gave a different mob, or none, or made into the other
 * kind of spawner. Kept and applied the same way as {@link ContainerPatches}: each names the template
 * and the spawner's spot in it, and changes the template as it loads, never its file.
 *
 * <p>A spawner gets just the new mob's id, so it spawns with its usual gear, and loses its list of
 * mobs to cycle through. A spawner picks its next mob from that list after every spawn, so keeping it
 * would bring the old mob straight back. A trial spawner's mobs change the same way, normal and
 * ominous, keeping the rest of its settings.
 */
public final class SpawnerPatches {
    private static final PatchStore<Patch> STORE = new PatchStore<>("spawners.json", "spawner", SpawnerPatches::parse, SpawnerPatches::toJson);
    public static final ResourceLocation SPAWNER = Ids.of("minecraft", "spawner");

    /**
     * Gives the spawner at {@code pos} in {@code template} the mob {@code mob}, or none when it's
     * empty, and with {@code to}, makes it that block instead: a spawner a trial spawner, or the other
     * way round. {@code block} is what it is in the template, {@code original} the mob it had, and
     * {@code others} how many more it made.
     */
    public record Patch(ResourceLocation template, BlockPos pos, ResourceLocation block, String original, int others, String mob,
                        ResourceLocation to) implements PatchStore.Spot {
        public Patch(ResourceLocation template, BlockPos pos, ResourceLocation block, String original, int others, String mob) {
            this(template, pos, block, original, others, mob, null);
        }

        /** The block it ends up as. */
        public ResourceLocation target() {
            return to != null ? to : block;
        }
    }

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

    /** Whether a block in a template is a spawner with a mob of its own, or a trial spawner. */
    public static boolean isSpawner(StructureTemplate.StructureBlockInfo info) {
        return TrialSpawners.is(info.state())
                || info.nbt() != null && Nbt.hasCompound(info.nbt(), "SpawnData") && info.state().getBlock() instanceof SpawnerBlock;
    }

    /** Whether a spawner can be made into this block: a spawner, or a trial spawner where the game has them. */
    public static boolean canBecome(ResourceLocation block) {
        return block.equals(SPAWNER) || TrialSpawners.exist() && block.equals(TrialSpawners.BLOCK);
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

    /**
     * The mob a spawner in a template makes, or makes most for a trial spawner: "" for none, or null
     * for a trial spawner whose config can't be read.
     */
    public static String mobOf(StructureTemplate.StructureBlockInfo info, ResourceManager resources) {
        return TrialSpawners.is(info.state()) ? TrialSpawners.mainMob(info.nbt(), resources) : mobOf(info.nbt());
    }

    /** How many other mobs a spawner in a template makes besides that one. */
    public static int othersOf(StructureTemplate.StructureBlockInfo info, ResourceManager resources) {
        return TrialSpawners.is(info.state()) ? TrialSpawners.otherMobs(info.nbt(), resources) : othersOf(info.nbt());
    }

    public static ResourceLocation blockOf(StructureTemplate.StructureBlockInfo info) {
        return BuiltInRegistries.BLOCK.getKey(info.state().getBlock());
    }

    /** Reads the patches again. Templates loaded from then on use them; loaded ones are dropped on /reload. */
    public static void load() {
        STORE.load();
    }

    /** Every patch saved, in the order they were saved, whether or not changes are in use. */
    public static List<Patch> all() {
        return STORE.all();
    }

    /**
     * Each template with spawners made the other kind, as text that changes whenever they do. The loot
     * index goes by these along with changed containers, as a trial spawner drops loot when it's beaten.
     */
    public static Map<ResourceLocation, String> switchesByTemplate() {
        Map<ResourceLocation, String> out = new TreeMap<>();
        STORE.patches().forEach((template, list) -> {
            List<String> lines = new ArrayList<>();
            for (Patch patch : list) {
                if (patch.to() != null) {
                    lines.add(patch.pos().toShortString() + " " + patch.block() + " > " + patch.to());
                }
            }
            if (!lines.isEmpty()) {
                lines.sort(null);
                out.put(template, String.join("\n", lines));
            }
        });
        return out;
    }

    public static Patch find(ResourceLocation template, BlockPos pos) {
        return STORE.find(template, pos);
    }

    /**
     * Changes the patched spawners in a template that's just been loaded. {@code resources} has the
     * trial spawner configs that are named rather than written in, and may be null.
     */
    public static void apply(ResourceLocation id, StructureTemplate template, ResourceManager resources) {
        List<Patch> patches = STORE.forTemplate(id);
        if (patches == null) {
            return;
        }
        for (Patch patch : patches) {
            // Templates load again after every /reload, so each problem is only worth one warning.
            String where = id + "@" + patch.pos().toShortString();
            try {
                if (patch.to() != null && !patch.to().equals(patch.block()) && !canBecome(patch.to())) {
                    JesLog.warnOnce("spawner-patch-to:" + where, "Not changing the spawner at {} in {} into {}: it can only become a spawner, or a trial spawner from 1.21",
                            patch.pos(), id, patch.to());
                } else if (!applyOne(template, patch, resources)) {
                    JesLog.warnOnce("spawner-patch:" + where, "Not changing the spawner at {} in {}: there's no {} there any more", patch.pos(), id, patch.block());
                }
            } catch (RuntimeException e) {
                JesLog.warnOnce("spawner-patch-error:" + where, "Couldn't change the spawner at {} in {}", patch.pos(), id, e);
            }
        }
    }

    private static boolean applyOne(StructureTemplate template, Patch patch, ResourceManager resources) {
        boolean applied = false;
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            List<StructureTemplate.StructureBlockInfo> blocks = palette.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                StructureTemplate.StructureBlockInfo info = blocks.get(i);
                if (!info.pos().equals(patch.pos()) || !isSpawner(info) || !blockOf(info).equals(patch.block())) {
                    continue;
                }
                String now = mobOf(info, resources);
                if (now != null && !now.equals(patch.original()) && !now.equals(patch.mob())) {
                    JesLog.warnOnce("spawner-patch-changed:" + patch.template() + "@" + patch.pos().toShortString(),
                            "The spawner at {} in {} had its mob changed by its mod, from {} to {}; using {} as set in the browser",
                            patch.pos(), patch.template(), patch.original(), now, patch.mob());
                }
                if (!patch.target().equals(patch.block())) {
                    // The other kind of spawner, new, with just the mob.
                    boolean trial = patch.target().equals(TrialSpawners.BLOCK);
                    blocks.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), trial ? TrialSpawners.waiting() : Blocks.SPAWNER.defaultBlockState(),
                            trial ? TrialSpawners.fresh(patch.mob()) : freshSpawner(patch.mob())));
                } else if (TrialSpawners.is(info.state())) {
                    CompoundTag nbt = info.nbt() == null ? new CompoundTag() : info.nbt();
                    TrialSpawners.setMob(nbt, patch.mob(), resources);
                    if (info.nbt() == null) {
                        blocks.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), nbt));
                    }
                } else {
                    setMob(info.nbt(), patch.mob());
                }
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

    /** A new spawner's data making {@code mob}, or nothing when it's "". */
    private static CompoundTag freshSpawner(String mob) {
        CompoundTag out = new CompoundTag();
        out.putString("id", "minecraft:mob_spawner");
        setMob(out, mob);
        return out;
    }

    /** Saves a patch, replacing any for the same spawner. It applies from the next /reload. */
    public static Component save(Patch patch) {
        return STORE.save(patch);
    }

    /** Stops patching a spawner. The patch moves to the file's list of removed ones rather than going. */
    public static Component remove(ResourceLocation template, BlockPos pos) {
        return STORE.remove(template, pos);
    }

    /**
     * The patch undone for a spawner, if the template as it's loaded now still has what it made.
     * Until the next /reload a template keeps a patch that's been undone, so the spawner in it then
     * is the patch's, not its own.
     */
    private static Patch undoneHere(ResourceLocation template, BlockPos pos, StructureTemplate.StructureBlockInfo loaded, ResourceManager resources) {
        Patch undone = STORE.undone(template, pos);
        return undone != null && blockOf(loaded).equals(undone.target()) && Objects.equals(mobOf(loaded, resources), undone.mob()) ? undone : null;
    }

    /** The mob a spawner had before any patch, given the template as it's loaded now. */
    public static String ownMob(ResourceLocation template, BlockPos pos, StructureTemplate.StructureBlockInfo loaded, ResourceManager resources) {
        Patch undone = undoneHere(template, pos, loaded, resources);
        String now = mobOf(loaded, resources);
        return undone != null ? undone.original() : now == null ? "" : now;
    }

    /** How many other mobs a spawner made before any patch, in the same way. */
    public static int ownOthers(ResourceLocation template, BlockPos pos, StructureTemplate.StructureBlockInfo loaded, ResourceManager resources) {
        Patch undone = undoneHere(template, pos, loaded, resources);
        return undone != null ? undone.others() : othersOf(loaded, resources);
    }

    /** The block a spawner was before any patch, in the same way. */
    public static ResourceLocation ownBlock(ResourceLocation template, BlockPos pos, StructureTemplate.StructureBlockInfo loaded, ResourceManager resources) {
        Patch undone = undoneHere(template, pos, loaded, resources);
        return undone != null ? undone.block() : blockOf(loaded);
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
        if (patch.to() != null) {
            entry.addProperty("to", patch.to().toString());
        }
        return entry;
    }

    private static Patch parse(JsonElement element) {
        try {
            JsonObject entry = element.getAsJsonObject();
            BlockPos pos = PatchFile.pos(entry);
            ResourceLocation template = ResourceLocation.tryParse(entry.get("template").getAsString());
            ResourceLocation block = ResourceLocation.tryParse(entry.get("block").getAsString());
            ResourceLocation to = entry.has("to") ? ResourceLocation.tryParse(entry.get("to").getAsString()) : null;
            String mob = entry.get("mob").getAsString();
            if (template == null || block == null || pos == null || entry.has("to") && to == null || !mob.isEmpty() && ResourceLocation.tryParse(mob) == null) {
                return null;
            }
            return new Patch(template, pos, block, entry.has("original") ? entry.get("original").getAsString() : "",
                    entry.has("others") ? entry.get("others").getAsInt() : 0, mob, to == null || to.equals(block) ? null : to);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
