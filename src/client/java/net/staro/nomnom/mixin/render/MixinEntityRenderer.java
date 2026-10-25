package net.staro.nomnom.mixin.render;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.staro.nomnom.module.modules.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    // The vanilla nametag is drawn from state.displayName, which is filled in at the end of the
    // base updateRenderState (player and living renderers call it through super). Clearing it
    // there removes the vanilla tag for every entity type.
    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void hideVanillaNametag(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        Nametags nametags = Nametags.INSTANCE;
        if (nametags != null && nametags.hidesVanillaTag(entity)) {
            state.displayName = null;
        }
    }
}