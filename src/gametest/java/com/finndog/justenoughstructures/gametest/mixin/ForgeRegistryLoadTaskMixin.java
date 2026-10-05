package com.finndog.justenoughstructures.gametest.mixin;

//? if forge && >=26.1 {
/*import com.finndog.justenoughstructures.gametest.forge.ForgeGameTests;
import java.util.Map;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Tells {@link ForgeGameTests} as each registry loaded with the world's data is about to be frozen,
 * the last moment the tests can still be added to theirs. Only for registries loaded from data: a
 * client gets the tests along with the rest of the registries the server sends it. Only on Forge
 * from 26.1.
 ^/
@Mixin(RegistryLoadTask.class)
public abstract class ForgeRegistryLoadTaskMixin<T> {
    @Shadow
    @Final
    private WritableRegistry<T> registry;

    @Inject(method = "freezeRegistry", at = @At("HEAD"))
    private void justenoughstructures_gametest$addTests(Map<ResourceKey<?>, Exception> loadingErrors, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ResourceManagerRegistryLoadTask<?>) {
            ForgeGameTests.beforeFreeze(registry);
        }
    }
}
*///?}
