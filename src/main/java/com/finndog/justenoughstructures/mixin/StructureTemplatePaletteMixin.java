package com.finndog.justenoughstructures.mixin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets threads share a template's blocks by kind. The game works each list out the first time a
 * thread asks and keeps it in a map that can't take two threads at once, so two pieces of the
 * same template placed at the same moment could throw, from a preview or from world generation
 * alike. C2ME makes the same change.
 */
@Mixin(StructureTemplate.Palette.class)
public abstract class StructureTemplatePaletteMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void justenoughstructures$shareableCache(CallbackInfo ci) {
        cache = Collections.synchronizedMap(cache);
    }
}
