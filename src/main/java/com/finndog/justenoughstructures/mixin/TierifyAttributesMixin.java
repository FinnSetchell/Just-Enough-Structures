package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.loot.LootRolls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tierify gives every rolled stack a random tier: for each stack it checks the item against every
 * attribute it knows, which in Prominence II made a roll about 29 times slower (2.37 ms against
 * 0.082 ms, the median over its 605 structure loot tables). The tier is only NBT, and the odds and
 * the loot index count which items come out, how many and their enchantments and potions, so their
 * rolls leave it off. A single chest rolled for a player to look at keeps it, as a real chest has it.
 * The method is Tierify's own, so its name is the same on every loader. Without Tierify this does nothing.
 */
@Pseudo
@Mixin(targets = "draylar.tiered.api.ModifierUtils", remap = false)
public abstract class TierifyAttributesMixin {
    @Inject(method = "setItemStackAttribute*", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void justenoughstructures$noTierInCountingRolls(CallbackInfo ci) {
        if (LootRolls.counting()) {
            ci.cancel();
        }
    }
}
