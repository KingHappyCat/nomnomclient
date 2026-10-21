package net.staro.nomnom.mixin.client;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.staro.nomnom.events.client.KeyEvent;
import net.staro.nomnom.utility.MixinHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class MixinKeyboard {
    @Inject(method = "onKey", at = @At("TAIL"), cancellable = true)
    private void onKey(long windowPointer, int action, KeyInput input, CallbackInfo ci) {
        int key = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        if (MinecraftClient.getInstance().currentScreen == null) {
            var event = KeyEvent.INSTANCE;
            event.setKey(key);
            event.setAction(action);
            MixinHelper.hook(event, ci);
        }
    }

}