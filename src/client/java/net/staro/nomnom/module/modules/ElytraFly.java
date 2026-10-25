package net.staro.nomnom.module.modules;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.UpdateEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/**
 * Elytra flight.
 *
 * <p>CONTROL: vanilla elytra physics (gravity, drag, lift) are switched off and you steer
 * directly. Forward/back/strafe move you at Speed, jump goes up and sneak goes down at
 * Vertical Speed, and with no keys held you hover on the spot.
 *
 * <p>BOOST: normal elytra physics, but holding forward pushes you along your look direction
 * towards Speed, the way a firework rocket does, without using any.
 *
 * <p>Auto Takeoff starts gliding as soon as you are falling with an elytra on, so you never
 * have to double jump.
 */
@SuppressWarnings("unused")
public class ElytraFly extends NomnomModule {
    public enum Mode {CONTROL, BOOST}

    /** Handle for MixinLivingEntity, which can't get module instances injected. */
    public static ElytraFly INSTANCE;

    private final Setting<Mode> mode = enumSetting("Mode", Mode.CONTROL);
    private final Setting<Double> speed = precise("Speed", 1.8, 0.5, 6.0);
    private final Setting<Double> verticalSpeed = precise("Vertical Speed", 0.8, 0.1, 3.0);
    private final Setting<Boolean> hover = bool("Hover", true);
    private final Setting<Boolean> lookVertical = bool("Look Vertical", false);
    private final Setting<Boolean> autoTakeoff = bool("Auto Takeoff", true);
    private final Setting<Boolean> antiKinetic = bool("Anti Kinetic", true);

    // Flying into a wall deals (speed lost - ...) * 10 - 3 damage, so a drop of 0.3 blocks per
    // tick or less is harmless. Above that, a collision hurts.
    private static final double KINETIC_SAFE_SPEED = 0.3;

    public ElytraFly(Nomnom nomnom) {
        super(nomnom, "ElytraFly", Category.MOVEMENT, "Controlled elytra flight");
        INSTANCE = this;
    }

    /**
     * True while vanilla elytra physics should be skipped for {@code entity}. Used by the
     * {@code calcGlidingVelocity} hook in MixinLivingEntity.
     */
    public static boolean overridesPhysics(Entity entity) {
        ElytraFly module = INSTANCE;
        return module != null
                && module.isEnabled()
                && module.mode.getValue() == Mode.CONTROL
                && entity == module.mc.player
                && module.mc.player.isGliding();
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        ClientPlayerEntity player = event.getPlayer();

        if (!player.isGliding()) {
            takeOff(player);
            return;
        }

        if (mode.getValue() == Mode.CONTROL) {
            control(player);
        } else {
            boost(player);
        }

        if (antiKinetic.getValue()) {
            preventImpact(player);
        }
    }

    /**
     * Looks one tick ahead. If the move we are about to make would run into a wall fast enough
     * to hurt, it is shortened to the largest move that doesn't touch anything, so you stop
     * right at the wall instead of hitting it.
     */
    private void preventImpact(ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();

        if (Math.hypot(velocity.x, velocity.z) <= KINETIC_SAFE_SPEED) {
            return;
        }

        Box box = player.getBoundingBox();

        if (mc.world.isSpaceEmpty(player, box.stretch(velocity.x, 0.0, velocity.z))) {
            return;
        }

        // Binary search for the longest fraction of the move that stays clear.
        double clear = 0.0;
        double blocked = 1.0;

        for (int i = 0; i < 8; i++) {
            double middle = (clear + blocked) / 2.0;

            if (mc.world.isSpaceEmpty(player, box.stretch(velocity.x * middle, 0.0, velocity.z * middle))) {
                clear = middle;
            } else {
                blocked = middle;
            }
        }

        // BOOST still gets vanilla physics applied on top, which can add a little speed, so
        // keep a bit of distance there. CONTROL moves exactly the velocity we set.
        if (mode.getValue() == Mode.BOOST) {
            clear *= 0.9;
        }

        player.setVelocity(velocity.x * clear, velocity.y, velocity.z * clear);
    }

    private void takeOff(ClientPlayerEntity player) {
        if (!autoTakeoff.getValue()
                || player.isOnGround()
                || player.getVelocity().y >= 0.0
                || !LivingEntity.canGlideWith(player.getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST)) {
            return;
        }

        // Same thing vanilla does when you press jump in the air: start gliding on the client
        // and tell the server.
        if (player.checkGliding()) {
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private void control(ClientPlayerEntity player) {
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double strafe = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);

        double x = 0.0;
        double y = player.getVelocity().y;
        double z = 0.0;

        if (forward != 0 || strafe != 0) {
            double length = Math.hypot(forward, strafe);
            forward /= length;
            strafe /= length;

            double yaw = Math.toRadians(player.getYaw());
            double flat = speed.getValue();

            if (lookVertical.getValue() && forward != 0) {
                // Move along the full look direction, pitch included.
                double pitch = Math.toRadians(player.getPitch());
                double cos = Math.cos(pitch);
                y = -Math.sin(pitch) * flat * forward;
                flat *= cos;
            } else if (hover.getValue()) {
                y = 0.0;
            }

            x = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * flat;
            z = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * flat;
        } else if (hover.getValue()) {
            y = 0.0;
        }

        boolean up = mc.options.jumpKey.isPressed();
        boolean down = mc.options.sneakKey.isPressed();

        if (up && !down) {
            y = verticalSpeed.getValue();
        } else if (down && !up) {
            y = -verticalSpeed.getValue();
        }

        player.setVelocity(x, y, z);
    }

    private void boost(ClientPlayerEntity player) {
        if (!mc.options.forwardKey.isPressed()) {
            return;
        }

        // The same acceleration a firework rocket applies, with Speed as the target speed.
        Vec3d look = player.getRotationVector();
        Vec3d velocity = player.getVelocity();
        double target = speed.getValue();

        player.setVelocity(velocity.add(
                look.x * 0.1 + (look.x * target - velocity.x) * 0.5,
                look.y * 0.1 + (look.y * target - velocity.y) * 0.5,
                look.z * 0.1 + (look.z * target - velocity.z) * 0.5
        ));
    }
}