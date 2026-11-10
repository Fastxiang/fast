package com.main.fast.entity.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 昔日勇者的幻影 - 皮肤几何。
 *
 * <p><b>为什么需要这个类（关键）：</b>{@code HumanoidModel} 继承 {@code AgeableModel}，
 * 它的 {@code renderToBuffer} 只画 {@code headParts()} + {@code bodyParts()} 两组部件，
 * 而原版玩家网格里 {@code jacket / right_sleeve / left_sleeve / right_pants / left_pants}
 * 是挂在 <b>root 上</b>、又不在那两组里 —— 也就是说拿 {@code ModelLayers.PLAYER} 直接当人形实体用，
 * 皮肤的<b>整个第二层（外套、袖子、裤腿）根本不会被渲染</b>，只剩 {@code hat}（头发）能显示。</p>
 *
 * <p>所以这里做两件事：</p>
 * <ol>
 *   <li>把这五个部件<b>挂到对应的身体部件下面</b>（外套挂 body、袖子挂手臂、裤腿挂腿），
 *       这样它们会跟着四肢一起动，也会被递归渲染出来；</li>
 *   <li>顺便把第二层加厚（原版只是 0.25~0.5 像素的薄壳），让头发 / 外套有体积感。</li>
 * </ol>
 *
 * <p>{@code hat} 仍然留在 root 上 —— {@code HumanoidModel} 用 {@code root.getChild("hat")} 取它，
 * 而且每帧 {@code copyFrom(head)} 跟随头部。基础层（头/身/四肢）完全沿用原版定义，
 * 动作、玩家动画、举盾 / 拉弓姿势都不受影响。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PhantomHeroLayers {

    /** 头发（帽子层）向外膨胀的厚度：原版 0.5 */
    private static final float HAT_THICKNESS = 0.9F;

    /** 外套 / 袖子 / 裤腿：原版 0.25 */
    private static final float CLOTH_THICKNESS = 0.5F;

    private PhantomHeroLayers() {
    }

    /**
     * 烘焙一套「能画出第二层 + 第二层加厚」的玩家模型。
     *
     * @return 可以直接丢给 {@link PhantomHeroModel} 的根部件
     */
    public static ModelPart bake() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot();

        CubeDeformation hat = new CubeDeformation(HAT_THICKNESS);
        CubeDeformation cloth = new CubeDeformation(CLOTH_THICKNESS);

        // ---- 头发：留在 root（HumanoidModel 只认 root 下的 hat），和头同枢轴 ----
        root.addOrReplaceChild(
                "hat",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, hat),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // ---- 外套：挂到 body 下（body 枢轴 0,0,0，所以子部件用 ZERO） ----
        root.getChild("body").addOrReplaceChild(
                "jacket",
                CubeListBuilder.create()
                        .texOffs(16, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, cloth),
                PartPose.ZERO
        );

        // ---- 袖子：挂到手臂下（箱子坐标就是手臂本身的箱子，ZERO 对齐） ----
        root.getChild("right_arm").addOrReplaceChild(
                "right_sleeve",
                CubeListBuilder.create()
                        .texOffs(40, 32)
                        .addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, cloth),
                PartPose.ZERO
        );
        root.getChild("left_arm").addOrReplaceChild(
                "left_sleeve",
                CubeListBuilder.create()
                        .texOffs(48, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, cloth),
                PartPose.ZERO
        );

        // ---- 裤腿：挂到腿下 ----
        root.getChild("right_leg").addOrReplaceChild(
                "right_pants",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, cloth),
                PartPose.ZERO
        );
        root.getChild("left_leg").addOrReplaceChild(
                "left_pants",
                CubeListBuilder.create()
                        .texOffs(0, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, cloth),
                PartPose.ZERO
        );

        // 原版玩家网格里还有 ear / cloak 两个占位方块（枢轴在脚底，平时看不见），
        // 直接隐藏，省得以后有人挪坐标时它们突然冒出来。
        ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        baked.getChild("ear").skipDraw = true;
        baked.getChild("cloak").skipDraw = true;
        return baked;
    }
}
