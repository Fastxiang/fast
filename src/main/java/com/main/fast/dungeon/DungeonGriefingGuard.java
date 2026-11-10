package com.main.fast.dungeon;

import com.main.fast.Fast;
import com.main.fast.dungeon.api.DungeonMobData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 副本里的灾变 BOSS 不许破坏方块。
 *
 * <p>灾变（L_Ender's Cataclysm）每只 BOSS 的破坏逻辑（Ignis 的 {@code blockbreak}、
 * Netherite Monstrosity 的 {@code ChargeBlockBreaking} / {@code berserkBlockBreaking}、
 * The Harbinger 的 {@code blockbreak} / {@code destroyBlock}、Ancient Remnant 的
 * {@code ChargeBlockBreaking} …）在真正动手前都会先调
 * {@code ForgeEventFactory.getMobGriefingEvent(level, this)}。所以不需要逐个 mixin 它们的
 * 私有方法，只要在这个事件里对「副本里的灾变怪」返回 {@link Event.Result#DENY}，
 * 上述所有破坏路径会一起跳过（掀地板、撞墙、震地都包括）。</p>
 *
 * <p>限定条件有意收得很窄，不影响整合包其它地方：</p>
 * <ul>
 *   <li>必须是 {@code cataclysm:} 命名空间的怪 —— 只按需求管灾变 BOSS；</li>
 *   <li>必须带 {@link DungeonMobData} —— 也就是副本系统自己刷出来的，不是自然生成的；</li>
 *   <li>必须还在进行中的副本范围内（{@link DungeonManager#isEntityInInstance}）——
 *       副本结束后立刻失效，不会把外面的灾变 BOSS 也阉掉。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class DungeonGriefingGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fast/Dungeon");
    private static final String CATACLYSM_NAMESPACE = "cataclysm";

    private DungeonGriefingGuard() {
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;
        // 只有灾变 BOSS 需要这条；其它模组的怪维持原样
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null || !CATACLYSM_NAMESPACE.equals(id.getNamespace())) return;
        // 只副本系统刷出来的怪 + 只在副本进行中
        if (DungeonMobData.of(entity) == null) return;
        if (!DungeonManager.isEntityInInstance(entity)) return;

        event.setResult(Event.Result.DENY);
        LOGGER.debug("[Fast/Dungeon] 已阻止副本灾变怪破坏方块: {}", id);
    }
}
