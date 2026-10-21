package net.staro.nomnom.module.modules;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.network.ClientPlayerEntity;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.SafeListener;
import net.staro.nomnom.events.CancellableEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

public class Step extends NomnomModule {
    private final Setting<Float> height = floating("Height", 2, 0, 10);

    public Step(Nomnom nomnom) {
        super(nomnom, "Step", Category.MOVEMENT, "Steps up");
    }

    @SafeListener
    @SuppressWarnings("unused")
    public void onStepHeight(StepHeightEvent event) {
        event.setHeight(height.getValue());
    }

    @Getter
    @AllArgsConstructor
    public static class StepHeightEvent extends CancellableEvent {
        private final ClientPlayerEntity player;
        private float height;

        public void setHeight(float height) {
            setCancelled(true);
            this.height = height;
        }
    }

}
