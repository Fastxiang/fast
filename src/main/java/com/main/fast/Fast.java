package com.main.fast;

import com.main.fast.entity.FastBossEntity;
import com.main.fast.registry.*;
import com.main.fast.shop.network.ShopNetwork;
import com.main.fast.skill.network.SkillIndicatorPacket;
import com.main.fast.skill.network.SkillLinePacket;
import com.main.fast.spell.network.SkillNetwork;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.main.fast.event.PlayerEventsHandler;


@Mod(Fast.MODID)
public class Fast {

    public static final String MODID = "fast";

    private static final Logger LOGGER = LogUtils.getLogger();


    public Fast(FMLJavaModLoadingContext context) {

        IEventBus bus = context.getModEventBus();


        FastItems.ITEMS.register(bus);

        FastEntities.ENTITIES.register(bus);

        FastAttributes.ATTRIBUTES.register(bus);

        FastCreativeTabs.TABS.register(bus);


        PlayerEventsHandler.register();

        ShopNetwork.init();

        SkillNetwork.init();

        SkillIndicatorPacket.init();

        SkillLinePacket.init();

        MinecraftForge.EVENT_BUS.register(this);
    }



    /**
     * 客户端事件
     * 重要：
     * 这个类不要放任何客户端 import 到主类
     */
    @Mod.EventBusSubscriber(
            modid = MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD,
            value = net.minecraftforge.api.distmarker.Dist.CLIENT
    )
    public static class ClientModEvents {


        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(com.main.fast.shop.key.ShopKeyHandler.OPEN_SHOP_KEY);
        }



        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {


            event.enqueueWork(() -> {


                net.minecraft.client.renderer.entity.EntityRenderers.register(
                        FastEntities.FAST_SWORD.get(),
                        com.main.fast.entity.client.FastSwordEntityRenderer::new
                );


                net.minecraft.client.renderer.entity.EntityRenderers.register(
                        FastEntities.FAST_BOSS.get(),
                        com.main.fast.entity.client.FastBossRenderer::new
                );


                net.minecraft.client.renderer.entity.EntityRenderers.register(
                        FastEntities.BOSS_ALEX.get(),
                        com.main.fast.entity.client.FastBossRenderer::new
                );


            });

        }

    }




    @Mod.EventBusSubscriber(
            modid = MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD
    )
    public static class ModEntityRegisterEvents {


        @SubscribeEvent
        public static void onEntityAttributeCreate(
                EntityAttributeCreationEvent event
        ) {


            event.put(
                    FastEntities.FAST_SWORD.get(),
                    com.main.fast.entity.FastSwordEntity.getDefaultAttribute()
            );


            event.put(
                    FastEntities.FAST_BOSS.get(),
                    FastBossEntity.createAttributes().build()
            );


            event.put(
                    FastEntities.BOSS_ALEX.get(),
                    FastBossEntity.createAttributes().build()
            );

        }

    }




    public static ResourceLocation id(
            @NotNull String path
    ) {

        return ResourceLocation.fromNamespaceAndPath(
                MODID,
                path
        );

    }

}