package com.main.fast.shop.money;

import com.main.fast.Fast;
import com.main.fast.shop.api.FastShop;
import com.main.fast.shop.network.PacketSyncMoney;
import com.main.fast.shop.network.ShopNetwork;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 玩家挂载 Capability
 */
@Mod.EventBusSubscriber(modid = "fast")
public class MoneyProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IMoney> MONEY =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation ID =
            Fast.id("money");

    private final MoneyCapability backend = new MoneyCapability();

    private final LazyOptional<IMoney> optional =
            LazyOptional.of(() -> backend);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == MONEY ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return backend.getData().saveNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        backend.getData().loadNBT(nbt);
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {

        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new MoneyProvider());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {

        if (event.getEntity() instanceof ServerPlayer player) {

            ShopNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncMoney(FastShop.getMoney(player))
            );
        }
    }

    /**
     * 死亡继承数据
     */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {

        if (!event.isWasDeath()) return;

        event.getOriginal().reviveCaps();

        event.getOriginal().getCapability(MONEY).ifPresent(oldCap -> {
            event.getEntity().getCapability(MONEY).ifPresent(newCap -> {
                newCap.setMoney(oldCap.getMoney());
            });
        });

        event.getOriginal().invalidateCaps();
    }
}
