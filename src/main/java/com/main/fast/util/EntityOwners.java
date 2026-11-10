package com.main.fast.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 找出「主人是玩家」的实体（女仆 / 召唤物 / 驯服生物）。
 *
 * <p>先看 {@link OwnableEntity}，再对没实现它的模组实体（例如女仆）反射 {@code getOwner()}，
 * 方法句柄按类缓存，避免每次击杀 / 刷怪都重建异常栈。</p>
 */
public final class EntityOwners {

    private static final Map<Class<?>, Optional<Method>> OWNER_METHODS = new ConcurrentHashMap<>();

    private EntityOwners() {
    }

    /** 击杀者 / 召唤物 -> 玩家主人；本身就是玩家则返回它自己；否则 null */
    public static ServerPlayer resolvePlayerOwner(LivingEntity entity) {
        if (entity == null) return null;
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof OwnableEntity ownable) {
            if (ownable.getOwner() instanceof ServerPlayer player) return player;
        }
        Method method = OWNER_METHODS
                .computeIfAbsent(entity.getClass(), EntityOwners::findOwnerMethod)
                .orElse(null);
        if (method == null) return null;
        try {
            Object owner = method.invoke(entity);
            if (owner instanceof ServerPlayer player) return player;
        } catch (Exception ignored) {
            // 调用失败就当没有主人
        }
        return null;
    }

    /** 是否有玩家主人（不关心具体是谁） */
    public static boolean hasPlayerOwner(LivingEntity entity) {
        return resolvePlayerOwner(entity) != null;
    }

    private static Optional<Method> findOwnerMethod(Class<?> type) {
        try {
            return Optional.of(type.getMethod("getOwner"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
