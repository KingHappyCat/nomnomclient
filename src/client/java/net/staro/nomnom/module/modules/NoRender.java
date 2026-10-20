
package net.staro.nomnom.module.modules;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

public class NoRender extends NomnomModule {

    private final Setting<Boolean> fire =
            bool("Fire", true);

    private final Setting<Boolean> hurtCamera =
            bool("Hurt Camera", true);

    private final Setting<Boolean> bossBar =
            bool("Boss Bar", false);

    private final Setting<Boolean> weather =
            bool("Weather", false);

    private final Setting<Boolean> potionParticles =
            bool("Potion Particles", false);

    private final Setting<Boolean> explosionParticles =
            bool("Explosion Particles", false);

    private final Setting<Boolean> vignette =
            bool("Vignette", true);

    private final Setting<Boolean> totemAnimation =
            bool("Totem Animation", false);

    public NoRender(Nomnom nomnom) {
        super(
                nomnom,
                "NoRender",
                Category.RENDER,
                "Hides selected visual effects"
        );
    }

    public boolean hideFire() {
        return isEnabled() && fire.getValue();
    }

    public boolean hideHurtCamera() {
        return isEnabled() && hurtCamera.getValue();
    }

    public boolean hideBossBar() {
        return isEnabled() && bossBar.getValue();
    }

    public boolean hideWeather() {
        return isEnabled() && weather.getValue();
    }

    public boolean hidePotionParticles() {
        return isEnabled() && potionParticles.getValue();
    }

    public boolean hideExplosionParticles() {
        return isEnabled() && explosionParticles.getValue();
    }

    public boolean hideVignette() {
        return isEnabled() && vignette.getValue();
    }

    public boolean hideTotemAnimation() {
        return isEnabled() && totemAnimation.getValue();
    }
}
