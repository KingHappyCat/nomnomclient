package net.staro.nomnom.mixin.render;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.staro.nomnom.module.modules.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: the first person fire overlay and the totem pop animation. */
@Mixin(InGameOverlayRenderer.class)
public class MixinInGameOverlayRenderer {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void nomnom$hideFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        Sprite sprite, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender != null && noRender.hideFire()) {
            ci.cancel();
        }
    }

    // Every way of showing the floating item (GameRenderer.showFloatingItem included) ends here.
    @Inject(method = "setFloatingItem", at = @At("HEAD"), cancellable = true)
    private void nomnom$hideTotemAnimation(ItemStack stack, Random random, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender != null && stack.isOf(Items.TOTEM_OF_UNDYING) && noRender.hideTotemAnimation()) {
            ci.cancel();
        }
    }
}