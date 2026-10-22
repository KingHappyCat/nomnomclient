package net.staro.nomnom.events;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.packet.Packet;

public class PacketEvent {
    @Getter
    @Setter
    public static class Receive extends CancellableEvent {
        private Packet<?> packet;
    }

    @Getter
    @Setter
    public static class Send extends CancellableEvent {
        private Packet<?> packet;
    }

}

