package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.LoadedPieces;
import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import com.finndog.justenoughstructures.server.JesServer;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * As a template loads, points its containers at the loot tables set in Pack tools and gives its
 * spawners their mobs. A failed load is noted for the capture that asked. A loaded template is
 * passed to LoadedPieces, which records it if JES work loaded it.
 */
@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {
    @Inject(method = "tryLoad", at = @At("RETURN"))
    private void justenoughstructures$patchContainers(ResourceLocation id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        Optional<StructureTemplate> template = cir.getReturnValue();
        if (template.isEmpty()) {
            StructureCapture.couldntLoad(id);
            return;
        }
        LoadedPieces.loaded((StructureTemplateManager) (Object) this, id, template.get());
        ContainerPatches.apply(id, template.get());
        // The server's resources, for trial spawner configs referenced by name instead of written inline.
        MinecraftServer server = JesServer.running();
        SpawnerPatches.apply(id, template.get(), server == null ? null : server.getResourceManager());
    }
}
