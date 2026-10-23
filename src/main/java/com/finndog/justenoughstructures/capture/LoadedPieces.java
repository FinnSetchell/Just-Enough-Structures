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
 * Limits how many structure pieces stay loaded because of JES. The game keeps every piece it loads
 * until the world closes. That's fine for normal generation, but JES builds every structure (for
 * list pictures and the loot index) and reads every piece a pool names, which would keep every piece
 * of every structure mod in memory for the whole session.
 *
 * <p>Pieces loaded during JES work are recorded. Once they add up to more than {@link #room} blocks,
 * the oldest are evicted. The newest stay because the next job often needs them: villages share
 * pieces, and a new layout of a structure reuses the last layout's pieces.
 *
 * <p>A piece is only evicted if JES loaded it and the game still holds that same, unchanged
 * instance. Pieces loaded by the world, reloaded after /reload, or saved over by a structure block
 * (which may exist only in memory) are never evicted. An evicted piece loads again when next needed.
 */
public final class LoadedPieces {
    /**
     * Default eviction limit in blocks, counting air and every palette of a piece. A block is about
     * 55 bytes, so 4 million is about 220 MB.
     */
    private static final long USUAL_ROOM = 4_000_000;
    private static long room = USUAL_ROOM;
    /** Nesting depth of JES work on this thread. A piece loaded at depth 0 was loaded by the world. */
    private static final ThreadLocal<int[]> AT_WORK = ThreadLocal.withInitial(() -> new int[1]);
    /** Pieces JES loaded and hasn't evicted yet, oldest first. */
    private static final Map<ResourceLocation, Held> HELD = new LinkedHashMap<>();
    private static long held;
    /**
     * The template manager the recorded pieces belong to. When it changes, another world has loaded
     * and the old records are dropped.
     */
    private static WeakReference<StructureTemplateManager> holder = new WeakReference<>(null);

    /**
     * A loaded piece's block count, with weak references to the piece and its first palette, so the
     * game can still unload it normally.
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
            // Never throw while the game loads a piece. A piece that can't be measured is never evicted.
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

    /** Runs {@code work}, recording the pieces the game loads for it, then evicts the oldest if there are too many. */
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
     * Ends a stretch of JES work. Eviction waits until the outermost stretch ends, or a structure
     * bigger than the limit would load its pieces over and over while it's built.
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
        // Evict outside the HELD lock. The game holds the template map's lock while it loads a piece,
        // and loading calls loaded(), which takes HELD. Taking them in the opposite order could
        // deadlock a capture ending while the loot index loads a piece.
        try {
            Map<ResourceLocation, Optional<StructureTemplate>> loaded = ((StructureTemplateManagerAccessor) templates).justenoughstructures$loaded();
            for (Map.Entry<ResourceLocation, Held> entry : going) {
                Held note = entry.getValue();
                StructureTemplate piece = note.piece().get();
                Optional<StructureTemplate> now = piece == null ? null : loaded.get(entry.getKey());
                if (now != null && now.orElse(null) == piece && asLoaded(piece, note)) {
                    // remove(key, value) only removes it if the game hasn't replaced it since the check.
                    loaded.remove(entry.getKey(), now);
                }
            }
        } catch (RuntimeException e) {
            // Another mod's template map doesn't support removal. The pieces stay loaded.
            JesLog.debug("Couldn't let go of loaded structure pieces", e);
        }
    }

    /** Whether a piece still has the blocks it was loaded with. A structure block saving over it gives it new ones. */
    private static boolean asLoaded(StructureTemplate piece, Held note) {
        try {
            List<StructureTemplate.Palette> sets = ((StructureTemplateAccessor) piece).justenoughstructures$palettes();
            return (sets.isEmpty() ? null : sets.get(0)) == note.blocks().get();
        } catch (RuntimeException e) {
            // Probably being saved over right now.
            return false;
        }
    }

    /** Sets the eviction limit in blocks, for tests, and returns the old one. A negative value restores the default. */
    public static long leaveLoaded(long blocks) {
        synchronized (HELD) {
            long was = room;
            room = blocks < 0 ? USUAL_ROOM : blocks;
            return was;
        }
    }
}
