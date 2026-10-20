package net.staro.nomnom.service.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.event.bus.Priority;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.client.KeyEvent;
import net.staro.nomnom.service.Service;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

public class KeyboardService extends Service {
    private final Nomnom nomnom;
    @Getter
    private final AtomicBoolean listening = new AtomicBoolean();
    @Setter
    private Module module;

    public KeyboardService(Nomnom nomnom) {
        super(nomnom);
        this.nomnom = nomnom;
    }

    @SuppressWarnings("unused")
    @Listener(priority = Priority.HIGH)
    public void onKeyPressed(KeyEvent event) {
        if (event.getAction() == 1 && !listening.get()) {
            nomnom.moduleManager().getModules().stream()
                    .filter(module -> module.getBind().getKey() == event.getKey())
                    .forEach(Module::toggle);
        }
    }

    @SuppressWarnings("unused")
    @Listener(priority = Priority.HIGHEST)
    public void onBindSet(KeyEvent event) {
        if (listening.get()) {
            listening.set(false);
            if (event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
                nomnom.chat().sendLogless(Text.literal("Operation cancelled").formatted(Formatting.GRAY));
                return;
            }

            module.setBind(event.getKey());
            nomnom.chat().sendLogless(Text.literal(
                    "The bind for " + module.getName().formatted(Formatting.WHITE).formatted(Formatting.BOLD)
                            + " has been set to " + module.getBind().getBindName()).formatted(Formatting.GRAY));
        }
    }

}
