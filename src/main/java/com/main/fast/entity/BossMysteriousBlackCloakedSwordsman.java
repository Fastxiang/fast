package com.main.fast.entity;

import com.main.fast.entity.ai.MysteriousComboAttackGoal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 神秘黑衣剑客 Boss。
 *
 * 近战剑客，手持下界合金剑，拥有连段 AOE 技能：
 *
 * {@link MysteriousComboAttackGoal} 一次性释放「实心方形斩」+「方形月环斩」，
 * 起手招式随机。技能期间 boss 不可移动、不可击退，
 * 两个 AOE 共用同一中心，形成进退走位博弈。
 *
 * 新增技能：在 registerGoals 里加一个 Goal，
 * 配合 {@link com.main.fast.entity.ai.BossAoeAttack} 即可快速实现新机制。
 */
public class BossMysteriousBlackCloakedSwordsman extends FastBossEntity {

    // 连段斩击冷却（包含实心斩 + 月环斩）
    public int comboAttackCooldown = 0;

    public BossMysteriousBlackCloakedSwordsman(
            EntityType<? extends PathfinderMob> type,
            Level level
    ) {
        super(type, level);

        // 手持下界合金剑
        this.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.NETHERITE_SWORD)
        );
    }

    @Override
    public ResourceLocation getSkinTexture() {
        return ResourceLocation.fromNamespaceAndPath(
                "fast",
                "textures/entity/mysterious_black_cloaked_swordsman.png"
        );
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(
                0,
                new FloatGoal(this)
        );

        // 连段 AOE 技能优先级高于普通近战
        this.goalSelector.addGoal(
                1,
                new MysteriousComboAttackGoal(this)
        );

        this.goalSelector.addGoal(
                3,
                new MeleeAttackGoal(
                        this,
                        1.2D,
                        true
                )
        );

        this.goalSelector.addGoal(
                4,
                new WaterAvoidingRandomStrollGoal(
                        this,
                        0.8D
                )
        );

        this.goalSelector.addGoal(
                5,
                new LookAtPlayerGoal(
                        this,
                        Player.class,
                        16.0F
                )
        );

        this.goalSelector.addGoal(
                6,
                new RandomLookAroundGoal(this)
        );

        this.targetSelector.addGoal(
                1,
                new HurtByTargetGoal(this)
        );

        this.targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(
                        this,
                        Player.class,
                        true
                )
        );
    }

    @Override
    public void tick() {
        super.tick();

        if (comboAttackCooldown > 0) {
            comboAttackCooldown--;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 击退抗性不在常驻属性里，而是由 MysteriousComboAttackGoal 在技能期间临时加满
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 700.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 20.0D)
                .add(Attributes.ARMOR, 15.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }
}
