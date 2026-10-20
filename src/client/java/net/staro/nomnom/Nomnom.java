package net.staro.nomnom;

import net.minecraft.client.MinecraftClient;
import net.staro.nomnom.api.Manager;
import net.staro.nomnom.api.event.bus.EventBus;
import net.staro.nomnom.command.Chat;
import net.staro.nomnom.manager.managers.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A record that serves as an access point for the entire project to access managers and eventbus
 *  without the need of creating new instances or using singleton classes.
 * @param eventBus is {@link EventBus}
 * @param commandManager is {@link CommandManager}
 */
public record Nomnom(MinecraftClient mc,
                   EventBus eventBus,
                   Chat chat,
                   ModuleManager moduleManager,
                   CommandManager commandManager,
                   FriendManager friendManager,
                   ServiceManager serviceManager,
                   JsonableManager jsonableManager,
                   ConfigManager configManager) {
    /**
     * A static eventBus reference for mixin usage.
     */
    public static EventBus EVENT_BUS;
    /**
     * A static arraylist for manager's initialization.
     */
    public static List<Manager> MANAGERS = new ArrayList<>();

}
