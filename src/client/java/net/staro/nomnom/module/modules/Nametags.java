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

/**
 * Name, health and distance above entities, visible through walls.
 * The vanilla nametags of the entities drawn here are hidden (see MixinEntityRenderer).
 */
@SuppressWarnings("unused")
public class Nametags extends NomnomModule {
    /** Handle for MixinEntityRenderer, which can't get module instances injected. */
    public static Nametags INSTANCE;

    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> hostiles = bool("Hostiles", false);
    private final Setting<Boolean> passives = bool("Passives", false);
    private final Setting<Boolean> health = bool("Health", true);
    private final Setting<Boolean> distance = bool("Distance", true);
    private final Setting<Integer> range = integer("Range", 128, 8, 256);

    public Nametags(Nomnom nomnom) {
        super(nomnom, "Nametags", Category.RENDER, "Name, health and distance above entities");
        INSTANCE = this;
    }

    /** True if the vanilla nametag of {@code entity} should be hidden because we draw our own. */
    public boolean hidesVanillaTag(Entity entity) {
        return isEnabled()
                && entity != null
                && entity != mc.player
                && EspUtil.wanted(entity, players.getValue(), hostiles.getValue(), passives.getValue());
    }

    @Listener
    public void onRender(Render2DEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        var ctx = event.getContext();
        float tickProgress = event.getTickProgress();
        var font = mc.textRenderer;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !(entity instanceof LivingEntity living) || !entity.isAlive()) continue;
            if (!EspUtil.wanted(entity, players.getValue(), hostiles.getValue(), passives.getValue())) continue;
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            Vec3d head = entity.getLerpedPos(tickProgress).add(0, entity.getHeight() + 0.35, 0);
            Vec3d screen = ProjectionUtil.toScreen(mc, head, tickProgress);
            if (screen == null) continue;

            String name = entity.getName().getString();
            String hp = "";
            if (health.getValue()) {
                hp = String.format(" %.1f", living.getHealth() + living.getAbsorptionAmount());
            }
            String dist = distance.getValue() ? String.format(" %dm", (int) mc.player.distanceTo(entity)) : "";

            int nameWidth = font.getWidth(name);
            int hpWidth = font.getWidth(hp);
            int distWidth = font.getWidth(dist);
            int total = nameWidth + hpWidth + distWidth;
            int x = (int) screen.x - total / 2;
            int y = (int) screen.y - font.fontHeight;

            ctx.fill(x - 2, y - 1, x + total + 2, y + font.fontHeight, 0x90000000);
            ctx.drawTextWithShadow(font, name, x, y, EspUtil.color(nomnom, entity));
            if (!hp.isEmpty()) {
                float fraction = Math.max(0f, Math.min(1f, (living.getHealth() + living.getAbsorptionAmount()) / living.getMaxHealth()));
                int red = (int) (255 * (1 - fraction));
                int green = (int) (255 * fraction);
                ctx.drawTextWithShadow(font, hp, x + nameWidth, y, 0xFF000000 | (red << 16) | (green << 8) | 0x40);
            }
            if (!dist.isEmpty()) {
                ctx.drawTextWithShadow(font, dist, x + nameWidth + hpWidth, y, 0xFFAAAAAA);
            }
        }
    }
}