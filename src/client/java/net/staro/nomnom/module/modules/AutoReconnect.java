package net.staro.nomnom.module.modules;

import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/** Rejoins the last server a few seconds after you get kicked or disconnected. */
@SuppressWarnings("unused")
public class AutoReconnect extends NomnomModule {
    private final Setting<Integer> delay = integer("Delay", 5, 1, 60);

    private ServerInfo lastServer;
    private int countdown = -1;

    public AutoReconnect(Nomnom nomnom) {
        super(nomnom, "AutoReconnect", Category.MISC, "Reconnects after a disconnect");
    }

    @Override
    protected void onEnable() {
        countdown = -1;
    }

    @Listener
    public void onTick(TickEvent event) {
        ServerInfo current = mc.getCurrentServerEntry();
        if (current != null && mc.world != null) {
            lastServer = current; // remember where we are, for when we get kicked
            countdown = -1;
            return;
        }

        if (!(mc.currentScreen instanceof DisconnectedScreen) || lastServer == null) {
            countdown = -1;
            return;
        }

        if (countdown < 0) {
            countdown = delay.getValue() * 20;
        }
        if (--countdown <= 0) {
            countdown = -1;
            ConnectScreen.connect(
                    new MultiplayerScreen(new TitleScreen()),
                    mc,
                    ServerAddress.parse(lastServer.address),
                    lastServer,
                    false,
                    null);
        }
    }
}
