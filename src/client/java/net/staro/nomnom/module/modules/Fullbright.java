package net.staro.nomnom.module.modules;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.UpdateEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/**
 * Keeps an infinite, hidden Night Vision effect on the player. It is re-applied every tick, so
 * it survives relogging, dying, changing dimension and servers that clear your effects.
 */
public class Fullbright extends NomnomModule {
    public Fullbright(Nomnom nomnom) {
        super(nomnom, "Fullbright", Category.RENDER, "Makes the world bright.");
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        ClientPlayerEntity player = event.getPlayer();
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);

        if (current == null || !current.isInfinite()) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
        }
    }

    @Override
    public void onEnable() {
        // Applied on the next tick by onUpdate; nothing to do here (the player may not exist yet).
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (player == null) {
            return;
        }

        // Only remove our own effect, not a real Night Vision from a potion or beacon.
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current != null && current.isInfinite()) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}