package com.main.fast.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

/**
 * 技能箭矢替换事件。
 *
 * 技能生成的箭矢（例如 addition 模组的 SkillArrowEntity）在加入世界时触发。
 * 监听者可通过 {@link #setArrow(AbstractArrow)} 替换为其它箭矢。
 *
 * 经过本事件的箭矢（无论是否被替换）都会被标记为技能箭，
 * 之后可用 {@link com.main.fast.util.ArrowHelper#isSkillArrow(AbstractArrow)} 区分于普通箭矢。
 */
public class SkillArrowReplaceEvent extends Event {

    private final Level level;
    private final AbstractArrow originalArrow;
    private AbstractArrow arrow;

    public SkillArrowReplaceEvent(Level level, AbstractArrow arrow) {
        this.level = level;
        this.originalArrow = arrow;
        this.arrow = arrow;
    }

    public Level getLevel() {
        return level;
    }

    /**
     * 事件触发时技能原本生成的箭矢。
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
     * 技能施放者。
     */
    public Entity getOwner() {
        return originalArrow.getOwner();
    }

    /**
     * 技能施放者，且必须是生物；否则返回 null。
     */
    public LivingEntity getShooter() {
        return originalArrow.getOwner() instanceof LivingEntity living ? living : null;
    }
}