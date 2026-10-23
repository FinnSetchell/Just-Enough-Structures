package com.finndog.justenoughstructures.mixin;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The game's map of loaded templates, used to check whether one is still the one JES loaded without
 * triggering a load.
 */
@Mixin(StructureTemplateManager.class)
public interface StructureTemplateManagerAccessor {
    @Accessor("structureRepository")
    Map<ResourceLocation, Optional<StructureTemplate>> justenoughstructures$loaded();
}
