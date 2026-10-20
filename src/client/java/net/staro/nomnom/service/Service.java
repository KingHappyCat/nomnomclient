package net.staro.nomnom.service;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.trait.Subscriber;

public abstract class Service implements Subscriber {
    protected Service(Nomnom nomnom) {
        subscribe(nomnom.eventBus());
    }

}
