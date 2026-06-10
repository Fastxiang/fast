package com.main.fast.skill.client;

import com.main.fast.Fast;
import com.main.fast.skill.network.SkillLinePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Mod.EventBusSubscriber(
        modid = Fast.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class SkillLineIndicatorRenderer {

    private static final ResourceLocation WHITE_TEXTURE =
            Fast.id("textures/misc/white.png");

    private static final List<ActiveLine> LINES =
            new LinkedList<>();

    private static final float DEFAULT_WIDTH = 0.4F;

    private static class ActiveLine {

        Entity startEntity;
        Entity endEntity;

        Vec3 startPos;
        Vec3 endPos;

        int duration;
        int tick;

        int color;

        ActiveLine(
                Entity startEntity,
                Entity endEntity,
                Vec3 startPos,
                Vec3 endPos,
                int duration,
                int color
        ) {
            this.startEntity = startEntity;
            this.endEntity = endEntity;
            this.startPos = startPos;
            this.endPos = endPos;
            this.duration = duration;
            this.tick = duration;
            this.color = color;
        }

        void update() {
            tick--;
        }

        boolean expired() {
            return tick <= 0;
        }

        boolean invalid() {

            if (startEntity != null && !startEntity.isAlive()) {
                return true;
            }

            if (endEntity != null && !endEntity.isAlive()) {
                return true;
            }

            return false;
        }

        Vec3 getStart() {

            if (startEntity != null) {
                return startEntity.position();
            }

            return startPos;
        }

        Vec3 getEnd() {

            if (endEntity != null) {
                return endEntity.position();
            }

            return endPos;
        }
    }

    // ==========================================
    // API
    // ==========================================

    public static void addLine(
            Vec3 start,
            Vec3 end,
            int duration,
            int color
    ) {
        LINES.add(
                new ActiveLine(
                        null,
                        null,
                        start,
                        end,
                        duration,
                        color
                )
        );
    }

    public static void addTrackingLine(
            Entity start,
            Entity end,
            int duration,
            int color
    ) {
        LINES.add(
                new ActiveLine(
                        start,
                        end,
                        null,
                        null,
                        duration,
                        color
                )
        );
    }

    public static void addTrackingLine(
            Entity start,
            Vec3 end,
            int duration,
            int color
    ) {
        LINES.add(
                new ActiveLine(
                        start,
                        null,
                        null,
                        end,
                        duration,
                        color
                )
        );
    }

    public static void addTrackingLine(
            Vec3 start,
            Entity end,
            int duration,
            int color
    ) {
        LINES.add(
                new ActiveLine(
                        null,
                        end,
                        start,
                        null,
                        duration,
                        color
                )
        );
    }

    public static void addLineFromPacket(
            SkillLinePacket packet
    ) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            return;
        }

        Entity startEntity = null;
        Entity endEntity = null;

        if (packet.isStartEntity()) {
            startEntity =
                    mc.level.getEntity(
                            packet.getStartEntityId()
                    );
        }

        if (packet.isEndEntity()) {
            endEntity =
                    mc.level.getEntity(
                            packet.getEndEntityId()
                    );
        }

        LINES.add(
                new ActiveLine(
                        startEntity,
                        endEntity,
                        packet.getStartPos(),
                        packet.getEndPos(),
                        packet.getDuration(),
                        packet.getColor()
                )
        );
    }

    // ==========================================
    // Tick
    // ==========================================

    @SubscribeEvent
    public static void clientTick(
            TickEvent.ClientTickEvent event
    ) {

        if (event.phase != TickEvent.Phase.END)
            return;

        Iterator<ActiveLine> iterator =
                LINES.iterator();

        while (iterator.hasNext()) {

            ActiveLine line =
                    iterator.next();

            line.update();

            if (line.expired() || line.invalid()) {
                iterator.remove();
            }
        }
    }

    // ==========================================
    // Render
    // ==========================================

    @SubscribeEvent
    public static void render(
            RenderLevelStageEvent event
    ) {

        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
            return;

        if (LINES.isEmpty())
            return;

        Minecraft mc =
                Minecraft.getInstance();

        if (mc.level == null)
            return;

        Vec3 cam =
                mc.gameRenderer
                        .getMainCamera()
                        .getPosition();

        PoseStack poseStack =
                event.getPoseStack();

        MultiBufferSource.BufferSource buffers =
                mc.renderBuffers()
                        .bufferSource();

        VertexConsumer consumer =
                buffers.getBuffer(
                        RenderType.entityTranslucent(
                                WHITE_TEXTURE
                        )
                );

        for (ActiveLine line : LINES) {

            Vec3 start =
                    line.getStart();

            Vec3 end =
                    line.getEnd();

            int argb =
                    line.color;

            float alpha =
                    ((argb >> 24) & 255)
                            / 255F;

            alpha *=
                    line.tick
                            / (float) line.duration;

            if (alpha <= 0.01F)
                continue;

            float r =
                    ((argb >> 16) & 255)
                            / 255F;

            float g =
                    ((argb >> 8) & 255)
                            / 255F;

            float b =
                    (argb & 255)
                            / 255F;

            drawLaserRect(
                    poseStack,
                    consumer,
                    cam,
                    start,
                    end,
                    DEFAULT_WIDTH,
                    r,
                    g,
                    b,
                    alpha
            );
        }

        buffers.endBatch(
                RenderType.entityTranslucent(
                        WHITE_TEXTURE
                )
        );
    }

    private static void drawLaserRect(
            PoseStack poseStack,
            VertexConsumer consumer,
            Vec3 cam,
            Vec3 start,
            Vec3 end,
            float width,
            float r,
            float g,
            float b,
            float a
    ) {

        Vec3 dir =
                end.subtract(start);

        double length =
                dir.length();

        if (length < 0.001)
            return;

        dir =
                dir.normalize();

        Vec3 side =
                new Vec3(
                        -dir.z,
                        0,
                        dir.x
                ).normalize()
                        .scale(width * 0.5);

        Vec3 p1 =
                start.add(side);

        Vec3 p2 =
                start.subtract(side);

        Vec3 p3 =
                end.subtract(side);

        Vec3 p4 =
                end.add(side);

        double yOffset = 0.03;

        poseStack.pushPose();

        Matrix4f mat =
                poseStack.last().pose();

        vertex(
                mat,
                consumer,
                p1.x - cam.x,
                p1.y - cam.y + yOffset,
                p1.z - cam.z,
                r,g,b,a,
                0,0
        );

        vertex(
                mat,
                consumer,
                p2.x - cam.x,
                p2.y - cam.y + yOffset,
                p2.z - cam.z,
                r,g,b,a,
                0,1
        );

        vertex(
                mat,
                consumer,
                p3.x - cam.x,
                p3.y - cam.y + yOffset,
                p3.z - cam.z,
                r,g,b,a,
                1,1
        );

        vertex(
                mat,
                consumer,
                p4.x - cam.x,
                p4.y - cam.y + yOffset,
                p4.z - cam.z,
                r,g,b,a,
                1,0
        );

        poseStack.popPose();
    }

    private static void vertex(
            Matrix4f mat,
            VertexConsumer consumer,
            double x,
            double y,
            double z,
            float r,
            float g,
            float b,
            float a,
            float u,
            float v
    ) {

        consumer.vertex(
                        mat,
                        (float)x,
                        (float)y,
                        (float)z
                )
                .color(r,g,b,a)
                .uv(u,v)
                .overlayCoords(
                        OverlayTexture.NO_OVERLAY
                )
                .uv2(15728880)
                .normal(0,1,0)
                .endVertex();
    }
}