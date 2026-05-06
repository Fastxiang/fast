package com.main.fast.skill.client;

import com.main.fast.Fast;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
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

@Mod.EventBusSubscriber(modid = Fast.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SkillIndicatorRenderer {

    private static final List<ActiveIndicator> indicators = new LinkedList<>();
    private static final ResourceLocation WHITE_TEXTURE = Fast.id("textures/misc/white.png");

    private static class ActiveIndicator {
        Vec3 center;    // 世界坐标（中心点）
        int size;       // 外边长
        int innerSize;  // 内边长，0=实心
        int maxDuration;
        int tick;
        int color;

        ActiveIndicator(Vec3 center, int size, int innerSize, int duration, int color) {
            this.center = center;
            this.size = size;
            this.innerSize = innerSize;
            this.maxDuration = duration;
            this.tick = duration;
            this.color = color;
        }

        boolean isExpired() { return tick <= 0; }
        void decrement() { if (tick > 0) tick--; }
    }

    public static void addIndicator(Vec3 center, int size, int innerSize, int duration, int color) {
        for (ActiveIndicator ind : indicators) {
            if (ind.center.equals(center) && ind.size == size && ind.innerSize == innerSize && ind.maxDuration == duration)
                return;
        }
        indicators.add(new ActiveIndicator(center, size, innerSize, duration, color));
    }

    private static void tickIndicators() {
        if (Minecraft.getInstance().level == null) {
            indicators.clear();
            return;
        }
        Iterator<ActiveIndicator> it = indicators.iterator();
        while (it.hasNext()) {
            ActiveIndicator ind = it.next();
            ind.decrement();
            if (ind.isExpired()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickIndicators();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (indicators.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();

        RenderType renderType = RenderType.entityTranslucent(WHITE_TEXTURE);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        for (ActiveIndicator ind : indicators) {
            // center 已经是世界坐标中心点，不再额外添加 0.5
            double x = ind.center.x - cam.x;
            double y = ind.center.y + 0.02 - cam.y;
            double z = ind.center.z - cam.z;
            float half = ind.size / 2.0f;
            float innerHalf = ind.innerSize / 2.0f;

            int argb = ind.color;
            float a = ((argb >> 24) & 0xFF) / 255f;
            float timeFade = ind.tick / (float) ind.maxDuration;
            a *= timeFade;
            if (a <= 0.01f) continue;

            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            Matrix4f mat = poseStack.last().pose();

            if (ind.innerSize <= 0) {
                drawQuad(mat, consumer, half, r / 255f, g / 255f, b / 255f, a);
            } else {
                drawSquareRing(mat, consumer, half, innerHalf, r / 255f, g / 255f, b / 255f, a);
            }

            poseStack.popPose();
        }
        bufferSource.endBatch(renderType);
    }

    private static void drawQuad(Matrix4f matrix, VertexConsumer consumer,
                                 float half, float r, float g, float b, float a) {
        consumer.vertex(matrix, -half, 0, -half).color(r, g, b, a).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, -half, 0,  half).color(r, g, b, a).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix,  half, 0,  half).color(r, g, b, a).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix,  half, 0, -half).color(r, g, b, a).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
    }

    private static void drawSquareRing(Matrix4f matrix, VertexConsumer consumer,
                                       float outerHalf, float innerHalf,
                                       float r, float g, float b, float a) {
        // 上边
        drawRect(matrix, consumer, -outerHalf, -outerHalf, outerHalf, -innerHalf, r, g, b, a);
        // 下边
        drawRect(matrix, consumer, -outerHalf, innerHalf, outerHalf, outerHalf, r, g, b, a);
        // 左边
        drawRect(matrix, consumer, -outerHalf, -innerHalf, -innerHalf, innerHalf, r, g, b, a);
        // 右边
        drawRect(matrix, consumer, innerHalf, -innerHalf, outerHalf, innerHalf, r, g, b, a);
    }

    private static void drawRect(Matrix4f matrix, VertexConsumer consumer,
                                 float x1, float z1, float x2, float z2,
                                 float r, float g, float b, float a) {
        consumer.vertex(matrix, x1, 0, z1).color(r, g, b, a).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x1, 0, z2).color(r, g, b, a).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x2, 0, z2).color(r, g, b, a).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x2, 0, z1).color(r, g, b, a).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0, 1, 0).endVertex();
    }
}