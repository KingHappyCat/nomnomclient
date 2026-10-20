package net.staro.nomnom.module;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.setting.SettingFactoryImpl;

@Getter
@RequiredArgsConstructor
public abstract class AbstractModule extends SettingFactoryImpl implements Module {
    protected final Nomnom nomnom;
    private final String name;
    private final Category category;
    private final String description;

    protected abstract void onEnable(Nomnom nomnom);

    protected abstract void onEnable();

    protected abstract void onDisable(Nomnom nomnom);

    protected abstract void onDisable();

}
