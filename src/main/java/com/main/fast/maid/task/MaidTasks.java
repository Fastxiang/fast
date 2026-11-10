package com.main.fast.maid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.google.common.collect.Lists;
import com.main.fast.maid.MaidJob;
import com.mojang.datafixers.util.Pair;

import java.util.List;
import java.util.function.Predicate;

/**
 * 五个女仆职业 + 一个旧的隐藏兼容任务。
 *
 * <p>UID 全部沿用 KubeJS 时代的写法，存档里的职业不会丢；其中
 * {@code fast:physical2} 是旧「物理三连」任务，合并进战士后只保留隐藏壳子，
 * 老存档的女仆仍能正常战斗，但菜单里只剩「战士」。</p>
 */
public final class MaidTasks {

    private MaidTasks() {
    }

    public static void register(TaskManager manager) {
        manager.add(new Warrior("physical", false));
        manager.add(new Warrior("physical2", true));
        manager.add(new Mage());
        manager.add(new Ranger());
        manager.add(new Guardian());
        manager.add(new Priest());
    }

    /** 战士：有勇者之剑 + 物理普攻武器 → 物理连段；否则普通近战 */
    public static final class Warrior extends MaidTaskBase {
        public Warrior(String path, boolean hidden) {
            super(path, MaidJob.WARRIOR, "fast:the_hero_sword", hidden);
        }
    }

    /** 法师：远程法术，普攻用基础魔法法术 */
    public static final class Mage extends MaidTaskBase {
        public Mage() {
            super("magic", MaidJob.MAGE, "fast:the_hero_staff", false);
        }
    }

    /** 游侠：箭类技能 + 无箭射击，需要好感度 1 级 */
    public static final class Ranger extends MaidTaskBase {
        public Ranger() {
            super("bow", MaidJob.RANGER, "fast:the_hero_bow", false);
        }

        @Override
        public boolean isEnable(EntityMaid maid) {
            return maid.getFavorabilityManager().getLevel() >= 1;
        }

        @Override
        public List<Pair<String, Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
            return Lists.newArrayList(Pair.of("need_level_1",
                    (Predicate<EntityMaid>) entity -> entity.getFavorabilityManager().getLevel() >= 1));
        }
    }

    /** 守卫：举盾、嘲讽、拉开距离，不主动输出 */
    public static final class Guardian extends MaidTaskBase {
        public Guardian() {
            super("shield", MaidJob.GUARDIAN, "fast:the_hero_shield", false);
        }
    }

    /** 牧师：治疗与增益优先，没有治疗目标时补一点法术伤害 */
    public static final class Priest extends MaidTaskBase {
        public Priest() {
            super("support", MaidJob.PRIEST, "minecraft:golden_apple", false);
        }
    }
}
