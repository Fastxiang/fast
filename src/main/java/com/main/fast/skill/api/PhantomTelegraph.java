package com.main.fast.skill.api;

import com.main.fast.skill.network.PhantomTelegraphPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 技能范围提示（对外接口）。
 *
 * 和旧的 SkillIndicator（方块对齐的正方形）不同，这里下发的是
 * 「技能真实的判定形状」，形状参数全部取自法术本体：
 *
 *   扇形 —— 对应 TripleStrikeSpell
 *      法术内部判定（已修正为严格扇形）：
 *          水平距离 <= range  &&  水平夹角 <= 60 度
 *      range = 施法者的 forge ENTITY_REACH 属性。
 *      => 这里画的就是同一个扇形：同中心、同半径、同半角。
 *
 *   直线 —— 对应 TripleMagicMissileSpell
 *      法术内部判定：TripleMagicMissileProjectile 沿 caster.getLookAngle()
 *      直线飞行（setNoGravity(true)，无追踪目标），碰到实体或方块才消失。
 *      => 这里是一条沿视线的长条，长度 = 实际飞行距离。
 *
 * 提示绑定施法者实体，客户端每帧按实体的最新位置和视线重画，
 * 预警期间 Boss 转身时红圈跟着转。
 */
public class PhantomTelegraph {

    /** 物理三连击的锥形判定半角，直接取自 TripleStrikeSpell 的 halfAngle */
    public static final float PHYSICAL_HALF_ANGLE = 60.0F;

    /** 魔法弹射物提示条的半宽（判定本体是弹射物包围盒 +0.1，这里略微加宽只为可见性） */
    public static final float MAGIC_HALF_WIDTH = 0.7F;

    /** 预警色：半透明红，会随时间渐隐 */
    public static final int COLOR_WARNING = 0x8CFF1414;

    /** 命中瞬间的高亮红：不透明，到期直接消失 */
    public static final int COLOR_FLASH = 0xFFFF3C3C;

    /**
     * 扇形提示。
     *
     * @param radius 半径，应当等于施法者 ENTITY_REACH 属性的值
     * @param halfAngleDeg 半角（度），TripleStrikeSpell 固定 60 度
     */
    public static void showSector(
            Level world,
            Entity owner,
            float radius,
            float halfAngleDeg,
            int durationTicks,
            int color
    ) {
        showSector(world, owner, radius, halfAngleDeg, durationTicks, durationTicks, color);
    }

    /**
     * 扇形提示。
     *
     * @param radius 半径，应当等于施法者 ENTITY_REACH 属性的值
     * @param halfAngleDeg 半角（度），TripleStrikeSpell 固定 60 度
     */
    public static void showSector(
            Level world,
            Entity owner,
            float radius,
            float halfAngleDeg,
            int durationTicks,
            int fadeTicks,
            int color
    ) {
        if (world == null || world.isClientSide) {
            return;
        }

        Vec3 origin = owner.position();

        PhantomTelegraphPacket.sendToNearby(
                world,
                origin,
                PhantomTelegraphPacket.SHAPE_SECTOR,
                owner.getId(),
                owner.getYRot(),
                radius,
                halfAngleDeg,
                0.0F,
                durationTicks,
                fadeTicks,
                color
        );
    }

    /**
     * 清除某个施法者所有残留的提示。
     *
     * 技能结束（Goal.stop）时必须调用一次：
     * 命中闪光的生命周期和动画段是分开算的，靠自然过期的话，
     * 最后一段的提示会残留到下一个动作开始之后。
     */
    public static void clear(Level world, Entity owner) {

        if (world == null || world.isClientSide || owner == null) {
            return;
        }

        PhantomTelegraphPacket.sendToNearby(
                world,
                owner.position(),
                PhantomTelegraphPacket.SHAPE_CLEAR,
                owner.getId(),
                owner.getYRot(),
                0.0F,
                0.0F,
                0.0F,
                0,
                0
        );
    }

    /**
     * 直线提示（弹射物飞行路径）。
     *
     * @param length 长度，即弹射物实际会飞过的距离
     */
    public static void showBeam(
            Level world,
            Entity owner,
            float length,
            float halfWidth,
            int durationTicks,
            int color
    ) {
        showBeam(world, owner, length, halfWidth, durationTicks, durationTicks, color);
    }

    /**
     * 直线提示（弹射物飞行路径），带独立的「淡出时长」。
     *
     * @param length        长度，即弹射物实际会飞过的距离
     * @param durationTicks 提示真正生效（保持高亮）的时长；
     *                      传 0 表示「一直有效到 fadeTicks 结束」，即全程渐隐
     * @param fadeTicks     提示从出现到彻底消失的总时长，必须 >= durationTicks
     */
    public static void showBeam(
            Level world,
            Entity owner,
            float length,
            float halfWidth,
            int durationTicks,
            int fadeTicks,
            int color
    ) {
        if (world == null || world.isClientSide) {
            return;
        }

        Vec3 origin = owner.position();

        PhantomTelegraphPacket.sendToNearby(
                world,
                origin,
                PhantomTelegraphPacket.SHAPE_BEAM,
                owner.getId(),
                owner.getYRot(),
                length,
                0.0F,
                halfWidth,
                durationTicks,
                fadeTicks,
                color
        );
    }
}
