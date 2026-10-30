package net.staro.nomnom.module.modules;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Module;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

@SuppressWarnings("unused")
public class AutoLog extends NomnomModule {
    private final Setting<Float> health = floating("Health", 6f, 1f, 20f);
    // Leave once you hold this many totems or fewer. 0 turns this check off.
    private final Setting<Integer> minTotems = integer("MinTotems", 0, 0, 36);
    // A totem in your offhand will save you from a lethal hit, so low health alone is not an emergency.
    private final Setting<Boolean> totemSafe = bool("TotemSafe", false);
    private final Setting<Boolean> players = bool("Players", false);
    private final Setting<Double> range = precise("Range", 16.0, 2.0, 64.0);
    private final Setting<Boolean> disableReconnect = bool("DisableReconnect", true);
    private final Setting<Boolean> disableSelf = bool("DisableSelf", true);

    public AutoLog(Nomnom nomnom) {
        super(nomnom, "AutoLog", Category.COMBAT, "Disconnects when you are in danger");
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.isInSingleplayer()
                || mc.getNetworkHandler() == null) {
            return;
        }

        String reason = danger();
        if (reason == null) {
            return;
        }

        if (disableReconnect.getValue()) {
            Module reconnect = nomnom.moduleManager().getModuleByName("AutoReconnect");
            if (reconnect != null && reconnect.isEnabled()) {
                reconnect.disable();
            }
        }

        if (disableSelf.getValue()) {
            disable();
        }

        mc.world.disconnect(Text.literal("[AutoLog] " + reason));
    }

    /** Why we should leave right now, or null if all is well. */
    private String danger() {
        float current = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (current <= health.getValue()
                && !(totemSafe.getValue() && mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))) {
            return String.format("Health was %.1f", current);
        }

        int limit = minTotems.getValue();
        if (limit > 0) {
            int totems = countTotems();
            if (totems <= limit) {
                return "Down to " + totems + " totem" + (totems == 1 ? "" : "s");
            }
        }

        if (players.getValue()) {
            for (PlayerEntity other : mc.world.getPlayers()) {
                if (other == mc.player || other.isSpectator() || !other.isAlive()
                        || nomnom.friendManager().isFriend(other)) {
                    continue;
                }

                if (mc.player.distanceTo(other) <= range.getValue()) {
                    return other.getName().getString() + " came within " + range.getValue().intValue() + " blocks";
                }
            }
        }

        return null;
    }

    private int countTotems() {
        int total = 0;

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                total += stack.getCount();
            }
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
            total += offhand.getCount();
        }

        return total;
    }
}