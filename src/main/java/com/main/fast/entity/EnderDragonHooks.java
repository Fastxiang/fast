package com.main.fast.entity;

import com.main.fast.Fast;
import com.main.fast_irons_spellbooks_addition.registry.FastAttributeRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 原版末影龙强化：百万血量 + 会放「魔法箭-移动龙息」+ 额外战利品。
 *
 * <p>末影龙不是 {@code PathfinderMob}，走不了 {@code PathfinderMobMixin} 那套施法流程，
 * 所以这里直接用 Iron's 的公开入口 {@link AbstractSpell#onCast}（和 {@code PhantomCantor} 一样）。</p>
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class EnderDragonHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/EnderDragon");

    /** 末影龙基础血量：100 万 */
    public static final float MAX_HEALTH = 1_000_000.0F;

    /** 龙息箭的魔法攻击力：末影龙自身没有这个属性，进世界时补上 */
    private static final double MAGIC_ATTACK = 10.0D;

    /** 施法间隔：5 秒 */
    private static final int CAST_INTERVAL_TICKS = 100;
    /** 索敌半径 */
    private static final double TARGET_RANGE = 48.0D;
    /** 法术等级 */
    private static final int SPELL_LEVEL = 3;

    private static final ResourceLocation DRAGON_BREATH_ARROW =
            new ResourceLocation("fast_irons_spellbooks_addition", "magic_arrow_moving_dragon_breath");

    /** 战利品表：原版末影龙 */
    private static final ResourceLocation ENDER_DRAGON_LOOT =
            new ResourceLocation("minecraft", "entities/ender_dragon");

    /** 额外掉落的物品（龙蛋是原版物品，这里只是补进表里） */
    private static final String[] EXTRA_LOOT = {
            "fast:dragon_bone",
            "fast:dragon_heart",
            "minecraft:dragon_egg"
    };

    /** 当前世界里的末影龙（弱引用，避免拖住实体） */
    private static final List<WeakReference<EnderDragon>> TRACKED = new ArrayList<>();

    /** AttributeMap#register(Attribute)，原版没公开，反射缓存一份 */
    private static final Method ATTRIBUTE_REGISTER = resolveAttributeRegister();

    private EnderDragonHooks() {
    }

    // ------------------------------------------------------------------
    // 血量 / 属性
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;

        applyHealth(dragon);
        applyMagicAttack(dragon);
        // 只在服务端跟踪一次
        synchronized (TRACKED) {
            for (WeakReference<EnderDragon> ref : TRACKED) {
                if (ref.get() == dragon) return;
            }
            TRACKED.add(new WeakReference<>(dragon));
        }
        LOGGER.info("[Fast/EnderDragon] 末影龙强化生效：{} 血", dragon.getMaxHealth());
    }

    private static void applyHealth(EnderDragon dragon) {
        AttributeInstance maxHealth = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.setBaseValue(MAX_HEALTH);
        // 只给「还没被强化过」的龙补满血：存档里的血量 > 200 说明已经在打这只了，重启后别回满
        if (dragon.getHealth() <= 200.0F) {
            dragon.setHealth(MAX_HEALTH);
        }
    }

    /** 末影龙原本没有魔法攻击属性，补一个，否则龙息伤害会是 0 */
    private static void applyMagicAttack(EnderDragon dragon) {
        Attribute magicAttack = FastAttributeRegistry.MAGIC_ATTACK.get();
        if (dragon.getAttribute(magicAttack) == null) {
            // AttributeMap#register 是 protected，原版没有公开入口，只能反射加一次
            try {
                if (ATTRIBUTE_REGISTER != null) ATTRIBUTE_REGISTER.invoke(dragon.getAttributes(), magicAttack);
            } catch (Exception e) {
                LOGGER.warn("[Fast/EnderDragon] 注册魔法攻击属性失败: {}", e.toString());
                return;
            }
        }
        AttributeInstance instance = dragon.getAttribute(magicAttack);
        if (instance != null) instance.setBaseValue(MAGIC_ATTACK);
    }

    private static Method resolveAttributeRegister() {
        try {
            Method method = AttributeMap.class.getDeclaredMethod("register", Attribute.class);
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            LOGGER.warn("[Fast/EnderDragon] 找不到 AttributeMap#register: {}", e.toString());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // AI：定期放魔法箭-移动龙息
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        List<WeakReference<EnderDragon>> snapshot;
        synchronized (TRACKED) {
            if (TRACKED.isEmpty()) return;
            snapshot = new ArrayList<>(TRACKED);
        }

        for (WeakReference<EnderDragon> ref : snapshot) {
            EnderDragon dragon = ref.get();
            if (dragon == null || !dragon.isAlive() || dragon.isRemoved()) {
                synchronized (TRACKED) {
                    TRACKED.remove(ref);
                }
                continue;
            }
            if (dragon.tickCount % CAST_INTERVAL_TICKS != 0) continue;
            tryCast(dragon);
        }
    }

    private static void tryCast(EnderDragon dragon) {
        Player target = dragon.level().getNearestPlayer(dragon, TARGET_RANGE);
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) return;

        AbstractSpell spell = SpellRegistry.getSpell(DRAGON_BREATH_ARROW);
        if (spell == null || spell == SpellRegistry.none()) return;

        // 先把头转向目标，投射物是按末影龙的朝向射出去的
        dragon.getLookControl().setLookAt(target, 30.0F, 30.0F);

        try {
            spell.onCast(dragon.level(), SPELL_LEVEL, dragon, CastSource.MOB, new MagicData(true));
        } catch (Exception e) {
            LOGGER.warn("[Fast/EnderDragon] 龙息箭释放失败: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 战利品
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!ENDER_DRAGON_LOOT.equals(event.getName())) return;

        LootTable table = event.getTable();
        for (String itemId : EXTRA_LOOT) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null) {
                LOGGER.warn("[Fast/EnderDragon] 战利品物品不存在，跳过: {}", itemId);
                continue;
            }
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(item))
                    .build());
        }
    }
}
