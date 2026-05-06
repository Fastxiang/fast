package com.main.fast;

import com.main.fast.entity.FastBossEntity;
import com.main.fast.entity.FastSwordEntity;
import com.main.fast.registry.*;
import com.main.fast.shop.key.ShopKeyHandler;
import com.main.fast.shop.network.ShopNetwork;
import com.main.fast.skill.network.SkillIndicatorPacket;
import com.mojang.logging.LogUtils;
import com.main.fast.entity.client.FastSwordEntityRenderer;
import com.main.fast.entity.client.FastBossRenderer;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import com.main.fast.event.PlayerEventsHandler;

import org.jetbrains.annotations.NotNull;

@Mod(Fast.MODID)
public class Fast {
    public static final String MODID = "fast";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Fast(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
    
        FastItems.ITEMS.register(bus);
        
        FastEntities.ENTITIES.register(bus);
        
        FastAttributes.ATTRIBUTES.register(bus);
        
        FastBiomes.BIOMES.register(bus);

        FastCreativeTabs.TABS.register(bus);
        
        PlayerEventsHandler.register();

        ShopNetwork.init();
        SkillIndicatorPacket.init();
        
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ShopKeyHandler.OPEN_SHOP_KEY);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
            
                EntityRenderers.register(
                    FastEntities.FAST_SWORD.get(),
                    FastSwordEntityRenderer::new
                );
                
                EntityRenderers.register(
                    FastEntities.FAST_BOSS.get(),
                    FastBossRenderer::new
                );
                
                EntityRenderers.register(
                    FastEntities.BOSS_ALEX.get(),
                    FastBossRenderer::new
                );
            });
        }
    }

    @Mod.EventBusSubscriber(modid = Fast.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEntityRegisterEvents {
        @SubscribeEvent
        public static void onEntityAttributeCreate(EntityAttributeCreationEvent event) {
            event.put(FastEntities.FAST_SWORD.get(), FastSwordEntity.getDefaultAttribute());
            event.put(FastEntities.FAST_BOSS.get(), FastBossEntity.createAttributes().build());
            event.put(FastEntities.BOSS_ALEX.get(), FastBossEntity.createAttributes().build());
        }
    }

    public static ResourceLocation id(@NotNull String path) {
        return ResourceLocation.fromNamespaceAndPath(Fast.MODID, path);
    }
}
