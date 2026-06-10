package com.main.fast.spell.network;

import com.main.fast.Fast;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SkillNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    Fast.id("skill_sort"),
                    () -> VERSION,
                    VERSION::equals,
                    VERSION::equals
            );

    private static int id = 0;

    public static void init() {

        CHANNEL.registerMessage(
                id++,
                PacketRequestSkillOrder.class,
                PacketRequestSkillOrder::encode,
                PacketRequestSkillOrder::decode,
                PacketRequestSkillOrder::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketSyncSkillOrder.class,
                PacketSyncSkillOrder::encode,
                PacketSyncSkillOrder::decode,
                PacketSyncSkillOrder::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketUpdateSkillOrder.class,
                PacketUpdateSkillOrder::encode,
                PacketUpdateSkillOrder::decode,
                PacketUpdateSkillOrder::handle
        );

        CHANNEL.registerMessage(
                id++,
                PacketOpenSkillSortGui.class,
                PacketOpenSkillSortGui::encode,
                PacketOpenSkillSortGui::decode,
                PacketOpenSkillSortGui::handle
        );
    }
}