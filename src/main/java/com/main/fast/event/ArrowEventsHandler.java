package com.main.fast.event;

import com.main.fast.util.ArrowHelper;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * 箭矢替换的触发点。
 *
 * 所有箭矢加入世界时统一拦截：
 *   - 技能箭（SkillArrowEntity 或已被标记） → {@link SkillArrowReplaceEvent}
 *   - 其它箭矢（原版弓、模组弓、弩、生物弓箭等） → {@link ArrowReplaceEvent}
 *
 * 替换后重新加入世界的那支箭不会再被拦截（{@link #REPLACING}），避免无限递归。
 */
public class ArrowEventsHandler {
    private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);

    private ArrowEventsHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ArrowEventsHandler::onEntityJoinLevel);
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof Arrow arrow) || REPLACING.get()) {
            return;
        }

        Level level = event.getLevel();

        AbstractArrow result = ArrowHelper.isSkillArrow(arrow)
                ? ArrowReplaceEvents.fireSkillArrowReplace(level, arrow)
                : ArrowReplaceEvents.fireArrowReplace(level, arrow);

        if (result == arrow) {
            return;
        }

        // 取消原箭矢的加入，改为加入替换后的箭矢
        event.setCanceled(true);

        REPLACING.set(true);
        try {
            level.addFreshEntity(result);
        } finally {
            REPLACING.set(false);
        }
    }
}