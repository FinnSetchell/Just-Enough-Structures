package com.finndog.justenoughstructures.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pieces a list element places on top of each other, to trace a container back to its template. */
@Mixin(ListPoolElement.class)
public interface ListPoolElementAccessor {
    @Accessor("elements")
    List<StructurePoolElement> justenoughstructures$elements();
}
