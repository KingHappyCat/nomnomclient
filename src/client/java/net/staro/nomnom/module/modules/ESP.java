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

/** Draws a 2D box around entities, visible through walls. Friends are cyan. */
@SuppressWarnings("unused")
public class ESP extends NomnomModule {
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> hostiles = bool("Hostiles", true);
    private final Setting<Boolean> passives = bool("Passives", false);
    private final Setting<Boolean> fill = bool("Fill", true);
    private final Setting<Integer> range = integer("Range", 128, 8, 256);

    public ESP(Nomnom nomnom) {
        super(nomnom, "ESP", Category.RENDER, "Boxes around entities");
    }

    @Listener
    public void onRender(Render2DEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        var ctx = event.getContext();
        float tickProgress = event.getTickProgress();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity) || !entity.isAlive()) continue;
            if (!EspUtil.wanted(entity, players.getValue(), hostiles.getValue(), passives.getValue())) continue;
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            Vec3d pos = entity.getLerpedPos(tickProgress);
            double halfWidth = entity.getWidth() / 2.0;
            double height = entity.getHeight();

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            boolean visible = true;

            for (int i = 0; i < 8 && visible; i++) {
                double dx = (i & 1) == 0 ? -halfWidth : halfWidth;
                double dy = (i & 2) == 0 ? 0 : height;
                double dz = (i & 4) == 0 ? -halfWidth : halfWidth;
                Vec3d screen = ProjectionUtil.toScreen(mc, pos.add(dx, dy, dz), tickProgress);
                if (screen == null) {
                    visible = false;
                    break;
                }
                minX = Math.min(minX, screen.x);
                minY = Math.min(minY, screen.y);
                maxX = Math.max(maxX, screen.x);
                maxY = Math.max(maxY, screen.y);
            }
            if (!visible) continue;

            int color = EspUtil.color(nomnom, entity);
            int x1 = (int) minX, y1 = (int) minY, x2 = (int) maxX, y2 = (int) maxY;
            if (fill.getValue()) {
                ctx.fill(x1, y1, x2, y2, (color & 0x00FFFFFF) | 0x30000000);
            }
            EspUtil.outline(ctx, x1, y1, x2, y2, color);
        }
    }
}
