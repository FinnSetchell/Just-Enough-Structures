package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.Levels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What BCLib and Better End do to the game's regions: only the chunks next to the middle one can be
 * written to. Here it's done to the mod's capture regions alone, so the tests capture as they would
 * in a pack with one of those, and nothing else in the test world changes.
 */
@Mixin(WorldGenRegion.class)
abstract class NearMiddleRegionMixin {
    @Shadow
    @Final
    private ChunkAccess center;

    @Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
    private void jesTest$nearMiddle(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (getClass().getName().endsWith(".CaptureRegion")) {
            ChunkPos middle = center.getPos();
            cir.setReturnValue(Math.abs(SectionPos.blockToSectionCoord(pos.getX()) - Levels.chunkX(middle)) < 2
                    && Math.abs(SectionPos.blockToSectionCoord(pos.getZ()) - Levels.chunkZ(middle)) < 2);
        }
    }
}
