package com.finndog.justenoughstructures.compat.ftbquests;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.mojang.datafixers.util.Either;
import dev.ftb.mods.ftbquests.quest.task.StructureTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.lang.reflect.Field;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * What an FTB Quests Find Structure task asks for: one structure, or with {@code tag} set, any of a
 * structure tag's. FTB Quests keeps it in a private field with no getter, so it's read by reflection.
 * If an FTB Quests build has no such field, no task has a target and the link stays out of the way.
 * Not client only, so the game tests can read real tasks.
 */
public record TaskTarget(ResourceLocation id, boolean tag) {
    private static Field structure;
    private static boolean broken;

    /** What {@code task} asks for, or null when it isn't a Find Structure task or can't be read. */
    public static TaskTarget of(Task task) {
        if (!(task instanceof StructureTask) || broken) {
            return null;
        }
        try {
            if (structure == null) {
                Field field = StructureTask.class.getDeclaredField("structure");
                field.setAccessible(true);
                structure = field;
            }
            if (!(structure.get(task) instanceof Either<?, ?> either)) {
                return null;
            }
            return either.map(TaskTarget::ofKey, TaskTarget::ofTag);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            JustEnoughStructures.LOGGER.warn("FTB Quests doesn't keep a Find Structure task's structure where it used to, so its tasks won't open the browser: {}", e.toString());
            return null;
        }
    }

    private static TaskTarget ofKey(Object key) {
        return key instanceof ResourceKey<?> structure ? new TaskTarget(Ids.of(structure), false) : null;
    }

    private static TaskTarget ofTag(Object tag) {
        return tag instanceof TagKey<?> structures ? new TaskTarget(structures.location(), true) : null;
    }
}
