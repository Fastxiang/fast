package com.main.fast.maid.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.Event;

/**
 * 女仆开始吟唱一个技能时抛出。
 *
 * <p>Java 侧用它做日志 / 后续扩展；KubeJS 侧用来沿用原来的「施法涨仇恨」逻辑
 * （{@code mobOverLimitSpellCast} 里对周围敌人调用 {@code AddOrSubtractMobHateValue}）。
 * 仇恨表本身还在 JS 的 global 里，Java 不复制一份。</p>
 */
public class MaidCastEvent extends Event {

    private final EntityMaid maid;
    private final AbstractSpell spell;
    private final int spellLevel;
    private final int castTime;

    public MaidCastEvent(EntityMaid maid, AbstractSpell spell, int spellLevel, int castTime) {
        this.maid = maid;
        this.spell = spell;
        this.spellLevel = spellLevel;
        this.castTime = castTime;
    }

    public EntityMaid getMaid() {
        return this.maid;
    }

    public AbstractSpell getSpell() {
        return this.spell;
    }

    public String getSpellId() {
        return this.spell.getSpellId();
    }

    public int getSpellLevel() {
        return this.spellLevel;
    }

    public int getCastTime() {
        return this.castTime;
    }
}
