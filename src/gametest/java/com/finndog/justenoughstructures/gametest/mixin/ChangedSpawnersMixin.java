package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.ChangedSpawners;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link ChangedSpawners} join the processors of the pieces a test places. */
@Mixin(StructurePlaceSettings.class)
abstract class ChangedSpawnersMixin {
    @Inject(method = "getProcessors", at = @At("RETURN"), cancellable = true)
    private void jesTest$join(CallbackInfoReturnable<List<StructureProcessor>> cir) {
        List<StructureProcessor> own = cir.getReturnValue();
        List<StructureProcessor> all = ChangedSpawners.with(own);
        if (all != own) {
            cir.setReturnValue(all);
        }
    }
}
