package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.StructureCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structure code sometimes reaches past the region it's placing into and asks the real level about
 * structures. Village cats do, to see if they're in a swamp hut. During a capture that would load
 * real chunks through the server thread, so hand it the sandbox's structures instead.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "structureManager", at = @At("HEAD"), cancellable = true)
    private void justenoughstructures$sandboxStructures(CallbackInfoReturnable<StructureManager> cir) {
        StructureManager sandbox = StructureCapture.sandboxStructures();
        if (sandbox != null) {
            cir.setReturnValue(sandbox);
        }
    }
}
