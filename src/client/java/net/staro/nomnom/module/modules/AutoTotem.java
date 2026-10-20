package net.staro.nomnom.module.modules;

import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

/**
 * Keeps a totem of undying in your offhand.
 * Health 20 = always keep one equipped; lower it to only swap when you are low.
 */
@SuppressWarnings("unused")
public class AutoTotem extends NomnomModule {
    private final Setting<Float> health = floating("Health", 20f, 1f, 20f);
    private final Setting<Integer> delay = integer("Delay", 1, 0, 10);

    private int timer;

    public AutoTotem(Nomnom nomnom) {
        super(nomnom, "AutoTotem", Category.COMBAT, "Keeps a totem in your offhand");
    }

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.getValue()) {
            return;
        }

        int slot = findTotemSlot();
        if (slot == -1) {
            return;
        }

        // Button 40 on a SWAP click means "swap with the offhand".
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                slot,
                40,
                SlotActionType.SWAP,
                mc.player);
        timer = delay.getValue();
    }

    /** Returns a slot id of the player screen handler (main inventory 9-35, hotbar 36-44). */
    private int findTotemSlot() {
        var inventory = mc.player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return 36 + i;
            }
        }
        return -1;
    }
}
