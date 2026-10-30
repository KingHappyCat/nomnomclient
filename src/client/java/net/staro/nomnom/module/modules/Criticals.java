package net.staro.nomnom.module.modules;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.PacketEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/**
 * Makes every melee hit a critical hit without jumping, by telling the server you were
 * briefly in the air right before the attack.
 *
 * <p>Two small hops are sent as position packets just before the attack packet leaves.
 * Your real position never changes: the last packet puts you back where you were.
 * Servers with a movement anticheat may flag the fake hop, so {@code Strict} uses smaller
 * steps that look more like a real fall.
 */
@SuppressWarnings("unused")
public class Criticals extends NomnomModule {
    public enum Mode {
        /** Two hops: 0.0625 up, then back down. Fast and works on vanilla servers. */
        PACKET(new double[]{0.0625, 0.0}),
        /** Three smaller hops that stay under what most anticheats consider a jump. */
        STRICT(new double[]{0.11, 0.1100013579, 0.0000013579});

        private final double[] offsets;

        Mode(double[] offsets) {
            this.offsets = offsets;
        }
    }

    private final Setting<Mode> mode = enumSetting("Mode", Mode.PACKET);
    // Only fake it when the target would not already be a crit, so a real jump hit is untouched.
    private final Setting<Boolean> onlyOnGround = bool("OnlyOnGround", true);

    public Criticals(Nomnom nomnom) {
        super(nomnom, "Criticals", Category.COMBAT, "Every hit is a critical hit");
    }

    @Listener
    public void onPacketSend(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet)) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.getNetworkHandler() == null || !isAttack(packet)) {
            return;
        }

        // A hit that is already a crit (falling) or that cannot be one (water, ladder, riding,
        // gliding, blindness) gains nothing from the fake hop.
        if (player.isTouchingWater() || player.isInLava() || player.isClimbing()
                || player.hasVehicle() || player.isGliding()
                || player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)) {
            return;
        }
        if (onlyOnGround.getValue() && !player.isOnGround()) {
            return;
        }

        Vec3d pos = player.getEntityPos();
        boolean collision = player.horizontalCollision;

        for (double offset : mode.getValue().offsets) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    pos.x, pos.y + offset, pos.z, false, collision));
        }
    }

    /** True when the packet is an attack, not a right click on an entity. */
    private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        boolean[] attack = {false};

        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(Hand hand) {
            }

            @Override
            public void interactAt(Hand hand, Vec3d pos) {
            }

            @Override
            public void attack() {
                attack[0] = true;
            }
        });

        return attack[0];
    }
}