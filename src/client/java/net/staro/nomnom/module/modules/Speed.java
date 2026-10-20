
package net.staro.nomnom.module.modules;

import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

public class Speed extends NomnomModule {

    public enum Mode {PACKET, NORMAL}

    private final Setting<Float> speed =
            floating("Speed", 1.5f, 1.0f, 5.0f);

    private final Setting<Boolean> groundCheck =
            bool("Ground Check", true);

    // Placeholder configuration for future packet-mode integration.
    private Mode mode = Mode.PACKET;

    private boolean packetModeInitialized = false;
    private int packetsObserved = 0;
    private double lastReportedX = 0.0;
    private double lastReportedY = 0.0;
    private double lastReportedZ = 0.0;

    public Speed(Nomnom nomnom) {
        super(
                nomnom,
                "Speed",
                Category.MOVEMENT,
                "Configurable movement speed"
        );
    }

    @Override
    protected void onEnable() {
        packetModeInitialized = false;
        packetsObserved = 0;

        if (mc.player != null) {
            lastReportedX = mc.player.getX();
            lastReportedY = mc.player.getY();
            lastReportedZ = mc.player.getZ();
        }
    }

    @Override
    protected void onDisable() {
        packetModeInitialized = false;
    }
}
