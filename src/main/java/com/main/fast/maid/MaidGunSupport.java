package com.main.fast.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.main.fast.compat.GunAmmo;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Map;

/**
 * 枪械模式（女仆模组原版 {@code touhou_little_maid:gun_attack}）下的技能驱动。
 *
 * <p>枪械模式不是我们的职业任务，{@link com.main.fast.maid.brain.MaidCombatBehavior} 不会被挂进它的
 * brain，所以这里单独走 {@code MaidTickEvent} 驱动，只做「按 gun 标签放技能」这一件事：</p>
 *
 * <ol>
 *     <li><b>弹药补给</b>：只在背包里匹配弹药 {@code <= LOW_AMMO} 发时放（女仆换弹是从背包里取的，
 *         和 {@link GunAmmo} 的口径一致），其余时候不浪费蓝和冷却</li>
 *     <li>其它带 {@code gun} 标签的技能（锋刃觉醒等）：冷却好了就放</li>
 *     <li>{@code control} 类（弱点标记）需要有攻击目标才放</li>
 * </ol>
 */
public final class MaidGunSupport {

    /** 女仆模组的枪械攻击任务 UID */
    public static final ResourceLocation GUN_TASK = new ResourceLocation("touhou_little_maid", "gun_attack");
    private static final ResourceLocation AMMO_SUPPLY =
            new ResourceLocation("fast_irons_spellbooks_addition", "ammo_supply");

    /** 弹药低于这个数才开始补：别一开打就补，否则技能栏被补弹占满 */
    private static final int AMMO_LOW = 32;
    /** 补到这个数才停 —— 2 组（128 发） */
    private static final int AMMO_TARGET = 128;
    /** 弹药补给的施法间隔（tick）：一次只给 10 发，间隔太长要补十几秒，压到 0.5 秒 */
    private static final int AMMO_SUPPLY_COOLDOWN = 10;
    /** 决策节流：半秒一次；补弹期间要连着放，节流不能设成 1 秒 */
    private static final int DECIDE_INTERVAL = 10;

    /** 正在补弹的女仆：从「低于 32」开始，一直到「补满 128」才放开，中间不中断 */
    private static final java.util.Set<java.util.UUID> REFILLING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private MaidGunSupport() {
    }

    /** 女仆当前是不是在枪械模式 */
    public static boolean isGunMode(EntityMaid maid) {
        var task = maid.getTask();
        return task != null && GUN_TASK.equals(task.getUid());
    }

    public static void tick(EntityMaid maid) {
        if (!isGunMode(maid) || maid.tickCount % DECIDE_INTERVAL != 0) {
            return;
        }
        if (maid instanceof IMagicEntity magic && magic.isCasting()) {
            return;
        }
        MaidSkillData data = MaidSkillRegistry.getData(maid);
        if (data == null || data.isEmpty()) {
            return;
        }
        long gameTime = maid.level().getGameTime();

        // 1) 弹药见底才补，一旦开始就补到 2 组为止（滞回：<32 开始，>=128 结束）
        if (data.hasSkill(AMMO_SUPPLY)) {
            int ammo = GunAmmo.countForMainHand(maid);
            java.util.UUID id = maid.getUUID();
            boolean refilling = REFILLING.contains(id);
            if (refilling && ammo >= AMMO_TARGET) {
                REFILLING.remove(id);
                refilling = false;
            } else if (!refilling && ammo < AMMO_LOW) {
                REFILLING.add(id);
                refilling = true;
            }
            if (refilling && data.isReady(AMMO_SUPPLY, gameTime)
                    && cast(maid, AMMO_SUPPLY, data, gameTime, AMMO_SUPPLY_COOLDOWN)) {
                return;
            }
        }

        // 2) 其余枪械技能：有目标就挑一个冷却好的
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(LivingEntity::isAlive).orElse(null);
        if (target == null) {
            return;
        }
        for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
            ResourceLocation spellId = entry.getKey();
            if (spellId.equals(AMMO_SUPPLY) || !MaidSkillRegistry.hasTag(spellId, "gun")) {
                continue;
            }
            if (MaidSkillRegistry.hasTag(spellId, "control") && !maid.canSee(target)) {
                continue;
            }
            if (!data.isReady(spellId, gameTime)) {
                continue;
            }
            if (cast(maid, spellId, data, gameTime, MaidCombatPlanner.cooldownFor(spellId,
                    Math.max(data.getLevel(spellId), 1)))) {
                return;
            }
        }
    }

    private static boolean cast(EntityMaid maid, ResourceLocation spellId, MaidSkillData data,
                                long gameTime, int cooldownTicks) {
        AbstractSpell spell = SpellRegistry.getSpell(spellId);
        if (spell == null || spell == SpellRegistry.none() || !spell.isEnabled()) {
            return false;
        }
        if (!(maid instanceof IMagicEntity magic)) {
            return false;
        }
        int level = Math.max(data.getLevel(spellId), 1);
        magic.initiateCastSpell(spell, level);
        if (!magic.isCasting()) {
            return false;
        }
        data.setCooldown(spellId, cooldownTicks, gameTime);
        return true;
    }

}
