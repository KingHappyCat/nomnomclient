package net.staro.nomnom.events.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.gui.DrawContext;

/** Posted every frame while the HUD is drawn. Modules draw 2D overlays with {@link #getContext()}. */
@Getter
@RequiredArgsConstructor
public class Render2DEvent {
    private final DrawContext context;
    private final float tickProgress;
}
