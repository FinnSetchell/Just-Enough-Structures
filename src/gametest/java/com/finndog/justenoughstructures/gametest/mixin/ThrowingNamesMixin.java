package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.ThrowingNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link ThrowingNames} fail a mob when the browser asks it for its name. */
@Mixin(Entity.class)
abstract class ThrowingNamesMixin {
    // A real Forge 1.20.1 game knows the method by its SRG name, and the test jar has no refmap to say so.
    //? if forge && <1.21 {
    /*@Inject(method = {"getName", "m_7755_"}, at = @At("HEAD"))
    *///?} else {
    @Inject(method = "getName", at = @At("HEAD"))
    //?}
    private void jesTest$name(CallbackInfoReturnable<Component> cir) {
        ThrowingNames.name((Entity) (Object) this);
    }
}
