package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.overrides.StructureNameFiles;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.1 {
import com.google.common.collect.ImmutableMap;
//?}

/**
 * Makes structure names saved by Pack tools in this game override every mod's names for them. The
 * names pack sits above the mods' packs, but the game merges lang files one namespace at a time, in
 * hash map order, so pack order only counts within a namespace. Explorer's Compass names vanilla
 * structures under its own namespace, and if that is merged after JES's, its names would win. So the
 * saved names are laid over the merged strings: the first map loadFrom copies, on every version.
 * NeoForge copies a second one, of its components, after it.
 */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    //? if >=26.1 {
    /*@WrapOperation(method = "loadFrom", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;copyOf(Ljava/util/Map;)Ljava/util/Map;", ordinal = 0))
    private static Map<String, String> justenoughstructures$savedNamesWin(Map<String, String> strings, Operation<Map<String, String>> copy,
                                                                       @Local(argsOnly = true) List<String> languages) {
        return copy.call(StructureNameFiles.laidOver(strings, languages));
    }
    *///?} else {
    @WrapOperation(method = "loadFrom", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;", ordinal = 0))
    private static ImmutableMap<String, String> justenoughstructures$savedNamesWin(Map<String, String> strings, Operation<ImmutableMap<String, String>> copy,
                                                                                @Local(argsOnly = true) List<String> languages) {
        return copy.call(StructureNameFiles.laidOver(strings, languages));
    }
    //?}
}
