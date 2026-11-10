package com.main.fast.compat.jade;

import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class FastJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // 服务端数据：等级 + 末影饰品（按需、单只实体，不再整包同步）
        registration.registerEntityDataProvider(new FastJadeServerData(), LivingEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new FastJadeInfo(), LivingEntity.class);
    }
}
