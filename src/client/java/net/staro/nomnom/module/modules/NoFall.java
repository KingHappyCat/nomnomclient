package net.staro.nomnom.module.modules;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.UpdateEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/**
 * Tells the server you are on the ground once you have fallen far enough,
 * which makes it reset your fall damage.
 */
@SuppressWarnings("unused")
public class NoFall extends NomnomModule {
    private final Setting<Double> distance = precise("Distance", 2.5, 1.0, 10.0);

    public NoFall(Nomnom nomnom) {
        super(nomnom, "NoFall", Category.MOVEMENT, "Prevents fall damage");
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        ClientPlayerEntity player = event.getPlayer();
        if (mc.getNetworkHandler() == null
                || player.isGliding()
                || player.isOnGround()
                || player.getAbilities().allowFlying) {
            return;
        }

        if (player.fallDistance > distance.getValue()) {
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
            player.fallDistance = 0;
        }
    }
}
