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
 * Tells a structure capture which processor set each spawner's mob, by hooking each processor call
 * in processBlockInfos: once before the call and once with its result.
 *
 * <p>This call runs for every block of every placed piece, in captures and in normal world
 * generation, so these hooks must not allocate. A @WrapOperation would allocate an array and a
 * lambda per call.
 *
 * <p>Not a @Redirect either. Compiled against JES's Mixin, a redirect uses a form that MixinExtras
 * before 0.5.5 can't read; Fabric Loader up to 0.19.3 ships that MixinExtras, and templates would
 * then fail to load.
 *
 * <p>Both hooks use require = 0, so a mod that rewrites the method costs the Mobs tab its spawner
 * lists instead of crashing.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureProcessorCallMixin {
    //? if forge && >=26.2 {
    /*// Forge 26.2+: like vanilla 26.2, processBlock takes the block's template position instead of the
    // original block info, plus the template.
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
    /*// Forge 26.1: each processor runs through Forge's processBlock overload, which also takes the template.
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
    /*// Forge and NeoForge run each processor through their process method, which also takes the
    // template. The loader adds both it and the processBlockInfos overload that calls it, so their
    // names are never obfuscated.
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
    /*// 26.2 passes the block's template position instead of the original block info.
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
