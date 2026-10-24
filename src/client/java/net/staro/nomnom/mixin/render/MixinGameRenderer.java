package net.staro.nomnom.mixin.render;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.staro.nomnom.module.modules.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: the camera tilt when you take damage. */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void nomnom$hideHurtCamera(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender != null && noRender.hideHurtCamera()) {
            ci.cancel();
        }
    }
}