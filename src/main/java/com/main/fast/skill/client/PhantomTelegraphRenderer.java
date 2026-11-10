package com.main.fast.skill.client;

import com.main.fast.Fast;
import com.main.fast.skill.network.PhantomTelegraphPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 技能范围提示渲染器。
 *
 * 形状参数由服务端按技能真实判定下发，两种形状：
 *
 *   扇形 SECTOR —— 物理三连击的判定区域（水平距离 <= range && 水平夹角 <= 60 度），
 *       range 取施法者的 ENTITY_REACH 属性，红圈用的就是同一组参数。
 *
 *   直线 BEAM   —— 弹射物飞行路径（魔法三连击）
 *
 * 提示绑定施法者实体，每帧刷新位置和朝向，所以是「跟身的红圈」而不是死板的固定圈。
 */
@Mod.EventBusSubscriber(modid = Fast.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PhantomTelegraphRenderer {

    private static final List<ActiveTelegraph> TELEGRAPHS = new ArrayList<>();

    private static final ResourceLocation WHITE_TEXTURE = Fast.id("textures/misc/white.png");

    // 扇形分段数，越大越圆滑
    private static final int SECTOR_SEGMENTS = 40;

    // 描边宽度（格）
    private static final float OUTLINE_WIDTH = 0.07F;

    private static class ActiveTelegraph {

        final int shape;
        final int ownerId;
        final Vec3 origin;
        final float yaw;
        final float radius;
        final float halfAngle;
        final float halfWidth;

        /** 保持高亮的 tick 数 */
        final int holdDuration;

        /** 从出现到彻底消失的总 tick 数（>= holdDuration） */
        final int maxDuration;

        final int color;

        int tick;

        ActiveTelegraph(
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
            this.holdDuration = Math.max(0, duration);
            this.maxDuration = Math.max(this.holdDuration, fade);
            this.color = color;
            this.tick = this.maxDuration;
        }

        boolean isExpired() {
            return tick <= 0;
        }

        /**
         * 当前透明度系数（0~1）。
         *
         *   - 高亮期（剩余 tick > holdDuration 之外的部分）：满亮度
         *   - 渐隐期：从 1 线性降到 0
         *
         * 这样「蓄力多久、提示就亮多久」可以被服务端精确控制，
         * 不会再出现「提示早就淡没了、技能还在蓄力」。
         */
        float alphaFactor() {

            if (maxDuration <= 0 || tick <= 0) {
                return 0.0F;
            }

            if (tick > holdDuration) {
                return 1.0F;
            }

            if (holdDuration <= 0) {
                // 没给高亮期：整段都是渐隐
                return tick / (float) maxDuration;
            }

            return tick / (float) holdDuration;
        }
    }

    public static void addTelegraph(
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

        /*
         * 同一个施法者同时只允许存在一个提示。
         *
         * 三连击是「预警圈 -> 段1命中闪光 -> 段2命中闪光 -> 段3命中闪光」，
         * 如果每次都往列表里追加，旧的没过期就会被新的盖在下面，
         * 看起来就是「上一个动作的提示残留到了下一个动作」。
         * 这里直接顶掉旧的，同时也能处理网络包乱序 / 延迟到达的情况。
         */
        if (ownerId >= 0) {
            TELEGRAPHS.removeIf(t -> t.ownerId == ownerId);
        }

        // 清除指令：抹完之后不用再加新的
        if (shape == PhantomTelegraphPacket.SHAPE_CLEAR) {
            return;
        }

        TELEGRAPHS.add(new ActiveTelegraph(
                shape,
                ownerId,
                origin,
                yaw,
                radius,
                halfAngle,
                halfWidth,
                duration,
                fade,
                color
        ));
    }

    /**
     * 单独清除某个施法者的提示（本地用）。
     */
    public static void clearFor(int ownerId) {
        if (ownerId >= 0) {
            TELEGRAPHS.removeIf(t -> t.ownerId == ownerId);
        }
    }

    private static void tickTelegraphs() {
        if (Minecraft.getInstance().level == null) {
            TELEGRAPHS.clear();
            return;
        }

        Iterator<ActiveTelegraph> it = TELEGRAPHS.iterator();

        while (it.hasNext()) {
            ActiveTelegraph t = it.next();
            t.tick--;
            if (t.isExpired()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickTelegraphs();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        if (TELEGRAPHS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        Level level = mc.level;

        if (level == null) {
            return;
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        PoseStack poseStack = event.getPoseStack();

        RenderType renderType = RenderType.entityTranslucent(WHITE_TEXTURE);

        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        Matrix4f mat = poseStack.last().pose();

        Iterator<ActiveTelegraph> it = TELEGRAPHS.iterator();

        while (it.hasNext()) {

            ActiveTelegraph t = it.next();

            int argb = t.color;

            float a = ((argb >> 24) & 0xFF) / 255F;

            /*
             * 统一渐隐。
             *
             * 透明度由 ActiveTelegraph#alphaFactor 给出：
             *   - 高亮期满亮度（保证「提示和技能同步」，中途不会突然变淡）
             *   - 收招期线性淡出（避免整块突然消失）
             */
            a *= t.alphaFactor();

            if (a <= 0.01F) {
                continue;
            }

            float r = ((argb >> 16) & 0xFF) / 255F;
            float g = ((argb >> 8) & 0xFF) / 255F;
            float b = (argb & 0xFF) / 255F;

            // ---------- 当前帧的原点和朝向 ----------

            Vec3 origin = t.origin;
            double dirX;
            double dirZ;

            Entity owner = t.ownerId >= 0 ? level.getEntity(t.ownerId) : null;

            // 施法者已经不在世界上（死了 / 卸载了），提示也没有继续存在的意义
            if (t.ownerId >= 0 && owner == null) {
                it.remove();
                continue;
            }

            if (owner != null) {

                origin = owner.position();

                Vec3 look = owner.getLookAngle();

                double len = Math.sqrt(look.x * look.x + look.z * look.z);

                if (len > 0.0001D) {
                    dirX = look.x / len;
                    dirZ = look.z / len;
                } else {
                    dirX = dirXFromYaw(t.yaw);
                    dirZ = dirZFromYaw(t.yaw);
                }

            } else {
                dirX = dirXFromYaw(t.yaw);
                dirZ = dirZFromYaw(t.yaw);
            }

            // ---------- 构造多边形（世界 XZ） ----------

            List<double[]> poly = buildPolygon(t, origin.x, origin.z, dirX, dirZ);

            if (poly == null || poly.size() < 3) {
                continue;
            }

            int n = poly.size();

            double[] xs = new double[n];
            double[] zs = new double[n];
            float[] ys = new float[n];

            for (int i = 0; i < n; i++) {
                xs[i] = poly.get(i)[0];
                zs[i] = poly.get(i)[1];
                // 贴地：按地形采样每个顶点的高度
                ys[i] = sampleGroundY(level, xs[i], zs[i], origin.y);
            }

            // ---------- 填充 + 描边 ----------

            fillPolygon(mat, consumer, xs, zs, ys, cam, r, g, b, a);
            outlinePolygon(mat, consumer, xs, zs, level, origin.y, cam, r, g, b, Math.min(a * 2.2F, 1.0F));
        }

        bufferSource.endBatch(renderType);
    }

    /**
     * 按形状构造水平多边形，点序为 [x, z]。
     */
    private static List<double[]> buildPolygon(
            ActiveTelegraph t,
            double ox,
            double oz,
            double dirX,
            double dirZ
    ) {

        if (t.shape == PhantomTelegraphPacket.SHAPE_BEAM) {

            List<double[]> poly = new ArrayList<>();

            // 左 / 右垂直于方向
            double px = -dirZ * t.halfWidth;
            double pz = dirX * t.halfWidth;

            double fx = ox + dirX * t.radius;
            double fz = oz + dirZ * t.radius;

            poly.add(new double[]{ox + px, oz + pz});
            poly.add(new double[]{fx + px, fz + pz});
            poly.add(new double[]{fx - px, fz - pz});
            poly.add(new double[]{ox - px, oz - pz});

            return poly;
        }

        // 扇形：半径 = 施法者的 ENTITY_REACH，半角 60 度，
        // 中心 = 施法者脚底（法术判定用的是 caster.position() 的水平分量），
        // 和 TripleStrikeSpell 里的判定区域一模一样
        List<double[]> poly = new ArrayList<>();

        poly.add(new double[]{ox, oz});

        double half = Math.toRadians(t.halfAngle);

        for (int i = 0; i <= SECTOR_SEGMENTS; i++) {

            double f = (double) i / SECTOR_SEGMENTS;
            double angle = -half + 2.0D * half * f;

            double vx = rotateX(dirX, dirZ, angle);
            double vz = rotateZ(dirX, dirZ, angle);

            poly.add(new double[]{ox + vx * t.radius, oz + vz * t.radius});
        }

        return poly;
    }

    /**
     * 绕 Y 轴旋转一个 XZ 向量（弧度）。
     */
    private static double rotateX(double x, double z, double angle) {
        return x * Math.cos(angle) - z * Math.sin(angle);
    }

    private static double rotateZ(double x, double z, double angle) {
        return x * Math.sin(angle) + z * Math.cos(angle);
    }

    private static double dirXFromYaw(float yaw) {
        return -Mth.sin(yaw * Mth.DEG_TO_RAD);
    }

    private static double dirZFromYaw(float yaw) {
        return Mth.cos(yaw * Mth.DEG_TO_RAD);
    }

    /**
     * 采样某个世界 XZ 处的地面高度，让红圈能贴着地形起伏。
     * 采样不到（区块未加载 / 极限情况）就退回 Boss 脚底高度。
     */
    private static float sampleGroundY(Level level, double x, double z, double baseY) {

        BlockPos pos = new BlockPos(Mth.floor(x), 64, Mth.floor(z));

        if (!level.hasChunkAt(pos)) {
            return (float) baseY + 0.03F;
        }

        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());

        float y = h + 0.03F;

        return Mth.clamp(y, (float) baseY - 2.0F, (float) baseY + 2.0F);
    }

    /**
     * 三角扇填充一个 XZ 平面多边形，顶点高度已按地形采样。
     */
    private static void fillPolygon(
            Matrix4f mat,
            VertexConsumer consumer,
            double[] xs,
            double[] zs,
            float[] ys,
            Vec3 cam,
            float r,
            float g,
            float b,
            float a
    ) {

        int n = xs.length;

        if (n < 3) {
            return;
        }

        // 统一绕序，避免背面剔除导致整块消失
        boolean reverse = signedArea(xs, zs) > 0;

        for (int i = 1; i < n - 1; i++) {

            int i1 = reverse ? (n - i) : i;
            int i2 = reverse ? (n - i - 1) : (i + 1);

            vertex(mat, consumer, xs[0], ys[0], zs[0], cam, r, g, b, a);
            vertex(mat, consumer, xs[i1], ys[i1], zs[i1], cam, r, g, b, a);
            vertex(mat, consumer, xs[i2], ys[i2], zs[i2], cam, r, g, b, a);
        }
    }

    /**
     * 沿多边形每条边画一圈描边，让提示更醒目。
     */
    private static void outlinePolygon(
            Matrix4f mat,
            VertexConsumer consumer,
            double[] xs,
            double[] zs,
            Level level,
            double baseY,
            Vec3 cam,
            float r,
            float g,
            float b,
            float a
    ) {

        int n = xs.length;

        double[] qx = new double[4];
        double[] qz = new double[4];
        float[] qy = new float[4];

        for (int i = 0; i < n; i++) {

            int j = (i + 1) % n;

            double dx = xs[j] - xs[i];
            double dz = zs[j] - zs[i];

            double len = Math.sqrt(dx * dx + dz * dz);

            if (len < 0.0001D) {
                continue;
            }

            double px = (-dz / len) * OUTLINE_WIDTH;
            double pz = (dx / len) * OUTLINE_WIDTH;

            qx[0] = xs[i] + px;
            qz[0] = zs[i] + pz;

            qx[1] = xs[j] + px;
            qz[1] = zs[j] + pz;

            qx[2] = xs[j] - px;
            qz[2] = zs[j] - pz;

            qx[3] = xs[i] - px;
            qz[3] = zs[i] - pz;

            for (int k = 0; k < 4; k++) {
                qy[k] = sampleGroundY(level, qx[k], qz[k], baseY);
            }

            fillPolygon(mat, consumer, qx, qz, qy, cam, r, g, b, a);
        }
    }

    private static double signedArea(double[] xs, double[] zs) {

        double area = 0.0D;

        for (int i = 0; i < xs.length; i++) {

            int j = (i + 1) % xs.length;

            area += xs[i] * zs[j] - xs[j] * zs[i];
        }

        return area;
    }

    private static void vertex(
            Matrix4f mat,
            VertexConsumer consumer,
            double x,
            float y,
            double z,
            Vec3 cam,
            float r,
            float g,
            float b,
            float a
    ) {
        consumer
                .vertex(mat, (float) (x - cam.x), y - (float) cam.y, (float) (z - cam.z))
                .color(r, g, b, a)
                .uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
                .normal(0, 1, 0)
                .endVertex();
    }
}
