package net.staro.nomnom.module.modules;

import net.minecraft.client.option.KeyBinding;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

@SuppressWarnings("unused")
public class AutoWalk extends NomnomModule {
    private final Setting<Boolean> sprint = bool("Sprint", true);

    private boolean holding;

    public AutoWalk(Nomnom nomnom) {
        super(nomnom, "AutoWalk", Category.MOVEMENT, "Walks forward by itself");
    }

    @Override
    protected void onDisable() {
        release();
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            holding = false;
            return;
        }

        if (mc.currentScreen != null) {
            release();
            return;
        }

        mc.options.forwardKey.setPressed(true);
        if (sprint.getValue()) {
            mc.options.sprintKey.setPressed(true);
        }
        holding = true;
    }

    /** Gives the keys back to the keyboard: pressed if you are really holding them, else not. */
    private void release() {
        if (!holding) {
            return;
        }

        holding = false;
        KeyBinding.updatePressedStates();
    }
}