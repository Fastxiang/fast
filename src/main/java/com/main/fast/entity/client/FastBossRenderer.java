package com.main.fast.entity.client;

import com.main.fast.entity.FastBossEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FastBossRenderer extends HumanoidMobRenderer<FastBossEntity, FastBossModel> {

    public FastBossRenderer(EntityRendererProvider.Context context) {
        super(context, new FastBossModel(context.bakeLayer(ModelLayers.PLAYER)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(FastBossEntity entity) {
        return entity.getSkinTexture();
    }
}
