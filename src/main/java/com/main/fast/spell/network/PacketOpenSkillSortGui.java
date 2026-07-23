package com.main.fast.spell.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketOpenSkillSortGui {

    private final Map<String, ?> skillList;

    public PacketOpenSkillSortGui(
            Map<String, ?> skillList
    ) {
        this.skillList = skillList;
    }

    public Map<String, ?> getSkillList() {
        return skillList;
    }

    public static void encode(
            PacketOpenSkillSortGui msg,
            FriendlyByteBuf buf
    ) {

        buf.writeInt(msg.skillList.size());

        msg.skillList.forEach((id, levelObj) -> {

            int level;

            if (levelObj instanceof Number number) {
                level = number.intValue();
            } else {
                level = 0;
            }

            buf.writeUtf(id);
            buf.writeInt(level);
        });
    }

    public static PacketOpenSkillSortGui decode(
            FriendlyByteBuf buf
    ) {

        int size = buf.readInt();

        Map<String, Integer> map =
                new HashMap<>();

        for (int i = 0; i < size; i++) {

            String id = buf.readUtf();
            int level = buf.readInt();

            map.put(id, level);
        }

        return new PacketOpenSkillSortGui(map);
    }

    public static void handle(
            PacketOpenSkillSortGui msg,
            Supplier<NetworkEvent.Context> ctx
    ) {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            com.main.fast.spell.network.client.SpellClientPacketHandlers.handleOpenSkillSortGui(msg, ctx);
        });
    }
}