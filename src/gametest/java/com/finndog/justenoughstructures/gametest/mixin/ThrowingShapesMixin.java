package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.ThrowingShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link ThrowingShapes} fail the shape questions the browser asks of blocks under the mouse: two, or one from 1.21.2. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class ThrowingShapesMixin {
    // A real Forge 1.20.1 game knows the method by its SRG name, and the test jar has no refmap to say so.
    //? if forge && <1.21 {
    /*@Inject(method = {"getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            "m_60651_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"},
            at = @At("HEAD"))
    *///?} else {
    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"))
    //?}
    private void jesTest$shape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        ThrowingShapes.shape(level);
    }

    // From 1.21.2 a block is solid or not whatever world it's in, and nothing is asked of it.
    //? if forge && <1.21 {
    /*@Inject(method = {"isSolidRender", "m_60804_"}, at = @At("HEAD"))
    private void jesTest$solid(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ThrowingShapes.solid(level);
    }
    *///?} else if <1.21.2 {
    @Inject(method = "isSolidRender", at = @At("HEAD"))
    private void jesTest$solid(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ThrowingShapes.solid(level);
    }
    //?}
}
