package net.staro.nomnom.module.modules;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

@SuppressWarnings("unused")
public class AutoXP extends NomnomModule {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // Keep throwing while any Mending armor is below this much durability (percent).
    private final Setting<Integer> repair = integer("Repair", 100, 1, 100);
    private final Setting<Integer> delay = integer("Delay", 2, 0, 20);
    private final Setting<Boolean> onlyOnGround = bool("OnlyOnGround", true);

    private int timer;

    public AutoXP(Nomnom nomnom) {
        super(nomnom, "AutoXP", Category.COMBAT, "Mends armor with experience bottles");
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

        if (mc.currentScreen != null || mc.player.isUsingItem()
                || (onlyOnGround.getValue() && !mc.player.isOnGround())) {
            return;
        }

        int slot = findBottleSlot();
        if (slot == -1 || !needsMending()) {
            return;
        }

        throwBottle(slot);
        timer = delay.getValue();
    }

    private void throwBottle(int slot) {
        var inventory = mc.player.getInventory();
        int previousSlot = inventory.getSelectedSlot();
        float previousPitch = mc.player.getPitch();

        // The throw packet carries our view, so look straight down for just this call.
        inventory.setSelectedSlot(slot);
        mc.player.setPitch(90.0f);

        try {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        } finally {
            mc.player.setPitch(previousPitch);
            inventory.setSelectedSlot(previousSlot);
        }
    }

    private int findBottleSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(Items.EXPERIENCE_BOTTLE)) {
                return slot;
            }
        }

        return -1;
    }

    /** True when a worn armor piece has Mending and is below the Repair durability. */
    private boolean needsMending() {
        try {
            var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            var mending = registry.getOrThrow(Enchantments.MENDING);

            for (EquipmentSlot slot : ARMOR) {
                ItemStack stack = mc.player.getEquippedStack(slot);

                if (stack.isEmpty() || !stack.isDamaged()
                        || EnchantmentHelper.getLevel(mending, stack) <= 0) {
                    continue;
                }

                double durability = 100.0 * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage();
                if (durability < repair.getValue()) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // No enchantment registry yet (loading the world): nothing to mend.
        }

        return false;
    }
}