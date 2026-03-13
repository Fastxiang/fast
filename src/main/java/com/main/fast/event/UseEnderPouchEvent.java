package com.main.fast.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

public class UseEnderPouchEvent extends Event {
    public final LivingEntity entity;
    public final ItemStack stack;

    public UseEnderPouchEvent(LivingEntity entity, ItemStack stack) {
        this.entity = entity;
        this.stack = stack;
    }
    
    public LivingEntity getEntity() {
        return this.entity;
    }

    public ItemStack getStack() {
        return this.stack;
    }
}
