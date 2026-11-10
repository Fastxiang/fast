package com.main.fast.maid.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.main.fast.maid.MaidSkillRegistry;
import com.main.fast_irons_spellbooks_addition.event.BasicAttackPreEvent;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 女仆战斗相关的服务端事件。
 *
 * <p>只做两件事：</p>
 * <ol>
 *     <li><b>命中特效</b>：监听铁魔法的 {@link BasicAttackPreEvent}。
 *         这个事件由 {@code triple_strike / six_strike / triple_slash / triple_magic_missile}
 *         在每次命中前抛出，所以不管末影饰品把法术换成哪一个，女仆的挥砍表现都跟着走，
 *         同时不会影响饰品对伤害的修改（我们只读，不动数值）。</li>
 *     <li><b>技能快照清理</b>：长时间没刷新的女仆数据定期丢弃。</li>
 * </ol>
 */
public final class MaidCombatEvents {

    /** 快照有效期：10 分钟没有刷新就丢掉 */
    private static final long SNAPSHOT_TTL_TICKS = 20L * 60L * 10L;
    private static final int PRUNE_INTERVAL_TICKS = 1200;

    private MaidCombatEvents() {
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MaidCombatEvents.class);
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fast-maid-combat");

    @SubscribeEvent
    public static void onBasicAttackPre(BasicAttackPreEvent event) {
        if (!(event.getAttacker() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide()) {
            return;
        }
        // 每段都记：连段到底走到第几段，只看这条日志，别再一次性的开关把信息吃掉
        LOGGER.info("[maid] 连段命中 {} combo={} type={}",
                maid.getName().getString(), event.getCombo(), event.getType());
        ServerLevel level = (ServerLevel) maid.level();

        boolean magic = "magic".equalsIgnoreCase(event.getType());
        boolean finisher = isFinisher(maid, event.getCombo());

        Vec3 look = maid.getLookAngle();
        double x = maid.getX() + look.x * 1.4D;
        double y = maid.getY() + maid.getBbHeight() * 0.6D;
        double z = maid.getZ() + look.z * 1.4D;

        if (magic) {
            // 魔法基础攻击：紫色附魔命中粒子，避免和物理挥砍混在一起
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, finisher ? 10 : 5, 0.3D, 0.3D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.WITCH, x, y, z, 2, 0.25D, 0.25D, 0.25D, 0.0D);
        } else {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (finisher) {
                level.sendParticles(ParticleTypes.CRIT, x, y, z, 12, 0.4D, 0.4D, 0.4D, 0.15D);
                level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 1.0F);
            }
            level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.55F,
                    0.9F + maid.getRandom().nextFloat() * 0.2F);
        }
    }

    /** 是否是这套连段的最后一段：读当前吟唱法术的重施段数，读不到就退回「第 3 段为重击」 */
    private static boolean isFinisher(EntityMaid maid, int combo) {
        if (maid instanceof IMagicEntity magic) {
            MagicData magicData = magic.getMagicData();
            if (magicData != null) {
                SpellData casting = magicData.getCastingSpell();
                if (casting != null) {
                    int total = casting.getSpell().getRecastCount(casting.getLevel(), maid);
                    if (total > 0) {
                        return combo >= total;
                    }
                }
            }
        }
        return combo >= 3;
    }

    @SubscribeEvent
    public static void onMaidTick(com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        // 枪械模式不是我们的职业任务，没有 MaidCombatBehavior，只能靠 tick 驱动技能
        com.main.fast.maid.MaidGunSupport.tick(maid);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % PRUNE_INTERVAL_TICKS != 0) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        MaidSkillRegistry.prune(gameTime, SNAPSHOT_TTL_TICKS);
    }
}
