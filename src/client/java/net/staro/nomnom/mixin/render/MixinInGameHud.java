package net.staro.nomnom.mixin.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.staro.nomnom.module.modules.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: the dark screen-edge vignette. */
@Mixin(InGameHud.class)
public class MixinInGameHud {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void nomnom$hideVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender != null && noRender.hideVignette()) {
            ci.cancel();
        }
    }
}