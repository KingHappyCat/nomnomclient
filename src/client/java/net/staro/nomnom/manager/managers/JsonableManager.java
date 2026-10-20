package net.staro.nomnom.manager.managers;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.trait.Jsonable;
import net.staro.nomnom.manager.GenericManager;

//TODO: honestly, this is retarded, but I cba to rewrite config system atm.
public class JsonableManager extends GenericManager<Jsonable> {
    @Override
    public void initialize(Nomnom nomnom) {
        register(nomnom.moduleManager());
        register(nomnom.commandManager());
        register(nomnom.friendManager());
    }

}
