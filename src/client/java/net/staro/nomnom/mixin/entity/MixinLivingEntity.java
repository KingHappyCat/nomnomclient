package net.staro.nomnom.mixin.entity;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.module.modules.ElytraFly;
import net.staro.nomnom.module.modules.Step;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @SuppressWarnings("ConstantValue")
    @Inject(method = "getStepHeight", at = @At("RETURN"), cancellable = true)
    private void getStepHeightHook(CallbackInfoReturnable<Float> cir) {
        if (ClientPlayerEntity.class.isInstance(this)) {
            var event = new Step.StepHeightEvent(ClientPlayerEntity.class.cast(this), cir.getReturnValueF());
            Nomnom.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                cir.setReturnValue(event.getHeight());
            }
        }
    }

    // Vanilla elytra physics (gravity, drag, lift). ElytraFly's CONTROL mode sets the velocity
    // itself, so for the local player the physics step must leave it untouched.
    @Inject(method = "calcGlidingVelocity", at = @At("HEAD"), cancellable = true)
    private void calcGlidingVelocityHook(Vec3d oldVelocity, CallbackInfoReturnable<Vec3d> cir) {
        if (ElytraFly.overridesPhysics((LivingEntity) (Object) this)) {
            cir.setReturnValue(oldVelocity);
        }
    }

}