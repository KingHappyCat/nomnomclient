package net.staro.nomnom.module.modules;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.staro.nomnom.Nomnom;
import net.staro.nomnom.api.Setting;
import net.staro.nomnom.api.event.listener.Listener;
import net.staro.nomnom.events.tick.TickEvent;
import net.staro.nomnom.module.Category;
import net.staro.nomnom.module.NomnomModule;

import java.util.Set;

@SuppressWarnings("unused")
public class AutoEat extends NomnomModule {
    private static final Set<Item> AVOID = Set.of(
            Items.ROTTEN_FLESH,
            Items.SPIDER_EYE,
            Items.PUFFERFISH,
            Items.POISONOUS_POTATO,
            Items.CHORUS_FRUIT,
            Items.SUSPICIOUS_STEW
    );
    private static final Set<Item> GAPPLES = Set.of(
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE
    );
    // Eating takes 32 ticks. Give up if nothing starts within a second or so.
    private static final int START_TIMEOUT = 24;

    // Eat when the hunger bar is at this many points or lower (20 = full).
    private final Setting<Integer> hunger = integer("Hunger", 14, 1, 19);
    private final Setting<Boolean> gapples = bool("Gapples", false);
    private final Setting<Boolean> switchBack = bool("SwitchBack", true);

    private boolean eating;
    private boolean holdingKey;
    private int previousSlot = -1;
    private int ticksWaiting;

    public AutoEat(Nomnom nomnom) {
        super(nomnom, "AutoEat", Category.MISC, "Eats food when you are hungry");
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        stop();
    }

    @Listener
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }

        if (mc.currentScreen != null) {
            if (eating) {
                stop();
            }
            return;
        }

        if (eating) {
            continueEating();
            return;
        }

        if (mc.player.isUsingItem()
                || mc.player.getHungerManager().getFoodLevel() > hunger.getValue()) {
            return;
        }

        start();
    }

    private void start() {
        int slot = bestHotbarSlot();
        boolean offhand = false;

        if (slot == -1 && mc.player.getMainHandStack().isEmpty()
                && isEdible(mc.player.getOffHandStack())) {
            offhand = true;
        } else if (slot == -1) {
            return;
        }

        previousSlot = mc.player.getInventory().getSelectedSlot();
        if (!offhand) {
            mc.player.getInventory().setSelectedSlot(slot);
        }

        eating = true;
        ticksWaiting = 0;
        press();
    }

    private void continueEating() {
        boolean hungry = mc.player.getHungerManager().getFoodLevel() < 20;

        if (mc.player.isUsingItem()) {
            ticksWaiting = 0;
            press();
            return;
        }

        // Not eating (yet, or any more). Right after a bite finished we may want another one.
        if (!hungry || mc.player.getHungerManager().getFoodLevel() > hunger.getValue()
                || ++ticksWaiting > START_TIMEOUT) {
            stop();
            return;
        }

        press();
    }

    private void press() {
        mc.options.useKey.setPressed(true);
        holdingKey = true;
    }

    private void stop() {
        if (holdingKey) {
            holdingKey = false;
            KeyBinding.updatePressedStates();
        }

        if (eating && switchBack.getValue() && previousSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }

        reset();
    }

    private void reset() {
        eating = false;
        previousSlot = -1;
        ticksWaiting = 0;
        if (mc.player == null) {
            holdingKey = false;
        }
    }

    /** The hotbar slot holding the best food, or -1. */
    private int bestHotbarSlot() {
        int best = -1;
        float bestSaturation = -1.0f;
        int bestNutrition = -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isEdible(stack)) {
                continue;
            }

            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food.saturation() > bestSaturation
                    || (food.saturation() == bestSaturation && food.nutrition() > bestNutrition)) {
                best = slot;
                bestSaturation = food.saturation();
                bestNutrition = food.nutrition();
            }
        }

        return best;
    }

    private boolean isEdible(ItemStack stack) {
        if (stack.isEmpty() || !stack.contains(DataComponentTypes.FOOD)) {
            return false;
        }
        if (AVOID.contains(stack.getItem())) {
            return false;
        }

        return gapples.getValue() || !GAPPLES.contains(stack.getItem());
    }
}