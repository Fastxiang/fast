package com.main.fast.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 女仆装备判定的公共入口。
 *
 * <p>战斗 AI 与走位 AI 都要知道「饰品栏里有没有勇者之剑 / 法杖」，这里是唯一实现，
 * 避免两处各扫一遍、判断口径还不一致。</p>
 */
public final class MaidEquipment {

    public static final String HERO_SWORD = "fast:the_hero_sword";
    public static final String HERO_STAFF = "fast:the_hero_staff";

    private MaidEquipment() {
    }

    /**
     * 女仆饰品栏里有没有这件物品。
     *
     * <p>槽位数是 {@code EntityMaid.BAUBLE_INV_SIZE = 30}（界面文案的「3x3」是旧说法），
     * 所以按 {@code getSlots()} 全量扫，别写死 9。</p>
     */
    public static boolean hasBauble(EntityMaid maid, String itemId) {
        var baubles = maid.getMaidBauble();
        if (baubles == null) {
            return false;
        }
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && itemId.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }
}
