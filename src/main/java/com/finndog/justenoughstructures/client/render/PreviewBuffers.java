package com.finndog.justenoughstructures.client.render;

//? if <26.1 {
import com.finndog.justenoughstructures.JesLog;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
//? if >=1.21 {
/*import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.SequencedMap;
*///?} else {
import com.mojang.blaze3d.vertex.BufferBuilder;
//?}

/**
 * Separate buffers that previews draw block entities and mobs into. Another mod's renderer that fails
 * partway through a vertex leaves it half written. In the game's buffers it would stay until they were
 * next drawn: on 1.21 that drawing fails and crashes the game, and on 1.20.1 everything else drawn with
 * that render type until then fails too. Here it's discarded with the rest of the frame's drawing.
 *
 * <p>The layout is copied from the game's buffers: render types the game gives a dedicated buffer
 * (chests, banners, enchantment glints and any a mod adds) get one here too, since a renderer may draw
 * with two of them at once, and the rest share one. If the game's layout can't be found, previews draw
 * into the game's buffers and a warning is logged.
 */
final class PreviewBuffers {
    private static final int SHARED_BYTES = 256 * 1024;
    /** The game's render types that have dedicated buffers, in its order; empty if they couldn't be found. */
    private static List<RenderType> ownBuffers;
    private static MultiBufferSource.BufferSource source;
    //? if >=1.21 {
    /*private static ByteBufferBuilder shared;
    private static SequencedMap<RenderType, ByteBufferBuilder> fixed;
    *///?} else {
    private static BufferBuilder shared;
    private static Map<RenderType, BufferBuilder> fixed;
    //?}

    private PreviewBuffers() {
    }

    /** The buffer source to draw into; a new one after {@link #discard()}. */
    static MultiBufferSource.BufferSource source() {
        if (source != null) {
            return source;
        }
        if (ownBuffers == null) {
            ownBuffers = gameLayout();
        }
        if (ownBuffers.isEmpty()) {
            return Minecraft.getInstance().renderBuffers().bufferSource();
        }
        if (fixed == null) {
            //? if >=1.21 {
            /*shared = new ByteBufferBuilder(SHARED_BYTES);
            fixed = new LinkedHashMap<>();
            for (RenderType type : ownBuffers) {
                fixed.put(type, new ByteBufferBuilder(type.bufferSize()));
            }
            *///?} else {
            shared = new BufferBuilder(SHARED_BYTES);
            fixed = new LinkedHashMap<>();
            for (RenderType type : ownBuffers) {
                fixed.put(type, new BufferBuilder(type.bufferSize()));
            }
            //?}
        }
        // Created directly, not through the game's factory, which other mods replace with sources that
        // use their own buffers.
        source = new OwnSource(shared, fixed);
        return source;
    }

    /**
     * Subclass to reach BufferSource's protected constructor. A named class, not an anonymous one:
     * against some builds of the game, the compiler gives an anonymous class's constructor two
     * parameters with the same name.
     */
    private static final class OwnSource extends MultiBufferSource.BufferSource {
        //? if >=1.21 {
        /*OwnSource(ByteBufferBuilder shared, SequencedMap<RenderType, ByteBufferBuilder> fixed) {
            super(shared, fixed);
        }
        *///?} else {
        OwnSource(BufferBuilder shared, Map<RenderType, BufferBuilder> fixed) {
            super(shared, fixed);
        }
        //?}
    }

    /**
     * Discards everything written since the buffers were last drawn, which may end partway through a
     * vertex. Does nothing while previews draw into the game's buffers.
     */
    static void discard() {
        if (fixed == null) {
            return;
        }
        //? if >=1.21 {
        /*// Data not yet drawn can't be removed from a buffer, so the buffers are freed and recreated.
        shared.close();
        fixed.values().forEach(ByteBufferBuilder::close);
        fixed = null;
        *///?} else {
        // 1.20.1's buffers can't be freed, so they're emptied instead. Ending one drops a vertex it was
        // partway through.
        empty(shared);
        fixed.values().forEach(PreviewBuffers::empty);
        //?}
        source = null;
    }

    //? if <1.21 {
    private static void empty(BufferBuilder builder) {
        if (builder.building()) {
            builder.end();
        }
        builder.discard();
    }
    //?}

    /**
     * The render types the game's buffer source has dedicated buffers for. Their map has no getter,
     * and its name differs between the loaders' mappings, so it's found by type: the only field of
     * that type.
     */
    private static List<RenderType> gameLayout() {
        List<RenderType> types = new ArrayList<>();
        try {
            //? if >=1.21 {
            /*Class<?> mapType = SequencedMap.class;
            *///?} else {
            Class<?> mapType = Map.class;
            //?}
            Field found = null;
            for (Field field : MultiBufferSource.BufferSource.class.getDeclaredFields()) {
                if (field.getType() == mapType) {
                    if (found != null) {
                        throw new IllegalStateException("more than one field of type " + mapType.getName());
                    }
                    found = field;
                }
            }
            if (found == null) {
                throw new IllegalStateException("no field of type " + mapType.getName());
            }
            found.setAccessible(true);
            for (Object type : ((Map<?, ?>) found.get(Minecraft.getInstance().renderBuffers().bufferSource())).keySet()) {
                if (type instanceof RenderType renderType) {
                    types.add(renderType);
                }
            }
            if (types.isEmpty()) {
                throw new IllegalStateException("the game keeps no render types apart");
            }
            JesLog.debug("Previews draw into buffers of their own, keeping {} render types apart as the game does", types.size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.warnOnce("preview-buffers", "Couldn't find how the game lays out its buffers, so previews draw into the game's, and a renderer that fails in one could still end the game: {}",
                    e.toString());
            types.clear();
        }
        return types;
    }
}
//?}
