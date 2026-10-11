package com.finndog.justenoughstructures.mixin;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The saved pieces the game has loaded and is holding on to, to see whether one is still the one
 * this mod had it load. Asking the game for a piece would load it.
 */
@Mixin(StructureTemplateManager.class)
public interface StructureTemplateManagerAccessor {
    @Accessor("structureRepository")
    Map<ResourceLocation, Optional<StructureTemplate>> justenoughstructures$loaded();
}
