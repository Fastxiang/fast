package com.main.fast.entity.client;

import com.main.fast.entity.BossPhantomHero;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 昔日勇者的幻影 - 客户端模型。
 *
 * 作用只有一个：把「player 动画」套到人形模型上。
 *
 * 为什么需要自己写：
 * playerAnimator（铁魔法用的动画库）的自动播放链路
 * PlayerAnimationAccess.getPlayerAssociatedData(player) 只接受 AbstractClientPlayer，
 * 非玩家实体走不通。
 *
 * 但它的底层渲染器是公开的：
 *   dev.kosmx.playerAnim.impl.animation.AnimationApplier
 *     - AnimationApplier(IAnimation)      构造
 *     - tick()                            推进一 tick
 *     - setTickDelta(float)               设置插值
 *     - updatePart(String, ModelPart)     把这一帧的旋转/位移写进模型部件
 *
 * 所以这里做三件事：
 *   1. 从 Boss 同步下来的动画资源位置，用 PlayerAnimationRegistry 取到 KeyframeAnimation
 *   2. 用 KeyframeAnimationPlayer 播放
 *   3. 每个渲染帧用 AnimationApplier 把结果写到 head / body / 双臂 / 双腿
 *
 * 部件名必须用 playerAnimator 的规范名：
 * head、torso、rightArm、leftArm、rightLeg、leftLeg
 * （json 里的 right_arm / left_leg 会在加载时归一化成驼峰）
 *
 * 【关键：每一帧都要还原部件位移】
 * AnimationApplier.updatePart 会同时写 ModelPart 的
 *   旋转（xRot / yRot / zRot）和位移（x / y / z）。
 * 而 HumanoidModel.setupAnim 只会重置旋转，以及左右手的 x / z，
 * 不会重置 y，也不会重置 head / body / 双腿的位移。
 * 所以动画一旦给某个部件写过位移，这个位移就会永久留在模型上
 * —— 表现就是「头和身体分离」。
 * 解决办法：构造时把所有部件的静止位移记下来，每帧先还原再叠加动画。
 */
@OnlyIn(Dist.CLIENT)
public class PhantomHeroModel extends HumanoidModel<BossPhantomHero> {

    private AnimationApplier applier;

    // 上一次看到的动画序号，用来判断「是不是要播新动画了」
    private int lastSeq = -1;

    // 当前动画还剩下多少 tick 要播。
    // <= 0 表示不限制（靠 KeyframeAnimationPlayer.isActive() 自己结束）。
    // 循环动画（continuous_thrust_one_handed / long_cast）的 isActive() 恒为 true，
    // 不限制的话就会永远播下去 —— 也就是「动画残留」。
    private int animTicksLeft = 0;

    // 上一次看到的实体 tick，用来按游戏 tick 推进动画
    private int lastEntityTick = -1;

    // 当前 applier 属于哪个实体（模型实例在所有 Boss 之间共享，
    // 多个幻影勇者同时存在时必须各记各的，否则动画状态会串）
    private BossPhantomHero currentOwner;

    // 各部件的静止位移（x / y / z），动画结束后要还原到这里
    private final float[] restHead = new float[3];
    private final float[] restBody = new float[3];
    private final float[] restRightArm = new float[3];
    private final float[] restLeftArm = new float[3];
    private final float[] restRightLeg = new float[3];
    private final float[] restLeftLeg = new float[3];

    public PhantomHeroModel(ModelPart root) {
        super(root);

        capture(restHead, this.head);
        capture(restBody, this.body);
        capture(restRightArm, this.rightArm);
        capture(restLeftArm, this.leftArm);
        capture(restRightLeg, this.rightLeg);
        capture(restLeftLeg, this.leftLeg);
    }

    private static void capture(float[] target, ModelPart part) {
        target[0] = part.x;
        target[1] = part.y;
        target[2] = part.z;
    }

    private static void restore(float[] source, ModelPart part) {
        part.x = source[0];
        part.y = source[1];
        part.z = source[2];
    }

    /**
     * 把六个部件全部还原到静止位移。
     *
     * 必须在 super.setupAnim 之后、叠加动画之前调用：
     * setupAnim 只重置旋转，位移要靠这里补。
     */
    private void resetOffsets() {
        restore(restHead, this.head);
        restore(restBody, this.body);
        restore(restRightArm, this.rightArm);
        restore(restLeftArm, this.leftArm);
        restore(restRightLeg, this.rightLeg);
        restore(restLeftLeg, this.leftLeg);
    }

    @Override
    public void setupAnim(
            BossPhantomHero entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {

        /*
         * 举盾姿势。
         *
         * 原版没有任何地方给非玩家实体设置 HumanoidModel 的 armPose
         * （只有玩家渲染器会设），所以这里自己设：
         * 主手是右手，盾在副手 = 左手，因此左臂摆成 BLOCK。
         *
         * 必须在 super.setupAnim 之前设，poseLeftArm 是在 setupAnim 里读的。
         */
        this.leftArmPose = entity.isBlocking()
                ? HumanoidModel.ArmPose.BLOCK
                : HumanoidModel.ArmPose.EMPTY;

        super.setupAnim(
                entity,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch
        );

        // 动画写进部件的位移不会被 setupAnim 清掉，必须手动还原，
        // 否则播完一次动画后头/腿就永远歪在别处
        resetOffsets();

        // LivingEntityRenderer 传进来的 ageInTicks = entity.tickCount + partialTick
        float partialTick = Mth.clamp(
                ageInTicks - (float) entity.tickCount,
                0.0F,
                1.0F
        );

        // ---------- 是否要播放新动画 ----------

        int seq = entity.getSpellAnimSeq();

        // 模型实例是共享的，换实体了要当成全新状态处理
        if (entity != currentOwner) {
            currentOwner = entity;
            lastSeq = -1;
            lastEntityTick = -1;
            animTicksLeft = 0;
            applier = null;
        }

        if (seq != lastSeq) {
            lastSeq = seq;
            animTicksLeft = entity.getSpellAnimDuration();
            startAnimation(entity.getSpellAnimKey());
        }

        // ---------- 按游戏 tick 推进 ----------

        int entityTick = entity.tickCount;

        int delta;

        if (lastEntityTick < 0) {
            delta = 0;
        } else {
            delta = entityTick - lastEntityTick;
        }

        lastEntityTick = entityTick;

        if (applier == null) {

            /*
             * 没有法术动画时，如果正在拉弓，就摆出和 Alex 一模一样的拉弓姿势。
             *
             * 为什么必须手写：
             * 原版 HumanoidModel 的 BOW_AND_ARROW 姿势要靠
             * LivingEntity#getUseItem() 拿到弓，而这个字段对非玩家实体
             * 是服务端独有的；这里直接按 Alex 的做法判断主手物品即可
             * （主手物品是我们自己用 DATA_WEAPON 同步下来的，客户端拿得到）。
             */
            if (entity.isUsingItem()
                    && entity.getMainHandItem().getItem() instanceof BowItem) {

                float headX = headPitch * ((float) Math.PI / 180F);
                float headY = netHeadYaw * ((float) Math.PI / 180F);

                // 右手持弓前伸
                this.rightArm.xRot = -((float) Math.PI / 2F) + headX;
                this.rightArm.yRot = -0.4F + headY;

                // 左手拉弦
                this.leftArm.xRot = -((float) Math.PI / 2F) + headX;
                this.leftArm.yRot = 0.4F + headY;

                this.hat.copyFrom(this.head);

                return;
            }

            // 没有动画，保持原版姿势即可
            this.hat.copyFrom(this.head);
            return;
        }

        if (delta > 0) {

            // 掉帧最多补 5 tick，避免卡顿后动画瞬间跳完
            int steps = Math.min(delta, 5);

            // 有时长限制时还要再被剩余 tick 截断，防止循环动画播过头
            if (animTicksLeft > 0) {
                steps = Math.min(steps, animTicksLeft);
            }

            for (int i = 0; i < steps; i++) {
                applier.tick();
            }

            if (animTicksLeft > 0) {

                animTicksLeft -= steps;

                // 播够了：直接停掉并回到原版姿势。
                // 对循环动画来说这是唯一的结束方式。
                if (animTicksLeft <= 0) {
                    applier = null;
                    this.hat.copyFrom(this.head);
                    return;
                }
            }
        }

        if (!applier.isActive()) {
            applier = null;
            this.hat.copyFrom(this.head);
            return;
        }

        // ---------- 写入模型部件 ----------

        applier.setTickDelta(partialTick);

        applier.updatePart("head", this.head);
        applier.updatePart("torso", this.body);
        applier.updatePart("rightArm", this.rightArm);
        applier.updatePart("leftArm", this.leftArm);
        applier.updatePart("rightLeg", this.rightLeg);
        applier.updatePart("leftLeg", this.leftLeg);

        // HumanoidModel.setupAnim 里只做了一次 hat.copyFrom(head)，
        // 上面改过 head 之后必须再同步一次，否则第二层头皮会留在原地
        this.hat.copyFrom(this.head);
    }

    /**
     * 开始播放一个动画。
     *
     * @param key 动画资源位置的字符串形式，空字符串表示停止
     */
    private void startAnimation(String key) {

        applier = null;

        if (key == null || key.isEmpty()) {
            return;
        }

        ResourceLocation location = ResourceLocation.tryParse(key);

        if (location == null) {
            return;
        }

        KeyframeAnimation animation =
                PlayerAnimationRegistry.getAnimation(location);

        if (animation == null) {
            return;
        }

        IAnimation player = new KeyframeAnimationPlayer(animation, 0);

        applier = new AnimationApplier(player);
    }
}
