package com.main.fast.compat.jade;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import com.main.fast.Fast;
import com.main.fast.event.JadeTooltipEvent;
import net.minecraftforge.common.MinecraftForge;

public class FastJadeInfo implements IEntityComponentProvider {

    public static final ResourceLocation ID = Fast.id("entity_info");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (accessor.getEntity() instanceof LivingEntity living) {
            MinecraftForge.EVENT_BUS.post(new JadeTooltipEvent(living, tooltip));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ID;
    }
}
