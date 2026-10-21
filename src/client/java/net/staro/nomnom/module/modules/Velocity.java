package net.staro.nomnom.module.modules;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.api.event.listener.SafeListener;
import net.staro.nomnom.events.CancellableEvent;
import net.staro.nomnom.events.PacketEvent;
import net.staro.nomnom.mixin.network.IExplosionS2CPacket;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

import java.util.Optional;

@SuppressWarnings("unused")
public class Velocity extends NomnomModule {
    private final Setting<Boolean> pushOutOfBlocks = bool("PushOutOfBlocks", true);
    private final Setting<Boolean> entityPush = bool("EntityPush", true);

    public Velocity(Nomnom nomnom) {
        super(nomnom, "Velocity", Category.MOVEMENT, "Manipulates the velocity");
    }

    @SuppressWarnings({"DataFlowIssue"})
    @SafeListener
    public void onEntityVelocityUpdateS2CPacket(PacketEvent.Receive event) {
        var player = mc.player;
        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket) {
            if (((EntityVelocityUpdateS2CPacket) event.getPacket()).getEntityId() == player.getId()) {
                event.setCancelled(true);
            }
        }
    }

    @Listener
    public void onPacketReceived(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ExplosionS2CPacket) {
            ((IExplosionS2CPacket) event.getPacket()).setPlayerKnockback(Optional.empty());
        }
    }

    @Listener
    public void onPushOutOfBlocks(PushOutOfBlocks event) {
        event.setCancelled(pushOutOfBlocks.getValue());
    }

    @Listener
    public void onEntityPush(EntityPush event) {
        event.setCancelled(entityPush.getValue());
    }

    public static final class PushOutOfBlocks extends CancellableEvent {}

    public static final class EntityPush extends CancellableEvent {}

}