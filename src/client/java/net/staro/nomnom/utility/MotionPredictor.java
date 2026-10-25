package net.staro.nomnom.utility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Where an entity will be a few ticks from now.
 *
 * <p>Other players' real velocity isn't known on the client, so it is measured from how far
 * they moved between ticks and smoothed. The predicted path then runs through the world
 * collision, so someone running at a wall is predicted to stop at it instead of inside it, and
 * someone in the air keeps falling. Client tick thread only.
 */
public class MotionPredictor {
    private static final double SMOOTHING = 0.6;
    private static final double MAX_STEP = 3.0;
    private static final long FORGET_TICKS = 100;

    private final MinecraftClient mc;
    private final Map<Integer, Vec3d> lastPosition = new HashMap<>();
    private final Map<Integer, Vec3d> velocity = new HashMap<>();
    private final Map<Integer, Long> lastTick = new HashMap<>();

    public MotionPredictor(MinecraftClient mc) {
        this.mc = mc;
    }

    public void reset() {
        lastPosition.clear();
        velocity.clear();
        lastTick.clear();
    }

    /** Measures the entity's movement. Call once per tick for every entity you predict. */
    public void observe(long tick, Entity entity) {
        int id = entity.getId();
        Long previousTick = lastTick.get(id);

        if (previousTick != null && previousTick == tick) {
            return;
        }

        Vec3d position = CrystalDamage.positionOf(entity);
        Vec3d previous = lastPosition.get(id);

        Vec3d measured = Vec3d.ZERO;
        if (previous != null && previousTick != null && tick - previousTick <= 3) {
            long elapsed = tick - previousTick;
            Vec3d step = position.subtract(previous).multiply(1.0 / elapsed);

            // A teleport is not movement.
            if (step.length() <= MAX_STEP) {
                measured = step;
            }
        }

        Vec3d old = velocity.getOrDefault(id, Vec3d.ZERO);
        velocity.put(id, old.multiply(1.0 - SMOOTHING).add(measured.multiply(SMOOTHING)));
        lastPosition.put(id, position);
        lastTick.put(id, tick);

        if (lastTick.size() > 64) {
            lastTick.values().removeIf(seen -> tick - seen > FORGET_TICKS);
            lastPosition.keySet().retainAll(lastTick.keySet());
            velocity.keySet().retainAll(lastTick.keySet());
        }
    }

    /**
     * Position of {@code entity} in {@code ticks} ticks. {@code exact} uses the entity's own
     * velocity (accurate for the local player) instead of the measured one.
     */
    public Vec3d predict(Entity entity, int ticks, boolean exact) {
        Vec3d position = CrystalDamage.positionOf(entity);

        if (ticks <= 0) {
            return position;
        }

        Vec3d v = exact ? entity.getVelocity() : velocity.getOrDefault(entity.getId(), Vec3d.ZERO);
        boolean grounded = entity.isOnGround();

        double vx = v.x;
        double vy = grounded ? Math.min(0.0, v.y) : v.y;
        double vz = v.z;

        Box box = entity.getBoundingBox();

        for (int i = 0; i < ticks; i++) {
            double dx = vx;
            double dy = vy;
            double dz = vz;

            if (dx != 0.0 && !free(entity, box.offset(dx, 0.0, 0.0))) {
                dx = 0.0;
                vx = 0.0;
            }
            box = box.offset(dx, 0.0, 0.0);

            if (dy != 0.0 && !free(entity, box.offset(0.0, dy, 0.0))) {
                dy = 0.0;
                vy = 0.0;
            }
            box = box.offset(0.0, dy, 0.0);

            if (dz != 0.0 && !free(entity, box.offset(0.0, 0.0, dz))) {
                dz = 0.0;
                vz = 0.0;
            }
            box = box.offset(0.0, 0.0, dz);

            position = position.add(dx, dy, dz);

            // Gravity and drag, unless something solid is right underneath.
            boolean supported = !free(entity, box.offset(0.0, -0.05, 0.0));
            vy = supported && vy <= 0.0 ? 0.0 : (vy - 0.08) * 0.98;
        }

        return position;
    }

    private boolean free(Entity entity, Box box) {
        return mc.world.isSpaceEmpty(entity, box);
    }
}