package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.FaultyFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
*///?} else {
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.world.level.BlockAndTintGetter;
//?}

/** Lets {@link FaultyFluids} fail the game's fluid drawing part way through a vertex. */
//? if >=26.1 {
/*@Mixin(FluidRenderer.class)
abstract class FaultyFluidsMixin {
    @Inject(method = "tesselate", at = @At("HEAD"))
    private void jesTest$tesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState state, FluidState fluid, CallbackInfo ci) {
        if (FaultyFluids.active()) {
            FaultyFluids.tesselate(output.getBuilder(ChunkSectionLayer.TRANSLUCENT));
        }
    }
}
*///?} else {
@Mixin(LiquidBlockRenderer.class)
abstract class FaultyFluidsMixin {
    @Inject(method = "tesselate", at = @At("HEAD"))
    private void jesTest$tesselate(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, BlockState state, FluidState fluid, CallbackInfo ci) {
        FaultyFluids.tesselate(consumer);
    }
}
//?}
