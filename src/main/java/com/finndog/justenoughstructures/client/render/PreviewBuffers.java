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
 * What previews draw block entities and mobs into: buffers of their own rather than the game's.
 * Another mod's renderer that fails part way through a vertex leaves it half written, and the game's
 * buffers would keep it until the game next drew them: on 1.21 that drawing fails and ends the game,
 * and on 1.20.1 everything else drawn with that render type until then fails with it. Here it's thrown
 * away with whatever else the frame had drawn so far.
 *
 * <p>They're laid out as the game's own are, read from them: the render types the game keeps buffers
 * of their own for (chests, banners, enchantment glints and any a mod adds) get them here too, as a
 * renderer may draw with two of those at once, and the rest share one. Should the game's layout not be
 * found, previews draw into the game's buffers as before, and say so in the log.
 */
final class PreviewBuffers {
    private static final int SHARED_BYTES = 256 * 1024;
    /** The game's render types with buffers of their own, in its order; empty if they couldn't be found. */
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

    /** Where to draw; a new one after {@link #discard()}. */
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
        // Made directly, not through the game's factory, which other mods replace with sources that
        // draw from buffers of their own.
        source = new OwnSource(shared, fixed);
        return source;
    }

    /**
     * The game's own kind of buffer source, whose constructor is protected. A named class with a
     * constructor of its own, as the compiler makes an anonymous one's constructor itself, and against
     * the game as some builds make it, that came out with two parameters of the same name.
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
     * Throws away everything drawn since the buffers were last drawn, which may end part way through
     * a vertex. Does nothing while previews draw into the game's buffers.
     */
    static void discard() {
        if (fixed == null) {
            return;
        }
        //? if >=1.21 {
        /*// What's in a buffer and not yet drawn can't be taken back out of it, so they're freed and made again.
        shared.close();
        fixed.values().forEach(ByteBufferBuilder::close);
        fixed = null;
        *///?} else {
        // 1.20.1's buffers can't be freed, so they're emptied instead. Ending one leaves out a vertex
        // it was part way through.
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
     * The render types the game's buffer source keeps buffers of their own for. The map they're in has
     * no getter, and its name differs between the mappings the loaders run with, so it's found by its
     * type: the only field of that type there.
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
