
package net.staro.nomnom.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.events.client.ToggleEvent;
import net.staro.nomnom.setting.GenericSetting;
import net.staro.nomnom.setting.type.Bind;
import net.staro.nomnom.utility.SettingUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class NomnomModule extends AbstractModule {

    private final Setting<Boolean> enabled = bool("Enabled", false);
    private final Setting<Boolean> drawn = bool("Drawn", true);
    private final Setting<Bind> bind =
            bind("Bind", new Bind(Bind.none().getKey()));

    private final AtomicBoolean atomicEnabled = new AtomicBoolean();
    private final AtomicBoolean atomicDrawn = new AtomicBoolean();

    protected final MinecraftClient mc;

    public NomnomModule(
            Nomnom nomnom,
            String name,
            Category category,
            String description
    ) {
        super(nomnom, name, category, description);
        this.mc = nomnom.mc();
    }

    @Override
    public boolean isDrawn() {
        atomicDrawn.set(drawn.getValue());
        return atomicDrawn.get();
    }

    @Override
    public void drawn(boolean state) {
        drawn.setValue(state);
    }

    @Override
    public boolean isEnabled() {
        atomicEnabled.set(enabled.getValue());
        return atomicEnabled.get();
    }

    @Override
    public void enable() {
        if (!isEnabled()) {
            enabled.setValue(true);
            onEnable(nomnom);
            onEnable();
            nomnom.eventBus().subscribe(this);
        }
    }

    @Override
    public void disable() {
        if (isEnabled()) {
            enabled.setValue(false);
            onDisable(nomnom);
            onDisable();
            nomnom.eventBus().unsubscribe(this);
        }
    }

    @Override
    public void toggle() {
        nomnom.eventBus().post(new ToggleEvent(this, !isEnabled()));

        if (isEnabled()) {
            disable();
        } else {
            enable();
        }
    }

    @Override
    public List<GenericSetting<?>> getSettings() {
        return Stream.of(
                        getClass(),
                        getClass().getSuperclass(),
                        getClass().getSuperclass().getSuperclass()
                )
                .flatMap(c -> Arrays.stream(c.getDeclaredFields()))
                .filter(field ->
                        field.getType().isAssignableFrom(GenericSetting.class))
                .peek(field -> field.setAccessible(true))
                .map(field -> {
                    try {
                        return (GenericSetting<?>) field.get(this);
                    } catch (IllegalAccessException e) {
                        log.debug("Unable to access module setting", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .peek(setting -> setting.setModule(this))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public GenericSetting<?> getSettingByName(String name) {
        return getSettings().stream()
                .filter(setting ->
                        setting.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void onEnable(Nomnom nomnom) {
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable(Nomnom nomnom) {
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();

        getSettings().forEach(setting -> {
            try {
                Object value = setting.getValue();

                if (value instanceof Bind bindValue) {
                    object.addProperty(
                            setting.getName(),
                            bindValue.getKey()
                    );
                } else if (value != null) {
                    object.addProperty(
                            setting.getName(),
                            value.toString()
                    );
                }
            } catch (Throwable e) {
                log.debug(
                        "Unable to save setting {}",
                        setting.getName(),
                        e
                );
            }
        });

        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        // Older configurations may not contain newly added modules.
        if (element == null
                || element.isJsonNull()
                || !element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();

        // Load available settings and preserve defaults for missing ones.
        for (GenericSetting<?> setting : getSettings()) {
            String name = setting.getName();

            // Restore Enabled separately through enable()/disable().
            if ("Enabled".equalsIgnoreCase(name)) {
                continue;
            }

            JsonElement value = object.get(name);

            if (value == null || value.isJsonNull()) {
                continue;
            }

            try {
                SettingUtil.setValueFromJson(this, setting, value);
            } catch (Throwable throwable) {
                log.debug(
                        "Failed to load setting {} for module {}",
                        name,
                        getName(),
                        throwable
                );
            }
        }

        // Avoid toggling an already-enabled module into the wrong state.
        JsonElement enabledElement = object.get("Enabled");

        if (enabledElement != null && !enabledElement.isJsonNull()) {
            try {
                boolean shouldEnable = enabledElement.getAsBoolean();

                if (shouldEnable && !isEnabled()) {
                    enable();
                } else if (!shouldEnable && isEnabled()) {
                    disable();
                }
            } catch (RuntimeException exception) {
                log.debug(
                        "Failed to restore enabled state for module {}",
                        getName(),
                        exception
                );
            }
        }
    }

    @Override
    public void setBind(int key) {
        this.bind.setValue(new Bind(key));
    }

    @Override
    public Bind getBind() {
        return this.bind.getValue();
    }
}
