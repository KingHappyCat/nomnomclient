package net.staro.nomnom;

import lombok.extern.slf4j.Slf4j;
import net.staro.nomnom.api.event.bus.EventBus;
import net.staro.nomnom.api.injection.PreClient;

@Slf4j
public class PreClientInitializer implements PreClient {
    @Override
    public void onInitialize() {
        log.info("NOMNOM_PRE_LAUNCH_START");

        /* This can be instantiated at the client's entry point as well.
        * I just want to show that early initialization is also possible. */
        Nomnom.EVENT_BUS = new EventBus();

        log.info("NOMNOM_PRE_LAUNCH_FINISH");
    }

}
