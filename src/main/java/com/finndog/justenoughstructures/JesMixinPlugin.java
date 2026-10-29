package com.finndog.justenoughstructures;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Leaves out the mixins into other mods when those mods aren't installed. Mixin skips them anyway,
 * but on Fabric only after a warning in the log about the missing class.
 */
public final class JesMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".C2meRandomMixin") || mixinClassName.endsWith(".FtbQuestsTaskButtonMixin")) {
            return installed(targetClassName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        // The FTB Quests link's, only on the builds that have it, as every mixin the config lists must
        // exist, and only with FTB Quests installed: Mixin warns about a missing target before asking
        // shouldApplyMixin about a mixin handed over here.
        //? if ftbquests {
        return installed("dev.ftb.mods.ftbquests.client.gui.quests.TaskButton") ? List.of("FtbQuestsTaskButtonMixin") : null;
        //?} else {
        /*return null;
        *///?}
    }

    /** Whether another mod's class is there, found without loading it. */
    private static boolean installed(String className) {
        return JesMixinPlugin.class.getClassLoader().getResource(className.replace('.', '/') + ".class") != null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
