package com.main.fast.entity.client;

import com.main.fast.entity.BossPhantomHero;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 昔日勇者的幻影 - 渲染器。
 *
 * 用原版玩家模型的网格 + 自定义皮肤；
 * 第二层（头发/外套）由 {@link PhantomHeroLayers} 加厚，
 * 动画在 {@link PhantomHeroModel} 里处理。
 */
@OnlyIn(Dist.CLIENT)
public class PhantomHeroRenderer
        extends HumanoidMobRenderer<BossPhantomHero, PhantomHeroModel> {

    public PhantomHeroRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new PhantomHeroModel(PhantomHeroLayers.bake()),
                0.5F
        );
    }

    @Override
    public ResourceLocation getTextureLocation(BossPhantomHero entity) {
        return entity.getSkinTexture();
    }
}
