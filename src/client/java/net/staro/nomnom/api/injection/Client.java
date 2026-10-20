package net.staro.nomnom.api.injection;

import net.minecraft.client.MinecraftClient;

/**
 * Self-explanatory. Only the main client class can implement this.
 */
public interface Client {
    /**
     * Initializes the client in {@link net.staro.nomnom.mixin.MixinMinecraftClient} right after the initialization of the MinecraftClient instance.
     * @param mc is the null safe MinecraftClass object.
     */
    void onInitializeClient(MinecraftClient mc);

}
