package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.FaultyDraws;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.1 {
/*import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/**
 * Lets {@link FaultyDraws} fail an entity as the game starts drawing it, which the preview asks of
 * every entity it draws: until 26.1 by drawing it, from 26.1 by asking what to draw.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class FaultyDrawsMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("HEAD"))
    private void jesTest$extract(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        FaultyDraws.draw(entity);
    }
    *///?} else {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void jesTest$render(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose,
                                MultiBufferSource buffers, int light, CallbackInfo ci) {
        FaultyDraws.draw(entity);
    }
    //?}
}
