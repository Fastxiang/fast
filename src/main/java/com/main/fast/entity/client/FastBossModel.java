package com.main.fast.entity.client;

import com.main.fast.entity.BossEntityAlex;
import com.main.fast.entity.FastBossEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.BowItem;
import net.minecraft.util.Mth;

public class FastBossModel extends HumanoidModel<FastBossEntity> {

    public FastBossModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(
            FastBossEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {

        super.setupAnim(
                entity,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch
        );

        // 普通拉弓
        if (entity.isUsingItem()
                && entity.getMainHandItem().getItem() instanceof BowItem) {

            float headX = headPitch * ((float)Math.PI / 180F);
            float headY = netHeadYaw * ((float)Math.PI / 180F);

            this.rightArm.xRot = -((float)Math.PI / 2F) + headX;
            this.rightArm.yRot = -0.4F + headY;

            this.leftArm.xRot = -((float)Math.PI / 2F) + headX;
            this.leftArm.yRot = 0.4F + headY;

            return;
        }

        // 箭雨动画
        if (entity instanceof BossEntityAlex alex
                && alex.getArrowRainAnimationTicks() > 0) {

            float progress =
                    1.0F - (alex.getArrowRainAnimationTicks() / 10.0F);

            float shootCurve =
                    Mth.sin(progress * Mth.PI);

            this.rightArm.xRot =
                    -2.3F - shootCurve * 0.4F;

            this.leftArm.xRot =
                    -2.3F - shootCurve * 0.4F;

            this.rightArm.yRot = -0.15F;
            this.leftArm.yRot = 0.15F;

            this.rightArm.zRot = 0;
            this.leftArm.zRot = 0;

            this.body.xRot = -0.05F;
        }
    }
}
