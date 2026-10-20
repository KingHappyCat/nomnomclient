package net.staro.nomnom.module.modules;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.UpdateEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/**
 * Horizontal speed.
 * STRAFE: sets your horizontal velocity to (sprint speed * Speed) in the direction you are pressing.
 * BHOP:   same, but also auto-jumps while you are moving.
 */
@SuppressWarnings("unused")
public class Speed extends NomnomModule {
    public enum Mode {STRAFE, BHOP}

    private static final double BASE_SPEED = 0.2873; // vanilla sprint speed, blocks per tick

    private final Setting<Mode> mode = enumSetting("Mode", Mode.STRAFE);
    private final Setting<Float> speed = floating("Speed", 1.5f, 1.0f, 5.0f);
    private final Setting<Boolean> groundCheck = bool("Ground Check", true);

    public Speed(Nomnom nomnom) {
        super(nomnom, "Speed", Category.MOVEMENT, "Configurable movement speed");
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        ClientPlayerEntity player = event.getPlayer();
        if (player.isGliding() || player.isTouchingWater() || player.isInLava()
                || player.isClimbing() || player.getAbilities().flying) {
            return;
        }

        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double strafe = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        if (forward == 0 && strafe == 0) {
            return;
        }

        boolean onGround = player.isOnGround();
        if (mode.getValue() == Mode.BHOP) {
            if (onGround) player.jump();
        } else if (groundCheck.getValue() && !onGround) {
            return; // STRAFE with Ground Check only boosts while on the ground
        }

        double base = BASE_SPEED;
        var effect = player.getStatusEffect(StatusEffects.SPEED);
        if (effect != null) {
            base *= 1.0 + 0.2 * (effect.getAmplifier() + 1);
        }
        double velocity = base * speed.getValue();

        double length = Math.hypot(forward, strafe);
        forward /= length;
        strafe /= length;

        double yaw = Math.toRadians(player.getYaw());
        double x = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * velocity;
        double z = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * velocity;
        player.setVelocity(x, player.getVelocity().y, z);
    }
}
