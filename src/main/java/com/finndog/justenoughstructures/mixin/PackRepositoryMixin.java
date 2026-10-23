package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.overrides.OverridePack;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the loot override pack to every world datapack repository: those whose sources include the game's built-in datapacks. */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void justenoughstructures$addLootOverrides(RepositorySource[] given, CallbackInfo ci) {
        if (sources.stream().anyMatch(source -> source instanceof ServerPacksSource)) {
            // Ordered, and still mutable: Forge adds its mods' packs to this set later.
            Set<RepositorySource> withOverrides = new LinkedHashSet<>(sources);
            withOverrides.add(new OverridePack());
            sources = withOverrides;
        }
    }
}
