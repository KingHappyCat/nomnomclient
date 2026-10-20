package net.staro.nomnom.module.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

public class Fullbright extends NomnomModule {
    public Fullbright(Nomnom nomnom) {
        super(nomnom, "Fullbright", Category.RENDER, "Makes the world bright.");
    }

    @Override
    public void onEnable() {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
        }
    }

    @Override
    public void onDisable() {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}