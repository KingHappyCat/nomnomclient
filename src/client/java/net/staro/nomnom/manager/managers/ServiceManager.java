package net.staro.nomnom.manager.managers;

import lombok.Getter;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.manager.GenericManager;
import net.staro.nomnom.service.Service;
import net.staro.nomnom.service.impl.ChatMessageService;
import net.staro.nomnom.service.impl.FirstLaunchService;
import net.staro.nomnom.service.impl.KeyboardService;
import net.staro.nomnom.service.impl.ShutdownService;

@Getter
public class ServiceManager extends GenericManager<Service> {
    private KeyboardService keyboardService;
    private FirstLaunchService firstLaunchService;

    @Override
    public void initialize(Nomnom nomnom) {
        register(new ChatMessageService(nomnom));
        firstLaunchService = new FirstLaunchService(nomnom);
        register(firstLaunchService);
        keyboardService = new KeyboardService(nomnom);
        register(keyboardService);
        register(new ShutdownService(nomnom));
    }

}
