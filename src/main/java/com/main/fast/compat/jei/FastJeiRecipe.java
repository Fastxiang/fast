package com.main.fast.compat.jei;

import com.main.fast.compat.jei.api.FastJei;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条送进 JEI 的「获取方式」。
 *
 * <p>由 {@link FastJei.Entry} 转换而来：字符串 id 在这里变成真正的 {@link ItemStack}，
 * 描述保留成 {@link Component}，翻译推迟到绘制那一刻。</p>
 */
public final class FastJeiRecipe {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ItemStack output;
    /** 需要的副本钥匙，没有就是 null */
    private final ItemStack input;
    /** 额外反查物品（通关凭证 / 无限钥匙等），左键和右键都能翻到 */
    private final List<ItemStack> lookups;
    private final Component description;
    /** 来源名（副本名），没有就是 null */
    private final Component source;

    private FastJeiRecipe(ItemStack output, ItemStack input, List<ItemStack> lookups,
                          Component description, Component source) {
        this.output = output;
        this.input = input;
        this.lookups = lookups;
        this.description = description;
        this.source = source;
    }

    /** 把脚本推过来的条目全部转成配方（物品 id 解不出来的直接丢掉） */
    public static List<FastJeiRecipe> buildAll() {
        List<FastJeiRecipe> recipes = new ArrayList<>();

        for (FastJei.Entry entry : FastJei.getEntries()) {
            ItemStack output = resolve(entry.getItemId(), entry.getNbt());
            if (output == null) {
                LOGGER.warn("[Fast/JEI] 认不出的物品 id：{}", entry.getItemId());
                continue;
            }

            List<ItemStack> lookups = new ArrayList<>();
            addLookup(lookups, entry.getPassItemId());
            for (String extra : entry.getExtraLookups()) {
                addLookup(lookups, extra);
            }

            recipes.add(new FastJeiRecipe(
                    output,
                    resolve(entry.getKeyItemId(), null),
                    lookups,
                    entry.getDescription(),
                    entry.getSource()));
        }

        return recipes;
    }

    private static void addLookup(List<ItemStack> lookups, String itemId) {
        if (itemId == null || itemId.isEmpty()) return;

        ItemStack stack = resolve(itemId, null);
        if (stack == null) return;

        for (ItemStack exists : lookups) {
            if (ItemStack.isSameItemSameTags(exists, stack)) return;
        }
        lookups.add(stack);
    }

    /** 物品 id + NBT → 物品堆；解析不出来返回 null */
    private static ItemStack resolve(String itemId, JsonElement nbt) {
        if (itemId == null || itemId.isEmpty()) return null;

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return null;

        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return null;

        ItemStack stack = new ItemStack(item);

        CompoundTag tag = toTag(nbt);
        // 空 compound 不写：否则会被当成「带 NBT 的变种」，普通物品查不到
        if (tag != null && !tag.isEmpty()) {
            stack.setTag(tag);
        }

        return stack;
    }

    /**
     * NBT 两种来源都吃：
     * <ul>
     *   <li>SNBT 字符串 —— 脚本里 {@code `${stack.nbt}`} 拿到的形式；</li>
     *   <li>JSON 对象 —— 脚本直接把手上的数据对象丢过来，这里是唯一可靠的路径
     *       （客户端脚本阶段 {@code Item.of('模组id')} 可能返回空堆，拿不到 NBT）。</li>
     * </ul>
     */
    private static CompoundTag toTag(JsonElement nbt) {
        if (nbt == null || nbt.isJsonNull()) return null;

        try {
            if (nbt.isJsonPrimitive()) {
                return TagParser.parseTag(nbt.getAsString());
            }
            if (nbt.isJsonObject()) {
                Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, nbt);
                return converted instanceof CompoundTag compound ? compound : null;
            }
        } catch (Exception e) {
            LOGGER.warn("[Fast/JEI] NBT 解析失败：{}", nbt);
        }

        return null;
    }

    public ItemStack getOutput() {
        return output;
    }

    /** 需要的副本钥匙，可能为 null */
    public ItemStack getInput() {
        return input;
    }

    /** 额外反查物品（通关凭证 / 无限钥匙等） */
    public List<ItemStack> getLookups() {
        return lookups;
    }

    public Component getDescription() {
        return description;
    }

    /** 来源名，可能为 null */
    public Component getSource() {
        return source;
    }
}
