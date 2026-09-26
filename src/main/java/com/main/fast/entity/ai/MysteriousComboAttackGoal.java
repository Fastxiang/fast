package com.main.fast.entity.ai;

import com.main.fast.entity.BossMysteriousBlackCloakedSwordsman;
import com.main.fast.skill.api.SkillIndicator;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 神秘黑衣剑客 - 连段斩击。
 *
 * 实心方形斩 + 方形月环斩 作为一个连续技能一起释放，
 * 起手是实心还是月环随机决定。两个 AOE 共用同一个中心，
 * 形成「出圈 → 进圈」或「进圈 → 出圈」的走位博弈。
 *
 * 技能期间：
 *   - 移动速度临时降到 0（不乱跑）
 *   - 击退抗性临时拉满（不被击退）
 *   - 锁定 MOVE / LOOK flag，普通近战不会插手
 *
 * 技能结束（或被打断）后自动移除这两个临时修饰器。
 */
public class MysteriousComboAttackGoal extends Goal {

    private final BossMysteriousBlackCloakedSwordsman boss;

    // 整个连段冷却（从起手开始计）
    private static final int COOLDOWN = 20 * 10;

    // 单段：预警 0.5 秒
    private static final int WARNING_TICKS = 10;

    // 单段：闪烁 0.5 秒
    private static final int FLASH_TICKS = 10;

    // 单段总时长
    private static final int PHASE_LEN = WARNING_TICKS + FLASH_TICKS;

    // 触发距离
    private static final float TRIGGER_DISTANCE = 12.0F;

    // 实心方形半径
    private static final int SOLID_RADIUS = 6;

    // 月环外半径
    private static final int RING_OUTER = 10;

    // 月环内半径（安全区）
    private static final int RING_INNER = 3;

    // AOE 伤害
    private static final float DAMAGE = 30.0F;

    // 提示颜色
    private static final int COLOR_WARNING = 0x80FF0000;
    private static final int COLOR_FLASH = 0xFFFF0000;

    // 技能期间临时修饰器（停止移动 + 免疫击退）
    private static final UUID SPEED_LOCK_ID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181A");
    private static final AttributeModifier SPEED_LOCK =
            new AttributeModifier(SPEED_LOCK_ID, "fast:boss_combo_speed_lock", -9.0D, AttributeModifier.Operation.ADDITION);

    private static final UUID KB_LOCK_ID = UUID.fromString("6DE28F63-B80B-4C44-B63A-A1EF04C28291");
    private static final AttributeModifier KB_LOCK =
            new AttributeModifier(KB_LOCK_ID, "fast:boss_combo_kb_lock", 1.0D, AttributeModifier.Operation.ADDITION);

    // true = 实心起手，false = 月环起手
    private boolean solidFirst;

    private int tick;
    private boolean started;

    // 两个 AOE 共用中心（技能起手时锁定 boss 位置）
    private Vec3 center;

    public MysteriousComboAttackGoal(BossMysteriousBlackCloakedSwordsman boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        if (boss.comboAttackCooldown > 0) {
            return false;
        }

        return boss.distanceTo(target) <= TRIGGER_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        return started && tick < 2 * PHASE_LEN;
    }

    @Override
    public void start() {
        started = true;
        tick = 0;

        // 随机决定起手招式
        solidFirst = boss.getRandom().nextBoolean();

        // 锁定 AOE 中心
        center = boss.position();

        boss.comboAttackCooldown = COOLDOWN;
        boss.getNavigation().stop();

        applyBuffs();

        // 第一段预警
        showWarning(isSolidPhase(0), center);
    }

    @Override
    public void tick() {
        boss.getNavigation().stop();

        LivingEntity target = boss.getTarget();

        if (target != null) {
            boss.getLookControl().setLookAt(
                    target,
                    30F,
                    30F
            );
        }

        tick++;

        // ===== 第一段（ticks 0 ~ PHASE_LEN）=====
        if (tick == WARNING_TICKS) {
            showFlash(isSolidPhase(0), center);
        }

        if (tick == PHASE_LEN) {
            // 第一段伤害
            dealDamage(isSolidPhase(0), center);
            // 紧接第二段预警
            showWarning(isSolidPhase(1), center);
        }

        // ===== 第二段（ticks PHASE_LEN ~ 2*PHASE_LEN）=====
        if (tick == PHASE_LEN + WARNING_TICKS) {
            showFlash(isSolidPhase(1), center);
        }

        if (tick == 2 * PHASE_LEN) {
            dealDamage(isSolidPhase(1), center);
        }
    }

    @Override
    public void stop() {
        started = false;
        tick = 0;
        removeBuffs();
    }

    /**
     * 指定阶段是否为实心斩。
     *
     * @param phase 0 = 第一段，1 = 第二段
     */
    private boolean isSolidPhase(int phase) {
        // phase 0 跟随 solidFirst；phase 1 取反
        return (phase == 0) == solidFirst;
    }

    private void showWarning(boolean solid, Vec3 pos) {
        if (solid) {
            SkillIndicator.showSquareByRadius(
                    boss.level(), pos, SOLID_RADIUS, WARNING_TICKS, COLOR_WARNING
            );
        } else {
            SkillIndicator.showSquareRingByRadius(
                    boss.level(), pos, RING_OUTER, RING_INNER, WARNING_TICKS, COLOR_WARNING
            );
        }
    }

    private void showFlash(boolean solid, Vec3 pos) {
        if (solid) {
            SkillIndicator.showSquareByRadius(
                    boss.level(), pos, SOLID_RADIUS, FLASH_TICKS, COLOR_FLASH
            );
        } else {
            SkillIndicator.showSquareRingByRadius(
                    boss.level(), pos, RING_OUTER, RING_INNER, FLASH_TICKS, COLOR_FLASH
            );
        }
    }

    private void dealDamage(boolean solid, Vec3 pos) {
        if (solid) {
            BossAoeAttack.damageSquare(
                    boss, DAMAGE, boss.level(), pos, SOLID_RADIUS
            );
        } else {
            BossAoeAttack.damageSquareRing(
                    boss, DAMAGE, boss.level(), pos, RING_OUTER, RING_INNER
            );
        }
    }

    private void applyBuffs() {
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_LOCK);
            speed.addTransientModifier(SPEED_LOCK);
        }

        AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(KB_LOCK);
            kb.addTransientModifier(KB_LOCK);
        }
    }

    private void removeBuffs() {
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_LOCK);
        }

        AttributeInstance kb = boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(KB_LOCK);
        }
    }
}
