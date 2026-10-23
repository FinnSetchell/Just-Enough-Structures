package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.overrides.NamesPack;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the names pack, with the structure names Pack tools saved in this game, to the client's
 * resource pack repository: the one whose sources include the game's built-in resources.
 */
@Mixin(PackRepository.class)
public abstract class ClientPackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void justenoughstructures$addNames(RepositorySource[] given, CallbackInfo ci) {
        if (sources.stream().anyMatch(source -> source instanceof ClientPackSource)) {
            // Ordered, and still mutable, since loaders add their sources to this set later.
            Set<RepositorySource> withNames = new LinkedHashSet<>(sources);
            withNames.add(new NamesPack());
            sources = withNames;
        }
    }
}
