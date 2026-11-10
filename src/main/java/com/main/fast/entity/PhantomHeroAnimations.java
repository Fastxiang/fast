package com.main.fast.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * 昔日勇者的幻影 - 技能动画资源。
 *
 * 这里记录的全部是「player 动画」的资源位置，
 * 也就是铁魔法 / 附属 mod 注册在
 * assets/&lt;modid&gt;/player_animation/ 下的那些动画。
 *
 * 铁魔法的施法动画系统（AnimationHelper）本质上是：
 *   1. AnimationHolder 里保存一个 ResourceLocation（动画名）
 *   2. 客户端用 PlayerAnimationRegistry.getAnimation(rl) 拿到 KeyframeAnimation
 *   3. 用 KeyframeAnimationPlayer 播放，再由 playerAnimator 把结果写到模型部件上
 *
 * 第 3 步 playerAnimator 只对 AbstractClientPlayer 生效，
 * 所以 Boss 用不了它的自动播放链路。但它的底层
 * dev.kosmx.playerAnim.impl.animation.AnimationApplier 是公开的，
 * 可以把任意 KeyframeAnimation 写到任意 ModelPart 上。
 *
 * 因此 Boss 的做法是：
 *   服务端把「动画资源位置」通过 SynchedEntityData 同步下来，
 *   客户端模型自己用 AnimationApplier 播放并写进人形模型部件。
 * 这样用的就是和玩家一模一样的那套动画文件，不需要复制资源。
 */
public class PhantomHeroAnimations {

    /**
     * 附属 mod（fast_irons_spellbooks_addition）的物理攻击动画
     */
    public static final ResourceLocation ATTACK_1 =
            ResourceLocation.fromNamespaceAndPath("fast_irons_spellbooks_addition", "attack1");

    public static final ResourceLocation ATTACK_2 =
            ResourceLocation.fromNamespaceAndPath("fast_irons_spellbooks_addition", "attack2");

    public static final ResourceLocation ATTACK_3 =
            ResourceLocation.fromNamespaceAndPath("fast_irons_spellbooks_addition", "attack3");

    public static final ResourceLocation ATTACK_4 =
            ResourceLocation.fromNamespaceAndPath("fast_irons_spellbooks_addition", "attack4");

    /**
     * 铁魔法本体的施法动画
     */
    public static final ResourceLocation CONTINUOUS_CAST_ONE_HANDED =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "continuous_thrust_one_handed");

    public static final ResourceLocation LONG_CAST =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "long_cast");

    public static final ResourceLocation INSTANT_PROJECTILE =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "instant_projectile");

    /**
     * 物理三连击的三段动画，顺序与 TripleStrikeSpell 的 combo 一致。
     */
    public static final ResourceLocation[] PHYSICAL_COMBO = {
            ATTACK_1,
            ATTACK_3,
            ATTACK_4
    };

    /**
     * 魔法三连击的三段动画，顺序与 TripleMagicMissileSpell 的 combo 一致。
     */
    public static final ResourceLocation[] MAGIC_COMBO = {
            CONTINUOUS_CAST_ONE_HANDED,
            CONTINUOUS_CAST_ONE_HANDED,
            LONG_CAST
    };

    /**
     * 三段各自的持续 tick。
     *
     * 这两条数值直接取自法术本身：
     *  - TripleStrikeSpell / TripleMagicMissileSpell 的 castTime = 8
     *  - 第三段（combo == 2）的 getEffectiveCastTime 返回 14
     *
     * 换算成动画长度正好是 0.4s / 0.4s / 0.7s，
     * 和 attack1 / attack3 / attack4 的 animation_length 完全一致。
     */
    public static final int[] COMBO_TICK_DELAY = {
            8,
            8,
            14
    };

    private PhantomHeroAnimations() {
    }
}
