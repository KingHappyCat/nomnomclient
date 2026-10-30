package net.staro.nomnom.module.modules;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

import java.util.UUID;

@SuppressWarnings("unused")
public class FakePlayer extends NomnomModule {
    private static final EquipmentSlot[] COPIED = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    // Negative, so it can never clash with an id the server hands out.
    private static final int ENTITY_ID = -133742;

    private final Setting<String> name = string("Name", "Nomnom");
    private final Setting<Float> health = floating("Health", 20f, 1f, 20f);
    private final Setting<Boolean> copyGear = bool("CopyGear", true);

    private OtherClientPlayerEntity dummy;
    private ClientWorld dummyWorld;

    public FakePlayer(Nomnom nomnom) {
        super(nomnom, "FakePlayer", Category.MISC, "A dummy player to test on");
    }

    @Override
    protected void onEnable() {
        spawn();
    }

    @Override
    protected void onDisable() {
        despawn();
    }

    @Listener
    public void onTick(TickEvent event) {
        if (dummy == null) {
            return;
        }

        // The world changed under us (disconnect, dimension change): the dummy went with it.
        if (mc.world == null || mc.world != dummyWorld) {
            dummy = null;
            dummyWorld = null;
            disable();
            return;
        }

        // Keep it topped up so it behaves like a target that never runs out of health.
        if (dummy.getHealth() < health.getValue()) {
            dummy.setHealth(health.getValue());
        }
    }

    private void spawn() {
        despawn();

        if (mc.player == null || mc.world == null) {
            disable();
            return;
        }

        String label = name.getValue().isBlank() ? "Nomnom" : name.getValue();
        GameProfile profile = new GameProfile(UUID.randomUUID(), label);

        OtherClientPlayerEntity entity = new OtherClientPlayerEntity(mc.world, profile);
        entity.copyPositionAndRotation(mc.player);
        entity.setHeadYaw(mc.player.getHeadYaw());
        entity.setHealth(health.getValue());
        entity.setId(ENTITY_ID);

        if (copyGear.getValue()) {
            for (EquipmentSlot slot : COPIED) {
                entity.equipStack(slot, mc.player.getEquippedStack(slot).copy());
            }
        }

        mc.world.addEntity(entity);
        dummy = entity;
        dummyWorld = mc.world;
    }

    private void despawn() {
        if (dummy != null && mc.world != null && mc.world == dummyWorld) {
            mc.world.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED);
        }

        dummy = null;
        dummyWorld = null;
    }
}