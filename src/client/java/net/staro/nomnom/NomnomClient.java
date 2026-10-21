package net.staro.nomnom;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.staro.nomnom.api.injection.Client;
import net.staro.nomnom.command.Chat;
import net.staro.nomnom.events.client.InitEvent;
import net.staro.nomnom.manager.managers.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.api.event.bus.EventBus;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;
import net.staro.nomnom.events.render.Render2DEvent;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class NomnomClient implements Client {
	@Override
	public void onInitializeClient(MinecraftClient mc) {
		log.info("NOMNOM_LAUNCH_START");

		Nomnom.EVENT_BUS = new EventBus();

		// _______________ Create a nomnom instance  _______________ //
		var configManager = new ConfigManager();
		var moduleManager = new ModuleManager();
		var nomnom = new Nomnom(
				mc,
				Nomnom.EVENT_BUS,
				new Chat(mc),
				moduleManager,
				new CommandManager(),
				new FriendManager(),
				new ServiceManager(),
				new JsonableManager(),
				configManager
		);

		// _____________ Initialize managers stuff  _____________ //
		Nomnom.MANAGERS.forEach(manager -> manager.initialize(nomnom));

		// _________________ Load up configs  __________________ //
		configManager.loadEverything(nomnom);

		Nomnom.EVENT_BUS.post(new InitEvent(nomnom));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (Nomnom.EVENT_BUS != null) {
				Nomnom.EVENT_BUS.post(TickEvent.INSTANCE);
			}
		});

		HudElementRegistry.addLast(Identifier.of("nomnom", "render2d"), (context, tickCounter) -> {
			if (Nomnom.EVENT_BUS != null) {
				Nomnom.EVENT_BUS.post(new Render2DEvent(context, tickCounter.getTickProgress(false)));
			}
		});

		log.info("NOMNOM_LAUNCH_FINISH");
	}

}