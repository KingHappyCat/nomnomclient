package net.staro.nomnom.module.modules;

import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.RegistryKeys;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("unused")
public class AutoCrystal extends NomnomModule {

    public enum TargetMode {NEAREST, LOWEST_HEALTH, WEAKEST_ARMOR}

    // --- General ---
    private final Setting<Boolean> autoPlace = bool("AutoPlace", true);
    private final Setting<Boolean> autoBreak = bool("AutoBreak", true);
    private final Setting<Integer> placeRange = integer("PlaceRange", 4, 1, 6);
    private final Setting<Integer> breakRange = integer("BreakRange", 5, 1, 6);
    private final Setting<Integer> targetRange = integer("TargetRange", 8, 2, 12);
    private final Setting<Integer> maxPlacementsPerTick = integer("MaxPlacementsPerTick", 3, 1, 8);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> mobs = bool("Mobs", true);
    private final Setting<TargetMode> targetMode = enumSetting("TargetMode", TargetMode.NEAREST);

    // --- Speed ---
    // Remove the crystal from the client world as soon as we hit it. This stops us hitting the
    // same ghost crystal every tick and frees the spot so a new crystal can be placed on it in
    // the same tick.
    private final Setting<Boolean> instantRemove = bool("InstantRemove", true);
    // Minimum ticks between two hits on the same crystal (only matters if it is still around).
    private final Setting<Integer> breakDelay = integer("BreakDelay", 2, 0, 5);
    // Send a silent rotation packet to the crystal / block before each action, then restore.
    private final Setting<Boolean> rotate = bool("Rotate", true);

    // --- Damage ---
    // Only damage dealt to the target is checked, never self damage.
    private final Setting<Double> minDamage = precise("MinDamage", 4.0, 0.5, 20.0);
    // Ticks of target movement to predict ahead when calculating damage.
    private final Setting<Integer> predict = integer("Predict", 1, 0, 5);
    // Spots that would kill the target (damage >= health + absorption) are used first.
    private final Setting<Boolean> lethalPriority = bool("LethalPriority", true);
    // When the target's health + absorption is at or below this, MinDamage drops to 1 so
    // any spot that hurts them is used (face placing). 0 disables it.
    private final Setting<Integer> facePlaceHealth = integer("FacePlaceHealth", 8, 0, 36);
    // Read the target's real Protection / Blast Protection enchants from their armor.
    private final Setting<Boolean> readEnchants = bool("ReadEnchants", true);
    // Used when ReadEnchants is off: 16 is Protection IV everywhere, 20 is the cap.
    private final Setting<Integer> assumedProtection = integer("AssumedProtection", 16, 0, 20);

    // --- Obsidian ---
    // When no valid crystal spot exists, place an obsidian base (needs obsidian in the hotbar)
    // on the best spot and crystal it on the following tick.
    private final Setting<Boolean> obsidianBase = bool("ObsidianBase", true);

    // --- Double pop ---
    // After the target pops a totem the server ignores any damage that doesn't beat their last
    // hit for ~10 ticks, so breaking early is wasted. We hold breaking (but keep placing, so
    // crystals are armed) and release right as that window ends.
    private final Setting<Boolean> doublePop = bool("DoublePop", true);
    private final Setting<Integer> popDelay = integer("PopDelay", 10, 0, 20);
    private final Setting<Boolean> pingComp = bool("PingComp", true);
    private final Setting<Boolean> debug = bool("Debug", false);

    private static final double POP_TRACK_RANGE = 12.0;
    private static final int TOTEM_POP_STATUS = 35;
    private static final double CRYSTAL_POWER = 6.0;
    private static final double REACH = 4.5;
    private static final int RESTOCK_THRESHOLD = 10;
    private static final int RESTOCK_COOLDOWN_TICKS = 5;
    private static final long POP_FORGET_NANOS = 5_000_000_000L;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record Received(Packet<?> packet, long nanos) {
    }

    private record Placement(BlockPos base, double damage, boolean lethal, Box crystalBox) {
    }

    // Written on the network thread (queue) and drained on the client tick.
    private final Queue<Received> pending = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Long> popNanos = new HashMap<>();
    private final Map<Integer, Double> swapAverageMs = new HashMap<>();
    private final Map<Integer, Long> lastHitTick = new ConcurrentHashMap<>();

    private long tickCounter;
    private long holdUntilTick = -1;
    private long lastRestockTick = -100;
    private int lastTargetId = -1;
    private Vec3d lastTargetPos = Vec3d.ZERO;
    private double protectionReduction;
    private boolean rotated;

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
        pending.clear();
        popNanos.clear();
        lastHitTick.clear();
        holdUntilTick = -1;
        lastTargetId = -1;
        rotated = false;
    }

    // ------------------------------------------------------------------ packets

    @Listener
    public void onPacket(PacketEvent.Receive event) {
        // Network thread: only queue the packet, it is resolved on the client tick.
        if (!doublePop.getValue()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        boolean pop = packet instanceof EntityStatusS2CPacket status
                && status.getStatus() == TOTEM_POP_STATUS;

        if (pop || packet instanceof EntityEquipmentUpdateS2CPacket) {
            pending.add(new Received(packet, System.nanoTime()));

            if (pending.size() > 512) {
                pending.poll();
            }
        }
    }

    // --------------------------------------------------------------------- tick

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null
                || mc.getNetworkHandler() == null) {
            return;
        }

        try {
            run();
        } finally {
            restoreRotation();
        }
    }

    private void run() {
        tickCounter++;
        processPending();

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
            lastTargetId = -1;
            return;
        }

        // Where the target will roughly be when our crystal explodes.
        Vec3d position = positionOf(target);
        Vec3d velocity = target.getId() == lastTargetId
                ? position.subtract(lastTargetPos)
                : Vec3d.ZERO;
        if (velocity.lengthSquared() > 1.0) {
            velocity = velocity.normalize();
        }
        lastTargetId = target.getId();
        lastTargetPos = position;
        Vec3d predicted = position.add(velocity.multiply(predict.getValue()));

        protectionReduction = protectionReductionOf(target);

        double healthTotal = target.getHealth() + target.getAbsorptionAmount();
        boolean facePlace = facePlaceHealth.getValue() > 0
                && healthTotal <= facePlaceHealth.getValue();
        double requiredDamage = facePlace ? 1.0 : minDamage.getValue();

        if (autoBreak.getValue() && !holding) {
            breakCrystals(target, predicted, requiredDamage);
        }

        if (autoPlace.getValue()) {
            placeCrystals(target, predicted, requiredDamage, healthTotal);
        }
    }

    // ------------------------------------------------------- pops and swap timing

    private void processPending() {
        long now = System.nanoTime();
        Received received;

        while ((received = pending.poll()) != null) {
            if (received.packet() instanceof EntityStatusS2CPacket status) {
                handlePop(status, received.nanos());
            } else if (received.packet() instanceof EntityEquipmentUpdateS2CPacket equipment) {
                handleEquipment(equipment, received.nanos());
            }
        }

        popNanos.values().removeIf(time -> now - time > POP_FORGET_NANOS);
    }

    private void handlePop(EntityStatusS2CPacket packet, long arrivedAt) {
        Entity entity = packet.getEntity(mc.world);
        if (entity == null
                || entity == mc.player
                || mc.player.squaredDistanceTo(entity) > POP_TRACK_RANGE * POP_TRACK_RANGE) {
            return;
        }

        popNanos.put(entity.getId(), arrivedAt);

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

    /** Measures how long a player takes to put a new totem in their offhand after popping. */
    private void handleEquipment(EntityEquipmentUpdateS2CPacket packet, long arrivedAt) {
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
                debugMessage(String.format("%s re-equipped a totem in %.0f ms (average %.0f ms)",
                        name, delayMs, average));

                popNanos.remove(id);
                return;
            }
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

    // ----------------------------------------------------------------- breaking

    private void breakCrystals(LivingEntity target, Vec3d predicted, double requiredDamage) {
        double range = breakRange.getValue();

        EndCrystalEntity best = null;
        double bestDamage = requiredDamage;

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity.isAlive()
                        && mc.player.squaredDistanceTo(entity) <= range * range
        )) {
            Long lastHit = lastHitTick.get(crystal.getId());
            if (lastHit != null && tickCounter - lastHit <= breakDelay.getValue()) {
                continue;
            }

            double damage = crystalDamage(target, predicted, positionOf(crystal));

            if (damage >= bestDamage) {
                best = crystal;
                bestDamage = damage;
            }
        }

        if (best == null) {
            return;
        }

        faceSilently(best.getBoundingBox().getCenter());

        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);

        lastHitTick.put(best.getId(), tickCounter);
        if (lastHitTick.size() > 64) {
            lastHitTick.values().removeIf(tick -> tickCounter - tick > 40);
        }

        if (instantRemove.getValue()) {
            mc.world.removeEntity(best.getId(), Entity.RemovalReason.KILLED);
        }
    }

    // ----------------------------------------------------------------- placing

    private void placeCrystals(LivingEntity target, Vec3d predicted,
                               double requiredDamage, double healthTotal) {
        boolean offhandCrystal =
                mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);

        int crystalSlot = findHotbarSlot(Items.END_CRYSTAL);

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

            double damage = crystalDamage(target, predicted, explosionCentre);

            if (damage < requiredDamage) {
                continue;
            }

            candidates.add(new Placement(
                    base.toImmutable(), damage, damage >= healthTotal, crystalBox));
        }

        if (candidates.isEmpty()) {
            if (obsidianBase.getValue()) {
                placeObsidianBase(target, predicted, requiredDamage);
            }
            return;
        }

        boolean lethalFirst = lethalPriority.getValue();
        candidates.sort((a, b) -> {
            if (lethalFirst && a.lethal() != b.lethal()) {
                return a.lethal() ? -1 : 1;
            }
            return Double.compare(b.damage(), a.damage());
        });

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

        Hand hand = offhandCrystal ? Hand.OFF_HAND : Hand.MAIN_HAND;

        Runnable placeAll = () -> {
            for (Placement placement : chosen) {
                Vec3d hitPoint = Vec3d.ofCenter(placement.base()).add(0, 0.5, 0);
                BlockHitResult hit = new BlockHitResult(
                        hitPoint,
                        Direction.UP,
                        placement.base(),
                        false
                );

                faceSilently(hitPoint);
                mc.interactionManager.interactBlock(mc.player, hand, hit);
                mc.player.swingHand(hand);
            }
        };

        if (offhandCrystal) {
            placeAll.run();
        } else {
            withSilentSlot(crystalSlot, placeAll);
        }
    }

    /** Places one obsidian block where a crystal on top of it would hurt the target most. */
    private void placeObsidianBase(LivingEntity target, Vec3d predicted, double requiredDamage) {
        int obsidianSlot = findHotbarSlot(Items.OBSIDIAN);
        if (obsidianSlot == -1) {
            return;
        }

        int range = placeRange.getValue();
        BlockPos targetPos = target.getBlockPos();
        Vec3d eye = mc.player.getEyePos();

        BlockHitResult bestHit = null;
        double bestDamage = requiredDamage;

        for (BlockPos base : BlockPos.iterate(
                targetPos.add(-range, -2, -range),
                targetPos.add(range, 2, range))) {

            if (mc.player.squaredDistanceTo(Vec3d.ofCenter(base)) > range * range) {
                continue;
            }

            if (!mc.world.getBlockState(base).isAir()) {
                continue;
            }

            BlockPos above = base.up();

            if (!mc.world.getBlockState(above).isAir()
                    || !mc.world.getBlockState(above.up()).isAir()) {
                continue;
            }

            // Nobody (including the target) may be standing where the block or crystal goes.
            Box space = new Box(
                    base.getX(), base.getY(), base.getZ(),
                    base.getX() + 1, base.getY() + 3, base.getZ() + 1
            );

            if (!mc.world.getOtherEntities(null, space).isEmpty()) {
                continue;
            }

            Vec3d explosionCentre = new Vec3d(
                    above.getX() + 0.5,
                    above.getY(),
                    above.getZ() + 0.5
            );

            double damage = crystalDamage(target, predicted, explosionCentre);

            if (damage <= bestDamage) {
                continue;
            }

            BlockHitResult hit = supportHit(base, eye);

            if (hit == null) {
                continue;
            }

            bestHit = hit;
            bestDamage = damage;
        }

        if (bestHit == null) {
            return;
        }

        BlockHitResult hit = bestHit;

        withSilentSlot(obsidianSlot, () -> {
            faceSilently(hit.getPos());
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        });
    }

    /** A click on a neighbouring solid block that places a block at {@code base}. */
    private BlockHitResult supportHit(BlockPos base, Vec3d eye) {
        for (Direction direction : Direction.values()) {
            BlockPos support = base.offset(direction);

            if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) {
                continue;
            }

            Direction face = direction.getOpposite();
            Vec3d point = Vec3d.ofCenter(support).add(
                    face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5,
                    face.getOffsetZ() * 0.5
            );

            if (eye.squaredDistanceTo(point) > REACH * REACH) {
                continue;
            }

            return new BlockHitResult(point, face, support, false);
        }

        return null;
    }

    // ------------------------------------------------------------ silent helpers

    /**
     * Silent hotbar switch: one packet to the slot before the action and one back afterwards.
     * The client never actually changes its selected slot.
     */
    private void withSilentSlot(int slot, Runnable action) {
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

    /** Tells the server we are looking at {@code point} without moving the player's camera. */
    private void faceSilently(Vec3d point) {
        if (!rotate.getValue()) {
            return;
        }

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

    /** Puts the server's idea of our rotation back to the real one. */
    private void restoreRotation() {
        if (!rotated || mc.player == null || mc.getNetworkHandler() == null) {
            rotated = false;
            return;
        }

        sendLook(mc.player.getYaw(), mc.player.getPitch());
        rotated = false;
    }

    private void sendLook(float yaw, float pitch) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    // ------------------------------------------------------------------- damage

    /**
     * Damage a crystal exploding at {@code centre} deals to {@code target} standing at
     * {@code targetPos}, following the vanilla explosion formula: distance falloff, exposure,
     * difficulty, armor, resistance and enchantment protection. Absorption is ignored.
     */
    private double crystalDamage(LivingEntity target, Vec3d targetPos, Vec3d centre) {
        double diameter = CRYSTAL_POWER * 2.0;
        double distance = targetPos.distanceTo(centre) / diameter;

        if (distance > 1.0) {
            return 0.0;
        }

        double impact = (1.0 - distance) * exposure(centre, target, targetPos);
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

        // Enchantment protection (computed once per tick for the target).
        damage *= 1.0 - protectionReduction;

        return Math.max(0.0, damage);
    }

    /**
     * Fraction (0..1) of the damage removed by the target's Protection / Blast Protection.
     * Protection counts 1 per level and Blast Protection 2 per level, capped at 20 points.
     */
    private double protectionReductionOf(LivingEntity target) {
        if (!(target instanceof PlayerEntity)) {
            return 0.0;
        }

        if (readEnchants.getValue()) {
            try {
                var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                var protection = registry.getOrThrow(Enchantments.PROTECTION);
                var blastProtection = registry.getOrThrow(Enchantments.BLAST_PROTECTION);

                int points = 0;
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    ItemStack stack = target.getEquippedStack(slot);

                    if (stack.isEmpty()) {
                        continue;
                    }

                    points += EnchantmentHelper.getLevel(protection, stack);
                    points += 2 * EnchantmentHelper.getLevel(blastProtection, stack);
                }

                return Math.min(20, points) / 25.0;
            } catch (RuntimeException ignored) {
                // Fall through to the assumed value.
            }
        }

        return Math.min(20, assumedProtection.getValue()) / 25.0;
    }

    /** Fraction of sample points on the target's hitbox that the explosion can reach. */
    private float exposure(Vec3d source, Entity entity, Vec3d entityPos) {
        Box box = entity.getBoundingBox().offset(entityPos.subtract(positionOf(entity)));

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

    private static Vec3d positionOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    // ---------------------------------------------------------------- inventory

    private int findHotbarSlot(Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(item)) {
                return slot;
            }
        }

        return -1;
    }

    /**
     * Tops the hotbar up with crystals from the main inventory. Shift-click merges into the
     * existing hotbar stack instead of swapping stacks back and forth, and it only runs every
     * few ticks so it can't spam inventory packets.
     */
    private void restockCrystals() {
        if (tickCounter - lastRestockTick < RESTOCK_COOLDOWN_TICKS) {
            return;
        }

        var inventory = mc.player.getInventory();
        int hotbarCrystals = 0;

        for (int slot = 0; slot < 9; slot++) {
            var stack = inventory.getStack(slot);

            if (stack.isOf(Items.END_CRYSTAL)) {
                hotbarCrystals += stack.getCount();
            }
        }

        if (hotbarCrystals > RESTOCK_THRESHOLD) {
            return;
        }

        for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
            if (!inventory.getStack(inventorySlot).isOf(Items.END_CRYSTAL)) {
                continue;
            }

            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    inventorySlot,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
            );

            lastRestockTick = tickCounter;
            return;
        }
    }

    // ----------------------------------------------------------------- targeting

    private LivingEntity findTarget() {
        double range = targetRange.getValue();

        var candidates = mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity != mc.player
                        && entity.isAlive()
                        && isValidTarget(entity)
                        && mc.player.squaredDistanceTo(entity) <= range * range
        );

        return candidates.stream().min((a, b) -> switch (targetMode.getValue()) {
            case NEAREST -> Double.compare(
                    mc.player.squaredDistanceTo(a),
                    mc.player.squaredDistanceTo(b));
            case LOWEST_HEALTH -> Double.compare(
                    a.getHealth() + a.getAbsorptionAmount(),
                    b.getHealth() + b.getAbsorptionAmount());
            case WEAKEST_ARMOR -> Double.compare(
                    a.getAttributeValue(EntityAttributes.ARMOR),
                    b.getAttributeValue(EntityAttributes.ARMOR));
        }).orElse(null);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            return players.getValue() && !nomnom.friendManager().isFriend(player);
        }
        return mobs.getValue();
    }
}