package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootIndex;
import com.finndog.justenoughstructures.loot.LootModifiers;
import com.finndog.justenoughstructures.loot.RemovedItems;
import com.finndog.justenoughstructures.loot.StructureScan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * What the game's loot modifiers name is in the loot index as soon as their tables are read, before
 * any of them is rolled: Forge's and NeoForge's own, and on Fabric Porting Lib's, as Farmer's Delight
 * bundles it in Prominence II. For every modifier that names the tables it acts on and the items or
 * loot tables it adds, each of those tables, read as the index reads it with no rolls, gives each of
 * those items and what each of those loot tables gives, unless another mod takes the item out of every
 * chest. Skipped where no modifier names both.
 *
 * <p>In a world with nothing saved, the first index the client received was sent before any table
 * was rolled, so it's checked too, for the tables structures use: that shows the server read the
 * modifiers before it sent it. A world with an index saved sends one that holds the rolls already,
 * which shows nothing about that, and the log says so.
 */
final class ModifierItemsScenario {
    private ModifierItemsScenario() {
    }

    static Director build(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new UnsupportedOperationException("needs a world of its own");
        }
        String[] result = new String[1];
        // Read now, on the client thread: what the client was first sent, and whether it was early.
        LootIndex first = ClientRequests.firstIndex();
        boolean firstEarly = ClientRequests.firstIndexEarly();
        Director d = new Director(mc);
        d.then(run(() -> server.execute(() -> result[0] = check(server, first, firstEarly))))
                .then(until(() -> result[0] != null, 20 * 60))
                .then(run(() -> {
                    if (result[0] == null) {
                        throw new IllegalStateException("The check of loot modifiers' items never ran on the server");
                    }
                    if (result[0].startsWith("skip:")) {
                        throw new UnsupportedOperationException(result[0].substring(5).trim());
                    }
                    if (result[0].startsWith("fail:")) {
                        throw new IllegalStateException(result[0].substring(5).trim());
                    }
                    JustEnoughStructures.LOGGER.info("Loot modifiers' items before any rolls: {}", result[0]);
                }));
        return d;
    }

    /** On the server thread. */
    private static String check(MinecraftServer server, LootIndex first, boolean firstEarly) {
        LootModifiers.Listing modifiers = LootModifiers.read(server);
        if (!modifiers.readable()) {
            // The loader, or Porting Lib on Fabric, has modifiers loaded and JES can't read them: a failure, not a skip.
            return "fail: this game's loot modifiers couldn't be read, so what they add is found only by rolling";
        }
        List<LootModifiers.Modifier> naming = modifiers.all().stream()
                .filter(m -> m.tables() != null && !m.tables().isEmpty() && m.json() != null && LootModifiers.listedBeforeRolls(m.json())
                        && !named(server, m).isEmpty()).toList();
        if (naming.isEmpty()) {
            return "skip: no loot modifier here names both the tables it acts on and the items or tables it adds";
        }
        Set<ResourceLocation> tables = new TreeSet<>();
        naming.forEach(m -> tables.addAll(m.tables()));
        // The tables as the index reads them, with nothing rolled.
        LootIndex read = LootIndex.of(server, new StructureScan(Map.of(Ids.parse("justenoughstructures:modifier_check"), tables), Map.of(), Map.of()));
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (LootModifiers.Modifier m : naming) {
            for (ResourceLocation table : m.tables()) {
                Set<ResourceLocation> given = read.itemsByTable().getOrDefault(table, Set.of());
                for (ResourceLocation item : named(server, m)) {
                    if (RemovedItems.removed(item)) {
                        continue;
                    }
                    checked++;
                    if (!given.contains(item)) {
                        missing.add(m.id() + " names " + item + " for " + table);
                    }
                }
            }
        }
        if (!missing.isEmpty()) {
            return "fail: " + missing.size() + " of " + checked + " items loot modifiers name aren't listed for their tables before any rolls: "
                    + missing.subList(0, Math.min(missing.size(), 20));
        }
        String sent;
        if (first == null) {
            sent = "no index was sent to this client yet";
        } else if (!firstEarly) {
            sent = "the first index sent held the rolls already, as this world had one saved, so it shows nothing of the order";
        } else {
            List<String> notSent = new ArrayList<>();
            int inSent = 0;
            for (LootModifiers.Modifier m : naming) {
                for (ResourceLocation table : m.tables()) {
                    Set<ResourceLocation> given = first.itemsByTable().get(table);
                    if (given == null) {
                        continue;
                    }
                    for (ResourceLocation item : named(server, m)) {
                        if (RemovedItems.removed(item)) {
                            continue;
                        }
                        inSent++;
                        if (!given.contains(item)) {
                            notSent.add(m.id() + " names " + item + " for " + table);
                        }
                    }
                }
            }
            if (!notSent.isEmpty()) {
                return "fail: the first index this client was sent, before any rolls, lacks " + notSent.size() + " of " + inSent
                        + " items loot modifiers name: " + notSent.subList(0, Math.min(notSent.size(), 20));
            }
            sent = inSent == 0 ? "no structure uses a table a modifier names, so the first index sent had none of them to show"
                    : "the first index sent, before any rolls, had all " + inSent + " of them for the tables structures use";
        }
        return naming.size() + " modifiers on " + tables.size() + " tables, " + checked + " items, all listed; " + sent;
    }

    /** The items a modifier names, and what each loot table it names gives. */
    private static Set<ResourceLocation> named(MinecraftServer server, LootModifiers.Modifier m) {
        Set<ResourceLocation> out = new TreeSet<>(m.items());
        for (ResourceLocation table : m.namedTables()) {
            out.addAll(LootIndex.itemsIn(server, table, new HashSet<>()));
        }
        return out;
    }
}
