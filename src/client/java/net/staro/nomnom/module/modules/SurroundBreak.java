package net.staro.nomnom.module.modules;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import net.staro.nomnom.utility.BlockPlacer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mines a block out of an enemy's surround so a crystal can reach their feet.
 *
 * <p>While the target stands in a closed ring of blocks, crystals can only hurt them through
 * the gaps above. This picks the side block that breaks fastest, and whose floor can still hold
 * a crystal, and mines it with packets: it tells the server we started digging, counts up the
 * same break progress the game would, and sends the finish packet the moment it is enough. The
 * block is gone one round trip later and AutoCrystal places on the freed spot by itself, so keep
 * AutoCrystal on.
 *
 * <p>The best pickaxe in the hotbar is used without the client ever switching to it: the server
 * is told to hold it only for the finish packet. Both packets are preceded by a silent rotation
 * to the block, because anticheats check that you are looking at what you mine.
 *
 * <p>LEGIT finishes at full progress, exactly like the normal client. FAST finishes at the point
 * where the server still accepts the block as mined (it allows a little early), which is about
 * a quarter quicker, but a server with a stricter check will refuse it and the mining restarts.
 */
@SuppressWarnings("unused")
public class SurroundBreak extends NomnomModule {
    public enum Mode {LEGIT, FAST}

    private static final Direction[] SIDES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    // Server break check: delta * ticks must reach 0.7. Our own count is one tick behind it, and
    // the extra margin covers one tick of timing jitter between our two packets.
    private static final double FAST_PROGRESS = 0.75;
    private static final double LEGIT_PROGRESS = 1.0;
    private static final int COOLDOWN_TICKS = 4;

    private final Setting<Mode> mode = enumSetting("Mode", Mode.LEGIT);
    private final Setting<Double> range = precise("Range", 4.5, 2.0, 6.0);
    private final Setting<Integer> targetRange = integer("TargetRange", 8, 2, 12);
    // Use the best pickaxe in the hotbar (silently).
    private final Setting<Boolean> pickaxe = bool("Pickaxe", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    // Mine blocks you can't see (through walls). The server only checks reach, but anticheats
    // like Grim check that you are looking at the block. Off = only blocks with a visible face.
    private final Setting<Boolean> walls = bool("Walls", true);
    // Only mine when the target is fully walled in. With an open side AutoCrystal can already
    // place there.
    private final Setting<Boolean> onlyEnclosed = bool("OnlyEnclosed", true);
    // Only mine blocks that have obsidian or bedrock under them, so a crystal can go there.
    private final Setting<Boolean> requireBase = bool("RequireBase", true);
    // Don't open spots closer than this to you (the crystal would hurt you too).
    private final Setting<Double> minDistance = precise("MinDistance", 2.5, 1.0, 6.0);
    private final Setting<Boolean> debug = bool("Debug", false);

    /** Handle for AutoCrystal, which reads {@link #opening()}. */
    public static SurroundBreak INSTANCE;

    private final BlockPlacer placer;

    private BlockPos miningPos;
    private Direction miningFace;
    private int pickSlot;
    private double progress;
    private boolean finishSent;
    private long finishTick;
    private long tickCounter;
    private long cooldownUntil;

    public SurroundBreak(Nomnom nomnom) {
        super(nomnom, "SurroundBreak", Category.COMBAT, "Mines a block out of the target's surround");
        this.placer = new BlockPlacer(mc);
        INSTANCE = this;
    }

    /**
     * The block whose finish packet has been sent and that the server should have broken, or
     * null. AutoCrystal uses it to place a crystal on the spot before our own client has seen
     * the block disappear.
     */
    public BlockPos opening() {
        return isEnabled() && miningPos != null && finishSent ? miningPos : null;
    }

    /** Tick on which {@link #opening()} was sent, to tell one opening from the next. */
    public long openingSince() {
        return finishTick;
    }

    @Override
    public void onDisable() {
        abort();
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return;
        }

        tickCounter++;

        try {
            run();
        } finally {
            placer.finish();
        }
    }

    private void run() {
        PlayerEntity target = findTarget();

        if (miningPos != null) {
            continueMining(target);
            return;
        }

        if (target == null || tickCounter < cooldownUntil) {
            return;
        }

        startMining(target);
    }

    // ------------------------------------------------------------------ mining

    private void startMining(PlayerEntity target) {
        Plan plan = plan(target);

        if (plan == null) {
            return;
        }

        miningPos = plan.pos();
        miningFace = plan.face();
        pickSlot = plan.slot();
        progress = 0.0;
        finishSent = false;

        if (rotate.getValue()) {
            placer.faceSilently(faceCentre(miningPos, miningFace));
        }

        send(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, miningPos, miningFace);
        mc.player.swingHand(Hand.MAIN_HAND);

        debugMessage(String.format("Mining %s, about %d ticks", miningPos.toShortString(), plan.ticks()));
    }

    private void continueMining(PlayerEntity target) {
        var state = mc.world.getBlockState(miningPos);

        // The block is gone: done. AutoCrystal takes it from here.
        if (state.isAir() || state.isReplaceable()) {
            debugMessage("Block " + miningPos.toShortString() + " is open");
            miningPos = null;
            cooldownUntil = tickCounter + COOLDOWN_TICKS;
            return;
        }

        boolean stillWanted = target != null
                && isSideOf(target, miningPos)
                && eyeDistance(faceCentre(miningPos, miningFace)) <= range.getValue() + 0.5;

        if (!stillWanted) {
            abort();
            return;
        }

        if (finishSent) {
            // The server should have broken it by now. If it didn't, start over.
            if (tickCounter - finishTick > pingTicks() + 8) {
                debugMessage("Server refused " + miningPos.toShortString() + ", restarting");
                miningPos = null;
                cooldownUntil = tickCounter + COOLDOWN_TICKS;
            }
            return;
        }

        progress += delta(miningPos, pickSlot);

        double needed = mode.getValue() == Mode.FAST ? FAST_PROGRESS : LEGIT_PROGRESS;
        if (progress < needed) {
            return;
        }

        BlockPos pos = miningPos;
        Direction face = miningFace;

        placer.withSilentSlot(pickSlot, () -> {
            if (rotate.getValue()) {
                placer.faceSilently(faceCentre(pos, face));
            }

            send(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, face);
            mc.player.swingHand(Hand.MAIN_HAND);
        });

        finishSent = true;
        finishTick = tickCounter;
    }

    /** Stops mining and tells the server, if a block was being dug. */
    private void abort() {
        if (miningPos != null && !finishSent && mc.getNetworkHandler() != null) {
            send(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, miningPos, miningFace);
        }

        miningPos = null;
        finishSent = false;
    }

    private void send(PlayerActionC2SPacket.Action action, BlockPos pos, Direction face) {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, face));
    }

    // ---------------------------------------------------------------- planning

    private record Plan(BlockPos pos, Direction face, int slot, int ticks) {
    }

    /** The side block of {@code target}'s surround that opens fastest, or null. */
    private Plan plan(PlayerEntity target) {
        Set<BlockPos> occupied = occupiedColumns(target);

        if (onlyEnclosed.getValue() && !enclosed(occupied)) {
            return null;
        }

        double needed = mode.getValue() == Mode.FAST ? FAST_PROGRESS : LEGIT_PROGRESS;
        Plan best = null;

        for (BlockPos column : occupied) {
            for (Direction side : SIDES) {
                BlockPos pos = column.offset(side);

                if (occupied.contains(pos)) {
                    continue;
                }

                var state = mc.world.getBlockState(pos);

                // Nothing to mine, or it can't be mined (bedrock).
                if (state.isAir() || state.isReplaceable() || state.getHardness(mc.world, pos) < 0.0f) {
                    continue;
                }

                if (requireBase.getValue()) {
                    var floor = mc.world.getBlockState(pos.down());

                    if (!floor.isOf(Blocks.OBSIDIAN) && !floor.isOf(Blocks.BEDROCK)) {
                        continue;
                    }
                }

                if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos)) < minDistance.getValue()) {
                    continue;
                }

                Direction face = visibleFace(pos);
                if (face == null) {
                    continue;
                }

                int slot = bestSlot(pos);
                double delta = delta(pos, slot);

                if (delta <= 0.0) {
                    continue;
                }

                int ticks = (int) Math.ceil(needed / delta);

                if (best == null || ticks < best.ticks()) {
                    best = new Plan(pos.toImmutable(), face, slot, ticks);
                }
            }
        }

        return best;
    }

    /** Every block column the target's hitbox touches, at feet height. */
    private Set<BlockPos> occupiedColumns(PlayerEntity target) {
        Box box = target.getBoundingBox();
        int y = MathHelper.floor(box.minY + 1.0E-3);
        Set<BlockPos> occupied = new LinkedHashSet<>();

        for (int x = MathHelper.floor(box.minX + 1.0E-3); x <= MathHelper.floor(box.maxX - 1.0E-3); x++) {
            for (int z = MathHelper.floor(box.minZ + 1.0E-3); z <= MathHelper.floor(box.maxZ - 1.0E-3); z++) {
                occupied.add(new BlockPos(x, y, z));
            }
        }

        return occupied;
    }

    /** True if every block next to the target at feet height is solid. */
    private boolean enclosed(Set<BlockPos> occupied) {
        for (BlockPos column : occupied) {
            for (Direction side : SIDES) {
                BlockPos pos = column.offset(side);

                if (!occupied.contains(pos) && mc.world.getBlockState(pos).isReplaceable()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isSideOf(PlayerEntity target, BlockPos pos) {
        for (BlockPos column : occupiedColumns(target)) {
            for (Direction side : SIDES) {
                if (column.offset(side).equals(pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ---------------------------------------------------------------- the block

    /**
     * The nearest face of {@code pos} in reach that we can see from here (any face in reach
     * with Walls on), or null.
     */
    private Direction visibleFace(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Direction best = null;
        double bestDistance = range.getValue() * range.getValue();

        for (Direction direction : Direction.values()) {
            Vec3d point = faceCentre(pos, direction);
            double distance = eye.squaredDistanceTo(point);

            if (distance > bestDistance) {
                continue;
            }

            // Through walls the nearest face in reach will do.
            if (walls.getValue()) {
                bestDistance = distance;
                best = direction;
                continue;
            }

            // A ray that ends just inside the face must hit this block and nothing before it.
            Vec3d inside = Vec3d.ofCenter(pos).add(
                    direction.getOffsetX() * 0.49,
                    direction.getOffsetY() * 0.49,
                    direction.getOffsetZ() * 0.49);

            var hit = mc.world.raycast(new RaycastContext(
                    eye, inside,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));

            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                bestDistance = distance;
                best = direction;
            }
        }

        return best;
    }

    private static Vec3d faceCentre(BlockPos pos, Direction face) {
        return Vec3d.ofCenter(pos).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
    }

    private double eyeDistance(Vec3d point) {
        return mc.player.getEyePos().distanceTo(point);
    }

    /** Hotbar slot with the fastest tool for {@code pos}, or the held slot. */
    private int bestSlot(BlockPos pos) {
        int held = mc.player.getInventory().getSelectedSlot();

        if (!pickaxe.getValue()) {
            return held;
        }

        int best = held;
        float bestDelta = delta(pos, held);

        for (int slot = 0; slot < 9; slot++) {
            float delta = delta(pos, slot);

            // Only switch for a clear gain, otherwise there is no point in sending packets.
            if (delta > bestDelta * 1.05f) {
                bestDelta = delta;
                best = slot;
            }
        }

        return best;
    }

    /**
     * Break progress per tick for {@code pos} with the item in {@code slot}. The game works it
     * out from the selected slot, so it is selected for the calculation and put straight back.
     */
    private float delta(BlockPos pos, int slot) {
        var inventory = mc.player.getInventory();
        int held = inventory.getSelectedSlot();

        try {
            inventory.setSelectedSlot(slot);
            return mc.world.getBlockState(pos).calcBlockBreakingDelta(mc.player, mc.world, pos);
        } finally {
            inventory.setSelectedSlot(held);
        }
    }

    // ---------------------------------------------------------------- targeting

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDistance = targetRange.getValue() * (double) targetRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || nomnom.friendManager().isFriend(player)) {
                continue;
            }

            double distance = mc.player.squaredDistanceTo(player);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }

        return best;
    }

    /** Round trip time in ticks, rounded down. 0 in singleplayer. */
    private int pingTicks() {
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency() / 50;
    }

    private void debugMessage(String message) {
        if (debug.getValue() && mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("[SurroundBreak] " + message), false);
        }
    }
}