package net.staro.nomnom.module.modules;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
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
import net.staro.nomnom.events.PacketEvent;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("unused")
public class AutoCrystal extends NomnomModule {

    private final Setting<Boolean> autoPlace = bool("AutoPlace", true);
    private final Setting<Boolean> autoBreak = bool("AutoBreak", true);
    private final Setting<Integer> placeRange = integer("PlaceRange", 4, 1, 6);
    private final Setting<Integer> breakRange = integer("BreakRange", 5, 1, 6);
    private final Setting<Integer> targetRange = integer("TargetRange", 8, 2, 12);
    private final Setting<Integer> maxPlacementsPerTick = integer("MaxPlacementsPerTick", 3, 1, 8);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> mobs = bool("Mobs", true);

    // Damage calculation. Only the damage dealt to the target is checked, never self damage.
    // Crystals (placed or broken) must deal at least MinDamage to the target, and placements
    // are ordered by damage so the strongest spots are used first.
    private final Setting<Double> minDamage = precise("MinDamage", 4.0, 0.5, 20.0);
    // Enchantment protection can't be read reliably on the client, so assume a value for
    // players: 16 is Protection IV on every piece, 20 (the cap) is Blast Protection IV.
    private final Setting<Integer> assumedProtection = integer("AssumedProtection", 16, 0, 20);

    // Double pop timing: after the target pops a totem the server ignores any damage that
    // doesn't beat their last hit for ~10 ticks, so breaking early is wasted. We hold breaking
    // (but keep placing, so crystals are armed) and release right as that window ends.
    private final Setting<Boolean> doublePop = bool("DoublePop", true);
    private final Setting<Integer> popDelay = integer("PopDelay", 10, 0, 20);
    private final Setting<Boolean> pingComp = bool("PingComp", true);
    private final Setting<Boolean> debug = bool("Debug", false);

    private static final double POP_TRACK_RANGE = 12.0;
    private static final int TOTEM_POP_STATUS = 35;
    private static final double CRYSTAL_POWER = 6.0;

    private final Queue<EntityStatusS2CPacket> pendingStatus = new ConcurrentLinkedQueue<>();
    private long tickCounter;
    private long holdUntilTick = -1;

    private static final int RESTOCK_THRESHOLD = 10;

    private record Placement(BlockPos base, Box crystalBox, double damage) {
    }

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

        LivingEntity target = findTarget();
        if (target == null) {
            return;
        }

        if (autoBreak.getValue() && !holding) {
            breakCrystals(target);
        }

        if (autoPlace.getValue()) {
            placeCrystals(target);
        }
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

    private void breakCrystals(LivingEntity target) {
        double range = breakRange.getValue();

        EndCrystalEntity best = null;
        double bestDamage = minDamage.getValue();

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity.isAlive()
                        && mc.player.squaredDistanceTo(entity) <= range * range
        )) {
            double damage = crystalDamage(
                    target,
                    new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ())
            );

            if (damage >= bestDamage) {
                best = crystal;
                bestDamage = damage;
            }
        }

        if (best == null) {
            return;
        }

        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void placeCrystals(LivingEntity target) {
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

        int range = placeRange.getValue();
        BlockPos targetPos = target.getBlockPos();

        // Collect every valid spot with the damage it would deal, then take the best ones.
        List<Placement> candidates = new ArrayList<>();

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

            Vec3d explosionCentre = new Vec3d(
                    above.getX() + 0.5,
                    above.getY(),
                    above.getZ() + 0.5
            );

            double damage = crystalDamage(target, explosionCentre);

            if (damage < minDamage.getValue()) {
                continue;
            }

            candidates.add(new Placement(base.toImmutable(), crystalBox, damage));
        }

        candidates.sort(Comparator.comparingDouble(Placement::damage).reversed());

        // Avoid requesting placements with overlapping crystal spaces.
        List<Placement> chosen = new ArrayList<>();

        for (Placement candidate : candidates) {
            if (chosen.size() >= placementLimit) {
                break;
            }

            boolean overlapsAttempt = false;

            for (Placement other : chosen) {
                if (other.crystalBox().intersects(candidate.crystalBox())) {
                    overlapsAttempt = true;
                    break;
                }
            }

            if (!overlapsAttempt) {
                chosen.add(candidate);
            }
        }

        if (chosen.isEmpty()) {
            return;
        }

        // Silent hotbar switch: one packet to the crystal slot before all placements and one
        // back afterwards (two packets per tick in total, not two per placement). The client
        // never actually changes its selected slot.
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = !offhandCrystal && crystalSlot != previousSlot;

        try {
            if (switched) {
                mc.getNetworkHandler().sendPacket(
                        new UpdateSelectedSlotC2SPacket(crystalSlot)
                );
            }

            Hand hand = offhandCrystal ? Hand.OFF_HAND : Hand.MAIN_HAND;

            for (Placement placement : chosen) {
                BlockHitResult hit = new BlockHitResult(
                        Vec3d.ofCenter(placement.base()).add(0, 0.5, 0),
                        Direction.UP,
                        placement.base(),
                        false
                );

                mc.interactionManager.interactBlock(mc.player, hand, hit);
                mc.player.swingHand(hand);
            }
        } finally {
            if (switched) {
                mc.getNetworkHandler().sendPacket(
                        new UpdateSelectedSlotC2SPacket(previousSlot)
                );
            }
        }
    }

    /**
     * Damage a crystal exploding at {@code centre} deals to {@code target}, following the
     * vanilla explosion formula: distance falloff, exposure, difficulty, armor, resistance
     * and (assumed) enchantment protection. Absorption is ignored.
     */
    private double crystalDamage(LivingEntity target, Vec3d centre) {
        double diameter = CRYSTAL_POWER * 2.0;
        double distance = Math.sqrt(target.squaredDistanceTo(centre)) / diameter;

        if (distance > 1.0) {
            return 0.0;
        }

        double impact = (1.0 - distance) * exposure(centre, target);
        double damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0);

        if (target instanceof PlayerEntity) {
            damage = switch (mc.world.getDifficulty()) {
                case PEACEFUL -> 0.0;
                case EASY -> Math.min(damage / 2.0 + 1.0, damage);
                case NORMAL -> damage;
                case HARD -> damage * 1.5;
            };
        }

        // Armor and toughness.
        double armor = target.getAttributeValue(EntityAttributes.ARMOR);
        double toughness = target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        double reduction = MathHelper.clamp(
                armor - damage / (2.0 + toughness / 4.0),
                armor * 0.2,
                20.0
        );
        damage *= 1.0 - reduction / 25.0;

        // Resistance effect.
        StatusEffectInstance resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            damage *= Math.max(0, 25 - (resistance.getAmplifier() + 1) * 5) / 25.0;
        }

        // Enchantment protection, assumed for players.
        if (target instanceof PlayerEntity) {
            damage *= 1.0 - Math.min(20, assumedProtection.getValue()) / 25.0;
        }

        return Math.max(0.0, damage);
    }

    /** Fraction of sample points on the target's hitbox that the explosion can reach. */
    private float exposure(Vec3d source, Entity entity) {
        Box box = entity.getBoundingBox();

        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);

        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            return 0.0f;
        }

        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

        int reached = 0;
        int total = 0;

        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    Vec3d point = new Vec3d(
                            MathHelper.lerp(x, box.minX, box.maxX) + offsetX,
                            MathHelper.lerp(y, box.minY, box.maxY),
                            MathHelper.lerp(z, box.minZ, box.maxZ) + offsetZ
                    );

                    HitResult hit = mc.world.raycast(new RaycastContext(
                            point,
                            source,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            entity
                    ));

                    if (hit.getType() == HitResult.Type.MISS) {
                        reached++;
                    }

                    total++;
                }
            }
        }

        return total == 0 ? 0.0f : (float) reached / total;
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
        double range = targetRange.getValue();

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