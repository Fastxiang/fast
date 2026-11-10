package com.main.fast.skill.network;

import com.main.fast.Fast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 技能范围提示包（扇形 / 直线）。
 *
 * 和旧的 SkillIndicator（方块对齐的正方形）不同，这里直接描述
 * 「技能真正的判定形状」：
 *
 *   扇形 SECTOR —— 物理三连击。TripleStrikeSpell 的判定是
 *      「以施法者为中心、半径 = ENTITY_REACH、半角 60 度的水平扇形」，
 *      所以提示也必须是一模一样的扇形：同半径、同半角、同一个中心点。
 *
 *   直线 BEAM   —— 魔法三连击。TripleMagicMissileSpell 是沿
 *      getLookAngle() 直线飞行的弹射物（重力 0、无追踪），
 *      所以提示是一条沿视线方向的长条。
 *
 * 扇形的关键参数（半径、半角）由服务端按施法者的 ENTITY_REACH 属性下发，
 * 和法术判定读的是同一个属性，因此红圈 = 判定区域本身：
 * 圈外绝对不会挨打，圈内必定挨打。
 *
 * 每个提示带两个时长：
 *   duration —— 保持高亮的 tick 数（「这次预警有多久」）
 *   fade     —— 从出现到彻底消失的总 tick 数，必须 >= duration
 * 蓄力类技能（狙击）就是靠这两个值对齐「蓄力时间」和「提示时间」的。
 */
public class PhantomTelegraphPacket {

    public static final int SHAPE_SECTOR = 0;
    public static final int SHAPE_BEAM = 1;

    /**
     * 清除指令：把某个施法者残留的提示全部抹掉。
     *
     * 服务端在技能结束（Goal.stop）时下发一次，
     * 避免上一段攻击的提示残留到下一段 / 下一个技能。
     */
    public static final int SHAPE_CLEAR = -1;

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Fast.id("phantom_telegraph"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private final int shape;
    private final int ownerId;
    private final Vec3 origin;
    private final float yaw;
    private final float radius;
    private final float halfAngle;
    private final float halfWidth;
    private final int duration;
    private final int fade;
    private final int color;

    public PhantomTelegraphPacket(
            int shape,
            int ownerId,
            Vec3 origin,
            float yaw,
            float radius,
            float halfAngle,
            float halfWidth,
            int duration,
            int fade,
            int color
    ) {
        this.shape = shape;
        this.ownerId = ownerId;
        this.origin = origin;
        this.yaw = yaw;
        this.radius = radius;
        this.halfAngle = halfAngle;
        this.halfWidth = halfWidth;

        this.duration = Math.max(0, duration);
        this.fade = Math.max(this.duration, fade);

        this.color = color;
    }

    public int getShape() {
        return shape;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public Vec3 getOrigin() {
        return origin;
    }

    public float getYaw() {
        return yaw;
    }

    public float getRadius() {
        return radius;
    }

    public float getHalfAngle() {
        return halfAngle;
    }

    public float getHalfWidth() {
        return halfWidth;
    }

    public int getDuration() {
        return duration;
    }

    public int getFade() {
        return fade;
    }

    public int getColor() {
        return color;
    }

    public static void init() {
        CHANNEL.registerMessage(
                0,
                PhantomTelegraphPacket.class,
                (msg, buf) -> {
                    buf.writeInt(msg.shape);
                    buf.writeInt(msg.ownerId);
                    buf.writeDouble(msg.origin.x);
                    buf.writeDouble(msg.origin.y);
                    buf.writeDouble(msg.origin.z);
                    buf.writeFloat(msg.yaw);
                    buf.writeFloat(msg.radius);
                    buf.writeFloat(msg.halfAngle);
                    buf.writeFloat(msg.halfWidth);
                    buf.writeInt(msg.duration);
                    buf.writeInt(msg.fade);
                    buf.writeInt(msg.color);
                },
                buf -> new PhantomTelegraphPacket(
                        buf.readInt(),
                        buf.readInt(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt()
                ),
                PhantomTelegraphPacket::handle
        );
    }

    public static void sendToNearby(
            Level world,
            Vec3 center,
            int shape,
            int ownerId,
            float yaw,
            float radius,
            float halfAngle,
            float halfWidth,
            int duration,
            int color
    ) {
        sendToNearby(
                world,
                center,
                shape,
                ownerId,
                yaw,
                radius,
                halfAngle,
                halfWidth,
                duration,
                duration,
                color
        );
    }

    public static void sendToNearby(
            Level world,
            Vec3 center,
            int shape,
            int ownerId,
            float yaw,
            float radius,
            float halfAngle,
            float halfWidth,
            int duration,
            int fade,
            int color
    ) {
        PacketDistributor.TargetPoint target = new PacketDistributor.TargetPoint(
                center.x,
                center.y,
                center.z,
                64,
                world.dimension()
        );

        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> target),
                new PhantomTelegraphPacket(
                        shape,
                        ownerId,
                        center,
                        yaw,
                        radius,
                        halfAngle,
                        halfWidth,
                        duration,
                        fade,
                        color
                )
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.main.fast.skill.network.client.SkillClientPacketHandlers
                        .handlePhantomTelegraph(this, ctx)
        );
    }
}
