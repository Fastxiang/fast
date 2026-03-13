package com.main.fast.util;

import com.main.fast.entity.FastSwordEntity;
import com.main.fast.entity.SpiritSwordEntity;
import net.minecraft.world.entity.Entity;

public class SwordEntityHelper {

    /**
     * 判断是否为 FastSwordEntity 或其子类
     */
    public static boolean isFastSword(Entity entity) {
        return entity instanceof FastSwordEntity;
    }

    /**
     * 判断是否为 SpiritSwordEntity
     */
    public static boolean isSpiritSword(Entity entity) {
        return entity instanceof SpiritSwordEntity;
    }

    /**
     * 判断是否为任意剑灵（FastSword 或 SpiritSword）
     */
    public static boolean isAnySword(Entity entity) {
        return entity instanceof FastSwordEntity || entity instanceof SpiritSwordEntity;
    }
}