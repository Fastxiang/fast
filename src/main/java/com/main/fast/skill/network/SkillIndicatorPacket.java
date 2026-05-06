package com.main.fast.skill.network;

import com.main.fast.Fast;
import com.main.fast.skill.client.SkillIndicatorRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

public class SkillIndicatorPacket {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Fast.id("skill_indicator"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private final Vec3 center;
    private final int size;      // 外圈边长
    private final int innerSize; // 内圈边长，若 <=0 则为实心方
    private final int duration;
    private final int color;

    public SkillIndicatorPacket(Vec3 center, int size, int innerSize, int duration, int color) {
        this.center = center;
        this.size = size;
        this.innerSize = innerSize;
        this.duration = duration;
        this.color = color;
    }

    public static void init() {
        CHANNEL.registerMessage(0, SkillIndicatorPacket.class,
                (msg, buf) -> {
                    buf.writeDouble(msg.center.x);
                    buf.writeDouble(msg.center.y);
                    buf.writeDouble(msg.center.z);
                    buf.writeInt(msg.size);
                    buf.writeInt(msg.innerSize);
                    buf.writeInt(msg.duration);
                    buf.writeInt(msg.color);
                },
                buf -> new SkillIndicatorPacket(
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt()
                ),
                SkillIndicatorPacket::handle
        );
    }

    public static void sendToNearby(Level world, Vec3 center, int size, int innerSize, int duration, int color) {
        PacketDistributor.TargetPoint target = new PacketDistributor.TargetPoint(
                center.x, center.y, center.z,
                64,
                world.dimension()
        );
        CHANNEL.send(PacketDistributor.NEAR.with(() -> target),
                new SkillIndicatorPacket(center, size, innerSize, duration, color));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SkillIndicatorRenderer.addIndicator(center, size, innerSize, duration, color);
        });
        ctx.get().setPacketHandled(true);
    }
}