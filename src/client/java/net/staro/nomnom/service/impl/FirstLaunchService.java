package net.staro.nomnom.service.impl;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.event.bus.Priority;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.client.InitEvent;
import net.staro.nomnom.service.Service;

import java.io.IOException;

public class FirstLaunchService extends Service {
    private final String URL = "https://youtu.be/f9iE8b8jh2A?si=EjFttPahMeqfe9Z2";

    public FirstLaunchService(Nomnom nomnom) {
        super(nomnom);
    }

    @SuppressWarnings({"unused", "deprecation"})
    @Listener(priority = Priority.HIGHEST)
    public void onInitialize(InitEvent event) {
        if (event.nomnom().configManager().isFirstLaunch()) {
            new Thread(() -> {
                try {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + URL);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            event.nomnom().eventBus().unsubscribe(this);
        }
    }

}
