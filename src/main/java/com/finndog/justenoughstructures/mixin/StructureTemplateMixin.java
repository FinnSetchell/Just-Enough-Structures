package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.capture.TemplatePlacements;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells a structure capture which template is placing blocks, so it can trace containers back to
 * it, and which processor gave each spawner its mob.
 */
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

    // Optional, so a mod that rewrites this method costs the Mobs tab its spawner lists rather than crashing.
    //? if forge && >=26.1 {
    /*// Forge from 26.1 runs each processor through its own processBlock, which takes the template too.
    @WrapOperation(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$processBlock(StructureProcessor processor, LevelReader level, BlockPos offset,
                                                                                         BlockPos pos, StructureTemplate.StructureBlockInfo original,
                                                                                         StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                                                                                         StructureTemplate template, Operation<StructureTemplate.StructureBlockInfo> call) {
        StructureTemplate.StructureBlockInfo processed = call.call(processor, level, offset, pos, original, current, settings, template);
        SpawnerPools.processed(processor, current, processed);
        return processed;
    }
    *///?} else if forge || neoforge {
    /*// Forge and NeoForge run each processor through their own process method, which takes the
    // template too. Both that and the method calling it are their own, so their names are never
    // obfuscated.
    @WrapOperation(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;process(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$processBlock(StructureProcessor processor, LevelReader level, BlockPos offset,
                                                                                         BlockPos pos, StructureTemplate.StructureBlockInfo original,
                                                                                         StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                                                                                         StructureTemplate template, Operation<StructureTemplate.StructureBlockInfo> call) {
        StructureTemplate.StructureBlockInfo processed = call.call(processor, level, offset, pos, original, current, settings, template);
        SpawnerPools.processed(processor, current, processed);
        return processed;
    }
    *///?} else if >=26.2 {
    /*// 26.2 hands each processor where the block is in the template, rather than all of it as it was there.
    @WrapOperation(method = "processBlockInfos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$processBlock(StructureProcessor processor, LevelReader level, BlockPos offset,
                                                                                         BlockPos pos, BlockPos templatePos,
                                                                                         StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                                                                                         Operation<StructureTemplate.StructureBlockInfo> call) {
        StructureTemplate.StructureBlockInfo processed = call.call(processor, level, offset, pos, templatePos, current, settings);
        SpawnerPools.processed(processor, current, processed);
        return processed;
    }
    *///?} else {
    @WrapOperation(method = "processBlockInfos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$processBlock(StructureProcessor processor, LevelReader level, BlockPos offset,
                                                                                         BlockPos pos, StructureTemplate.StructureBlockInfo original,
                                                                                         StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                                                                                         Operation<StructureTemplate.StructureBlockInfo> call) {
        StructureTemplate.StructureBlockInfo processed = call.call(processor, level, offset, pos, original, current, settings);
        SpawnerPools.processed(processor, current, processed);
        return processed;
    }
    //?}
}
