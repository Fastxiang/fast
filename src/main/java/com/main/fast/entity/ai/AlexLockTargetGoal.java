package com.main.fast.entity.ai;

import com.main.fast.entity.BossEntityAlex;
import com.main.fast.skill.api.SkillLineIndicator;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class AlexLockTargetGoal extends Goal {

    private final BossEntityAlex boss;

    private static final int LOCK_DURATION = 40; // 2秒

    private static final int COOLDOWN = 20 * 10; // 10秒

    private int cooldown = 0;

    public AlexLockTargetGoal(BossEntityAlex boss) {
        this.boss = boss;
    }

    @Override
    public boolean canContinueToUse() {

        if (!(boss instanceof IMagicEntity mob))
            return false;

        return mob.isCasting();
    }

    @Override
    public void tick() {

        boss.getNavigation().stop();

        LivingEntity target = boss.getTarget();

        if (target != null) {
            boss.getLookControl().setLookAt(
                    target,
                    30,
                    30
            );
        }
    }

    @Override
    public boolean canUse() {

        LivingEntity target = boss.getTarget();

        if (target == null)
            return false;

        if (boss.lockTargetCooldown > 0)
            return false;

        return boss.distanceTo(target) >= 12.0F;
    }

    @Override
    public void start() {

        LivingEntity target = boss.getTarget();

        if (target != null) {
            SkillLineIndicator.showTrackingLine(
                    boss,
                    target,
                    40,
                    0xFFFF0000
            );
            if (boss instanceof IMagicEntity mob) {
                mob.initiateCastSpell(SpellRegistry.POISON_ARROW_SPELL.get(), 1);
            }
        }

        boss.lockTargetCooldown = 20 * 6;
    }
}