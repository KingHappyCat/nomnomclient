package net.staro.nomnom.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Silent block placement: switches the server's held slot for the placement and back again
 * without ever changing what the client has selected, and optionally looks at the block
 * with a rotation packet that is undone afterwards. Call {@link #finish()} at the end of
 * every tick that placed something. Client tick thread only.
 */
public class BlockPlacer {
    private final MinecraftClient mc;
    private boolean rotated;

    public BlockPlacer(MinecraftClient mc) {
        this.mc = mc;
    }

    /** Hotbar slot holding {@code item}, or -1. */
    public int findHotbarSlot(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    /**
     * A click on a neighbouring solid block that places a block at {@code pos}, or null if
     * there is nothing to click on within {@code reach}. Blocks that open something when
     * clicked (chests, furnaces...) are not used, because the click would open them instead.
     */
    public BlockHitResult findHit(BlockPos pos, Vec3d eye, double reach) {
        BlockHitResult best = null;
        double bestDistance = reach * reach;

        for (Direction direction : Direction.values()) {
            BlockPos support = pos.offset(direction);
            var state = mc.world.getBlockState(support);

            if (!state.isSolidBlock(mc.world, support)
                    || state.hasBlockEntity()
                    || state.createScreenHandlerFactory(mc.world, support) != null) {
                continue;
            }

            Direction face = direction.getOpposite();
            Vec3d point = Vec3d.ofCenter(support).add(
                    face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5,
                    face.getOffsetZ() * 0.5
            );

            double distance = eye.squaredDistanceTo(point);

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = new BlockHitResult(point, face, support, false);
            }
        }

        return best;
    }

    /**
     * "Air place": a click on the empty position itself, for when there is no neighbouring
     * block to click on. Vanilla servers accept it, but anticheats that look for clicks on air
     * (Grim has one) flag it. Null if {@code pos} is out of reach.
     */
    public BlockHitResult airHit(BlockPos pos, Vec3d eye, double reach) {
        Vec3d point = Vec3d.ofCenter(pos);

        if (eye.squaredDistanceTo(point) > reach * reach) {
            return null;
        }

        return new BlockHitResult(point, Direction.UP, pos, false);
    }

    /** Runs {@code action} with the server's held slot switched to {@code slot}. */
    public void withSilentSlot(int slot, Runnable action) {
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = slot != previousSlot;

        try {
            if (switched) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            action.run();
        } finally {
            if (switched) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
        }
    }

    /** Clicks {@code hit} with the held item (the slot must already be switched). */
    public void place(BlockHitResult hit, boolean rotate) {
        if (rotate) {
            faceSilently(hit.getPos());
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Puts the server's idea of our rotation back to the real one, if it was changed. */
    public void finish() {
        if (!rotated) {
            return;
        }

        rotated = false;

        if (mc.player != null && mc.getNetworkHandler() != null) {
            sendLook(mc.player.getYaw(), mc.player.getPitch());
        }
    }

    private void faceSilently(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.hypot(dx, dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        sendLook(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f));
        rotated = true;
    }

    private void sendLook(float yaw, float pitch) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }
}