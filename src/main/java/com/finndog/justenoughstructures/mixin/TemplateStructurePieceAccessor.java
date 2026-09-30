package com.finndog.justenoughstructures.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Which template a piece places, and how, to trace a container back to its spot in it. */
@Mixin(TemplateStructurePiece.class)
public interface TemplateStructurePieceAccessor {
    @Invoker("makeTemplateLocation")
    ResourceLocation justenoughstructures$templateLocation();

    @Accessor("placeSettings")
    StructurePlaceSettings justenoughstructures$placeSettings();

    @Accessor("templatePosition")
    BlockPos justenoughstructures$templatePosition();

    @Accessor("template")
    StructureTemplate justenoughstructures$template();
}
