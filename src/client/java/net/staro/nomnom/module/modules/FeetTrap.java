package net.staro.nomnom.module.modules;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import net.staro.nomnom.utility.BlockPlacer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walls you in with obsidian: a block on every side of your feet, so crystals and players can't
 * get at you from the side.
 *
 * <p>It works from the blocks your hitbox really covers, so standing on the edge between two
 * blocks (or in a corner of four) still gets a complete ring. Sides facing the nearest enemy
 * are filled first, and anything that can't be placed because nothing is there to click on gets
 * a support block underneath first. Missing blocks are replaced the next tick, so a block that
 * gets mined or blown up is back straight away.
 *
 * <p>Height 2 adds a second layer on top, which stops crystals being placed on top of the ring
 * and exploding beside your upper body (face placing). Roof also covers the block above your
 * head.
 */
@SuppressWarnings("unused")
public class FeetTrap extends NomnomModule {
    private static final Direction[] SIDES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final double ENEMY_RANGE = 16.0;

    private final Setting<Integer> blocksPerTick = integer("BlocksPerTick", 4, 1, 8);
    // 2 = a second layer on top of the ring, against crystals placed on top of it.
    private final Setting<Integer> height = integer("Height", 1, 1, 2);
    // Also cover the block above your head.
    private final Setting<Boolean> roof = bool("Roof", false);
    // Place a block underneath first when there is nothing to click on.
    private final Setting<Boolean> support = bool("Support", true);
    // Use ender chests when you have no obsidian in the hotbar.
    // Click on the empty spot itself when there is nothing to click on, instead of building a
    // support block. Faster, but anticheats like Grim flag clicks on air.
    private final Setting<Boolean> airPlace = bool("AirPlace", false);
    private final Setting<Boolean> enderChests = bool("EnderChests", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Double> range = precise("Range", 4.5, 2.0, 6.0);
    // Turn off when you leave the block you enabled it on.
    private final Setting<Boolean> disableOnMove = bool("DisableOnMove", false);

    private final BlockPlacer placer;
    // Positions we sent a placement for. The block only shows up on our client after the
    // server answers, so without this the same position would be placed again every tick.
    private final Map<BlockPos, Long> recent = new HashMap<>();
    private BlockPos anchor;
    private long tickCounter;

    public FeetTrap(Nomnom nomnom) {
        super(nomnom, "FeetTrap", Category.COMBAT, "Surrounds your feet with obsidian");
        this.placer = new BlockPlacer(mc);
    }

    @Override
    public void onEnable() {
        anchor = mc.player == null ? null : mc.player.getBlockPos();
        recent.clear();
    }

    @Override
    public void onDisable() {
        anchor = null;
        recent.clear();
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null
                || mc.interactionManager == null || mc.getNetworkHandler() == null) {
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
        BlockPos feet = mc.player.getBlockPos();

        if (anchor == null) {
            anchor = feet;
        }

        if (disableOnMove.getValue() && !feet.equals(anchor)) {
            disable();
            return;
        }

        // In the air there is no ring to build.
        if (!mc.player.isOnGround()) {
            return;
        }

        int slot = placer.findHotbarSlot(Items.OBSIDIAN);
        if (slot == -1 && enderChests.getValue()) {
            slot = placer.findHotbarSlot(Items.ENDER_CHEST);
        }
        if (slot == -1) {
            return;
        }

        List<BlockPos> missing = missingBlocks(feet);
        if (missing.isEmpty()) {
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        double reach = range.getValue();
        int budget = blocksPerTick.getValue();
        int pending = pingTicks() + 2;

        List<BlockPos> positions = new ArrayList<>();
        List<BlockHitResult> hits = new ArrayList<>();

        for (BlockPos pos : missing) {
            if (positions.size() >= budget) {
                break;
            }

            Long placedAt = recent.get(pos);
            if (placedAt != null && tickCounter - placedAt < pending) {
                continue;
            }

            BlockHitResult hit = placer.findHit(pos, eye, reach);

            if (hit == null && airPlace.getValue()) {
                hit = placer.airHit(pos, eye, reach);
            }

            if (hit != null) {
                positions.add(pos);
                hits.add(hit);
                continue;
            }

            // Nothing to click on: build the block underneath it instead, then this one next tick.
            if (support.getValue()) {
                BlockPos below = pos.down();
                Long supportedAt = recent.get(below);

                if ((supportedAt == null || tickCounter - supportedAt >= pending)
                        && canPlaceAt(below)) {
                    BlockHitResult supportHit = placer.findHit(below, eye, reach);

                    if (supportHit != null) {
                        positions.add(below);
                        hits.add(supportHit);
                    }
                }
            }
        }

        if (positions.isEmpty()) {
            return;
        }

        boolean spin = rotate.getValue();

        placer.withSilentSlot(slot, () -> {
            for (int i = 0; i < positions.size(); i++) {
                placer.place(hits.get(i), spin);
                recent.put(positions.get(i), tickCounter);
            }
        });

        if (recent.size() > 64) {
            recent.values().removeIf(tick -> tickCounter - tick > 40);
        }
    }

    /**
     * Blocks that should be solid around you but aren't (and can be filled), nearest to the
     * closest enemy first and lowest layer first.
     */
    private List<BlockPos> missingBlocks(BlockPos feet) {
        Box box = mc.player.getBoundingBox();
        int y = feet.getY();

        // Every block column the hitbox touches. 1 block when you stand in the middle.
        Set<BlockPos> occupied = new LinkedHashSet<>();
        for (int x = MathHelper.floor(box.minX + 1.0E-3); x <= MathHelper.floor(box.maxX - 1.0E-3); x++) {
            for (int z = MathHelper.floor(box.minZ + 1.0E-3); z <= MathHelper.floor(box.maxZ - 1.0E-3); z++) {
                occupied.add(new BlockPos(x, y, z));
            }
        }

        record Wanted(BlockPos pos, int layer) {
        }

        Set<BlockPos> seen = new LinkedHashSet<>();
        List<Wanted> wanted = new ArrayList<>();

        for (int layer = 0; layer < height.getValue(); layer++) {
            for (BlockPos column : occupied) {
                for (Direction side : SIDES) {
                    BlockPos pos = column.offset(side).up(layer);

                    if (!occupied.contains(column.offset(side)) && seen.add(pos)) {
                        wanted.add(new Wanted(pos, layer));
                    }
                }
            }
        }

        if (roof.getValue()) {
            for (BlockPos column : occupied) {
                BlockPos pos = column.up(2);

                if (seen.add(pos)) {
                    wanted.add(new Wanted(pos, height.getValue()));
                }
            }
        }

        Vec3d enemy = nearestEnemy();

        wanted.sort(Comparator
                .comparingInt(Wanted::layer)
                .thenComparingDouble(entry -> enemy == null
                        ? 0.0
                        : Vec3d.ofCenter(entry.pos()).squaredDistanceTo(enemy)));

        List<BlockPos> missing = new ArrayList<>();
        for (Wanted entry : wanted) {
            if (canPlaceAt(entry.pos())) {
                missing.add(entry.pos());
            }
        }

        return missing;
    }

    /** True if the block at {@code pos} is open to build in and nobody is standing there. */
    private boolean canPlaceAt(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) {
            return false;
        }

        // A crystal, player or mob in the way stops the placement.
        return mc.world.getOtherEntities(
                null,
                new Box(pos),
                entity -> entity.isAlive()
                        && !entity.isSpectator()
                        && (entity instanceof LivingEntity || entity.getType() == EntityType.END_CRYSTAL)
        ).isEmpty();
    }

    private Vec3d nearestEnemy() {
        PlayerEntity nearest = null;
        double best = ENEMY_RANGE * ENEMY_RANGE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || nomnom.friendManager().isFriend(player)) {
                continue;
            }

            double distance = mc.player.squaredDistanceTo(player);

            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }

        return nearest == null ? null : new Vec3d(nearest.getX(), nearest.getY(), nearest.getZ());
    }

    /** Round trip time in ticks, rounded down. 0 in singleplayer. */
    private int pingTicks() {
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency() / 50;
    }
}