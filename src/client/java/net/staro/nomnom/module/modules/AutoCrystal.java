package net.staro.nomnom.module.modules;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
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
import net.staro.nomnom.utility.CrystalDamage;
import net.staro.nomnom.utility.HitWindow;
import net.staro.nomnom.utility.MotionPredictor;
import net.staro.nomnom.utility.PopTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Crystal PvP.
 *
 * <p>What it does each tick:
 * <ol>
 *   <li>Picks targets and works out where they will be when the crystal goes off (measured
 *       velocity, run through the world collision).</li>
 *   <li>Breaks crystals that hurt the target enough and hurt you little enough, with up to
 *       MaxBreaks hits per tick, and also the moment a crystal spawns (SpawnBreak), not on the
 *       next tick.</li>
 *   <li>Places crystals on the best spots. The search sorts every spot by a cheap upper bound
 *       of its damage and only does the expensive raycasts for the most promising ones, then
 *       stops as soon as nothing left can beat what it has.</li>
 * </ol>
 *
 * <p>Scoring: damage to the target, plus a share of the damage to the other targets, minus
 * SelfWeight times the damage to you. Spots that would hurt you more than your limit, would
 * leave you under SafeHealth, or would hurt a friend are dropped, and a spot that kills the
 * target is allowed to cost you more (LethalSelfDamage).
 *
 * <p>Timing: after a hit a player ignores damage up to that hit for half a second, and after a
 * totem pop for a bit longer. Breaks wait until a crystal would do real damage again (HitWindow,
 * DoublePop) while placing carries on, so fresh crystals are ready the moment the window ends.
 */
@SuppressWarnings("unused")
public class AutoCrystal extends NomnomModule {

    public enum TargetMode {NEAREST, LOWEST_HEALTH, WEAKEST_ARMOR}

    // --- General ---
    private final Setting<Boolean> autoPlace = bool("AutoPlace", true);
    private final Setting<Boolean> autoBreak = bool("AutoBreak", true);
    private final Setting<Integer> placeRange = integer("PlaceRange", 4, 1, 6);
    private final Setting<Integer> breakRange = integer("BreakRange", 5, 1, 6);
    // Beyond these distances a target spot / crystal must be in plain sight. Anticheats are much
    // stricter about reaching through walls.
    private final Setting<Double> placeWallRange = precise("PlaceWallRange", 3.5, 1.0, 6.0);
    private final Setting<Double> breakWallRange = precise("BreakWallRange", 3.5, 1.0, 6.0);
    private final Setting<Integer> targetRange = integer("TargetRange", 8, 2, 12);
    private final Setting<Integer> maxPlacementsPerTick = integer("MaxPlacementsPerTick", 3, 1, 8);
    private final Setting<Integer> maxBreaks = integer("MaxBreaks", 2, 1, 4);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> mobs = bool("Mobs", true);
    private final Setting<TargetMode> targetMode = enumSetting("TargetMode", TargetMode.NEAREST);
    // How many targets are considered. The first one decides what is lethal; the others add to
    // the score of a spot, so crystals that hit two people are preferred.
    private final Setting<Integer> maxTargets = integer("MaxTargets", 3, 1, 5);
    private final Setting<Double> secondaryWeight = precise("SecondaryWeight", 0.4, 0.0, 1.0);

    // --- Speed ---
    // Remove the crystal from the client world as soon as we hit it. This stops us hitting the
    // same ghost crystal every tick and frees the spot so a new crystal can be placed on it in
    // the same tick.
    private final Setting<Boolean> instantRemove = bool("InstantRemove", true);
    // Break a crystal the moment the server tells us it exists, instead of on the next tick.
    private final Setting<Boolean> spawnBreak = bool("SpawnBreak", true);
    // Minimum ticks between two hits on the same crystal (only matters if it is still around).
    private final Setting<Integer> breakDelay = integer("BreakDelay", 2, 0, 5);
    // Send a silent rotation packet to the crystal / block before each action, then restore.
    private final Setting<Boolean> rotate = bool("Rotate", true);

    // --- Damage ---
    private final Setting<Double> minDamage = precise("MinDamage", 4.0, 0.5, 20.0);
    // Ticks of target movement to predict ahead when calculating damage.
    private final Setting<Integer> predict = integer("Predict", 1, 0, 5);
    // Spots that would kill the target (damage >= health + absorption) are used first.
    private final Setting<Boolean> lethalPriority = bool("LethalPriority", true);
    // When the target's health + absorption is at or below this, MinDamage drops to 1 so
    // any spot that hurts them is used (face placing). 0 disables it.
    private final Setting<Integer> facePlaceHealth = integer("FacePlaceHealth", 8, 0, 36);
    // Face place as well when a piece of the target's armor is below this percent durability,
    // so the armor gets broken faster. 0 disables it.
    private final Setting<Integer> armorBreak = integer("ArmorBreak", 20, 0, 100);
    // Read the target's real Protection / Blast Protection enchants from their armor.
    private final Setting<Boolean> readEnchants = bool("ReadEnchants", true);
    // Used when ReadEnchants is off: 16 is Protection IV everywhere, 20 is the cap.
    private final Setting<Integer> assumedProtection = integer("AssumedProtection", 16, 0, 20);

    // --- Safety ---
    private final Setting<Boolean> selfCheck = bool("SelfCheck", true);
    // Most damage a crystal may deal to you.
    private final Setting<Double> maxSelfDamage = precise("MaxSelfDamage", 6.0, 0.5, 20.0);
    // The same limit when the crystal kills the target.
    private final Setting<Double> lethalSelfDamage = precise("LethalSelfDamage", 10.0, 0.5, 30.0);
    // How much each point of self damage costs in the score of a spot.
    private final Setting<Double> selfWeight = precise("SelfWeight", 0.6, 0.0, 3.0);
    // A crystal may never leave you with less than this much health (plus absorption).
    private final Setting<Double> safeHealth = precise("SafeHealth", 3.0, 0.0, 20.0);
    // Without a totem on you, only risk half of what is left.
    private final Setting<Boolean> strictNoTotem = bool("StrictNoTotem", true);
    // Ticks of your own movement to predict ahead.
    private final Setting<Integer> selfPredict = integer("SelfPredict", 1, 0, 3);
    private final Setting<Boolean> friendSafe = bool("FriendSafe", true);
    private final Setting<Double> maxFriendDamage = precise("MaxFriendDamage", 2.0, 0.0, 20.0);

    // --- Obsidian ---
    // When no valid crystal spot exists, place an obsidian base (needs obsidian in the hotbar)
    // on the best spot and crystal it on the following tick.
    private final Setting<Boolean> obsidianBase = bool("ObsidianBase", true);

    // --- Timing ---
    // Hold breaks while the target is inside its post-hit immunity and the crystal wouldn't do
    // real damage yet. See HitWindow.
    private final Setting<Boolean> hitWindow = bool("HitWindow", true);
    // After the target pops a totem the server ignores any damage that doesn't beat their last
    // hit for ~10 ticks, so breaking early is wasted. We hold breaking (but keep placing, so
    // crystals are armed) and release right as that window ends. See PopTracker.
    private final Setting<Boolean> doublePop = bool("DoublePop", true);
    private final Setting<Integer> popDelay = integer("PopDelay", 10, 0, 20);
    private final Setting<Boolean> pingComp = bool("PingComp", true);
    private final Setting<Boolean> debug = bool("Debug", false);

    private static final double REACH = 4.5;
    private static final double EXPLOSION_REACH = 12.0;
    private static final double LETHAL_BONUS = 1000.0;
    private static final int RESTOCK_THRESHOLD = 10;
    private static final int RESTOCK_COOLDOWN_TICKS = 5;
    private static final int MEMORY_SIZE_LIMIT = 64;
    private static final int MEMORY_MAX_AGE_TICKS = 40;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record Tracked(LivingEntity entity, Vec3d position) {
    }

    /** Everything a tick works out once and the break / place code then reuses. */
    private record Context(
            long tick,
            LivingEntity primary,
            Vec3d primaryPosition,
            List<Tracked> secondaries,
            List<PlayerEntity> friends,
            Vec3d selfPosition,
            double required,
            double healthTotal,
            double selfLimit,
            double selfLimitLethal,
            int latency
    ) {
    }

    private static final class Spot {
        BlockPos base;
        Vec3d centre;
        Box box;
        BlockHitResult support;
        double bound;
        double damage;
        double self;
        double key;
        boolean lethal;
    }

    private record Option(EndCrystalEntity crystal, double key) {
    }

    private final PopTracker pops;
    private final CrystalDamage calculator;
    private final MotionPredictor motion;
    private final HitWindow window = new HitWindow();

    private final Map<Integer, Long> lastHitTick = new HashMap<>();
    // Crystal spots we already sent a placement for. The crystal entity only shows up on our
    // client after the server's spawn packet (about one ping later), so without this the same
    // spot looks empty and gets re-placed every tick.
    private final Map<BlockPos, Long> recentPlacements = new HashMap<>();

    private Context lastContext;
    private long tickCounter;
    private long lastRestockTick = -100;
    private long lastObsidianTick = -100;
    private boolean rotated;

    public AutoCrystal(Nomnom nomnom) {
        super(
                nomnom,
                "AutoCrystal",
                Category.COMBAT,
                "Automatically places and breaks end crystals"
        );

        this.pops = new PopTracker(mc);
        this.calculator = new CrystalDamage(mc);
        this.motion = new MotionPredictor(mc);
    }

    @Override
    public void onDisable() {
        pops.reset();
        calculator.reset();
        motion.reset();
        window.reset();
        lastHitTick.clear();
        recentPlacements.clear();
        lastContext = null;
        rotated = false;
    }

    // ------------------------------------------------------------------ packets

    @Listener
    public void onPacket(PacketEvent.Receive event) {
        // Network thread: nothing here may touch the world directly.
        Packet<?> packet = event.getPacket();

        if (doublePop.getValue()) {
            pops.onPacket(packet);
        }

        if (spawnBreak.getValue()
                && autoBreak.getValue()
                && packet instanceof EntitySpawnS2CPacket spawn
                && spawn.getEntityType() == EntityType.END_CRYSTAL) {
            int id = spawn.getEntityId();

            // This hook runs before the packet is handled, which happens on the game thread.
            // Queueing from inside a game thread task puts the second task behind the packet's
            // own, so by the time it runs the crystal exists.
            mc.execute(() -> mc.execute(() -> breakSpawned(id)));
        }
    }

    /** Hits a freshly spawned crystal right away, using what the last tick worked out. */
    private void breakSpawned(int id) {
        try {
            Context context = lastContext;

            if (!isEnabled()
                    || context == null
                    || mc.player == null
                    || mc.world == null
                    || mc.interactionManager == null
                    || mc.getNetworkHandler() == null
                    || tickCounter - context.tick() > 2
                    || !context.primary().isAlive()
                    || pops.holding(tickCounter, doublePop.getValue(), this::debugMessage)) {
                return;
            }

            breakCrystals(context, id);
        } catch (RuntimeException exception) {
            debugMessage("Spawn break failed: " + exception);
        } finally {
            restoreRotation();
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

        pops.process(tickCounter, popDelay.getValue(), pingComp.getValue(), this::debugMessage);
        boolean holding = pops.holding(tickCounter, doublePop.getValue(), this::debugMessage);

        restockCrystals();

        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            lastContext = null;
            return;
        }

        calculator.update(tickCounter, readEnchants.getValue(), assumedProtection.getValue());

        for (LivingEntity target : targets) {
            motion.observe(tickCounter, target);
            window.observe(tickCounter, target);
        }

        LivingEntity primary = targets.get(0);

        List<Tracked> secondaries = new ArrayList<>();
        for (int i = 1; i < targets.size(); i++) {
            LivingEntity other = targets.get(i);
            secondaries.add(new Tracked(other, motion.predict(other, predict.getValue(), false)));
        }

        double healthTotal = primary.getHealth() + primary.getAbsorptionAmount();
        boolean facePlace = (facePlaceHealth.getValue() > 0 && healthTotal <= facePlaceHealth.getValue())
                || armorLow(primary);

        double ownHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double budget = ownHealth - safeHealth.getValue();
        if (strictNoTotem.getValue() && !hasTotem()) {
            budget *= 0.5;
        }
        budget = Math.max(0.0, budget);

        boolean checkSelf = selfChecking();

        Context context = new Context(
                tickCounter,
                primary,
                motion.predict(primary, predict.getValue(), false),
                secondaries,
                nearbyFriends(),
                motion.predict(mc.player, selfPredict.getValue(), true),
                facePlace ? 1.0 : minDamage.getValue(),
                healthTotal,
                checkSelf ? Math.min(maxSelfDamage.getValue(), budget) : Double.MAX_VALUE,
                checkSelf ? Math.min(lethalSelfDamage.getValue(), budget) : Double.MAX_VALUE,
                pops.pingTicks()
        );

        lastContext = context;

        if (autoBreak.getValue() && !holding) {
            breakCrystals(context, -1);
        }

        if (autoPlace.getValue()) {
            placeCrystals(context);
        }
    }

    private void debugMessage(String message) {
        if (debug.getValue() && mc.player != null) {
            mc.player.sendMessage(Text.literal("[AutoCrystal] " + message), false);
        }
    }

    /** Ticks a sent placement is remembered: about one round trip plus a little slack. */
    private int memoryTicks() {
        return pops.pingTicks() + 2;
    }

    private void pruneMemory(Map<?, Long> memory) {
        if (memory.size() > MEMORY_SIZE_LIMIT) {
            memory.values().removeIf(tick -> tickCounter - tick > MEMORY_MAX_AGE_TICKS);
        }
    }

    // ------------------------------------------------------------------- safety

    /** False when the check is off or you can't be hurt anyway (creative, spectator). */
    private boolean selfChecking() {
        return selfCheck.getValue()
                && !mc.player.isCreative()
                && !mc.player.isSpectator()
                && !mc.player.getAbilities().invulnerable;
    }

    private boolean selfSafe(Context context, double selfDamage, boolean lethal) {
        return selfDamage <= (lethal ? context.selfLimitLethal() : context.selfLimit()) + 1.0E-6;
    }

    /** True if no friend would take more than the limit from an explosion at {@code centre}. */
    private boolean friendsSafe(Context context, Vec3d centre) {
        if (!friendSafe.getValue() || context.friends().isEmpty()) {
            return true;
        }

        double limit = maxFriendDamage.getValue();

        for (PlayerEntity friend : context.friends()) {
            Vec3d position = CrystalDamage.positionOf(friend);

            if (calculator.estimate(friend, position, centre) > limit
                    && calculator.damage(friend, position, centre) > limit) {
                return false;
            }
        }

        return true;
    }

    /** Share of the damage the other targets would take, for the score. */
    private double secondaryBonus(Context context, Vec3d centre) {
        if (context.secondaries().isEmpty() || secondaryWeight.getValue() <= 0.0) {
            return 0.0;
        }

        double sum = 0.0;

        for (Tracked tracked : context.secondaries()) {
            if (calculator.estimate(tracked.entity(), tracked.position(), centre) >= 1.0) {
                sum += calculator.damage(tracked.entity(), tracked.position(), centre);
            }
        }

        return sum * secondaryWeight.getValue();
    }

    private double secondaryBound(Context context, Vec3d centre) {
        if (context.secondaries().isEmpty() || secondaryWeight.getValue() <= 0.0) {
            return 0.0;
        }

        double sum = 0.0;

        for (Tracked tracked : context.secondaries()) {
            sum += calculator.estimate(tracked.entity(), tracked.position(), centre);
        }

        return sum * secondaryWeight.getValue();
    }

    private List<PlayerEntity> nearbyFriends() {
        List<PlayerEntity> friends = new ArrayList<>();

        if (!friendSafe.getValue()) {
            return friends;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player
                    && player.isAlive()
                    && nomnom.friendManager().isFriend(player)
                    && mc.player.squaredDistanceTo(player) <= 24.0 * 24.0) {
                friends.add(player);
            }
        }

        return friends;
    }

    private boolean hasTotem() {
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return true;
        }

        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getStack(slot).isOf(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }

        return false;
    }

    /** True when one of the target's armor pieces is almost broken. */
    private boolean armorLow(LivingEntity target) {
        int percent = armorBreak.getValue();

        if (percent <= 0) {
            return false;
        }

        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = target.getEquippedStack(slot);

            if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                double left = 1.0 - (double) stack.getDamage() / stack.getMaxDamage();

                if (left * 100.0 < percent) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean visible(Vec3d from, Vec3d to) {
        return mc.world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        )).getType() == HitResult.Type.MISS;
    }

    // ----------------------------------------------------------------- breaking

    private void breakCrystals(Context context, int onlyId) {
        double range = breakRange.getValue();
        Vec3d eye = mc.player.getEyePos();

        // Damage the target would still ignore from its last hit.
        double ignored = hitWindow.getValue()
                ? window.ignored(context.primary(), context.latency())
                : 0.0;

        List<Option> options = new ArrayList<>();

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity.isAlive()
                        && (onlyId < 0 || entity.getId() == onlyId)
                        && mc.player.squaredDistanceTo(entity) <= range * range
        )) {
            Long lastHit = lastHitTick.get(crystal.getId());
            if (lastHit != null && tickCounter - lastHit <= breakDelay.getValue()) {
                continue;
            }

            Vec3d centre = CrystalDamage.positionOf(crystal);

            // Cheap upper bound first: most crystals can be ruled out without any raycast.
            if (calculator.estimate(context.primary(), context.primaryPosition(), centre) - ignored
                    < context.required()) {
                continue;
            }

            Vec3d middle = crystal.getBoundingBox().getCenter();
            if (eye.distanceTo(middle) > breakWallRange.getValue() && !visible(eye, middle)) {
                continue;
            }

            double full = calculator.damage(context.primary(), context.primaryPosition(), centre);
            double marginal = Math.max(0.0, full - ignored);

            if (marginal < context.required()) {
                continue;
            }

            boolean lethal = marginal >= context.healthTotal();

            // Inside the immunity window a hit that only adds a sliver is not worth the crystal:
            // wait the few ticks until it does full damage.
            if (ignored > 0.0 && !lethal && marginal < full * 0.5) {
                continue;
            }

            double self = selfChecking()
                    ? calculator.damage(mc.player, context.selfPosition(), centre)
                    : 0.0;

            if (!selfSafe(context, self, lethal) || !friendsSafe(context, centre)) {
                continue;
            }

            double score = marginal + secondaryBonus(context, centre) - selfWeight.getValue() * self;
            options.add(new Option(crystal, score + (lethal && lethalPriority.getValue() ? LETHAL_BONUS : 0.0)));
        }

        if (options.isEmpty()) {
            return;
        }

        options.sort(Comparator.comparingDouble(Option::key).reversed());

        int hits = Math.min(maxBreaks.getValue(), options.size());

        for (int i = 0; i < hits; i++) {
            EndCrystalEntity crystal = options.get(i).crystal();

            faceSilently(crystal.getBoundingBox().getCenter());

            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);

            lastHitTick.put(crystal.getId(), tickCounter);

            // The spot is free again, so a new crystal may go there straight away.
            recentPlacements.remove(BlockPos.ofFloored(crystal.getX(), crystal.getY() - 1.0, crystal.getZ()));

            if (instantRemove.getValue()) {
                mc.world.removeEntity(crystal.getId(), Entity.RemovalReason.KILLED);
            }
        }

        pruneMemory(lastHitTick);
    }

    // ----------------------------------------------------------------- placing

    private void placeCrystals(Context context) {
        boolean offhandCrystal = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);

        int crystalSlot = findHotbarSlot(Items.END_CRYSTAL);

        if (!offhandCrystal && crystalSlot == -1) {
            return;
        }

        int availableCrystals = offhandCrystal
                ? mc.player.getOffHandStack().getCount()
                : mc.player.getInventory().getStack(crystalSlot).getCount();

        int placementLimit = Math.min(maxPlacementsPerTick.getValue(), availableCrystals);

        if (placementLimit <= 0) {
            return;
        }

        List<Spot> chosen = scan(context, false, placementLimit);

        if (chosen.isEmpty()) {
            if (obsidianBase.getValue()) {
                placeObsidianBase(context);
            }
            return;
        }

        Hand hand = offhandCrystal ? Hand.OFF_HAND : Hand.MAIN_HAND;

        Runnable placeAll = () -> {
            for (Spot spot : chosen) {
                Vec3d hitPoint = Vec3d.ofCenter(spot.base).add(0, 0.5, 0);
                BlockHitResult hit = new BlockHitResult(hitPoint, Direction.UP, spot.base, false);

                faceSilently(hitPoint);
                mc.interactionManager.interactBlock(mc.player, hand, hit);
                mc.player.swingHand(hand);

                recentPlacements.put(spot.base, tickCounter);
                debugMessage(String.format("Place %s: %.1f to target, %.1f to you%s",
                        spot.base.toShortString(), spot.damage, spot.self, spot.lethal ? " (lethal)" : ""));
            }
        };

        if (offhandCrystal) {
            placeAll.run();
        } else {
            withSilentSlot(crystalSlot, placeAll);
        }

        pruneMemory(recentPlacements);
    }

    /** Places one obsidian block where a crystal on top of it would be best. */
    private void placeObsidianBase(Context context) {
        // The block only appears on our client after the server's update (about one ping), so
        // wait that long before placing another instead of stacking blocks on every tick.
        if (tickCounter - lastObsidianTick < memoryTicks()) {
            return;
        }

        int obsidianSlot = findHotbarSlot(Items.OBSIDIAN);
        if (obsidianSlot == -1) {
            return;
        }

        List<Spot> spots = scan(context, true, 1);

        if (spots.isEmpty()) {
            return;
        }

        BlockHitResult hit = spots.get(0).support;

        withSilentSlot(obsidianSlot, () -> {
            faceSilently(hit.getPos());
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        });

        lastObsidianTick = tickCounter;
    }

    /**
     * Finds the best {@code limit} spots for a crystal, or for an obsidian base to put one on.
     *
     * <p>Branch and bound: every candidate gets an upper bound of its score from plain math
     * (full exposure, no self damage). They are sorted by it, and the exact score, which needs
     * raycasts for the target, you and everyone else involved, is only worked out from the top
     * down until the next bound can't beat the worst spot already kept.
     */
    private List<Spot> scan(Context context, boolean obsidianMode, int limit) {
        LivingEntity primary = context.primary();
        Vec3d targetPosition = context.primaryPosition();
        Vec3d eye = mc.player.getEyePos();

        int range = placeRange.getValue();
        double rangeSquared = (double) range * range;
        int memory = memoryTicks();
        boolean lethalFirst = lethalPriority.getValue();

        BlockPos origin = primary.getBlockPos();
        List<Spot> candidates = new ArrayList<>();

        for (BlockPos base : BlockPos.iterate(
                origin.add(-range, -2, -range),
                origin.add(range, 2, range))) {

            if (mc.player.squaredDistanceTo(Vec3d.ofCenter(base)) > rangeSquared) {
                continue;
            }

            var state = mc.world.getBlockState(base);

            if (obsidianMode) {
                if (!state.isAir()) {
                    continue;
                }
            } else {
                if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) {
                    continue;
                }

                Long placedAt = recentPlacements.get(base);
                if (placedAt != null && tickCounter - placedAt < memory) {
                    continue;
                }
            }

            BlockPos above = base.up();

            if (!mc.world.getBlockState(above).isAir()) {
                continue;
            }

            Vec3d centre = new Vec3d(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);

            if (centre.squaredDistanceTo(targetPosition) > EXPLOSION_REACH * EXPLOSION_REACH) {
                continue;
            }

            double targetBound = calculator.estimate(primary, targetPosition, centre);

            if (targetBound < context.required()) {
                continue;
            }

            Spot spot = new Spot();
            spot.base = base.toImmutable();
            spot.centre = centre;
            spot.box = new Box(
                    above.getX(), above.getY(), above.getZ(),
                    above.getX() + 1, above.getY() + 2, above.getZ() + 1
            );
            spot.bound = targetBound + secondaryBound(context, centre)
                    + (lethalFirst && targetBound >= context.healthTotal() ? LETHAL_BONUS : 0.0);

            candidates.add(spot);
        }

        candidates.sort(Comparator.comparingDouble((Spot spot) -> spot.bound).reversed());

        List<Spot> best = new ArrayList<>();

        for (Spot spot : candidates) {
            if (best.size() >= limit && spot.bound <= best.get(best.size() - 1).key) {
                break;
            }

            // Nobody (including the target) may be standing where the crystal goes, or in an
            // obsidian base's way.
            Box space = obsidianMode
                    ? new Box(spot.base.getX(), spot.base.getY(), spot.base.getZ(),
                    spot.base.getX() + 1, spot.base.getY() + 3, spot.base.getZ() + 1)
                    : spot.box;

            if (!mc.world.getOtherEntities(null, space).isEmpty()) {
                continue;
            }

            Vec3d clickPoint;
            if (obsidianMode) {
                spot.support = supportHit(spot.base, eye);

                if (spot.support == null) {
                    continue;
                }

                clickPoint = spot.support.getPos();
            } else {
                clickPoint = Vec3d.ofCenter(spot.base).add(0, 0.5, 0);
            }

            if (eye.distanceTo(clickPoint) > placeWallRange.getValue()
                    && !visible(eye, clickPoint.add(0.0, 0.02, 0.0))) {
                continue;
            }

            double damage = calculator.damage(primary, targetPosition, spot.centre);

            if (damage < context.required()) {
                continue;
            }

            boolean lethal = damage >= context.healthTotal();
            double self = selfChecking()
                    ? calculator.damage(mc.player, context.selfPosition(), spot.centre)
                    : 0.0;

            if (!selfSafe(context, self, lethal) || !friendsSafe(context, spot.centre)) {
                continue;
            }

            spot.damage = damage;
            spot.self = self;
            spot.lethal = lethal;
            spot.key = damage + secondaryBonus(context, spot.centre) - selfWeight.getValue() * self
                    + (lethal && lethalFirst ? LETHAL_BONUS : 0.0);

            insert(best, spot, limit);
        }

        return best;
    }

    /** Adds {@code spot} to {@code best} (highest key first), keeping spots from overlapping. */
    private static void insert(List<Spot> best, Spot spot, int limit) {
        List<Spot> overlapping = new ArrayList<>();

        for (Iterator<Spot> iterator = best.iterator(); iterator.hasNext(); ) {
            Spot other = iterator.next();

            if (other.box.intersects(spot.box)) {
                if (other.key >= spot.key) {
                    return;
                }
                overlapping.add(other);
            }
        }

        best.removeAll(overlapping);
        best.add(spot);
        best.sort(Comparator.comparingDouble((Spot entry) -> entry.key).reversed());

        while (best.size() > limit) {
            best.remove(best.size() - 1);
        }
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

    private List<LivingEntity> findTargets() {
        double range = targetRange.getValue();

        var found = mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(range),
                entity -> entity != mc.player
                        && entity.isAlive()
                        && isValidTarget(entity)
                        && mc.player.squaredDistanceTo(entity) <= range * range
        );

        found.sort(switch (targetMode.getValue()) {
            case NEAREST -> Comparator.comparingDouble((LivingEntity entity) -> mc.player.squaredDistanceTo(entity));
            case LOWEST_HEALTH -> Comparator.comparingDouble(
                    (LivingEntity entity) -> entity.getHealth() + entity.getAbsorptionAmount());
            case WEAKEST_ARMOR -> Comparator.comparingDouble(
                    (LivingEntity entity) -> entity.getAttributeValue(EntityAttributes.ARMOR));
        });

        int count = Math.min(maxTargets.getValue(), found.size());
        return new ArrayList<>(found.subList(0, count));
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            return players.getValue() && !nomnom.friendManager().isFriend(player);
        }
        return mobs.getValue();
    }
}