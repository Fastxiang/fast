package com.main.fast.maid.api;

import com.main.fast.maid.MaidSkillData;
import com.main.fast.maid.MaidSkillRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 女仆 AI 对外 API（KubeJS 侧唯一入口）。
 *
 * <pre>{@code
 * const $FastMaid = Java.loadClass("com.main.fast.maid.api.FastMaid")
 *
 * // 1) 法术分类：原来写进 MagicSkillList / ArrowSkillList 的那张表，现在存到 Java
 * $FastMaid.registerSpellType("irons_spellbooks:fireball", "magic", 0, 0)
 *
 * // 2) 技能标签：skill_tag.js 的 addSkillTag 顺手推一份，决策器靠它判断该不该放
 * $FastMaid.registerSkillTag("irons_spellbooks:fireball", "aoe")
 *
 * // 3) 每个女仆的技能表 + 基础攻击法术（末影饰品会换法术，必须由 JS 决定）
 * $FastMaid.setMaidData(maid, JSON.stringify({
 *     skills: skillList,
 *     basic: {
 *         physical: "fast_irons_spellbooks_addition:triple_strike",
 *         magic: "fast_irons_spellbooks_addition:triple_magic_missile"
 *     }
 * }))
 * }</pre>
 *
 * <p>职业任务、连段、动画与特效全部在 Java 里；这份 API 只负责把「数据」送进来。
 * JS 不推送时，Java 侧按空技能表处理，职业退化成普通武器行为（基础实现）。</p>
 */
public final class FastMaid {

    private FastMaid() {
    }

    // ---------------- 登记 ----------------

    public static void registerSpellType(String spellId, String type, int buffCooldown, int buffCooldownPerLevel) {
        MaidSkillRegistry.registerSpellType(spellId, type, buffCooldown, buffCooldownPerLevel);
    }

    public static void registerSkillTag(String spellId, String tag) {
        MaidSkillRegistry.registerSkillTag(spellId, tag);
    }

    /** 覆盖一个女仆的技能快照，结构与 {@link MaidSkillRegistry#updateFromJson} 一致 */
    public static void setMaidData(Entity entity, String json) {
        MaidSkillRegistry.updateFromJson(entity, json);
    }

    public static void clearMaidData(Entity entity) {
        MaidSkillRegistry.clear(entity);
    }

    // ---------------- 查询（给脚本用） ----------------

    public static int getSkillLevel(Entity entity, String spellId) {
        MaidSkillData data = MaidSkillRegistry.getData(entity);
        if (data == null) {
            return 0;
        }
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        return id == null ? 0 : data.getLevel(id);
    }

    public static boolean hasSkill(Entity entity, String spellId) {
        return getSkillLevel(entity, spellId) > 0;
    }

    public static String getSpellCategory(String spellId) {
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        if (id == null) {
            return "none";
        }
        return MaidSkillRegistry.getCategory(id).name().toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean hasSkillTag(String spellId, String tag) {
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        return id != null && MaidSkillRegistry.hasTag(id, tag);
    }

    /** buff 技能冷却（tick），供 {@code mobOverLimitSpellCastWithCooldown} 沿用同一套公式 */
    public static int getBuffCooldown(String spellId, int spellLevel) {
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        return id == null ? 0 : MaidSkillRegistry.getBuffCooldown(id, spellLevel);
    }
}
