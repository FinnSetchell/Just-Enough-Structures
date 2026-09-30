package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Getting a previewed structure out of the game as a material list. */
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
}
