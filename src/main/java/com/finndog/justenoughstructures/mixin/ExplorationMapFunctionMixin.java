package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.loot.LootRolls;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JES rolls loot with no position to search from, so on its own an exploration map comes out blank,
 * or from 26.3 not at all, and treasure maps would never show in chances or chest previews. In those
 * rolls it gives {@link LootRolls#blankMap} instead. Every other roll is left alone. Optional, so a mod
 * that rewrites the method costs treasure maps their chances instead of crashing.
 */
@Mixin(ExplorationMapFunction.class)
public abstract class ExplorationMapFunctionMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$blankInJesRolls(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        if (LootRolls.rolling()) {
            cir.setReturnValue(LootRolls.blankMap(stack));
        }
    }
}
