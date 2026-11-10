package com.main.fast.compat.jade;

import com.main.fast.Fast;
import com.main.fast.difficulty.MobLevels;
import com.main.fast.dungeon.api.DungeonMobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade 服务端数据提供者：只把「当前注视的这一只」实体的等级和末影饰品配置发下去。
 *
 * <p>旧做法是 KubeJS 每次刷完一波怪就 {@code sendData} 一份**全服所有怪物**的等级 / 饰品 JSON，
 * 客户端还要自己维护两张表。现在换成 Jade 原生的 server data：按需、单只、只在打开提示框时发。</p>
 */
public class FastJadeServerData implements IServerDataProvider<EntityAccessor> {

    public static final ResourceLocation ID = Fast.id("entity_info_data");

    /** 实体等级（int） */
    public static final String KEY_LEVEL = "fast:level";
    /** 末影饰品配置 JSON（[{id,count}]），只有副本怪有 */
    public static final String KEY_BONUS = "fast:bonus";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (!(entity instanceof LivingEntity living)) return;

        int level = MobLevels.getStored(living);
        if (level > 0) {
            data.putInt(KEY_LEVEL, level);
        }

        DungeonMobData dungeonMob = DungeonMobData.of(living);
        if (dungeonMob != null) {
            String bonus = dungeonMob.getEnderBonusJson();
            if (bonus != null && !bonus.isEmpty()) {
                data.putString(KEY_BONUS, bonus);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ID;
    }
}
