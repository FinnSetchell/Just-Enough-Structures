package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.mixin.StructureTemplateManagerAccessor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Keeps down how many saved pieces the game holds on this mod's account. The game holds on to every
 * piece it loads for as long as the world is open, which is no trouble while it only loads what
 * generates near players. This mod builds every structure there is, for the list's pictures and the
 * loot index, and reads every piece a pool names: left alone, that's every piece of every structure
 * mod held in memory for the rest of the session.
 *
 * <p>So the pieces the game loads while this mod is at work are noted, and once they come to more
 * than {@link #room}, the ones loaded longest ago are forgotten again. The most recent stay, as what
 * comes next often wants the same ones: villages share pieces, and a new layout of a previewed
 * structure uses the pieces of the last. Forgetting them all at once made the loot index take half
 * as long again.
 *
 * <p>Only a piece loaded on this mod's account is ever forgotten, and only while the game still holds
 * that very one, as it was loaded. One the game had already was loaded for the world. One it has
 * loaded again since, after a /reload, is its own. And one a structure block has saved over is the
 * player's work, which may be nowhere but in memory. All of those stay. A forgotten piece is simply
 * loaded again the next time anything wants it.
 */
public final class LoadedPieces {
    /**
     * How much is left loaded, in blocks: every block a piece holds, air too, and in each of its sets
     * of blocks where it has several. A block is about 55 bytes, so four million is some 220 MB.
     */
    private static final long USUAL_ROOM = 4_000_000;
    private static long room = USUAL_ROOM;
    /** How deep in this mod's own work this thread is. A piece loaded at nought was loaded for the world. */
    private static final ThreadLocal<int[]> AT_WORK = ThreadLocal.withInitial(() -> new int[1]);
    /** The pieces loaded on this mod's account that haven't been let go of yet, oldest first. */
    private static final Map<ResourceLocation, Held> HELD = new LinkedHashMap<>();
    private static long held;
    /** Whose pieces those are. Another world's are another game's to hold. */
    private static WeakReference<StructureTemplateManager> holder = new WeakReference<>(null);

    /**
     * A piece as it was loaded: how many blocks, which piece it was, and the first set of blocks it
     * had. Neither is held on to here, so a piece the game lets go of by itself is gone.
     */
    private record Held(long size, WeakReference<StructureTemplate> piece, WeakReference<StructureTemplate.Palette> blocks) {
    }

    private LoadedPieces() {
    }

    /** Called as the game loads a piece it didn't have. */
    public static void loaded(StructureTemplateManager templates, ResourceLocation id, StructureTemplate piece) {
        if (AT_WORK.get()[0] == 0) {
            return;
        }
        Held note;
        try {
            List<StructureTemplate.Palette> sets = ((StructureTemplateAccessor) piece).justenoughstructures$palettes();
            long size = 0;
            for (StructureTemplate.Palette blocks : sets) {
                size += blocks.blocks().size();
            }
            note = new Held(Math.max(1, size), new WeakReference<>(piece), new WeakReference<>(sets.isEmpty() ? null : sets.get(0)));
        } catch (RuntimeException e) {
            // This is the game loading a piece. One that can't be noted is just never forgotten.
            return;
        }
        synchronized (HELD) {
            if (holder.get() != templates) {
                // Another world. The last one's pieces went with it.
                HELD.clear();
                held = 0;
                holder = new WeakReference<>(templates);
            }
            Held before = HELD.remove(id);
            held += note.size() - (before == null ? 0 : before.size());
            HELD.put(id, note);
        }
    }

    /** Runs {@code work} with the pieces the game loads for it noted, then lets go of the oldest if there are too many. */
    public static <T> T during(MinecraftServer server, Supplier<T> work) {
        begin();
        try {
            return work.get();
        } finally {
            end(server);
        }
    }

    static void begin() {
        AT_WORK.get()[0]++;
    }

    /**
     * Ends a stretch of this mod's work. Nothing is forgotten in the middle of one, as a structure
     * bigger than the room there is would have its own pieces loaded over and over while it's built.
     */
    static void end(MinecraftServer server) {
        int[] depth = AT_WORK.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
        if (depth[0] > 0) {
            return;
        }
        StructureTemplateManager templates = server.getStructureManager();
        List<Map.Entry<ResourceLocation, Held>> going = null;
        synchronized (HELD) {
            if (holder.get() != templates) {
                return;
            }
            Iterator<Map.Entry<ResourceLocation, Held>> oldest = HELD.entrySet().iterator();
            while (held > room && oldest.hasNext()) {
                Map.Entry<ResourceLocation, Held> next = oldest.next();
                if (going == null) {
                    going = new ArrayList<>();
                }
                going.add(Map.entry(next.getKey(), next.getValue()));
                held -= next.getValue().size();
                oldest.remove();
            }
        }
        if (going == null) {
            return;
        }
        // Only now that the notes are let go of. The game keeps its pieces locked while it loads one,
        // and loading one makes a note here. Were the two taken the other way round here as well, a
        // capture ending as the loot index loaded a piece would leave each waiting on the other for good.
        try {
            Map<ResourceLocation, Optional<StructureTemplate>> loaded = ((StructureTemplateManagerAccessor) templates).justenoughstructures$loaded();
            for (Map.Entry<ResourceLocation, Held> entry : going) {
                Held note = entry.getValue();
                StructureTemplate piece = note.piece().get();
                Optional<StructureTemplate> now = piece == null ? null : loaded.get(entry.getKey());
                if (now != null && now.orElse(null) == piece && asLoaded(piece, note)) {
                    // And only if it's still that one the game holds, should it be swapped this instant.
                    loaded.remove(entry.getKey(), now);
                }
            }
        } catch (RuntimeException e) {
            // Another mod's way of keeping pieces that won't have one taken out. They stay, as they always did.
            JesLog.debug("Couldn't let go of loaded structure pieces", e);
        }
    }

    /** Whether a piece still has the blocks it was loaded with. A structure block saving over it gives it new ones. */
    private static boolean asLoaded(StructureTemplate piece, Held note) {
        try {
            List<StructureTemplate.Palette> sets = ((StructureTemplateAccessor) piece).justenoughstructures$palettes();
            return (sets.isEmpty() ? null : sets.get(0)) == note.blocks().get();
        } catch (RuntimeException e) {
            // Being saved over as this looks.
            return false;
        }
    }

    /** Sets how much is left loaded, for the tests, and says what it was. Less than nought puts it back as it usually is. */
    public static long leaveLoaded(long blocks) {
        synchronized (HELD) {
            long was = room;
            room = blocks < 0 ? USUAL_ROOM : blocks;
            return was;
        }
    }
}
