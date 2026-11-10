package com.main.fast.compat.stardew;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 女仆钓鱼的宝藏结算（星露谷钓鱼 + 宝藏末影饰品）。
 *
 * <p>女仆用的是女仆模组自己的 {@code MaidFishingHook}，不是原版 {@code FishingHook}，
 * 所以 {@code stardew_fishing} 的 mixin（只认原版钩子 / Aquaculture 浮标）完全覆盖不到她们。
 * 这里挂在 {@code MaidFishingHook#spawnLoot} 头部，直接往最终掉落列表里补宝藏。</p>
 *
 * <p>女仆<b>永远按自动模式</b>算：只吃「出不出宝藏 / 出不出金宝藏」，
 * 不吃勇者之竿手动模式的三倍战利品（那个是玩家自己玩小游戏的补偿）。</p>
 *
 * <p>概率与玩家侧保持一致：</p>
 * <ul>
 *   <li>基础宝藏率读 {@code SFConfig.getTreasureChestChance()}（默认 0.15）；</li>
 *   <li>装了宝藏诱饵 {@code fast:treasure_bait} → 必定出普通宝藏；</li>
 *   <li>金宝箱率 = {@code SFConfig.getGoldenChestChance()}（默认 0.10）+ 每件宝藏囤积者 +1%；</li>
 *   <li>金宝箱结算 2 次，另有 25% → 3 次、再 50% → 4 次，和模组原逻辑一致。</li>
 * </ul>
 *
 * <p>掉落内容走 {@code stardew_fishing:treasure_chest(_nether)} 战利品表，
 * 已被 {@code kubejs/data/stardew_fishing/loot_tables/} 覆盖成整合包自己的物品与钱币。</p>
 */
public final class MaidFishingTreasure {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/StardewFishing");

    private static final ResourceLocation OVERWORLD_TABLE =
            new ResourceLocation("stardew_fishing", "treasure_chest");
    private static final ResourceLocation NETHER_TABLE =
            new ResourceLocation("stardew_fishing", "treasure_chest_nether");

    /** 宝藏诱饵：必定出普通宝藏 */
    private static final String TREASURE_BAIT = "fast:treasure_bait";
    /** 宝藏囤积者：每件 +1% 金宝箱 */
    private static final String TREASURE_HOARDER = "fast:treasure_hoarder";

    private static final double DEFAULT_TREASURE_CHANCE = 0.15D;
    private static final double DEFAULT_GOLDEN_CHANCE = 0.10D;

    private static boolean resolved = false;
    private static Method treasureChanceMethod;
    private static Method goldenChanceMethod;

    private MaidFishingTreasure() {
    }

    /** 由 {@code MaidFishingHook#spawnLoot} 头部调用 */
    public static void addTreasure(List<ItemStack> drops, EntityMaid maid) {
        if (drops == null || maid == null) return;
        if (!ModList.get().isLoaded("stardew_fishing")) return;
        if (!(maid.level() instanceof ServerLevel level)) return;

        boolean bait = countBauble(maid, TREASURE_BAIT) > 0;
        if (!bait && level.random.nextDouble() >= treasureChance()) return;

        int hoarder = countBauble(maid, TREASURE_HOARDER);
        boolean golden = level.random.nextDouble() < goldenChance() + 0.01D * hoarder;

        int rolls = 1;
        if (golden) {
            rolls = 2;
            if (level.random.nextFloat() < 0.25F) {
                rolls++;
                if (level.random.nextFloat() < 0.5F) {
                    rolls++;
                }
            }
        }

        try {
            LootTable table = level.getServer().getLootData()
                    .getLootTable(level.dimension() == Level.NETHER ? NETHER_TABLE : OVERWORLD_TABLE);
            LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
            for (int i = 0; i < rolls; i++) {
                drops.addAll(table.getRandomItems(params));
            }
        } catch (Throwable t) {
            LOGGER.warn("[Fast/StardewFishing] 女仆宝藏结算失败: {}", t.toString());
        }
    }

    /** 女仆饰品栏里某件饰品的总数量（不可堆叠的也是每格 1 件） */
    private static int countBauble(EntityMaid maid, String itemId) {
        try {
            var bauble = maid.getMaidBauble();
            int count = 0;
            for (int i = 0; i < bauble.getSlots(); i++) {
                ItemStack stack = bauble.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null && itemId.equals(id.toString())) {
                    count += stack.getCount();
                }
            }
            return count;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static double treasureChance() {
        double value = readConfig(true);
        return value >= 0.0D ? value : DEFAULT_TREASURE_CHANCE;
    }

    private static double goldenChance() {
        double value = readConfig(false);
        return value >= 0.0D ? value : DEFAULT_GOLDEN_CHANCE;
    }

    private static double readConfig(boolean treasure) {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> config = Class.forName("com.bonker.stardewfishing.SFConfig");
                treasureChanceMethod = config.getMethod("getTreasureChestChance");
                goldenChanceMethod = config.getMethod("getGoldenChestChance");
            } catch (Throwable ignored) {
                treasureChanceMethod = null;
                goldenChanceMethod = null;
            }
        }
        Method method = treasure ? treasureChanceMethod : goldenChanceMethod;
        if (method == null) return -1.0D;
        try {
            return ((Number) method.invoke(null)).doubleValue();
        } catch (Throwable t) {
            return -1.0D;
        }
    }
}
