package com.main.fast.entity.client;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.jetbrains.annotations.NotNull;

/**
 * 自定义箭矢共用渲染器（穿透箭 / 追踪箭 / 魔法箭）
 */
public class FastArrowRenderer extends ArrowRenderer<AbstractArrow> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/projectiles/arrow.png");

    public FastArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull AbstractArrow entity) {
        return TEXTURE;
    }
}