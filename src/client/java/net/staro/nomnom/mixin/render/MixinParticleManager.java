package net.staro.nomnom.mixin.render;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.staro.nomnom.module.modules.NoRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NoRender: potion swirl particles and explosion particles. */
@Mixin(ParticleManager.class)
public class MixinParticleManager {
    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nomnom$hideParticles(ParticleEffect parameters, double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      CallbackInfoReturnable<Particle> cir) {
        NoRender noRender = NoRender.INSTANCE;
        if (noRender == null) {
            return;
        }

        var type = parameters.getType();

        if ((type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER)
                && noRender.hideExplosionParticles()) {
            cir.setReturnValue(null);
        } else if (type == ParticleTypes.ENTITY_EFFECT && noRender.hidePotionParticles()) {
            cir.setReturnValue(null);
        }
    }
}