package com.main.fast.maid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.main.fast.maid.MaidEquipment;
import com.main.fast.maid.MaidSkillRegistry;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * 战士站位：按「物理三连的扇形边缘」拉扯，而不是原版近战那样贴脸。
 *
 * <p>物理三连（{@code triple_strike}）的判定是<b>以施法者眼睛为中心、半径 3 格、半角 60°</b> 的水平扇形。
 * 女仆如果按原版近战 AI 一路贴到 1 格再打，这个扇形几乎全被面前那只怪占满，后面的怪根本不在范围里，
 * 看起来就像「AOE 比玩家的三连击短」——判定其实一模一样，差的是站位。</p>
 *
 * <p>所以这里接管战士的行走：</p>
 * <ul>
 *     <li>有勇者之剑 → 保持在 2.6 格左右（扇形半径 3，留一点余量），太近就退、太远就进</li>
 *     <li>没有勇者之剑 → 还是原版近战的 1.2 格，贴脸普攻</li>
 * </ul>
 */
public class MaidComboPositionTask extends Behavior<EntityMaid> {

    /** 有勇者之剑时的站位：扇形半径 3 格，站到 2.6 格让扇形铺开 */
    private static final double COMBO_STAND_OFF = 2.6D;
    /** 近到多少就后退，避免被怪顶到脸上 */
    private static final double COMBO_MIN_DISTANCE = 1.6D;
    /** 没有勇者之剑时按原版近战距离 */
    private static final double MELEE_STAND_OFF = 1.2D;

    /** 剑气三连击（triple_slash）是弹道技能：按远程站位，别贴脸 */
    private static final double RANGED_MIN_DISTANCE = 6.0D;
    private static final double RANGED_MAX_DISTANCE = 11.0D;

    private static final float APPROACH_SPEED = 0.6F;
    private static final float BACK_OFF_SPEED = 0.7F;

    public MaidComboPositionTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT
        ), 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return this.checkExtraStartConditions(level, maid);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null || !target.isAlive()) {
            return;
        }
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));

        // 吟唱中不挪窝：位移会打乱扇形朝向，铁魔法自己会在 initiateCast 时停一次导航
        if (maid instanceof IMagicEntity magic && magic.isCasting()) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }

        boolean combo = MaidEquipment.hasBauble(maid, MaidEquipment.HERO_SWORD);
        double standOff = combo ? COMBO_STAND_OFF : MELEE_STAND_OFF;

        if (!combo) {
            // 没有勇者之剑：交给原版近战距离判断，这条行为只负责靠近
            if (maid.distanceTo(target) > MELEE_STAND_OFF) {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(target, false), APPROACH_SPEED, 0));
            } else {
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
            return;
        }

        // 剑气三连击（带月牙之刃时）是弹道技能：按远程站位，和法师一样拉开距离打
        if (MaidSkillRegistry.isRangedBasicAttack(maid)) {
            this.tickRanged(maid, target);
            return;
        }

        double distance = maid.distanceTo(target);
        if (distance > standOff + 0.35D) {
            // 太远：走到目标身上，走位行为会在合适距离自动失效由下面接管
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(target, false), APPROACH_SPEED, 0));
        } else if (distance < COMBO_MIN_DISTANCE) {
            // 太近：往反方向退一步，把扇形铺开
            BlockPos back = this.backOffPos(maid, target, standOff - distance);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(back, BACK_OFF_SPEED, 1));
        } else {
            // 站住打
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    /** 远程形态（剑气三连击）：太远走近，太近后撤，中间站定输出 */
    private void tickRanged(EntityMaid maid, LivingEntity target) {
        double distance = maid.distanceTo(target);
        if (distance > RANGED_MAX_DISTANCE) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(target, false), APPROACH_SPEED, 0));
        } else if (distance < RANGED_MIN_DISTANCE) {
            BlockPos back = this.backOffPos(maid, target, RANGED_MIN_DISTANCE - distance);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(back, BACK_OFF_SPEED, 1));
        } else {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    /** 目标反方向、水平面上的一个落脚点 */
    private BlockPos backOffPos(EntityMaid maid, LivingEntity target, double distance) {
        Vec3 away = maid.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0.0D, away.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            // 正好重叠：随便挑一个方向，别除以 0
            flat = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 dest = maid.position().add(flat.normalize().scale(Math.max(distance, 0.8D)));
        return BlockPos.containing(dest.x, maid.getY(), dest.z);
    }
}
