package com.main.fast.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import snownee.jade.api.ITooltip;
import net.minecraftforge.eventbus.api.Event;

public class JadeTooltipEvent extends Event {
    public final LivingEntity entity;
    public final ITooltip tooltip;

    public JadeTooltipEvent(LivingEntity entity, ITooltip tooltip) {
        this.entity = entity;
        this.tooltip = tooltip;
    }
    
    public Entity getEntity() {
        return this.entity;
    }
    
    public void addText(String text) {
        if (text != null && !text.isEmpty()) {
            tooltip.add(Component.literal(text));
        }
    }
    
    public void addComponent(Component component) {
        if (component != null) {
            tooltip.add(component);
        }
    }
}
