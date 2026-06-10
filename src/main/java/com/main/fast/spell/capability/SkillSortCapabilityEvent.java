package com.main.fast.spell.capability;

import com.main.fast.Fast;
import com.main.fast.spell.api.SkillSortApi;
import com.main.fast.spell.network.PacketSyncSkillOrder;
import com.main.fast.spell.network.SkillNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = Fast.MODID)
public class SkillSortCapabilityEvent {

    private static final ResourceLocation ID =
            Fast.id("skill_sort");

    @SubscribeEvent
    public static void attach(
            AttachCapabilitiesEvent<Entity> event
    ) {

        if (event.getObject() instanceof Player) {

            event.addCapability(
                    ID,
                    new SkillSortCapabilityProvider()
            );

        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SkillSortApi.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        SkillSortApi.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {

        event.getOriginal()
                .reviveCaps();

        event.getOriginal()
                .getCapability(
                        SkillSortCapabilityProvider.CAPABILITY
                )
                .ifPresent(oldCap -> {

                    event.getEntity()
                            .getCapability(
                                    SkillSortCapabilityProvider.CAPABILITY
                            )
                            .ifPresent(newCap -> {

                                newCap.setSkillOrder(
                                        oldCap.getSkillOrder()
                                );
                            });
                });

        event.getOriginal().invalidateCaps();
    }
}