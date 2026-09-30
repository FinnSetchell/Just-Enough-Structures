package com.finndog.justenoughstructures.capture;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The template being placed right now on a thread that's capturing a structure, so the capture
 * knows which template really filled each container. Pieces can place several templates at one
 * spot, and processors can drop blocks, so where a template could have put a container isn't
 * enough. On any other thread this does nothing.
 */
public final class TemplatePlacements {
    private static final ThreadLocal<Deque<StructureTemplate>> PLACING = new ThreadLocal<>();

    private TemplatePlacements() {
    }

    static void begin() {
        PLACING.set(new ArrayDeque<>());
    }

    static void end() {
        PLACING.remove();
    }

    public static void push(StructureTemplate template) {
        Deque<StructureTemplate> placing = PLACING.get();
        if (placing != null) {
            placing.push(template);
        }
    }

    public static void pop() {
        Deque<StructureTemplate> placing = PLACING.get();
        if (placing != null && !placing.isEmpty()) {
            placing.pop();
        }
    }

    /** The template being placed, or null if it's structure code placing blocks itself. */
    static StructureTemplate current() {
        Deque<StructureTemplate> placing = PLACING.get();
        return placing == null ? null : placing.peek();
    }
}
