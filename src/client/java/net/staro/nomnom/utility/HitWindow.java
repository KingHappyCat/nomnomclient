package net.staro.nomnom.utility;

import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Damage immunity after a hit.
 *
 * <p>For about half a second after something hurts a player, further hits only count for the
 * part that is bigger than the last hit. A crystal that deals 8 right after a hit of 8 does
 * nothing, so breaking it then is wasted. This tracks how much each entity took in its last hit
 * (from its health dropping while it is flashing red) so the caller can work out how much a
 * crystal would really do right now, or wait until the window is over. Client tick thread only.
 */
public class HitWindow {
    private static final double MAX_TRACKED = 40.0;
    private static final long FORGET_TICKS = 100;

    private static final class State {
        double health;
        double lastDamage;
        int lastHurtTime;
        long seen;
    }

    private final Map<Integer, State> states = new HashMap<>();

    public void reset() {
        states.clear();
    }

    /** Call once per tick for every entity you ask about. */
    public void observe(long tick, LivingEntity entity) {
        double total = entity.getHealth() + entity.getAbsorptionAmount();
        State state = states.get(entity.getId());

        if (state == null) {
            state = new State();
            states.put(entity.getId(), state);
        } else if (state.seen == tick) {
            return;
        } else if (entity.hurtTime > 0) {
            double drop = state.health - total;

            if (drop > 0.05) {
                // A hurt timer that went up means a fresh hit, otherwise the extra damage is the
                // part of a bigger hit that got through the window.
                state.lastDamage = entity.hurtTime > state.lastHurtTime
                        ? drop
                        : state.lastDamage + drop;
                state.lastDamage = Math.min(state.lastDamage, MAX_TRACKED);
            }
        } else {
            state.lastDamage = 0.0;
        }

        state.health = total;
        state.lastHurtTime = entity.hurtTime;
        state.seen = tick;

        if (states.size() > 64) {
            states.values().removeIf(old -> tick - old.seen > FORGET_TICKS);
        }
    }

    /**
     * Damage that would still be ignored when a hit lands {@code latencyTicks} from now. The
     * hurt timer we see is already old and our hit still has to travel, so the window is over
     * for us once the timer is down to the round trip time.
     */
    public double ignored(LivingEntity entity, int latencyTicks) {
        State state = states.get(entity.getId());

        if (state == null || entity.hurtTime <= latencyTicks) {
            return 0.0;
        }

        return state.lastDamage;
    }
}