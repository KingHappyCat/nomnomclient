package net.staro.nomnom.module.modules;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.mixin.client.IMinecraftClient;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/** Removes (or shortens) the delay between item uses / block placements. */
@SuppressWarnings("unused")
public class FastPlace extends NomnomModule {
    private final Setting<Integer> delay = integer("Delay", 0, 0, 4);

    public FastPlace(Nomnom nomnom) {
        super(nomnom, "FastPlace", Category.MISC, "Place blocks and use items faster");
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null) {
            return;
        }
        var client = (IMinecraftClient) mc;
        if (client.getItemUseCooldown() > delay.getValue()) {
            client.setItemUseCooldown(delay.getValue());
        }
    }
}
