package net.staro.nomnom.utility;

import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Projects a world position onto the (scaled) screen so we can draw 2D overlays on top of the HUD.
 * Uses the camera entity's position and rotation and the FOV option, so it is accurate in first person.
 * It ignores dynamic FOV effects (sprinting, bows) and third-person camera offset.
 */
@UtilityClass
public class ProjectionUtil {

    /**
     * @return a vector (screenX, screenY, depth) in GUI-scaled pixels, or null if the point is behind the camera.
     */
    public Vec3d toScreen(MinecraftClient mc, Vec3d point, float tickProgress) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return null;
        }

        Vec3d origin = camera.getCameraPosVec(tickProgress);
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(tickProgress), camera.getYaw(tickProgress));
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(forward);

        Vec3d relative = point.subtract(origin);
        double depth = relative.dotProduct(forward);
        if (depth < 0.05) {
            return null;
        }

        double tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
        double ndcX = relative.dotProduct(right) / (depth * tan * aspect);
        double ndcY = relative.dotProduct(up) / (depth * tan);

        double x = (ndcX * 0.5 + 0.5) * mc.getWindow().getScaledWidth();
        double y = (0.5 - ndcY * 0.5) * mc.getWindow().getScaledHeight();
        return new Vec3d(x, y, depth);
    }
}
