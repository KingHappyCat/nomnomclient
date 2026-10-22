package net.staro.nomnom.module.modules;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.PacketEvent;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.Locale;

@SuppressWarnings("unused")
public class AutoCrystal extends NomnomModule {

    private final Setting<Boolean> autoPlace = bool("AutoPlace", true);
    private final Setting<Boolean> autoBreak = bool("AutoBreak", true);
    private final Setting<Integer> placeRange = integer("PlaceRange", 4, 1, 6);
    private final Setting<Integer> breakRange = integer("BreakRange", 5, 1, 6);
    private final Setting<Integer> maxPlacementsPerTick = integer("MaxPlacementsPerTick", 3, 1, 8);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> mobs = bool("Mobs", true);

    // Double pop timing: after the target pops a totem the server ignores any damage that
    // doesn't beat their last hit for ~10 ticks, so breaking early is wasted. We hold breaking
    // (but keep placing, so crystals are armed) and release right as that window ends.
    private final Setting<Boolean> doublePop = bool("DoublePop", true);
    private final Setting<Integer> popDelay = integer("PopDelay", 10, 0, 20);
    private final Setting<Boolean> pingComp = bool("PingComp", true);
    private final Setting<Boolean> debug = bool("Debug", false);

    private static final double POP_TRACK_RANGE = 12.0;
    private static final int TOTEM_POP_STATUS = 35;

    private final Queue<EntityStatusS2CPacket> pendingStatus = new ConcurrentLinkedQueue<>();
    private long tickCounter;
    private long holdUntilTick = -1;

    private static final int RESTOCK_THRESHOLD = 10;
    private static final long STATS_INTERVAL_NS = 1_000_000_000L;

    // Performance statistics
    private long statsWindowStart = System.nanoTime();
    private int placeAttemptsWindow;
    private int breakAttemptsWindow;

    public AutoCrystal(Nomnom nomnom) {
        super(
                nomnom,
                "AutoCrystal",
                Category.COMBAT,
                "Automatically places and breaks end crystals"
        );
    }

    @Override
    public void onDisable() {
        pendingStatus.clear();
        holdUntilTick = -1;
    }

    @Listener
    public void onPacket(PacketEvent.Receive event) {
        // Network thread: only queue the packet, it is resolved on the client tick.
        if (doublePop.getValue()
                && event.getPacket() instanceof EntityStatusS2CPacket packet
                && packet.getStatus() == TOTEM_POP_STATUS) {
            pendingStatus.add(packet);
        }
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null
                || mc.getNetworkHandler() == null) {
            return;
        }

        tickCounter++;
        processPops();

        boolean holding = false;
        if (holdUntilTick >= 0) {
            if (doublePop.getValue() && tickCounter < holdUntilTick) {
                holding = true;
            } else {
                holdUntilTick = -1;
                debugMessage("Hold over, breaking crystals");
            }
        }

        restockCrystals();

        if (autoBreak.getValue() && !holding) {
            breakCrystals();
        }

        if (autoPlace.getValue()) {
            placeCrystals();
        }

        reportStatsIfDue();
    }

    private void processPops() {
        EntityStatusS2CPacket packet;
        while ((packet = pendingStatus.poll()) != null) {
            Entity entity = packet.getEntity(mc.world);
            if (entity == null
                    || entity == mc.player
                    || mc.player.squaredDistanceTo(entity) > POP_TRACK_RANGE * POP_TRACK_RANGE) {
                continue;
            }

            // The pop packet reaches us about half a round trip late and our attack needs the
            // other half to get back, so the wait shrinks by the ping (floored, to stay safe).
            int wait = popDelay.getValue();
            if (pingComp.getValue()) {
                wait -= pingTicks();
            }
            wait = Math.max(0, wait);

            holdUntilTick = tickCounter + wait;
            debugMessage("Pop on " + entity.getName().getString() + ", holding breaks for " + wait + " ticks");
        }
    }

    private int pingTicks() {
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency() / 50;
    }

    private void debugMessage(String message) {
        if (debug.getValue() && mc.player != null) {
            mc.player.sendMessage(Text.literal("[AutoCrystal] " + message), false);
        }
    }

    private void breakCrystals() {
        double range = breakRange.getValue();

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity.isAlive()
                        && mc.player.squaredDistanceTo(entity) <= range * range
        )) {
            breakAttemptsWindow++;

            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
    }

    private void placeCrystals() {
        boolean offhandCrystal =
                mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);

        int crystalSlot = findCrystalSlot();

        if (!offhandCrystal && crystalSlot == -1) {
            return;
        }

        int availableCrystals = offhandCrystal
                ? mc.player.getOffHandStack().getCount()
                : mc.player.getInventory().getStack(crystalSlot).getCount();

        int placementLimit = Math.min(
                maxPlacementsPerTick.getValue(),
                availableCrystals
        );

        if (placementLimit <= 0) {
            return;
        }

        LivingEntity target = findTarget();

        if (target == null) {
            return;
        }

        int range = placeRange.getValue();
        BlockPos targetPos = target.getBlockPos();

        int placementsThisTick = 0;
        List<Box> attemptedBoxes = new ArrayList<>();

        for (BlockPos base : BlockPos.iterate(
                targetPos.add(-range, -2, -range),
                targetPos.add(range, 2, range))) {

            if (placementsThisTick >= placementLimit) {
                break;
            }

            if (mc.player.squaredDistanceTo(Vec3d.ofCenter(base))
                    > range * range) {
                continue;
            }

            if (!mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                    && !mc.world.getBlockState(base).isOf(Blocks.BEDROCK)) {
                continue;
            }

            BlockPos above = base.up();

            // Require space for the crystal.
            if (!mc.world.getBlockState(above).isAir()
                    || !mc.world.getBlockState(above.up()).isAir()) {
                continue;
            }

            Box crystalBox = new Box(
                    above.getX(),
                    above.getY(),
                    above.getZ(),
                    above.getX() + 1,
                    above.getY() + 2,
                    above.getZ() + 1
            );

            if (!mc.world.getOtherEntities(null, crystalBox).isEmpty()) {
                continue;
            }

            // Avoid requesting placements with overlapping crystal spaces.
            boolean overlapsAttempt = false;

            for (Box attemptedBox : attemptedBoxes) {
                if (attemptedBox.intersects(crystalBox)) {
                    overlapsAttempt = true;
                    break;
                }
            }

            if (overlapsAttempt) {
                continue;
            }

            BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(base).add(0, 0.5, 0),
                    Direction.UP,
                    base,
                    false
            );

            // Count attempts, not server-confirmed successful placements.
            placeAttemptsWindow++;

            if (offhandCrystal) {
                mc.interactionManager.interactBlock(
                        mc.player, Hand.OFF_HAND, hit
                );
                mc.player.swingHand(Hand.OFF_HAND);
            } else {
                int previousSlot =
                        mc.player.getInventory().getSelectedSlot();

                try {
                    mc.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(crystalSlot)
                    );

                    mc.interactionManager.interactBlock(
                            mc.player, Hand.MAIN_HAND, hit
                    );

                    mc.player.swingHand(Hand.MAIN_HAND);
                } finally {
                    mc.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(previousSlot)
                    );
                }
            }

            attemptedBoxes.add(crystalBox);
            placementsThisTick++;
        }
    }

    private int findCrystalSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot)
                    .isOf(Items.END_CRYSTAL)) {
                return slot;
            }
        }

        return -1;
    }

    private void restockCrystals() {
        var inventory = mc.player.getInventory();
        int totalCrystals = 0;

        for (int slot = 0; slot < 36; slot++) {
            var stack = inventory.getStack(slot);

            if (stack.isOf(Items.END_CRYSTAL)) {
                totalCrystals += stack.getCount();
            }
        }

        if (totalCrystals > RESTOCK_THRESHOLD) {
            return;
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            var hotbarStack = inventory.getStack(hotbarSlot);

            if (!hotbarStack.isOf(Items.END_CRYSTAL)) {
                continue;
            }

            for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
                var inventoryStack = inventory.getStack(inventorySlot);

                if (!inventoryStack.isOf(Items.END_CRYSTAL)) {
                    continue;
                }

                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        inventorySlot,
                        hotbarSlot,
                        SlotActionType.SWAP,
                        mc.player
                );

                return;
            }
        }
    }

    private LivingEntity findTarget() {
        double range = placeRange.getValue();

        return mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity != mc.player
                        && entity.isAlive()
                        && isValidTarget(entity)
                        && mc.player.squaredDistanceTo(entity) <= range * range
        ).stream().min((a, b) -> Double.compare(
                mc.player.squaredDistanceTo(a),
                mc.player.squaredDistanceTo(b)
        )).orElse(null);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            return players.getValue() && !nomnom.friendManager().isFriend(player);
        }
        return mobs.getValue();
    }
}