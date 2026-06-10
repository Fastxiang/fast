package com.main.fast.skill.network;

import com.main.fast.Fast;
import com.main.fast.skill.client.SkillLineIndicatorRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class SkillLinePacket {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    Fast.id("skill_line"),
                    () -> PROTOCOL,
                    PROTOCOL::equals,
                    PROTOCOL::equals
            );

    private final int startEntityId;
    private final int endEntityId;

    private final boolean startIsEntity;
    private final boolean endIsEntity;

    private final Vec3 startPos;
    private final Vec3 endPos;

    private final int duration;
    private final int color;

    public SkillLinePacket(
            int startEntityId,
            int endEntityId,
            boolean startIsEntity,
            boolean endIsEntity,
            Vec3 startPos,
            Vec3 endPos,
            int duration,
            int color
    ) {
        this.startEntityId = startEntityId;
        this.endEntityId = endEntityId;

        this.startIsEntity = startIsEntity;
        this.endIsEntity = endIsEntity;

        this.startPos = startPos;
        this.endPos = endPos;

        this.duration = duration;
        this.color = color;
    }

    public static void init() {

        CHANNEL.registerMessage(
                0,
                SkillLinePacket.class,

                (msg, buf) -> {

                    buf.writeBoolean(msg.startIsEntity);
                    buf.writeBoolean(msg.endIsEntity);

                    buf.writeInt(msg.startEntityId);
                    buf.writeInt(msg.endEntityId);

                    buf.writeDouble(msg.startPos.x);
                    buf.writeDouble(msg.startPos.y);
                    buf.writeDouble(msg.startPos.z);

                    buf.writeDouble(msg.endPos.x);
                    buf.writeDouble(msg.endPos.y);
                    buf.writeDouble(msg.endPos.z);

                    buf.writeInt(msg.duration);
                    buf.writeInt(msg.color);
                },

                buf -> {

                    boolean startIsEntity =
                            buf.readBoolean();

                    boolean endIsEntity =
                            buf.readBoolean();

                    int startEntityId =
                            buf.readInt();

                    int endEntityId =
                            buf.readInt();

                    Vec3 startPos =
                            new Vec3(
                                    buf.readDouble(),
                                    buf.readDouble(),
                                    buf.readDouble()
                            );

                    Vec3 endPos =
                            new Vec3(
                                    buf.readDouble(),
                                    buf.readDouble(),
                                    buf.readDouble()
                            );

                    int duration =
                            buf.readInt();

                    int color =
                            buf.readInt();

                    return new SkillLinePacket(
                            startEntityId,
                            endEntityId,
                            startIsEntity,
                            endIsEntity,
                            startPos,
                            endPos,
                            duration,
                            color
                    );
                },

                SkillLinePacket::handle
        );
    }

    private static void handle(
            SkillLinePacket packet,
            Supplier<NetworkEvent.Context> ctx
    ) {

        ctx.get().enqueueWork(() -> {

            SkillLineIndicatorRenderer.addLineFromPacket(
                    packet
            );
        });

        ctx.get().setPacketHandled(true);
    }

    //==================================================
    // Send
    //==================================================

    public static void sendEntityToEntity(
            Entity start,
            Entity end,
            int duration,
            int color
    ) {

        Level level = start.level();

        PacketDistributor.TargetPoint point =
                new PacketDistributor.TargetPoint(
                        start.getX(),
                        start.getY(),
                        start.getZ(),
                        64,
                        level.dimension()
                );

        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> point),

                new SkillLinePacket(
                        start.getId(),
                        end.getId(),

                        true,
                        true,

                        Vec3.ZERO,
                        Vec3.ZERO,

                        duration,
                        color
                )
        );
    }

    public static void sendEntityToPos(
            Entity start,
            Vec3 end,
            int duration,
            int color
    ) {

        Level level = start.level();

        PacketDistributor.TargetPoint point =
                new PacketDistributor.TargetPoint(
                        start.getX(),
                        start.getY(),
                        start.getZ(),
                        64,
                        level.dimension()
                );

        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> point),

                new SkillLinePacket(
                        start.getId(),
                        -1,

                        true,
                        false,

                        Vec3.ZERO,
                        end,

                        duration,
                        color
                )
        );
    }

    public static void sendPosToEntity(
            Level level,
            Vec3 start,
            Entity end,
            int duration,
            int color
    ) {

        PacketDistributor.TargetPoint point =
                new PacketDistributor.TargetPoint(
                        start.x,
                        start.y,
                        start.z,
                        64,
                        level.dimension()
                );

        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> point),

                new SkillLinePacket(
                        -1,
                        end.getId(),

                        false,
                        true,

                        start,
                        Vec3.ZERO,

                        duration,
                        color
                )
        );
    }

    public static void sendPosToPos(
            Level level,
            Vec3 start,
            Vec3 end,
            int duration,
            int color
    ) {

        PacketDistributor.TargetPoint point =
                new PacketDistributor.TargetPoint(
                        start.x,
                        start.y,
                        start.z,
                        64,
                        level.dimension()
                );

        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> point),

                new SkillLinePacket(
                        -1,
                        -1,

                        false,
                        false,

                        start,
                        end,

                        duration,
                        color
                )
        );
    }

    // Getter

    public int getStartEntityId() {
        return startEntityId;
    }

    public int getEndEntityId() {
        return endEntityId;
    }

    public boolean isStartEntity() {
        return startIsEntity;
    }

    public boolean isEndEntity() {
        return endIsEntity;
    }

    public Vec3 getStartPos() {
        return startPos;
    }

    public Vec3 getEndPos() {
        return endPos;
    }

    public int getDuration() {
        return duration;
    }

    public int getColor() {
        return color;
    }
}