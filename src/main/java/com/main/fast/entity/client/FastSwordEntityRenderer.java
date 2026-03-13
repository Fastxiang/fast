package com.main.fast.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.main.fast.entity.FastSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.NotNull;

public class FastSwordEntityRenderer extends EntityRenderer<FastSwordEntity> {

    public FastSwordEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(FastSwordEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(@NotNull FastSwordEntity swordEntity, float entityYaw, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        swordEntity.setRenderPose(poseStack, entityYaw, partialTick);
        Minecraft.getInstance().gameRenderer.itemInHandRenderer.renderItem(
                swordEntity,
                swordEntity.getItemStack(),
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false,
                poseStack,
                buffer,
                packedLight
        );
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull FastSwordEntity entity) {
        return ResourceLocation.parse("");
    }
}
