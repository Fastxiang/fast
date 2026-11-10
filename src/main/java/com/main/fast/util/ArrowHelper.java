package com.main.fast.util;

import com.main.fast_irons_spellbooks_addition.util.FastEntityUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;

/**
 * 箭矢辅助工具。
 *
 * 技能箭与普通箭的区分方式：在箭矢的持久化数据（ForgeData）上打标记，
 * 凡是经过技能箭矢替换事件的箭矢都会被标记为技能箭。
 * 标记会随实体一起存档，因此读档后依然有效。
 */
public class ArrowHelper {

    /** 技能箭标记的 NBT key */
    public static final String SKILL_ARROW_TAG = "fast:skill_arrow";

    private ArrowHelper() {
    }

    /**
     * 是否为技能箭。
     * 技能箭实体（addition 模组的 SkillArrowEntity）以及被标记过的箭矢都算技能箭。
     */
    public static boolean isSkillArrow(AbstractArrow arrow) {
        if (arrow instanceof Arrow vanillaArrow && FastEntityUtil.isSkillArrow(vanillaArrow)) {
            return true;
        }

        return isMarkedSkillArrow(arrow);
    }

    /**
     * 是否被标记为技能箭（只看持久化标记，不看实体类型）。
     */
    public static boolean isMarkedSkillArrow(AbstractArrow arrow) {
        return arrow.getPersistentData().getBoolean(SKILL_ARROW_TAG);
    }

    /**
     * 把箭矢标记为技能箭。
     */
    public static void markSkillArrow(AbstractArrow arrow) {
        arrow.getPersistentData().putBoolean(SKILL_ARROW_TAG, true);
    }

    /**
     * 把旧箭矢的状态（位置、速度、朝向、伤害、穿透、主人等）复制到新箭矢，
     * 用于替换实体后保持弹道与威力一致。
     */
    public static void copyArrowState(AbstractArrow oldArrow, AbstractArrow newArrow) {
        if (oldArrow == newArrow) {
            return;
        }

        CompoundTag tag = new CompoundTag();
        oldArrow.saveWithoutId(tag);
        newArrow.load(tag);
    }
}