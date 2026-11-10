package com.main.fast.dungeon.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 技能树经验（Puffish Skills）软依赖桥。
 *
 * <p>旧 JS 调用 addPlayerXp(player, xp, "puffish_skills:fast")，这里用反射做等价操作：
 * SkillsAPI.getCategory(id).getExperience().addTotal(player, xp)，并把「+N 技能树经验」
 * 显示在玩家动作栏上。</p>
 *
 * <p>Puffish Skills 不是必需前置：找不到就静默跳过（只 warn 一次）。</p>
 */
public final class SkillsXp {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Dungeon");

    private static boolean resolved = false;
    private static boolean available = false;
    private static Method getCategory;
    private static Method getExperience;
    private static Method addTotal;

    private SkillsXp() {
    }

    public static void addXp(ServerPlayer player, int amount, String categoryId) {
        if (amount <= 0 || player == null) return;
        if (!resolve()) return;

        try {
            ResourceLocation id = ResourceLocation.tryParse(categoryId == null ? "puffish_skills:fast" : categoryId);
            if (id == null) return;

            Object categoryOptional = getCategory.invoke(null, id);
            if (!(categoryOptional instanceof Optional<?> optional) || optional.isEmpty()) return;

            Object category = optional.get();
            Object experienceOptional = getExperience.invoke(category);
            if (!(experienceOptional instanceof Optional<?> expOptional) || expOptional.isEmpty()) return;

            addTotal.invoke(expOptional.get(), player, amount);
            player.displayClientMessage(Component.literal("§a+" + amount + " 技能树经验"), true);
        } catch (Throwable t) {
            LOGGER.warn("[Fast/Dungeon] 发放技能树经验失败: {}", t.toString());
        }
    }

    private static boolean resolve() {
        if (resolved) return available;
        resolved = true;
        try {
            Class<?> api = Class.forName("net.puffish.skillsmod.api.SkillsAPI");
            Class<?> category = Class.forName("net.puffish.skillsmod.api.Category");
            Class<?> experience = Class.forName("net.puffish.skillsmod.api.Experience");

            getCategory = api.getMethod("getCategory", ResourceLocation.class);
            getExperience = category.getMethod("getExperience");
            addTotal = experience.getMethod("addTotal", ServerPlayer.class, int.class);
            available = true;
        } catch (Throwable t) {
            LOGGER.info("[Fast/Dungeon] 未检测到 Puffish Skills，副本经验不会发放");
            available = false;
        }
        return available;
    }
}
