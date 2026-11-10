package com.main.fast.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

/**
 * 普通箭矢替换事件。
 *
 * 在原版弓、模组弓、弩以及生物弓箭射出箭矢并加入世界时触发。
 * 监听者可通过 {@link #setArrow(AbstractArrow)} 把生成的箭矢替换为其它箭矢。
 *
 * 该事件触发的箭矢均为普通箭矢，技能箭走 {@link SkillArrowReplaceEvent}。
 */
public class ArrowReplaceEvent extends Event {

    private final Level level;
    private final AbstractArrow originalArrow;
    private AbstractArrow arrow;

    public ArrowReplaceEvent(Level level, AbstractArrow arrow) {
        this.level = level;
        this.originalArrow = arrow;
        this.arrow = arrow;
    }

    public Level getLevel() {
        return level;
    }

    /**
     * 事件触发时原本生成的箭矢。
     */
    public AbstractArrow getOriginalArrow() {
        return originalArrow;
    }

    /**
     * 当前将要使用的箭矢，默认为原箭矢。
     */
    public AbstractArrow getArrow() {
        return arrow;
    }

    /**
     * 替换为其它箭矢。
     */
    public void setArrow(AbstractArrow arrow) {
        this.arrow = arrow;
    }

    /**
     * 射箭者，发射器射出的箭可能为 null。
     */
    public Entity getOwner() {
        return originalArrow.getOwner();
    }

    /**
     * 射箭者，且必须是生物；否则返回 null。
     */
    public LivingEntity getShooter() {
        return originalArrow.getOwner() instanceof LivingEntity living ? living : null;
    }
}