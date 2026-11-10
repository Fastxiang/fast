package com.main.fast.event;

import com.main.fast.util.ArrowHelper;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

/**
 * 箭矢替换事件的触发入口。
 *
 * 除了内部拦截器会自动触发外，其它模组/代码也可以直接调用这里的方法手动触发。
 */
public class ArrowReplaceEvents {

    private ArrowReplaceEvents() {
    }

    /**
     * 触发普通箭矢替换事件，返回最终使用的箭矢。
     * 若没有监听者替换，则返回传入的原箭矢。
     */
    public static AbstractArrow fireArrowReplace(Level level, AbstractArrow arrow) {
        ArrowReplaceEvent event = new ArrowReplaceEvent(level, arrow);
        MinecraftForge.EVENT_BUS.post(event);

        return resolve(event.getArrow(), arrow);
    }

    /**
     * 触发技能箭矢替换事件，返回最终使用的箭矢。
     * 无论是否被替换，返回的箭矢都会被标记为技能箭。
     */
    public static AbstractArrow fireSkillArrowReplace(Level level, AbstractArrow arrow) {
        SkillArrowReplaceEvent event = new SkillArrowReplaceEvent(level, arrow);
        MinecraftForge.EVENT_BUS.post(event);

        AbstractArrow result = resolve(event.getArrow(), arrow);
        ArrowHelper.markSkillArrow(result);

        return result;
    }

    /**
     * 处理替换结果：若被替换则把原箭矢的状态复制到新箭矢上，保持弹道与威力。
     */
    private static AbstractArrow resolve(AbstractArrow replacement, AbstractArrow original) {
        if (replacement == null || replacement == original) {
            return original;
        }

        ArrowHelper.copyArrowState(original, replacement);

        return replacement;
    }
}