package com.finndog.justenoughstructures.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A template's blocks, to find and patch the containers in it, and its entities, to read what loot they hold. */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<StructureTemplate.Palette> justenoughstructures$palettes();

    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> justenoughstructures$entities();
}
