
package net.staro.nomnom.module.modules;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

@SuppressWarnings("unused")
public class AutoCrystal extends NomnomModule {
    private final Setting<Boolean> autoPlace =
            bool("AutoPlace", true);

    private final Setting<Boolean> autoBreak =
            bool("AutoBreak", true);

    private final Setting<Integer> placeRange =
            integer("PlaceRange", 4, 1, 6);

    private final Setting<Integer> breakRange =
            integer("BreakRange", 5, 1, 6);

    private static final int RESTOCK_THRESHOLD = 10;

    public AutoCrystal(Nomnom nomnom) {
        super(nomnom, "AutoCrystal", Category.COMBAT,
                "Automatically places and breaks end crystals");
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null
                || mc.interactionManager == null
                || mc.getNetworkHandler() == null) {
            return;
        }

        restockCrystals();

        if (autoBreak.getValue()) {
            breakCrystals();
        }

        if (autoPlace.getValue()) {
            placeCrystals();
        }
    }

    private void breakCrystals() {
        double range = breakRange.getValue();

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity.isAlive()
                        && mc.player.squaredDistanceTo(entity)
                        <= range * range)) {

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

        LivingEntity target = findTarget();

        if (target == null) {
            return;
        }

        int range = placeRange.getValue();
        BlockPos targetPos = target.getBlockPos();

        for (BlockPos base : BlockPos.iterate(
                targetPos.add(-range, -2, -range),
                targetPos.add(range, 2, range))) {

            if (mc.player.squaredDistanceTo(Vec3d.ofCenter(base))
                    > range * range) {
                continue;
            }

            if (!mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                    && !mc.world.getBlockState(base).isOf(Blocks.BEDROCK)) {
                continue;
            }

            BlockPos above = base.up();

            boolean oneBlockHigh = mc.world.getBlockState(above).isAir();
            boolean twoBlocksHigh = oneBlockHigh
                    && mc.world.getBlockState(above.up()).isAir();

            if (!oneBlockHigh) {
                continue;
            }

            Box crystalBox = new Box(
                    above.getX(), above.getY(), above.getZ(),
                    above.getX() + 1, above.getY() + 2,
                    above.getZ() + 1);

            if (!mc.world.getOtherEntities(null, crystalBox).isEmpty()) {
                continue;
            }

            BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(base).add(0, 0.5, 0),
                    Direction.UP,
                    base,
                    false);

            if (offhandCrystal) {
                mc.interactionManager.interactBlock(
                        mc.player, Hand.OFF_HAND, hit);
                mc.player.swingHand(Hand.OFF_HAND);
            } else {
                int previousSlot =
                        mc.player.getInventory().getSelectedSlot();

                try {
                    // Select the crystal slot on the server without
                    // changing the selected slot displayed in the HUD.
                    mc.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(crystalSlot));

                    mc.interactionManager.interactBlock(
                            mc.player, Hand.MAIN_HAND, hit);

                    mc.player.swingHand(Hand.MAIN_HAND);
                } finally {
                    mc.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(previousSlot));
                }
            }

            return;
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

        // Count crystals across the 36 main inventory slots.
        for (int slot = 0; slot < 36; slot++) {
            var stack = inventory.getStack(slot);

            if (stack.isOf(Items.END_CRYSTAL)) {
                totalCrystals += stack.getCount();
            }
        }

        if (totalCrystals > RESTOCK_THRESHOLD) {
            return;
        }

        // Refill an existing hotbar crystal stack from the main inventory.
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            var hotbarStack = inventory.getStack(hotbarSlot);

            if (!hotbarStack.isOf(Items.END_CRYSTAL)) {
                continue;
            }

            for (int inventorySlot = 9; inventorySlot < 36;
                 inventorySlot++) {

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
                                && !(entity instanceof PlayerEntity)
                                && mc.player.squaredDistanceTo(entity)
                                <= range * range
                ).stream()
                .min((a, b) -> Double.compare(
                        mc.player.squaredDistanceTo(a),
                        mc.player.squaredDistanceTo(b)))
                .orElse(null);
    }
}
