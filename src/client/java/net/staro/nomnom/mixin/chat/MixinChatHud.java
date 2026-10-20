package net.staro.nomnom.mixin.chat;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSignatureData;
import net.staro.nomnom.ducks.IChatHud;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.ListIterator;

@Mixin(ChatHud.class)
public abstract class MixinChatHud implements IChatHud {
    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    protected abstract void refresh();

    @Override
    @Invoker("addMessage")
    public abstract void nomnom$addMessage(@Nullable ChatHudLine message);

    @Override
    @Invoker("addVisibleMessage")
    public abstract void nomnom$addVisibleMessage(@Nullable ChatHudLine message);

    @Override
    public void nomnom$deleteMessage(@Nullable MessageSignatureData signature, boolean all) {
        if (signature == null) {
            return;
        }

        ListIterator<ChatHudLine> listIterator = this.messages.listIterator();
        boolean changed = false;
        while (listIterator.hasNext()) {
            var message = listIterator.next();
            if (signature.equals(message.signature())) {
                listIterator.remove();
                changed = true;
                if (!all) {
                    break;
                }
            }
        }

        if (changed) {
            this.refresh();
        }
    }

}
