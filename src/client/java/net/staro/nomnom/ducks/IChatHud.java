package net.staro.nomnom.ducks;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSignatureData;
import org.jetbrains.annotations.Nullable;

public interface IChatHud {
    void nomnom$addMessage(@Nullable ChatHudLine message);

    void nomnom$addVisibleMessage(@Nullable ChatHudLine message);

    void nomnom$deleteMessage(@Nullable MessageSignatureData signature, boolean all);

}
