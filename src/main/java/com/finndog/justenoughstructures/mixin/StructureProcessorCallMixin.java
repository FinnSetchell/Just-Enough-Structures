package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tells a structure capture which processor gave each spawner its mob, by listening in on the call
 * the game makes to each processor for each block of a template it places: once as the processor
 * is about to be asked, and once with its answer.
 *
 * <p>That call is made for every block of every piece in every world, a capture's or not, so
 * listening makes nothing. Wrapping the call instead made an array and a function each time, which
 * came to two fifths of all the memory a loot index went through.
 *
 * <p>It's not a redirect of the call either. Built against the Mixin this mod is, a redirect is
 * written down in a way MixinExtras before 0.5.5 can't read, and that MixinExtras comes with Fabric
 * Loader up to 0.19.3: the game then can't load templates at all.
 *
 * <p>Both halves are optional, so a mod that rewrites the method costs the Mobs tab its spawner
 * lists rather than crashing.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureProcessorCallMixin {
    //? if forge && >=26.2 {
    /*// Forge from 26.2 hands its processBlock where the block is in the template, as 26.2 itself does.
    @ModifyReceiver(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureProcessor justenoughstructures$asking(StructureProcessor processor, LevelReader level, BlockPos offset, BlockPos pos,
                                                                  BlockPos templatePos, StructureTemplate.StructureBlockInfo current,
                                                                  StructurePlaceSettings settings, StructureTemplate template) {
        SpawnerPools.asking(processor, current);
        return processor;
    }

    @ModifyExpressionValue(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$answered(StructureTemplate.StructureBlockInfo processed) {
        SpawnerPools.answered(processed);
        return processed;
    }
    *///?} else if forge && >=26.1 {
    /*// Forge from 26.1 runs each processor through its own processBlock, which takes the template too.
    @ModifyReceiver(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureProcessor justenoughstructures$asking(StructureProcessor processor, LevelReader level, BlockPos offset, BlockPos pos,
                                                                  StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current,
                                                                  StructurePlaceSettings settings, StructureTemplate template) {
        SpawnerPools.asking(processor, current);
        return processor;
    }

    @ModifyExpressionValue(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$answered(StructureTemplate.StructureBlockInfo processed) {
        SpawnerPools.answered(processed);
        return processed;
    }
    *///?} else if forge || neoforge {
    /*// Forge and NeoForge run each processor through their own process method, which takes the
    // template too. Both that and the method calling it are their own, so their names are never
    // obfuscated.
    @ModifyReceiver(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;process(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureProcessor justenoughstructures$asking(StructureProcessor processor, LevelReader level, BlockPos offset, BlockPos pos,
                                                                  StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current,
                                                                  StructurePlaceSettings settings, StructureTemplate template) {
        SpawnerPools.asking(processor, current);
        return processor;
    }

    @ModifyExpressionValue(method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            require = 0, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;process(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$answered(StructureTemplate.StructureBlockInfo processed) {
        SpawnerPools.answered(processed);
        return processed;
    }
    *///?} else if >=26.2 {
    /*// 26.2 hands each processor where the block is in the template, rather than all of it as it was there.
    @ModifyReceiver(method = "processBlockInfos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureProcessor justenoughstructures$asking(StructureProcessor processor, LevelReader level, BlockPos offset, BlockPos pos,
                                                                  BlockPos templatePos, StructureTemplate.StructureBlockInfo current,
                                                                  StructurePlaceSettings settings) {
        SpawnerPools.asking(processor, current);
        return processor;
    }

    @ModifyExpressionValue(method = "processBlockInfos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$answered(StructureTemplate.StructureBlockInfo processed) {
        SpawnerPools.answered(processed);
        return processed;
    }
    *///?} else {
    @ModifyReceiver(method = "processBlockInfos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureProcessor justenoughstructures$asking(StructureProcessor processor, LevelReader level, BlockPos offset, BlockPos pos,
                                                                  StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current,
                                                                  StructurePlaceSettings settings) {
        SpawnerPools.asking(processor, current);
        return processor;
    }

    @ModifyExpressionValue(method = "processBlockInfos", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
    private static StructureTemplate.StructureBlockInfo justenoughstructures$answered(StructureTemplate.StructureBlockInfo processed) {
        SpawnerPools.answered(processed);
        return processed;
    }
    //?}
}
