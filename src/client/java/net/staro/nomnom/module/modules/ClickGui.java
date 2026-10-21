package net.staro.nomnom.module.modules;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.gui.ClickGuiScreen;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;
import org.lwjgl.glfw.GLFW;

public class ClickGui extends NomnomModule {
    public ClickGui(Nomnom nomnom) {
        super(nomnom, "ClickGui", Category.CLIENT, "A GUI for modules");
        setBind(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        mc.setScreen(new ClickGuiScreen(this, nomnom.moduleManager()));
    }

    @Override
    public void onDisable() {
        // If the screen is already closing (ESC / Right Shift), calling setScreen again
        // re-enters MinecraftClient.setScreen and Fabric's screen hook gets a null screen.
        if (mc.currentScreen instanceof ClickGuiScreen gui && !gui.isClosing()) {
            mc.setScreen(null);
        }
    }
}