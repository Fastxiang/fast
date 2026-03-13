package com.main.fast;

import com.main.fast.registry.FastAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "fast", bus = Mod.EventBusSubscriber.Bus.MOD)
public class FastCommonEvents {

    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, FastAttributes.SWORD_LIMIT.get())) {
            event.add(EntityType.PLAYER, FastAttributes.SWORD_LIMIT.get());
        }
        if (!event.has(EntityType.PLAYER, FastAttributes.SWORD_HOVER_RADIUS.get())) {
            event.add(EntityType.PLAYER, FastAttributes.SWORD_HOVER_RADIUS.get());
        }
        if (!event.has(EntityType.PLAYER, FastAttributes.SWORD_HOVER_SPEED.get())) {
            event.add(EntityType.PLAYER, FastAttributes.SWORD_HOVER_SPEED.get());
        }
        
        if (!event.has(EntityType.PLAYER, FastAttributes.BOW_DRAW_SPEED.get())) {
            event.add(EntityType.PLAYER, FastAttributes.BOW_DRAW_SPEED.get());
        }
    }
}
