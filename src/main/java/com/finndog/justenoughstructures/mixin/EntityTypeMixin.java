package com.finndog.justenoughstructures.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.2 {
/*import com.finndog.justenoughstructures.capture.RealWorldGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.At;
*///?}

/**
 * From 26.2 a Peaceful world won't make monsters at all, where before it made them and took them
 * away on their first tick. A structure being captured makes them anyway, so a Peaceful world sees
 * the same mobs in it as any other. Nothing to do before 26.2.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    //? if >=26.2 {
    /*@WrapOperation(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;",
            require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSpawn(Lnet/minecraft/world/level/Level;)Z"))
    private boolean justenoughstructures$evenInPeaceful(EntityType<?> type, Level level, Operation<Boolean> call) {
        return call.call(type, level) || RealWorldGuard.current() != null && type.isEnabled(level.enabledFeatures());
    }
    *///?}
}
