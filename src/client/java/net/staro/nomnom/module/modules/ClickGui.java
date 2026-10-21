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
        if (mc.currentScreen instanceof ClickGuiScreen) {
            mc.setScreen(null);
        }
    }
}