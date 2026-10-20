package net.staro.nomnom.module.modules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.render.Render2DEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import net.staro.nomnom.utility.EspUtil;
import net.staro.nomnom.utility.ProjectionUtil;

/** Draws a line from the bottom centre of your screen to entities. Friends are cyan. */
@SuppressWarnings("unused")
public class Tracers extends NomnomModule {
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> hostiles = bool("Hostiles", false);
    private final Setting<Boolean> passives = bool("Passives", false);
    private final Setting<Integer> range = integer("Range", 128, 8, 256);

    public Tracers(Nomnom nomnom) {
        super(nomnom, "Tracers", Category.RENDER, "Lines pointing to entities");
    }

    @Listener
    public void onRender(Render2DEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        var ctx = event.getContext();
        float tickProgress = event.getTickProgress();
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity) || !entity.isAlive()) continue;
            if (!EspUtil.wanted(entity, players.getValue(), hostiles.getValue(), passives.getValue())) continue;
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            Vec3d centre = entity.getLerpedPos(tickProgress).add(0, entity.getHeight() / 2.0, 0);
            Vec3d screen = ProjectionUtil.toScreen(mc, centre, tickProgress);
            if (screen == null) continue;

            EspUtil.line(ctx, width / 2, height, (int) screen.x, (int) screen.y,
                    EspUtil.color(nomnom, entity), width, height);
        }
    }
}
