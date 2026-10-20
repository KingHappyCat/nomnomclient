package net.staro.nomnom.utility;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.staro.nomnom.Nomnom;

/** Shared helpers for ESP, Tracers, Nametags and KillAura. */
@UtilityClass
public class EspUtil {
    public static final int FRIEND = 0xFF55FFFF;
    public static final int PLAYER = 0xFFFF5555;
    public static final int HOSTILE = 0xFFFFAA00;
    public static final int PASSIVE = 0xFF55FF55;

    public boolean wanted(Entity entity, boolean players, boolean hostiles, boolean passives) {
        if (entity instanceof PlayerEntity) return players;
        if (entity instanceof Monster) return hostiles;
        if (entity instanceof PassiveEntity) return passives;
        return false;
    }

    public int color(Nomnom nomnom, Entity entity) {
        if (entity instanceof PlayerEntity player) {
            return nomnom.friendManager().isFriend(player) ? FRIEND : PLAYER;
        }
        return entity instanceof Monster ? HOSTILE : PASSIVE;
    }

    /** 1px rectangle outline. */
    public void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    /** 1px line, drawn pixel by pixel; pixels outside the screen are skipped. */
    public void line(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int screenW, int screenH) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round((float) dx * i / steps);
            int y = y1 + Math.round((float) dy * i / steps);
            if (x < 0 || y < 0 || x >= screenW || y >= screenH) {
                continue;
            }
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }
}
