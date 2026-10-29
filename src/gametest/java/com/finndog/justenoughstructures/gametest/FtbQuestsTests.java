package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesMixinPlugin;
import com.finndog.justenoughstructures.compat.ftbquests.TaskTarget;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.StructureTask;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
//? if >=26.1 {
/*import de.marhali.json5.Json5Object;
*///?} else {
import net.minecraft.nbt.CompoundTag;
//?}

/**
 * The FTB Quests link against the real FTB Quests, which every test run on these builds has. A test
 * server has no screens, so the click itself is checked in the scripted client scenario; these check
 * what the link reads from FTB Quests and the calls its mixin wraps.
 */
public final class FtbQuestsTests {
    private static final String MIXIN = "com/finndog/justenoughstructures/mixin/FtbQuestsTaskButtonMixin";
    private static final String TASK_BUTTON = "dev/ftb/mods/ftbquests/client/gui/quests/TaskButton";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

    private FtbQuestsTests() {
    }

    /** A Find Structure task's structure or tag is read as FTB Quests loads it from a quest file. Other tasks have none. */
    public static void findStructureTasksNameWhatTheyAskFor(GameTestHelper helper) {
        TaskTarget mineshaft = TaskTarget.of(structureTask(helper, "minecraft:mineshaft"));
        helper.assertTrue(new TaskTarget(Ids.parse("mineshaft"), false).equals(mineshaft), "a task for the mineshaft read as " + mineshaft);
        TaskTarget village = TaskTarget.of(structureTask(helper, "#minecraft:village"));
        helper.assertTrue(new TaskTarget(Ids.parse("village"), true).equals(village), "a task for the village tag read as " + village);
        TaskTarget modded = TaskTarget.of(structureTask(helper, "some_mod:tower/tall"));
        helper.assertTrue(new TaskTarget(Ids.of("some_mod", "tower/tall"), false).equals(modded), "a task for a modded structure read as " + modded);
        TaskTarget checkmark = TaskTarget.of(new CheckmarkTask(4L, null));
        helper.assertTrue(checkmark == null, "a checkmark task read as asking for " + checkmark);
        helper.succeed();
    }

    /**
     * Every call the mixin wraps is in the real FTB Quests' task button, read from its class file as a
     * test server never loads it. The hooks use require = 0, so a missing call would only show as a
     * click doing nothing.
     */
    public static void taskButtonMakesTheCallsTheLinkWraps(GameTestHelper helper) {
        ClassNode mixin = read(MIXIN);
        ClassNode button = read(TASK_BUTTON);
        List<String> missing = new ArrayList<>();
        int hooks = 0;
        for (MethodNode handler : mixin.methods) {
            for (AnnotationNode annotation : annotations(handler)) {
                if (!annotation.desc.equals(WRAP_OPERATION)) {
                    continue;
                }
                hooks++;
                String method = ((List<?>) value(annotation, "method")).get(0).toString();
                String target = value((AnnotationNode) ((List<?>) value(annotation, "at")).get(0), "target").toString();
                if (!calls(button, method, target)) {
                    missing.add(method + " -> " + target);
                }
            }
        }
        helper.assertTrue(hooks == 2, "the mixin has " + hooks + " hooks, not the click's and the tooltip's");
        helper.assertTrue(missing.isEmpty(), "FTB Quests' task button doesn't make these calls: " + missing);

        JesMixinPlugin plugin = new JesMixinPlugin();
        helper.assertTrue(plugin.getMixins() != null && plugin.getMixins().contains("FtbQuestsTaskButtonMixin"), "the mixin plugin doesn't list the FTB Quests mixin");
        helper.assertTrue(plugin.shouldApplyMixin(TASK_BUTTON.replace('/', '.'), MIXIN.replace('/', '.')),
                "the mixin plugin wouldn't apply the FTB Quests mixin with FTB Quests installed");
        helper.succeed();
    }

    /** A Find Structure task as FTB Quests reads it from a quest file. */
    private static StructureTask structureTask(GameTestHelper helper, String structure) {
        StructureTask task = new StructureTask(1L, null);
        //? if >=26.1 {
        /*Json5Object json = new Json5Object();
        json.addProperty("structure", structure);
        task.readData(json, helper.getLevel().registryAccess());
        *///?} else if >=1.21 {
        /*CompoundTag tag = new CompoundTag();
        tag.putString("structure", structure);
        task.readData(tag, helper.getLevel().registryAccess());
        *///?} else {
        CompoundTag tag = new CompoundTag();
        tag.putString("structure", structure);
        task.readData(tag);
        //?}
        return task;
    }

    /** Whether {@code owner}'s methods named {@code method} call {@code target}, written as a mixin target. */
    private static boolean calls(ClassNode owner, String method, String target) {
        for (MethodNode node : owner.methods) {
            if (!node.name.equals(method)) {
                continue;
            }
            for (AbstractInsnNode insn : node.instructions) {
                if (insn instanceof MethodInsnNode call && target.equals("L" + call.owner + ";" + call.name + call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ClassNode read(String name) {
        try (InputStream in = FtbQuestsTests.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) {
                throw new IllegalStateException("no class file for " + name);
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("couldn't read " + name, e);
        }
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> all = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            all.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            all.addAll(method.invisibleAnnotations);
        }
        return all;
    }

    /** An annotation's value, given as name, value pairs in ASM. */
    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) {
                return annotation.values.get(i + 1);
            }
        }
        throw new IllegalStateException(annotation.desc + " has no " + name);
    }
}
