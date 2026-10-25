package net.staro.nomnom.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;

/**
 * End crystal damage, following the vanilla explosion formula: distance falloff, exposure,
 * difficulty, armor, resistance and enchantment protection. Absorption is ignored.
 *
 * <p>Exposure needs about 45 raycasts per call, so it is the expensive part. Three things keep
 * that cheap:
 * <ul>
 *   <li>{@link #estimate} gives an upper bound (full exposure) with plain math, so callers can
 *       throw away spots that can't matter and stop searching once nothing left can win;</li>
 *   <li>entity positions are snapped to 1/16 block for the cache, so a slowly moving or
 *       standing target keeps hitting the cache;</li>
 *   <li>results are cached per entity for a few ticks.</li>
 * </ul>
 * Works for any entity, so the same code gives the damage to the target and to yourself.
 * Client tick thread only.
 */
public class CrystalDamage {
    private static final double CRYSTAL_POWER = 6.0;
    private static final int CACHE_TICKS = 5;
    private static final int MAX_CACHE_ENTRIES = 8192;
    private static final double DIAMETER = CRYSTAL_POWER * 2.0;
    private static final double SNAP = 16.0;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record ExposureKey(int id, double cx, double cy, double cz, double tx, double ty, double tz) {
    }

    private final MinecraftClient mc;
    private final Map<ExposureKey, Float> exposureCache = new HashMap<>();
    private final Map<Integer, Double> protectionCache = new HashMap<>();
    private long cacheStartTick = -1;
    private boolean readEnchants = true;
    private int assumedProtection = 16;

    public CrystalDamage(MinecraftClient mc) {
        this.mc = mc;
    }

    /** Call once per tick before asking for damage values. */
    public void update(long tick, boolean readEnchants, int assumedProtection) {
        this.readEnchants = readEnchants;
        this.assumedProtection = assumedProtection;

        // Armor can change from one tick to the next, so protection is looked up again each tick.
        protectionCache.clear();

        if (tick - cacheStartTick >= CACHE_TICKS || exposureCache.size() > MAX_CACHE_ENTRIES) {
            exposureCache.clear();
            cacheStartTick = tick;
        }
    }

    public void reset() {
        exposureCache.clear();
        protectionCache.clear();
        cacheStartTick = -1;
    }

    /**
     * Damage a crystal exploding at {@code centre} deals to {@code entity} standing at
     * {@code entityPos}.
     */
    public double damage(LivingEntity entity, Vec3d entityPos, Vec3d centre) {
        double distance = entityPos.distanceTo(centre) / DIAMETER;

        if (distance > 1.0) {
            return 0.0;
        }

        double x = snap(entityPos.x);
        double y = snap(entityPos.y);
        double z = snap(entityPos.z);

        ExposureKey key = new ExposureKey(entity.getId(), centre.x, centre.y, centre.z, x, y, z);
        float exposure = exposureCache.computeIfAbsent(
                key, k -> exposure(centre, entity, new Vec3d(x, y, z)));

        return reduce(entity, distance, exposure);
    }

    /**
     * Upper bound of {@link #damage}: assumes nothing blocks the explosion. Plain math, no
     * raycasts, so it is safe to call for every candidate spot.
     */
    public double estimate(LivingEntity entity, Vec3d entityPos, Vec3d centre) {
        double distance = entityPos.distanceTo(centre) / DIAMETER;

        if (distance > 1.0) {
            return 0.0;
        }

        return reduce(entity, distance, 1.0);
    }

    private double reduce(LivingEntity entity, double distance, double exposure) {
        double impact = (1.0 - distance) * exposure;
        double damage = (int) ((impact * impact + impact) / 2.0 * 7.0 * DIAMETER + 1.0);

        if (entity instanceof PlayerEntity) {
            damage = switch (mc.world.getDifficulty()) {
                case PEACEFUL -> 0.0;
                case EASY -> Math.min(damage / 2.0 + 1.0, damage);
                case NORMAL -> damage;
                case HARD -> damage * 1.5;
            };
        }

        // Armor and toughness.
        double armor = entity.getAttributeValue(EntityAttributes.ARMOR);
        double toughness = entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        double reduction = MathHelper.clamp(
                armor - damage / (2.0 + toughness / 4.0),
                armor * 0.2,
                20.0
        );
        damage *= 1.0 - reduction / 25.0;

        // Resistance effect.
        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            damage *= Math.max(0, 25 - (resistance.getAmplifier() + 1) * 5) / 25.0;
        }

        // Enchantment protection (looked up once per entity per tick).
        damage *= 1.0 - protectionCache.computeIfAbsent(
                entity.getId(), id -> protectionReductionOf(entity, readEnchants, assumedProtection));

        return Math.max(0.0, damage);
    }

    private static double snap(double value) {
        return Math.round(value * SNAP) / SNAP;
    }

    /**
     * Fraction (0..1) of the damage removed by the target's Protection / Blast Protection.
     * Protection counts 1 per level and Blast Protection 2 per level, capped at 20 points.
     */
    private double protectionReductionOf(LivingEntity target, boolean readEnchants, int assumed) {
        if (!(target instanceof PlayerEntity)) {
            return 0.0;
        }

        if (readEnchants) {
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

        return Math.min(20, assumed) / 25.0;
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

    public static Vec3d positionOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }
}