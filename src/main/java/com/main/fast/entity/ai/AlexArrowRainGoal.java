package com.main.fast.entity.ai;

import com.main.fast.entity.BossEntityAlex;
import com.main.fast.skill.api.SkillIndicator;
import com.main.fast_irons_spellbooks_addition.entity.spells.magic.ArrowRainEntity;
import com.main.fast_irons_spellbooks_addition.registry.FastEntityRegistry;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AlexArrowRainGoal extends Goal {

    private final BossEntityAlex boss;

    private Vec3[] positions;

    private int tick;

    private int step;

    private static final int INDICATOR_DURATION = 40; // 2秒

    private static final int INTERVAL = 20; // 1秒

    private static final int COOLDOWN = 20 * 30; // 30秒

    public AlexArrowRainGoal(BossEntityAlex boss) {
        this.boss = boss;

        this.setFlags(EnumSet.of(
                Goal.Flag.MOVE,
                Goal.Flag.LOOK
        ));
    }

    @Override
    public boolean canUse() {

        LivingEntity target = boss.getTarget();

        if (target == null)
            return false;

        if (boss.arrowRainCooldown > 0)
            return false;

        return boss.distanceTo(target) <= 8;
    }

    @Override
    public boolean canContinueToUse() {
        return step < positions.length;
    }

    @Override
    public void start() {

        boss.arrowRainCooldown = COOLDOWN;

        tick = 0;
        step = 0;

        Vec3 center = boss.position();

        double r = 4;

        positions = new Vec3[]{
                center,
                center.add(-r, 0, -r),
                center.add(r, 0, -r),
                center.add(-r, 0, r),
                center.add(r, 0, r)
        };

        // 第一个提示
        showIndicator(0);

        boss.arrowRainCooldown = COOLDOWN;
        boss.arrowRainPressureTime = 20 * 21;
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

        if (tick >= INTERVAL) {

            spawnArrowRain(positions[step]);

            step++;

            if (step < positions.length) {
                showIndicator(step);
            }

            tick = 0;
        }
    }

    private void showIndicator(int index) {

        Vec3 pos = positions[index];

        SkillIndicator.showSquareByRadius(
                boss.level(),
                pos,
                2,
                INDICATOR_DURATION,
                0x80FF0000
        );
    }

    private void spawnArrowRain(Vec3 pos) {

        boss.lastArrowRainCenter = pos;
        boss.setArrowRainAnimationTicks(10);

        ArrowRainEntity rain = new ArrowRainEntity(
                FastEntityRegistry.ARROW_RAIN.get(),
                boss.level()
        );

        rain.setOwner(boss);

        rain.setPos(
                pos.x,
                pos.y,
                pos.z
        );

        double attackDamage = boss.getAttribute(Attributes.ATTACK_DAMAGE).getValue();

        rain.setDamage((float) attackDamage);

        rain.setRows(60);

        rain.setArrowsPerRow(8);

        boss.level().addFreshEntity(rain);
    }

    @Override
    public void stop() {
        tick = 0;
        step = positions == null ? 5 : positions.length;
    }
}