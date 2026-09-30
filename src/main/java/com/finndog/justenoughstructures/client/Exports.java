package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Getting a previewed structure out of the game: a material list or a structure file. */
public final class Exports {
    private Exports() {
    }

    private static WeakReference<StructureSnapshot> countedFor = new WeakReference<>(null);
    private static List<Map.Entry<Block, Integer>> counted;

    /** How many of each block the structure uses, most first. Kept for the last snapshot asked about. */
    public static List<Map.Entry<Block, Integer>> blockCounts(StructureSnapshot s) {
        if (s != countedFor.get()) {
            counted = count(s);
            countedFor = new WeakReference<>(s);
        }
        return counted;
    }

    private static List<Map.Entry<Block, Integer>> count(StructureSnapshot s) {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < s.blockCount(); i++) {
            counts.merge(s.state(i).getBlock(), 1, Integer::sum);
        }
        List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted;
    }

    public static Component copyMaterialList(ResourceLocation id, StructureSnapshot s) {
        StringBuilder text = new StringBuilder(id.toString()).append('\n');
        for (Map.Entry<Block, Integer> e : blockCounts(s)) {
            text.append(e.getValue()).append(" x ").append(e.getKey().getName().getString()).append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
        return Component.translatable("screen.justenoughstructures.copied");
    }

    /**
     * Writes the snapshot as a vanilla structure file (the format structure blocks save), which
     * structure blocks, Litematica and most structure tools can open.
     */
    public static Component saveStructure(ResourceLocation id, StructureSnapshot s) {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.put("size", ints(s.size().getX(), s.size().getY(), s.size().getZ()));

        ListTag palette = new ListTag();
        for (BlockState state : s.palette()) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        root.put("palette", palette);

        Map<Integer, CompoundTag> blockEntities = new LinkedHashMap<>();
        for (CompoundTag tag : s.blockEntities()) {
            CompoundTag copy = tag.copy();
            int packed = StructureSnapshot.pack(copy.getInt("x"), copy.getInt("y"), copy.getInt("z"));
            copy.remove("x");
            copy.remove("y");
            copy.remove("z");
            blockEntities.put(packed, copy);
        }
        ListTag blocks = new ListTag();
        for (int i = 0; i < s.blockCount(); i++) {
            int packed = s.packedPosition(i);
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(StructureSnapshot.unpackX(packed), StructureSnapshot.unpackY(packed), StructureSnapshot.unpackZ(packed)));
            block.putInt("state", s.paletteIndex(i));
            CompoundTag nbt = blockEntities.get(packed);
            if (nbt != null) {
                block.put("nbt", nbt);
            }
            blocks.add(block);
        }
        root.put("blocks", blocks);

        ListTag entities = new ListTag();
        for (CompoundTag tag : s.entities()) {
            ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() != 3) {
                continue;
            }
            CompoundTag entity = new CompoundTag();
            ListTag at = new ListTag();
            for (int i = 0; i < 3; i++) {
                at.add(DoubleTag.valueOf(pos.getDouble(i)));
            }
            entity.put("pos", at);
            BlockPos block = BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            entity.put("blockPos", ints(block.getX(), block.getY(), block.getZ()));
            CompoundTag nbt = tag.copy();
            nbt.remove("UUID");
            entity.put("nbt", nbt);
            entities.add(entity);
        }
        root.put("entities", entities);

        String name = (id.getNamespace() + "_" + id.getPath().replace('/', '_') + "_" + Long.toHexString(s.seed())).toLowerCase(Locale.ROOT);
        File folder = new File(Minecraft.getInstance().gameDirectory, "jes_exports");
        File file = new File(folder, name + ".nbt");
        try {
            folder.mkdirs();
            NbtIo.writeCompressed(root, file);
            return Component.translatable("screen.justenoughstructures.saved", "jes_exports/" + file.getName());
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.error("Couldn't save {}", file, e);
            return Component.translatable("screen.justenoughstructures.save_failed", e.getMessage());
        }
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int v : values) {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
