package net.staro.nomnom.service.impl;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.event.bus.Priority;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.client.ShutdownEvent;
import net.staro.nomnom.service.Service;

public class ShutdownService extends Service {
    private final Nomnom nomnom;

    public ShutdownService(Nomnom nomnom) {
        super(nomnom);
        this.nomnom = nomnom;
    }

    @SuppressWarnings("unused")
    @Listener(priority = Priority.HIGHEST)
    public void onShutdown(ShutdownEvent event) {
        nomnom.configManager().saveEverything(nomnom);
    }

}
