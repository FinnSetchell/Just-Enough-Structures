package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.HeldLoads;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link HeldLoads} stop the game in the middle of loading a saved piece. */
@Mixin(StructureTemplateManager.class)
abstract class HeldLoadsMixin {
    @Inject(method = "tryLoad", at = @At("HEAD"))
    private void jesTest$hold(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        HeldLoads.loading(id);
    }
}
