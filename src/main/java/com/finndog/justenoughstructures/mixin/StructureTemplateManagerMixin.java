package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.overrides.ContainerPatches;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Points containers in a template at the loot tables devs chose, as the template is loaded. */
@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {
    @Inject(method = "tryLoad", at = @At("RETURN"))
    private void justenoughstructures$patchContainers(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        cir.getReturnValue().ifPresent(template -> ContainerPatches.apply(id, template));
    }
}
