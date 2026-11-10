package com.main.fast.dungeon.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Create 包裹（PackageItem / PackageEntity）软依赖桥。
 *
 * <p>旧 JS：{@code PackageItem.containing(ItemStackHandler)} + {@code PackageEntity}。
 * 这里全部走反射，没装 Create 时由调用方回落到「直接掉物品」。</p>
 */
public final class CreatePackages {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Dungeon");

    private static boolean resolved = false;
    private static boolean available = false;
    private static Class<?> handlerClass;
    private static Method setStackInSlot;
    private static Method containing;
    private static java.lang.reflect.Constructor<?> packageEntityConstructor;
    private static Method setBox;

    private CreatePackages() {
    }

    public static boolean spawn(ServerLevel level, double x, double y, double z, List<ItemStack> items) {
        if (!resolve()) return false;
        try {
            // 一个包裹只有 9 格：超出就拆成多个包裹，别把奖励吞掉
            for (int start = 0; start < items.size(); start += 9) {
                Object handler = handlerClass.getConstructor(int.class).newInstance(9);
                int limit = Math.min(items.size() - start, 9);
                for (int i = 0; i < limit; i++) {
                    setStackInSlot.invoke(handler, i, items.get(start + i));
                }

                Object box = containing.invoke(null, handler);
                Object entity = packageEntityConstructor.newInstance(level, x + 0.5D, y, z + 0.5D);
                setBox.invoke(entity, box);
                level.addFreshEntity((Entity) entity);
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[Fast/Dungeon] 生成 Create 包裹失败，改为直接掉落: {}", t.toString());
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) return available;
        resolved = true;
        try {
            handlerClass = Class.forName("net.minecraftforge.items.ItemStackHandler");
            Class<?> packageItem = Class.forName("com.simibubi.create.content.logistics.box.PackageItem");
            Class<?> packageEntity = Class.forName("com.simibubi.create.content.logistics.box.PackageEntity");

            setStackInSlot = handlerClass.getMethod("setStackInSlot", int.class, ItemStack.class);
            containing = packageItem.getMethod("containing", handlerClass);
            packageEntityConstructor = packageEntity.getConstructor(Level.class, double.class, double.class, double.class);
            setBox = packageEntity.getMethod("setBox", ItemStack.class);
            available = true;
        } catch (Throwable t) {
            LOGGER.info("[Fast/Dungeon] 未检测到 Create，副本奖励将直接掉落");
            available = false;
        }
        return available;
    }
}
