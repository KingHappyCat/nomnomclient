package net.staro.nomnom.mixin.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.staro.nomnom.module.modules.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoRender: boss bars. */
@Mixin(BossBarHud.class)
public class MixinBossBarHud {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nomnom$hideBossBar(DrawContext context, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender != null && noRender.hideBossBar()) {
            ci.cancel();
        }
    }
}