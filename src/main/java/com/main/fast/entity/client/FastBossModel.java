package com.main.fast.entity.client;

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
    public void setupAnim(FastBossEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (entity.isUsingItem() && entity.getMainHandItem().getItem() instanceof BowItem) {
            // 拉弓动画手动旋转手臂
            float headX = headPitch * ((float)Math.PI / 180F);
            float headY = netHeadYaw * ((float)Math.PI / 180F);

            this.rightArm.xRot = -((float)Math.PI / 2F) + headX;
            this.rightArm.yRot = -0.4F + headY;
            this.rightArm.zRot = 0.0F;

            this.leftArm.xRot = -((float)Math.PI / 2F) + headX;
            this.leftArm.yRot = 0.4F + headY;
            this.leftArm.zRot = 0.0F;
        }
    }
}
