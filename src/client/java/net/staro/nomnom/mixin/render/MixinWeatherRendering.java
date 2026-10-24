package net.staro.nomnom.mixin.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.state.WeatherRenderState;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.module.modules.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender: rain and snow streaks. Rain splash particles and the rain sound still happen,
 * those come from {@code addParticlesAndSound}.
 */
@Mixin(WeatherRendering.class)
public class MixinWeatherRendering {
    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
    private void nomnom$hideWeather(VertexConsumerProvider vertexConsumers, Vec3d pos,
                                    WeatherRenderState state, CallbackInfo ci) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender != null && noRender.hideWeather()) {
            ci.cancel();
        }
    }
}