package com.main.fast.difficulty;

import com.main.fast.Fast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 难度系统入口：实体进入世界时上等级 / 基础属性。
 *
 * <p>用 {@link EventPriority#HIGHEST} 是为了保证在 KubeJS 的
 * {@code EntityEvents.spawned}（Architectury 同一事件、默认优先级）之前跑完，
 * 脚本那边只做末影饰品的属性换算（Rpg）和回满血。</p>
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class DifficultyHooks {

    private DifficultyHooks() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        MobLevels.apply(living);
    }
}
