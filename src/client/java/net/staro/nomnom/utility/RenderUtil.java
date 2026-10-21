
package net.staro.nomnom.utility;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gui.DrawContext;

@UtilityClass
public class RenderUtil {

    public void rect(
            DrawContext drawContext,
            float x,
            float y,
            float width,
            float height,
            int color
    ) {
        drawContext.fill(
                (int) x,
                (int) y,
                (int) (x + width),
                (int) (y + height),
                color
        );
    }

    public void minusrect(
            DrawContext drawContext,
            float x,
            float y,
            float width,
            float height,
            int color
    ) {
        // Draws a normal filled rectangle.
        // The old custom OpenGL blend mode is not reproduced here.
        rect(drawContext, x, y, width, height, color);
    }
}
