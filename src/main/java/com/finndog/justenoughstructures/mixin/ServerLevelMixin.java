package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.LevelRandom;
import com.finndog.justenoughstructures.capture.StructureCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure code sometimes reaches past the region it's placing into and asks the real level about
 * structures. During a capture that would load real chunks through the server thread, so these
 * answer from the sandbox instead, or not at all.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    /** So draws from this world's random during a capture can be told apart, see {@code LegacyRandomSourceMixin}. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void justenoughstructures$markRandom(CallbackInfo ci) {
        if (((Level) (Object) this).getRandom() instanceof LevelRandom random) {
            random.justenoughstructures$markLevelRandom();
        }
    }

    /** Village cats ask whether they're in a swamp hut. */
    @Inject(method = "structureManager", at = @At("HEAD"), cancellable = true)
    private void justenoughstructures$sandboxStructures(CallbackInfoReturnable<StructureManager> cir) {
        StructureManager sandbox = StructureCapture.sandboxStructures();
        if (sandbox != null) {
            cir.setReturnValue(sandbox);
        }
    }

    /**
     * Saving a placed villager rolls its trades, and a cartographer's map trade searches the world
     * for a structure, which can take seconds and generates chunks. Nothing found: no map trade.
     */
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void justenoughstructures$noSearchesWhileCapturing(TagKey<Structure> structures, BlockPos from, int radius, boolean skipKnown,
                                                               CallbackInfoReturnable<BlockPos> cir) {
        if (StructureCapture.sandboxStructures() != null) {
            cir.setReturnValue(null);
        }
    }
}
