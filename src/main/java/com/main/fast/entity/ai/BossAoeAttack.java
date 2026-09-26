package com.main.fast.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Boss 范围伤害工具类。
 *
 * 提供「实心方形」与「方形月环」两种 AOE 伤害判定，
 * 配合 {@link com.main.fast.skill.api.SkillIndicator} 的地面提示使用。
 *
 * 新增技能时直接调用这两个方法即可，无需重复编写查实体 / 判区域的逻辑。
 */
public class BossAoeAttack {

    private BossAoeAttack() {
    }

    /**
     * 实心方形范围伤害。
     *
     * @param attacker 发起者（不伤害自己）
     * @param damage   伤害值
     * @param level    世界
     * @param center   中心坐标
     * @param radius   半径（切比雪夫距离 <= radius 的实体受伤）
     */
    public static void damageSquare(
            LivingEntity attacker,
            float damage,
            Level level,
            Vec3 center,
            double radius
    ) {
        if (level.isClientSide) {
            return;
        }

        AABB box = new AABB(
                center.x - radius, center.y - 2.0D, center.z - radius,
                center.x + radius, center.y + 4.0D, center.z + radius
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e != attacker && e.isAlive()
        );

        for (LivingEntity e : entities) {

            double dx = Math.abs(e.getX() - center.x);
            double dz = Math.abs(e.getZ() - center.z);

            if (dx <= radius && dz <= radius) {
                e.hurt(
                        attacker.damageSources().mobAttack(attacker),
                        damage
                );
            }
        }
    }

    /**
     * 方形月环伤害（外圈受伤，内圈安全）。
     *
     * @param attacker    发起者（不伤害自己）
     * @param damage      伤害值
     * @param level       世界
     * @param center      中心坐标
     * @param outerRadius 外半径
     * @param innerRadius 内半径（切比雪夫距离 <= innerRadius 的实体安全）
     */
    public static void damageSquareRing(
            LivingEntity attacker,
            float damage,
            Level level,
            Vec3 center,
            double outerRadius,
            double innerRadius
    ) {
        if (level.isClientSide) {
            return;
        }

        AABB box = new AABB(
                center.x - outerRadius, center.y - 2.0D, center.z - outerRadius,
                center.x + outerRadius, center.y + 4.0D, center.z + outerRadius
        );

        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e != attacker && e.isAlive()
        );

        for (LivingEntity e : entities) {

            double chebyshev = Math.max(
                    Math.abs(e.getX() - center.x),
                    Math.abs(e.getZ() - center.z)
            );

            if (chebyshev <= outerRadius && chebyshev > innerRadius) {
                e.hurt(
                        attacker.damageSources().mobAttack(attacker),
                        damage
                );
            }
        }
    }
}
