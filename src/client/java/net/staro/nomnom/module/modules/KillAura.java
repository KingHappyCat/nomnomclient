package net.staro.nomnom.module.modules;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.UpdateEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import net.staro.nomnom.utility.EspUtil;

/** Attacks the closest valid target in range. Friends are never targeted. */
@SuppressWarnings("unused")
public class KillAura extends NomnomModule {
    private final Setting<Double> range = precise("Range", 3.8, 1.0, 6.0);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> hostiles = bool("Hostiles", true);
    private final Setting<Boolean> passives = bool("Passives", false);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> cooldown = bool("Cooldown", true);

    public KillAura(Nomnom nomnom) {
        super(nomnom, "KillAura", Category.COMBAT, "Attacks nearby entities");
    }

    @Listener
    public void onUpdate(UpdateEvent event) {
        ClientPlayerEntity player = event.getPlayer();
        if (mc.world == null || mc.interactionManager == null) {
            return;
        }

        LivingEntity target = findTarget(player);
        if (target == null) {
            return;
        }

        if (rotate.getValue()) {
            face(player, target);
        }
        if (cooldown.getValue() && player.getAttackCooldownProgress(0.5f) < 1.0f) {
            return;
        }

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
    }

    private LivingEntity findTarget(ClientPlayerEntity player) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive()
                    || living.isSpectator()) {
                continue;
            }
            if (!EspUtil.wanted(entity, players.getValue(), hostiles.getValue(), passives.getValue())) {
                continue;
            }
            if (entity instanceof PlayerEntity other && nomnom.friendManager().isFriend(other)) {
                continue;
            }
            double distance = player.distanceTo(entity);
            if (distance <= range.getValue() && distance < bestDistance) {
                best = living;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void face(ClientPlayerEntity player, LivingEntity target) {
        Vec3d from = player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.hypot(dx, dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        player.setYaw(MathHelper.wrapDegrees(yaw));
        player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
    }
}
