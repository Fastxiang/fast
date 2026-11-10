package com.main.fast.compat.jade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.main.fast.Fast;
import com.main.fast.compat.jade.client.JadeTooltipEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jade 实体提示：等级 / 末影饰品 / 防御 / 格挡率 / 格挡值。
 *
 * <p>数据来源：等级与末影饰品走服务端数据包（{@link FastJadeServerData}），
 * 防御 / 格挡直接读客户端实体属性（原版会同步属性，不需要额外包）。</p>
 */
public class FastJadeInfo implements IEntityComponentProvider {

    public static final ResourceLocation ID = Fast.id("entity_info");

    private static final ResourceLocation DEFENSE = new ResourceLocation("fast:defense");
    private static final ResourceLocation BLOCK_CHANCE = new ResourceLocation("fast:block_chance");
    private static final ResourceLocation BLOCK_VALUE = new ResourceLocation("fast:block_value");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof LivingEntity living)) return;

        CompoundTag data = accessor.getServerData();
        if (data == null) data = new CompoundTag();

        int level = data.getInt(FastJadeServerData.KEY_LEVEL);
        if (level > 0) {
            tooltip.add(Component.literal("等级: " + level));
        }

        String bonusJson = data.getString(FastJadeServerData.KEY_BONUS);
        if (!bonusJson.isEmpty()) {
            String line = enderBonusLine(bonusJson);
            if (line != null) {
                tooltip.add(Component.literal(line));
            }
        }

        double defense = valueOf(living, DEFENSE);
        if (defense != 0) {
            tooltip.add(Component.literal("防御: " + format(defense)));
        }

        double blockChance = valueOf(living, BLOCK_CHANCE);
        if (blockChance != 0) {
            tooltip.add(Component.literal("格挡率: " + Math.round(blockChance * 100) + "%"));
        }

        double blockValue = valueOf(living, BLOCK_VALUE);
        if (blockValue != 0) {
            tooltip.add(Component.literal("格挡值: " + format(blockValue)));
        }

        // 保留给脚本 / 其它模组的扩展点（当前 JS 侧已无监听者）
        MinecraftForge.EVENT_BUS.post(new JadeTooltipEvent(living, tooltip));
    }

    /** [{id,count}] -> "末影饰品: 名字(2) 名字" */
    private static String enderBonusLine(String json) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("id")) continue;
                String id = object.get("id").getAsString();
                int count = object.has("count") ? object.get("count").getAsInt() : 1;
                counter.merge(id, count, Integer::sum);
            }
        } catch (Exception e) {
            return null;
        }

        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null) continue;
            String name = new ItemStack(item).getHoverName().getString();
            names.add(entry.getValue() > 1 ? name + "(" + entry.getValue() + ")" : name);
        }
        return names.isEmpty() ? null : "末影饰品: " + String.join(" ", names);
    }

    private static double valueOf(LivingEntity entity, ResourceLocation attributeId) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        if (attribute == null) return 0;
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0 : instance.getValue();
    }

    /** 5.0 -> "5"，5.5 -> "5.5"（和旧 JS 的数字输出一致） */
    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return Long.toString((long) Math.rint(value));
        }
        return String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    @Override
    public ResourceLocation getUid() {
        return ID;
    }
}
