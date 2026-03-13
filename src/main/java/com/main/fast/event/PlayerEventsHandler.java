package com.main.fast.event;

import com.main.fast.registry.FastAttributes;
import fuzs.puzzleslib.api.event.v1.core.EventResult;
import fuzs.puzzleslib.api.event.v1.data.MutableInt;
import fuzs.puzzleslib.api.event.v1.entity.living.UseItemEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

public class PlayerEventsHandler {

    public static void register() {
        UseItemEvents.TICK.register(PlayerEventsHandler::onItemUseTick);
    }

    public static EventResult onItemUseTick(LivingEntity entity, ItemStack useItem, MutableInt useItemRemaining) {
        if (useItem.getItem() instanceof BowItem && BowItem.getPowerForTime(useItem.getUseDuration() - useItemRemaining.getAsInt()) < 1.0F) {

        double bowSpeed = 1;
        if (entity instanceof Player player) {
            if (player.getAttribute(FastAttributes.BOW_DRAW_SPEED.get()) != null) {
               bowSpeed = 1 * (player.getAttribute(FastAttributes.BOW_DRAW_SPEED.get()).getValue());
            }
        }
            
        final int NewBowSpeed = (int) bowSpeed;
        
        if (bowSpeed > 0) {
            useItemRemaining.mapInt(i -> i - NewBowSpeed);
        }
        
        }

        return EventResult.PASS;
    }
}
