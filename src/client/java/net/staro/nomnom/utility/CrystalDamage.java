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
 * <p>Exposure needs about 45 raycasts per call, so results are cached for a few ticks. A
 * target that stands still (common while crystalling) then costs almost nothing after the
 * first tick. Client tick thread only.
 */
public class CrystalDamage {
    private static final double CRYSTAL_POWER = 6.0;
    private static final int CACHE_TICKS = 5;
    private static final int MAX_CACHE_ENTRIES = 4096;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record ExposureKey(double cx, double cy, double cz, double tx, double ty, double tz) {
    }

    private final MinecraftClient mc;
    private final Map<ExposureKey, Float> exposureCache = new HashMap<>();
    private long cacheStartTick = -1;
    private int cacheTargetId = -1;
    private double protectionReduction;

    public CrystalDamage(MinecraftClient mc) {
        this.mc = mc;
    }

    /** Call once per tick before asking for damage values for {@code target}. */
    public void update(long tick, LivingEntity target, boolean readEnchants, int assumedProtection) {
        if (target.getId() != cacheTargetId
                || tick - cacheStartTick >= CACHE_TICKS
                || exposureCache.size() > MAX_CACHE_ENTRIES) {
            exposureCache.clear();
            cacheStartTick = tick;
            cacheTargetId = target.getId();
        }

        protectionReduction = protectionReductionOf(target, readEnchants, assumedProtection);
    }

    public void reset() {
        exposureCache.clear();
        cacheTargetId = -1;
        cacheStartTick = -1;
    }

    /**
     * Damage a crystal exploding at {@code centre} deals to {@code target} standing at
     * {@code targetPos}.
     */
    public double damage(LivingEntity target, Vec3d targetPos, Vec3d centre) {
        double diameter = CRYSTAL_POWER * 2.0;
        double distance = targetPos.distanceTo(centre) / diameter;

        if (distance > 1.0) {
            return 0.0;
        }

        ExposureKey key = new ExposureKey(
                centre.x, centre.y, centre.z, targetPos.x, targetPos.y, targetPos.z);
        float exposure = exposureCache.computeIfAbsent(key, k -> exposure(centre, target, targetPos));

        double impact = (1.0 - distance) * exposure;
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