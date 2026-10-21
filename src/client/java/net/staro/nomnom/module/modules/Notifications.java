package net.staro.nomnom.module.modules;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.event.listener.SafeListener;
import net.staro.nomnom.events.client.ToggleEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

public class Notifications extends NomnomModule {
    public Notifications(Nomnom nomnom) {
        super(nomnom, "Notifications", Category.CLIENT, "Shows selected notifications in chat.");
    }

    @SafeListener
    @SuppressWarnings("unused")
    public void onSettingChanged(ToggleEvent event) {
        var module = event.module();
        String message = event.state() ? " enabled." : " disabled.";
        Formatting formatting = event.state() ? Formatting.AQUA : Formatting.RED;
        nomnom.chat().sendLogless(Text.literal("")
                .append(Text.literal(module.getName()).formatted(Formatting.BOLD))
                .append(Text.literal(message).formatted(formatting)), module.getName());
    }

}
