package net.staro.nomnom.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Tracks totem pops of nearby players for the double pop timing.
 *
 * <p>After a pop the server ignores any damage that doesn't beat the player's last hit for
 * about 10 ticks, so breaking crystals early is wasted. {@link #holding} tells the caller to
 * wait, and the wait shrinks by the ping so the hit lands right as the window ends. It also
 * measures how long each player takes to put a new totem in their offhand.
 *
 * <p>{@link #onPacket} may be called from the network thread, everything else from the
 * client tick.
 */
public class PopTracker {
    private static final int TOTEM_POP_STATUS = 35;
    private static final double TRACK_RANGE = 12.0;
    private static final long FORGET_NANOS = 5_000_000_000L;
    private static final int MAX_PENDING = 512;

    private record Received(Packet<?> packet, long nanos) {
    }

    private final MinecraftClient mc;

    // Filled on the network thread, drained on the client tick.
    private final Queue<Received> pending = new ConcurrentLinkedQueue<>();
    // Client tick thread only.
    private final Map<Integer, Long> popNanos = new HashMap<>();
    private final Map<Integer, Double> swapAverageMs = new HashMap<>();
    private long holdUntilTick = -1;

    public PopTracker(MinecraftClient mc) {
        this.mc = mc;
    }

    /** Queues pop and equipment packets. Safe to call from the network thread. */
    public void onPacket(Packet<?> packet) {
        boolean pop = packet instanceof EntityStatusS2CPacket status
                && status.getStatus() == TOTEM_POP_STATUS;

        if (pop || packet instanceof EntityEquipmentUpdateS2CPacket) {
            pending.add(new Received(packet, System.nanoTime()));

            if (pending.size() > MAX_PENDING) {
                pending.poll();
            }
        }
    }

    /** Resolves queued packets. Call once per client tick. */
    public void process(long tick, int popDelay, boolean pingComp, Consumer<String> log) {
        long now = System.nanoTime();
        Received received;

        while ((received = pending.poll()) != null) {
            if (received.packet() instanceof EntityStatusS2CPacket status) {
                handlePop(status, received.nanos(), tick, popDelay, pingComp, log);
            } else if (received.packet() instanceof EntityEquipmentUpdateS2CPacket equipment) {
                handleEquipment(equipment, received.nanos(), log);
            }
        }

        popNanos.values().removeIf(time -> now - time > FORGET_NANOS);
    }

    /** True while crystals should not be broken. Logs once when the hold ends. */
    public boolean holding(long tick, boolean enabled, Consumer<String> log) {
        if (holdUntilTick < 0) {
            return false;
        }

        if (enabled && tick < holdUntilTick) {
            return true;
        }

        holdUntilTick = -1;
        log.accept("Hold over, breaking crystals");
        return false;
    }

    public void reset() {
        pending.clear();
        popNanos.clear();
        holdUntilTick = -1;
    }

    /** Round trip time in ticks, rounded down. 0 in singleplayer. */
    public int pingTicks() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return 0;
        }

        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency() / 50;
    }

    private void handlePop(EntityStatusS2CPacket packet, long arrivedAt, long tick,
                           int popDelay, boolean pingComp, Consumer<String> log) {
        Entity entity = packet.getEntity(mc.world);
        if (entity == null
                || entity == mc.player
                || mc.player.squaredDistanceTo(entity) > TRACK_RANGE * TRACK_RANGE) {
            return;
        }

        popNanos.put(entity.getId(), arrivedAt);

        // The pop packet reaches us about half a round trip late and our attack needs the
        // other half to get back, so the wait shrinks by the ping (floored, to stay safe).
        int wait = popDelay;
        if (pingComp) {
            wait -= pingTicks();
        }
        wait = Math.max(0, wait);

        holdUntilTick = tick + wait;
        log.accept("Pop on " + entity.getName().getString() + ", holding breaks for " + wait + " ticks");
    }

    /** Measures how long a player takes to put a new totem in their offhand after popping. */
    private void handleEquipment(EntityEquipmentUpdateS2CPacket packet, long arrivedAt,
                                 Consumer<String> log) {
        int id = packet.getEntityId();
        Long poppedAt = popNanos.get(id);
        if (poppedAt == null) {
            return;
        }

        for (var pair : packet.getEquipmentList()) {
            if (pair.getFirst() == EquipmentSlot.OFFHAND
                    && pair.getSecond().isOf(Items.TOTEM_OF_UNDYING)) {
                double delayMs = (arrivedAt - poppedAt) / 1_000_000.0;
                double average = swapAverageMs.merge(id, delayMs, (old, now) -> old * 0.5 + now * 0.5);

                Entity entity = mc.world.getEntityById(id);
                String name = entity == null ? "player" : entity.getName().getString();
                log.accept(String.format("%s re-equipped a totem in %.0f ms (average %.0f ms)",
                        name, delayMs, average));

                popNanos.remove(id);
                return;
            }
        }
    }
}