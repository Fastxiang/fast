package com.main.fast.maid;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个女仆的技能快照（服务端，仅内存）。
 *
 * <p>数据由 KubeJS 推送：{@code maid_update_ender_bonus.js} 解析完末影饰品 / 技能树后，
 * 把 {@code skillList} 与「基础攻击该用哪个法术」一起交给 {@link com.main.fast.maid.api.FastMaidApi}。
 * Java 侧只读这份快照，不反向读取 KubeJS 的 global 表。</p>
 *
 * <p>冷却也记在这里：按「女仆 + 法术」记一条到期时间，用世界 gameTime 计算，
 * 与 JS 侧原来的 {@code addCooldown / isInCooldown} 语义一致（不落盘）。</p>
 */
public final class MaidSkillData {

    /** 技能 id -> 等级 */
    private final Map<ResourceLocation, Integer> skills = new HashMap<>();
    /** 槽位（physical / magic / arrow）-> 基础攻击法术 id */
    private final Map<ResourceLocation, ResourceLocation> basicSpells = new HashMap<>();
    /** 法术 id -> 冷却结束的 gameTime */
    private final Map<ResourceLocation, Long> readyAt = new HashMap<>();

    private long updatedAt;

    public Map<ResourceLocation, Integer> getSkills() {
        return this.skills;
    }

    public boolean hasSkill(ResourceLocation spellId) {
        Integer level = this.skills.get(spellId);
        return level != null && level > 0;
    }

    public int getLevel(ResourceLocation spellId) {
        Integer level = this.skills.get(spellId);
        return level == null ? 0 : level;
    }

    public void putSkill(ResourceLocation spellId, int level) {
        if (level <= 0) {
            this.skills.remove(spellId);
        } else {
            this.skills.put(spellId, level);
        }
    }

    public boolean isEmpty() {
        return this.skills.isEmpty();
    }

    public ResourceLocation getBasicSpell(String slot) {
        return this.basicSpells.get(new ResourceLocation(slot));
    }

    public void putBasicSpell(String slot, ResourceLocation spellId) {
        if (spellId == null) {
            this.basicSpells.remove(new ResourceLocation(slot));
        } else {
            this.basicSpells.put(new ResourceLocation(slot), spellId);
        }
    }

    /**
     * 该法术是否已经冷却完毕
     *
     * @param spellId 法术 id
     * @param gameTime 当前世界 gameTime
     */
    public boolean isReady(ResourceLocation spellId, long gameTime) {
        Long ready = this.readyAt.get(spellId);
        return ready == null || ready <= gameTime;
    }

    public void setCooldown(ResourceLocation spellId, int ticks, long gameTime) {
        if (ticks <= 0) {
            this.readyAt.remove(spellId);
        } else {
            this.readyAt.put(spellId, gameTime + ticks);
        }
    }

    public long getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 整套数据被替换时保留冷却，只清空技能与基础攻击 */
    public void replaceContent(Map<ResourceLocation, Integer> newSkills,
                               Map<ResourceLocation, ResourceLocation> newBasicSpells) {
        this.skills.clear();
        this.skills.putAll(newSkills);
        this.basicSpells.clear();
        this.basicSpells.putAll(newBasicSpells);
        // 不再拥有的技能，冷却一并丢弃，避免同名技能再次获得时被旧冷却卡住
        this.readyAt.keySet().removeIf(spellId -> !this.skills.containsKey(spellId));
    }
}
