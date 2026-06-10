package com.main.fast.spell.api;

import com.main.fast.spell.capability.SkillSortCapabilityProvider;
import com.main.fast.spell.network.PacketOpenSkillSortGui;
import com.main.fast.spell.network.PacketSyncSkillOrder;
import com.main.fast.spell.network.SkillNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillSortApi {

    /**
     * 服务端打开技能排序界面
     *
     * @param player    玩家
     * @param skillList 技能列表
     */
    public static void open(
            ServerPlayer player,
            Map<String, ?> skillList
    ) {

        SkillNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenSkillSortGui(skillList)
        );
    }

    public static List<String> getOrder(
            Player player
    ) {

        return player.getCapability(
                        SkillSortCapabilityProvider.CAPABILITY
                )
                .map(cap ->
                        new ArrayList<>(
                                cap.getSkillOrder()
                        )
                )
                .orElseGet(ArrayList::new);
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer) {
            player.getCapability(SkillSortCapabilityProvider.CAPABILITY).ifPresent(cap -> {
                SkillNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                        new PacketSyncSkillOrder(cap.getSkillOrder())
                );
            });
        }
    }
}