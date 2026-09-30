package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.TemplatePlacements;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells a structure capture which template is placing blocks, so it can trace containers back to it. */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    @Inject(method = "placeInWorld", at = @At("HEAD"))
    private void justenoughstructures$startPlacing(ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings,
                                                   RandomSource random, int flags, CallbackInfoReturnable<Boolean> cir) {
        TemplatePlacements.push((StructureTemplate) (Object) this);
    }

    @Inject(method = "placeInWorld", at = @At("RETURN"))
    private void justenoughstructures$stopPlacing(ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings,
                                                  RandomSource random, int flags, CallbackInfoReturnable<Boolean> cir) {
        TemplatePlacements.pop();
    }
}
