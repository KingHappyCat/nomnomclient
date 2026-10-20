package net.staro.nomnom.utility;

import lombok.experimental.UtilityClass;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.events.CancellableEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Utility for {@link org.spongepowered.asm.mixin.Mixin}.
 *
 * @author 3arthqu4ke.
 */
@UtilityClass
public class MixinHelper {
    /**
     * Posts the given event on the {@link Nomnom#EVENT_BUS} and cancels the given {@link CallbackInfo} if
     * the event has been cancelled. Do not forget to mark your {@link org.spongepowered.asm.mixin.injection.Inject}
     * annotation as cancellable!
     *
     * @param event the event to post.
     * @param callbackInfo the CallbackInfo to cancel if the event has been cancelled.
     */
    public void hook(CancellableEvent event, CallbackInfo callbackInfo) {
        Nomnom.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

}
