package com.main.fast.maid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingAnyItemTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.main.fast.maid.MaidJob;
import com.main.fast.maid.MaidSkillData;
import com.main.fast.maid.MaidSkillRegistry;
import com.main.fast.maid.brain.MaidCombatBehavior;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 职业任务基类：目标获取 + 走位 + {@link MaidCombatBehavior} 的统一装配。
 *
 * <p>与原 KubeJS 版本的行为对齐（优先级、走位、举盾），但判断改到 Java 侧，
 * 且「能打不能打」不再只看主手武器，也看女仆身上有没有可用技能。</p>
 */
public abstract class MaidTaskBase implements IAttackTask {

    /** 远程职业的搜索半径，和旧 JS 的 searchRadius 保持一致 */
    private static final float RANGED_SEARCH_RADIUS = 48.0F;

    private final ResourceLocation uid;
    private final MaidJob job;
    private final String iconItemId;
    private final boolean hidden;

    protected MaidTaskBase(String path, MaidJob job, String iconItemId, boolean hidden) {
        this.uid = new ResourceLocation("fast", path);
        this.job = job;
        this.iconItemId = iconItemId;
        this.hidden = hidden;
    }

    public MaidJob getJob() {
        return this.job;
    }

    @Override
    public ResourceLocation getUid() {
        return this.uid;
    }

    @Override
    public ItemStack getIcon() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(this.iconItemId));
        if (item != null && item != Items.AIR) {
            return new ItemStack(item);
        }
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override
    @Nullable
    public SoundEvent getAmbientSound(EntityMaid maid) {
        if (this.job == MaidJob.RANGER) {
            return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK.get(), 0.5F);
        }
        return SoundUtil.attackSound(maid, InitSounds.MAID_ATTACK.get(), 0.5F);
    }

    @Override
    public boolean isHidden(EntityMaid maid) {
        return this.hidden;
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        return false;
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        // 与 IMaidTask 的默认实现保持一致：近战按工作范围，远程固定超视距
        return this.job.isMelee() ? maid.getRestrictRadius() : RANGED_SEARCH_RADIUS;
    }

    // ---------------- AI 装配 ----------------

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks = Lists.newArrayList();

        if (this.job.isAggressive()) {
            BehaviorControl<EntityMaid> startAttacking =
                    StartAttacking.create(this::canStartAttack, IAttackTask::findFirstValidAttackTarget);
            BehaviorControl<EntityMaid> stopAttacking = StopAttackingIfTargetInvalid.create(
                    target -> !this.canStartAttack(maid) || this.outOfRange(target, maid));
            tasks.add(Pair.of(5, startAttacking));
            tasks.add(Pair.of(5, stopAttacking));
        }

        if (this.job == MaidJob.WARRIOR) {
            // 战士自己控站位：有勇者之剑时站在物理三连扇形（半径 3）的边缘拉扯，没有就贴脸普攻
            tasks.add(Pair.of(5, new MaidComboPositionTask()));
        } else if (this.job.isMelee()) {
            BehaviorControl<Mob> walkToTarget = SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.6F);
            tasks.add(Pair.of(5, walkToTarget));
        } else if (this.job != MaidJob.GUARDIAN) {
            tasks.add(Pair.of(5, MaidRangedWalkToTarget.create(0.5F)));
        }

        // 远程 / 治疗 / 守卫边打边绕，必要时后撤（守卫用更小的距离，贴着主人守）
        if (this.job == MaidJob.MAGE || this.job == MaidJob.RANGER || this.job == MaidJob.PRIEST
                || this.job == MaidJob.GUARDIAN) {
            float strafeRange = this.job == MaidJob.GUARDIAN ? 8.0F : 12.0F;
            tasks.add(Pair.of(5, new MaidAttackStrafingAnyItemTask(stack -> true, strafeRange, 0.5F)));
        }

        tasks.add(Pair.of(5, new MaidCombatBehavior(this.job)));

        // 举盾：战士与守卫（与原 JS 的 MaidUseShieldTask 配置一致）
        if (this.job == MaidJob.WARRIOR || this.job == MaidJob.GUARDIAN) {
            tasks.add(Pair.of(5, new MaidUseShieldTask()));
        }
        return tasks;
    }

    // ---------------- 能不能打 ----------------

    private boolean canStartAttack(EntityMaid maid) {
        return this.isWeapon(maid, maid.getMainHandItem()) || hasJobSkill(maid);
    }

    /**
     * 有没有这个职业能用的技能。
     *
     * <p>注意：旧实现里法师必须手持带 {@code BasicAttackData.magic} 的武器才会开打，
     * 女仆装备了饰品却空手时会站着不动。这里放宽成「有技能就能打」。</p>
     */
    protected boolean hasJobSkill(EntityMaid maid) {
        MaidSkillData data = MaidSkillRegistry.getData(maid);
        if (data == null || data.isEmpty()) {
            return false;
        }
        for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
            MaidSkillRegistry.Category category = MaidSkillRegistry.getCategory(entry.getKey());
            if (category == MaidSkillRegistry.Category.BUFF) {
                continue;
            }
            if (category == this.job.getSkillCategory()
                    || category == MaidSkillRegistry.Category.MAGIC
                    || category == MaidSkillRegistry.Category.PHYSICAL
                    || category == MaidSkillRegistry.Category.ARROW
                    || category == MaidSkillRegistry.Category.TANK) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        return switch (this.job) {
            case WARRIOR -> stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
            case MAGE, PRIEST -> hasBasicAttackData(stack, "magic");
            case RANGER -> stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "tools/bows")))
                    || stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "tools/crossbows")));
            case GUARDIAN -> stack.getItem() instanceof ShieldItem;
        };
    }

    private static boolean hasBasicAttackData(ItemStack stack, String key) {
        if (stack.isEmpty() || stack.getTag() == null) {
            return false;
        }
        return stack.getTag().getCompound("BasicAttackData").getBoolean(key);
    }

    /** 远程职业按搜索半径算脱战距离（旧 JS 的 RangedAttackTask 就是这么判的） */
    private boolean outOfRange(LivingEntity target, EntityMaid maid) {
        if (this.job.isMelee()) {
            return this.farAway(target, maid);
        }
        return !target.isAlive() || maid.distanceTo(target) > this.searchRadius(maid);
    }

    /** 与旧 TaskAttack 的 farAway 一致：家模式下看女仆自己，否则看主人 */
    private boolean farAway(LivingEntity target, EntityMaid maid) {
        if (!target.isAlive()) {
            return true;
        }
        float radius = maid.getRestrictRadius();
        if (!maid.isHomeModeEnable() && maid.getOwner() != null) {
            return maid.getOwner().distanceTo(target) > radius;
        }
        return maid.distanceTo(target) > radius;
    }
}
