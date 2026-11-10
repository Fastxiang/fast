package com.main.fast.entity;

import com.main.fast.compat.PhantomTacGun;
import com.main.fast.entity.ai.PhantomHeroApproachGoal;
import com.main.fast.entity.ai.PhantomHeroBowAttackGoal;
import com.main.fast.entity.ai.PhantomHeroComboGoal;
import com.main.fast.entity.ai.PhantomHeroPhaseTwoGoal;
import com.main.fast.entity.ai.PhantomHeroShieldRushGoal;
import com.main.fast.entity.ai.PhantomHeroSniperGoal;
import com.main.fast_irons_spellbooks_addition.registry.FastAttributeRegistry;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

/**
 * 昔日勇者的幻影。
 *
 * 一个还残留着生前战斗本能的英雄亡魂，只会生前那几招：
 *
 * 行为基调：这是一个「贴脸近战」的 Boss，不是风筝怪。
 *
 * 默认会一路走到玩家面前抡物理三连击；
 * 只有当玩家主动拉开距离（{@link #isTargetFleeing()}）时，
 * 才会切到远程：魔法三连击 / 弓箭。
 *
 * 1. 物理三连击（附属 mod 的 triple_strike）
 *    - 释放前 1 秒在脚下出现红色扇形提示，形状 = 法术真正的判定区域
 *      （半径 = 本实体的 ENTITY_REACH 属性，半角 60 度）
 *    - 手持钻石剑
 *    - 默认手段：Boss 会主动走到玩家面前放
 * 2. 魔法三连击（附属 mod 的 triple_magic_missile）
 *    - 释放前 1 秒出现沿视线的红色直线提示（弹射物飞行路径）
 *    - 空手
 *    - 只在玩家主动远离时才会用
 * 3. 普攻弓箭（站桩拉弓射击）
 *    - 手持弓
 *    - 同样只在玩家主动远离时才会用
 *
 * 所有技能统一通过
 * {@link #playSpellAnimation(ResourceLocation)} 把动画资源位置同步给客户端，
 * 客户端模型（PhantomHeroModel）负责真正播放动画。
 */
public class BossPhantomHero extends FastBossEntity implements RangedAttackMob {

    // 三连击技能冷却
    public int comboCooldown = 0;

    /**
     * 近战交战距离：走到这么近就不用再往前挤了，交给三连击 goal。
     */
    public static final double MELEE_ENGAGE_RANGE = 2.4D;

    // ---------------------------------------------------------------
    // 「玩家是不是在主动拉开距离」
    // ---------------------------------------------------------------

    /**
     * 每 tick 距离增加超过这个值，就算玩家在主动远离。
     * 0.05 格/tick ≈ 1 格/秒的相对拉开速度，
     * 玩家正常跑开、Boss 又追不太上时会稳定超过这个值。
     */
    private static final double FLEE_DELTA_PER_TICK = 0.05D;

    /**
     * 停止远离后，「远程模式」还会保留多少 tick。
     * 留一点余量，避免玩家边跑边停时远程攻击被切得支离破碎。
     */
    private static final int FLEE_HOLD_MAX = 40;

    private double lastTargetDistance = -1.0D;
    private int fleeTicks = 0;
    /**
     * 手持武器状态。
     *
     * 0 空手 / 1 钻石剑 / 2 弓 / 3 AWM 狙击枪 / 4 钻石剑 + 盾牌
     *
     * 原版 LivingEntity 不会同步生物的手持物品，
     * 所以状态本身用一个 byte 同步（见 DATA_WEAPON），
     * 服务端和客户端各自按这个状态换上同样的 ItemStack。
     */
    public static final int WEAPON_NONE = 0;
    public static final int WEAPON_SWORD = 1;
    public static final int WEAPON_BOW = 2;
    public static final int WEAPON_SNIPER = 3;
    public static final int WEAPON_SHIELD = 4;

    // 狙击技能冷却
    public int sniperCooldown = 0;

    // 剑盾状态冷却
    public int shieldRushCooldown = 0;

    // ---------------------------------------------------------------
    // 二阶段（半血转化）
    // ---------------------------------------------------------------

    /**
     * 二阶段触发线：最大生命值的一半。
     */
    public static final float PHASE_TWO_HEALTH_RATIO = 0.5F;

    // 二阶段状态
    public int phase = 1;
    public int transitionTicks = 0;
    public int shieldBreakCooldown = 0;

    /**
     * 二阶段下是否允许移动（近战三连击期间）。
     *
     * 一阶段所有技能都是站桩的；二阶段只对「三连击」放开移动，
     * 其它技能（狙击 / 弓箭 / 转化演出）依然锁在原地。
     */
    public boolean permitComboMovement = false;

    /**
     * 是否正在选招 —— 选招期间任何攻击 goal 都不许起手。
     *
     * 二阶段在近战距离下是「随机抽两个技能 -> 打完回剑盾」：
     * 每打完一个技能就进一次选招，避免技能打完的瞬间
     * 有别的 goal 抢先进来，把「两个」变成「三个」。
     */
    public boolean attackSelection = false;

    /** 二阶段「抽两个技能」还剩几次没放 */
    public int pendingAttacks = 0;

    /** 二阶段每一轮随机技能的个数 */
    public static final int ATTACKS_PER_PHASE_TWO_ROUND = 2;

    /** 选招的空档时长 */
    public static final int SELECTION_TICKS = 10;

    /** 转化演出的时长 */
    public static final int TRANSITION_TICKS = 60;

    /** 盾牌被打碎后，隔多久自动修复 */
    public static final int SHIELD_REGEN_TICKS = 20 * 5;

    // ---------------------------------------------------------------
    // 二阶段 goal 引用
    //
    // 二阶段的随机选招需要「强制起手某个技能」，而技能 goal 是由
    // GoalSelector 内部维护的、外面拿不到实例，所以注册时把引用存下来。
    // ---------------------------------------------------------------

    private PhantomHeroComboGoal comboGoal;
    private PhantomHeroSniperGoal sniperGoal;
    private PhantomHeroBowAttackGoal bowGoal;
    private PhantomHeroPhaseTwoGoal phaseTwoGoal;

    public void setPhaseTwoGoals(
            PhantomHeroComboGoal comboGoal,
            PhantomHeroSniperGoal sniperGoal,
            PhantomHeroBowAttackGoal bowGoal,
            PhantomHeroPhaseTwoGoal phaseTwoGoal
    ) {
        this.comboGoal = comboGoal;
        this.sniperGoal = sniperGoal;
        this.bowGoal = bowGoal;
        this.phaseTwoGoal = phaseTwoGoal;
    }

    public PhantomHeroPhaseTwoGoal getPhaseTwoGoal() {
        return this.phaseTwoGoal;
    }

    public PhantomHeroComboGoal getComboGoal() {
        return this.comboGoal;
    }

    public PhantomHeroSniperGoal getSniperGoal() {
        return this.sniperGoal;
    }

    public PhantomHeroBowAttackGoal getBowGoal() {
        return this.bowGoal;
    }


    private static final EntityDataAccessor<String> DATA_ANIM_KEY =
            SynchedEntityData.defineId(BossPhantomHero.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> DATA_ANIM_SEQ =
            SynchedEntityData.defineId(BossPhantomHero.class, EntityDataSerializers.INT);

    /**
     * 当前动画要播多少 tick。
     *
     * 必须和服务端播放动画时一起下发：铁魔法的 continuous_thrust_one_handed /
     * long_cast 是「循环动画」，KeyframeAnimationPlayer 对循环动画的 isActive()
     * 永远返回 true，客户端模型没法靠它判断结束 —— 播完就再也停不下来，
     * 手一直举着。所以改成由服务端告诉客户端「播够这么多 tick 就停」。
     * <= 0 表示不限制，靠动画自身结束。
     */
    private static final EntityDataAccessor<Integer> DATA_ANIM_DURATION =
            SynchedEntityData.defineId(BossPhantomHero.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Byte> DATA_WEAPON =
            SynchedEntityData.defineId(BossPhantomHero.class, EntityDataSerializers.BYTE);

    /**
     * 当前阶段（1 / 2）。
     *
     * 只用来让客户端知道「该按哪个阶段渲染」；
     * 血量门槛在服务端自己维护，不需要同步。
     */
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(BossPhantomHero.class, EntityDataSerializers.INT);

    /**
     * 副手现在有没有盾（用于客户端渲染与格挡表现）。
     *
     * 为什么不能直接看 getOffhandItem()：
     *   非玩家实体的手持物品同步本来就有时序问题 ——
     *   服务端刚把盾塞进副手时，客户端可能还晚一两 tick 才收到更新，
     *   这期间客户端手上是空的（渲染不出盾），但服务端已经开始格挡，
     *   就会出现「盾牌渲染没了，打她却还被格挡」。
     *
     * 所以盾的存在与否单独用一个 byte 同步，客户端渲染和格挡表现
     * 全部读这个字段，和服务端的判定同源、同帧。
     */
    private static final EntityDataAccessor<Byte> DATA_SHIELD =
            SynchedEntityData.defineId(BossPhantomHero.class, EntityDataSerializers.BYTE);

    public BossPhantomHero(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);

        // 默认手持弓，走 Alex 那套普攻弓箭 AI
        this.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.BOW)
        );
    }

    // ---------------------------------------------------------------
    // 动画同步
    // ---------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        this.entityData.define(DATA_ANIM_KEY, "");
        this.entityData.define(DATA_ANIM_SEQ, 0);
        this.entityData.define(DATA_ANIM_DURATION, 0);
        this.entityData.define(DATA_WEAPON, (byte) WEAPON_BOW);
        this.entityData.define(DATA_PHASE, 1);
        this.entityData.define(DATA_SHIELD, (byte) 0);
    }

    // ---------------------------------------------------------------
    // 手持武器切换
    // ---------------------------------------------------------------

    /**
     * 切换手持武器。
     *
     * 原版 LivingEntity 不会同步生物的手持物品，
     * 所以这里自己用一个 byte 同步状态：
     *   服务端直接换 ItemStack，
     *   客户端在 onSyncedDataUpdated 里换同一个 ItemStack，
     * 两边一致，渲染层才看得到剑/弓。
     *
     * 物理三连击 -> 钻石剑，射箭 -> 弓，施法 -> 空手。
     */
    public void setWeapon(int weapon) {

        if (this.level().isClientSide) {
            return;
        }

        byte value = (byte) weapon;

        if (this.entityData.get(DATA_WEAPON) == value) {
            return;
        }

        this.entityData.set(DATA_WEAPON, value);

        applyWeaponItem(weapon);
    }

    private void applyWeaponItem(int weapon) {

        ItemStack mainHand = switch (weapon) {
            case WEAPON_SWORD -> new ItemStack(Items.DIAMOND_SWORD);
            case WEAPON_BOW -> new ItemStack(Items.BOW);
            case WEAPON_SHIELD -> new ItemStack(Items.DIAMOND_SWORD);
            case WEAPON_SNIPER -> createSniperStack();
            default -> ItemStack.EMPTY;
        };

        this.setItemInHand(InteractionHand.MAIN_HAND, mainHand);

        /*
         * 副手放什么。
         *
         * 一阶段：只有剑盾状态才拿盾，其它武器一律空手。
         *
         * 二阶段：盾是「全程常驻」的 —— 不论手里是剑 / 弓 / 枪 / 空手，
         * 副手都挂着盾（原版单手武器本来就能同时举盾）。
         * 唯一会让它消失的情况是「盾被打碎」，那时要等 5 秒修复。
         */
        if (isPhaseTwo()) {

            // 二阶段：有盾就留，没盾（碎了）就空着等修复
            if (this.isShieldEquipped()) {
                this.setOffhandShield(this.getOffhandItem());
            }

            return;
        }

        if (weapon == WEAPON_SHIELD) {
            this.setOffhandShield(new ItemStack(Items.SHIELD));
        } else {
            this.setOffhandShield(ItemStack.EMPTY);
        }
    }

    /**
     * 设置副手的盾（或者清空），并把状态同步给客户端。
     *
     * 副手物品 + {@link #DATA_SHIELD} 一起改，保证客户端渲染、
     * 客户端格挡表现、服务端格挡判定三者永远一致。
     */
    private void setOffhandShield(ItemStack stack) {

        this.setItemInHand(InteractionHand.OFF_HAND, stack);

        boolean has = stack.canPerformAction(ToolActions.SHIELD_BLOCK);

        this.entityData.set(DATA_SHIELD, (byte) (has ? 1 : 0));
    }

    /**
     * 副手上现在有没有盾（服务端看真实物品，客户端看同步字段）。
     *
     * 客户端必须读 {@link #DATA_SHIELD}：手持物品的同步有时序差，
     * 光看 getOffhandItem() 会出现「渲染已经没盾、判定还在格挡」。
     *
     * 服务端也优先读同步字段：它和客户端是同一个来源，能保证
     * 「渲染有盾」和「判定有盾」永远一致（副手物品本身只在
     * {@link #setOffhandShield(ItemStack)} / 盾碎时被改动，都是成对更新的）。
     */
    public boolean hasShield() {
        return this.entityData.get(DATA_SHIELD) != 0;
    }

    /**
     * 服务端视角：副手物品本身是不是盾。
     *
     * 注意：用物品判断而不是 durability —— 盾被打碎时物品会直接消失，
     * 所以「有物品 = 有盾」是成立的。
     */
    private boolean isShieldEquipped() {
        return this.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * 狙击枪的物品由 TACZ 提供，没装 TACZ 时返回空手。
     */
    private ItemStack createSniperStack() {

        if (!PhantomTacGun.isAvailable()) {
            return ItemStack.EMPTY;
        }

        return PhantomTacGun.createSniperStack();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {

        super.onSyncedDataUpdated(key);

        /*
         * 客户端只跟着同步状态走，不自己判定副手该放什么。
         *
         * 之前这里会调 applyWeaponItem()，而它内部要读副手物品来判断，
         * 可客户端那时候物品还没同步过来 —— 结果就是
         * 「服务端把盾塞好了，客户端一算又把它清掉了」，
         * 表现为盾牌渲染消失、但服务端照常格挡。
         *
         * 现在改成：主手物品按武器状态换，副手盾完全由 DATA_SHIELD 说了算。
         */
        if (!this.level().isClientSide) {
            return;
        }

        if (DATA_WEAPON.equals(key)) {
            applyClientMainHand(this.entityData.get(DATA_WEAPON));
        }

        if (DATA_SHIELD.equals(key)) {
            applyClientShield(this.entityData.get(DATA_SHIELD) != 0);
        }
    }

    /**
     * 客户端：按武器状态换主手物品。
     *
     * 和 {@link #applyWeaponItem(int)} 的区别是「只碰主手」，
     * 副手交给 {@link #applyClientShield(boolean)}，
     * 免得客户端读到还没同步好的副手物品做出错误判断。
     */
    private void applyClientMainHand(int weapon) {

        ItemStack mainHand = switch (weapon) {
            case WEAPON_SWORD -> new ItemStack(Items.DIAMOND_SWORD);
            case WEAPON_BOW -> new ItemStack(Items.BOW);
            case WEAPON_SHIELD -> new ItemStack(Items.DIAMOND_SWORD);
            case WEAPON_SNIPER -> createSniperStack();
            default -> ItemStack.EMPTY;
        };

        this.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
    }

    /**
     * 客户端：按同步字段放 / 收副手的盾。
     */
    private void applyClientShield(boolean present) {

        boolean currentlyShield = this.getOffhandItem()
                .canPerformAction(ToolActions.SHIELD_BLOCK);

        // 已经在正确状态就不用动，避免每帧换物品导致耐久被重置
        if (present == currentlyShield) {
            return;
        }

        this.setItemInHand(
                InteractionHand.OFF_HAND,
                present ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY
        );
    }

    /**
     * 服务端调用：让客户端播放指定动画。
     *
     * 每次都把 seq + 1，保证即使连续播放同一个动画，
     * SynchedEntityData 也会判定为「值变了」并同步下去。
     */
    public void playSpellAnimation(ResourceLocation animation) {
        playSpellAnimation(animation, 0);
    }

    /**
     * 服务端调用：让客户端播放指定动画。
     *
     * 每次都把 seq + 1，保证即使连续播放同一个动画，
     * SynchedEntityData 也会判定为「值变了」并同步下去。
     *
     * @param durationTicks 这段动画要播多少 tick，<= 0 表示靠动画自己结束。
     *                      循环动画（continuous_thrust_one_handed / long_cast）
     *                      必须给一个正数，否则会永远播下去。
     */
    public void playSpellAnimation(ResourceLocation animation, int durationTicks) {

        if (this.level().isClientSide) {
            return;
        }

        int seq = this.entityData.get(DATA_ANIM_SEQ) + 1;

        if (seq > 1_000_000) {
            seq = 1;
        }

        // 时长必须比序号先写：客户端是靠序号变化才去读时长的，
        // 反过来的话会读到上一段动画的时长
        this.entityData.set(
                DATA_ANIM_DURATION,
                Math.max(0, durationTicks)
        );

        this.entityData.set(
                DATA_ANIM_KEY,
                animation == null ? "" : animation.toString()
        );

        this.entityData.set(DATA_ANIM_SEQ, seq);
    }

    /**
     * 停止动画（客户端模型收到空 key 后会把当前动画清掉）。
     */
    public void clearSpellAnimation() {
        playSpellAnimation(null);
    }

    public int getSpellAnimSeq() {
        return this.entityData.get(DATA_ANIM_SEQ);
    }

    public String getSpellAnimKey() {
        return this.entityData.get(DATA_ANIM_KEY);
    }

    /**
     * 当前动画要播的 tick 数，<= 0 表示不限制。
     */
    public int getSpellAnimDuration() {
        return this.entityData.get(DATA_ANIM_DURATION);
    }

    // ---------------------------------------------------------------
    // 基础
    // ---------------------------------------------------------------

    @Override
    public ResourceLocation getSkinTexture() {
        return ResourceLocation.fromNamespaceAndPath(
                "fast",
                "textures/entity/phantom_hero.png"
        );
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(
                0,
                new FloatGoal(this)
        );

        /*
         * 二阶段随机选招调度，优先级压在三连击之上。
         *
         * 它自己不占 MOVE / LOOK，所以不会真的「抢走」技能的控制权，
         * 只是在合适的时机把某个技能强制起手。
         */
        PhantomHeroPhaseTwoGoal phaseTwoGoal = new PhantomHeroPhaseTwoGoal(this);

        this.goalSelector.addGoal(
                1,
                phaseTwoGoal
        );
        // 三连击：近身物理 / 玩家远离时的远程魔法
        PhantomHeroComboGoal comboGoal = new PhantomHeroComboGoal(this);

        this.goalSelector.addGoal(
                2,
                comboGoal
        );

        // AWM 狙击：远距离惩罚，前摇很长
        PhantomHeroSniperGoal sniperGoal = PhantomTacGun.isAvailable()
                ? new PhantomHeroSniperGoal(this)
                : null;

        if (sniperGoal != null) {
            this.goalSelector.addGoal(
                    3,
                    sniperGoal
            );
        }

        // 剑盾突进：贴身肉搏状态，持续一段时间后进冷却
        // （二阶段它是常驻的，不看冷却）
        this.goalSelector.addGoal(
                4,
                new PhantomHeroShieldRushGoal(this)
        );

        // 远程普攻弓箭：只在玩家主动远离时触发
        PhantomHeroBowAttackGoal bowGoal =
                new PhantomHeroBowAttackGoal(this, 16);

        this.goalSelector.addGoal(
                5,
                bowGoal
        );

        // 把实例存下来，二阶段选招要靠它们强制起手
        setPhaseTwoGoals(comboGoal, sniperGoal, bowGoal, phaseTwoGoal);

        // 默认行为：主动走到玩家面前抡近战
        /*
         * 速度倍率是乘在 MOVEMENT_SPEED 属性上的
         * （MoveControl#tick 里 speed = speedModifier * 属性值），
         * 所以这里给 1.0 = 正常速度，原版 MeleeAttackGoal 也是这么用的。
         */
        this.goalSelector.addGoal(
                6,
                new PhantomHeroApproachGoal(this, 1.0D)
        );

        this.goalSelector.addGoal(
                7,
                new WaterAvoidingRandomStrollGoal(this, 0.8D)
        );

        this.goalSelector.addGoal(
                8,
                new LookAtPlayerGoal(this, Player.class, 8.0F)
        );

        this.goalSelector.addGoal(
                9,
                new RandomLookAroundGoal(this)
        );

        this.targetSelector.addGoal(
                1,
                new HurtByTargetGoal(this)
        );

        this.targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(this, Player.class, true)
        );
    }

    @Override
    public void tick() {
        super.tick();

        if (comboCooldown > 0) {
            comboCooldown--;
        }

        if (sniperCooldown > 0) {
            sniperCooldown--;
        }

        if (shieldRushCooldown > 0) {
            shieldRushCooldown--;
        }

        // 被投掷物打中时临时举盾的剩余时间
        if (passiveUseShieldTick > 0) {

            passiveUseShieldTick--;

            if (passiveUseShieldTick == 0 && !isSwordState()) {
                this.stopUsingItem();
            }
        }

        if (!this.level().isClientSide) {
            updateFleeState();
            tickPhase();
            tickShieldMaintenance();
        }
    }

    // ---------------------------------------------------------------
    // 盾牌
    // ---------------------------------------------------------------

    /**
     * 被投掷物命中时临时举盾的剩余 tick。
     *
     * 学自女仆的 EntityMaid#hurt：吃掉一发投掷物后就举一会儿盾，
     * 免得被远处放风筝放死。
     */
    private int passiveUseShieldTick = 0;

    private static final int PASSIVE_SHIELD_TICKS = 20 * 3;

    /**
     * 现在能不能举盾。
     *
     * 和女仆 EntityMaid#canUseShield 一样，用 ToolActions.SHIELD_BLOCK 判断，
     * 这样任何模组加的盾都能用。
     *
     * 注：女仆那边还额外检查了 getCooldowns()，但那是 Player 才有的 API，
     * 生物拿不到，所以这里只看副手物品本身。
     */
    public boolean canUseShield() {
        return this.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * 现在盾是不是「举起来」的。
     *
     * 二阶段：盾全程举着 —— 只要是二阶段且副手有盾，就一直在格挡，
     *         不看 isUsingItem（Boss 要一边施法 / 射箭一边保持格挡）。
     * 一阶段：只要副手挂着盾就算在格挡（Boss 不像玩家那样分举/收盾两套动作）。
     */
    public boolean isShieldBlockingState() {

        /*
         * 只有「副手真的挂着盾」才算在格挡。
         *
         * 一二阶段都不再看 isUsingItem：
         *   - 二阶段盾本来就是全程举着的；
         *   - 一阶段的盾是随时备着的（挨打才会真的触发格挡），
         *     Boss 不像玩家那样有「举盾/收盾」两套动作，
         *     用 useItem 判断反而会因为 5 tick 门槛（MC-100949）
         *     和客户端 useItem 不同步而漏挡。
         */
        return this.hasShield();
    }

    /**
     * 现在在主手拿剑（近战）姿势。
     */
    public boolean isSwordState() {
        return this.entityData.get(DATA_WEAPON) == (byte) WEAPON_SHIELD;
    }

    /**
     * 格挡判定的正面范围（半角，度）。
     *
     * 原版要求攻击源严格落在视线正前方（±90 度）。
     * Boss 是 AI，不会像玩家那样主动把盾怼到刀口上，
     * 站偏一点就完全挡不住的话，观感就是「明明举着盾还被打飞」。
     * 这里放宽到 110 度，背后（超过 110 度）依然挡不住，保留绕背破盾的玩法。
     */
    private static final double SHIELD_BLOCK_ARC_DEGREES = 110.0D;

    /**
     * 伤害处理。
     *
     * 相比原版多了两件事：
     *   1. 被投掷物打中时被动举盾（女仆 EntityMaid#hurt 同款，仅一阶段需要）；
     *   2. 显式接管「举盾格挡」的结算（原版那条路对非玩家实体走不通，
     *      详见 {@link #isShieldBlocking(DamageSource)}）。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {

        if (this.level().isClientSide) {
            return false;
        }

        // 和原版一致：无敌 / 已死的时候直接不吃伤害，也不该走格挡结算
        if (this.isInvulnerableTo(source) || this.isDeadOrDying()) {
            return false;
        }

        // 全局兜底：Boss 不允许被任何东西打死（掉进虚空也救回来）
        // 这个是 Boss 的「底线保障」，和半血转化是两回事，别混在一起
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            this.setHealth(Math.max(1.0F, this.getHealth() - amount));
            return false;
        }

        /*
         * 被投掷物打中就举一会儿盾，免得被远处放风筝放死。
         *
         * 只在一阶段补这个动作：二阶段的盾本来就全程举着（见
         * {@link #isShieldBlockingState()}），不需要也不该在这里动
         * useItem —— 一旦在二阶段调 startUsingItem，客户端那边
         * 「有没有在举盾」又会跟着 useItem 抖一次，正是盾牌渲染乱跳的来源。
         *
         * 注意这段要在格挡判定之前：先把手举起来，这一次投掷物才挡得住。
         */
        if (!this.isPhaseTwo()
                && source.is(DamageTypeTags.IS_PROJECTILE)
                && this.canUseShield()) {

            boolean alreadyBlocking = this.isUsingItem()
                    && this.getUsedItemHand() == InteractionHand.OFF_HAND;

            if (!alreadyBlocking) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }

            passiveUseShieldTick = PASSIVE_SHIELD_TICKS;
        }

        /*
         * 举盾格挡结算 —— 放在半血门槛**之前**。
         *
         * 顺序很关键：被盾挡下来的这一下不该推进阶段。
         * 如果先算门槛，一次「本该被挡住」的重击也会把 Boss 打进二阶段，
         * 玩家就会看到「明明挡了，Boss 还是变身了」。
         *
         * 原版 LivingEntity#hurt 里那段格挡分支对非玩家实体是不可靠的：
         *   - 原版 isBlocking() 要求盾「举起满 5 tick」才算数（MC-100949），
         *     Boss 常常是在挨打的前一瞬才切进剑盾状态，永远差那 5 tick；
         *   - 原版成功格挡时的 item.shield.block 音效属于玩家链路，
         *     女仆模组也是自己补的（EntityMaid#hurtCurrentlyUsedShield）。
         * 所以这里直接判定并结算：免伤 + 免击退 + 出音效 + 扣盾耐久。
         *
         * 注：返回 false 就等于「这一下完全无效」，和被盾挡掉是一致的，
         *     击退、着火、凋零之类附带效果也随之全部失效。
         */
        if (this.isShieldBlocking(source)) {

            // 扣盾耐久 + 播格挡音效（见 hurtCurrentlyUsedShield）
            this.hurtCurrentlyUsedShield(amount);

            // 原版格挡成功后的收尾：交给攻击者自己处理（斧头破盾等）
            Entity attacker = source.getDirectEntity();

            if (attacker instanceof LivingEntity livingAttacker) {
                this.blockUsingShield(livingAttacker);
            }

            return false;
        }

        /*
         * 半血门槛。
         *
         * 这一下如果会打过半血线，就进入转化阶段 —— 注意伤害**照常全额结算**，
         * 不是把它变成无敌，血线由 enterPhaseTwo 精确锁在 50%，
         * 所以玩家这一下依然看得到伤害数字、吃得到命中反馈。
         */
        amount = clampToPhaseThreshold(source, amount);

        return super.hurt(source, amount);
    }

    /**
     * 这一下伤害算不算被盾挡住。
     *
     * 二阶段不看 isUsingItem（盾全程举着），其它条件照旧。
     *
     * @see #SHIELD_BLOCK_ARC_DEGREES 为什么比原版宽松
     */
    private boolean isShieldBlocking(DamageSource source) {

        if (!this.isShieldBlockingState()) {
            return false;
        }

        // 无视护甲的伤害（虚空、/kill 之类）不是盾能挡的东西
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return false;
        }

        // 穿透附魔的箭无视盾牌，和原版一致
        if (source.getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getPierceLevel() > 0) {
            return false;
        }

        Vec3 sourcePos = source.getSourcePosition();

        // 没有来源位置（摔落、中毒、着火）同样不是盾能挡的
        if (sourcePos == null) {
            return false;
        }

        Vec3 look = this.getViewVector(1.0F);

        Vec3 lookH = new Vec3(look.x, 0.0D, look.z);
        Vec3 toSourceH = new Vec3(
                sourcePos.x - this.getX(),
                0.0D,
                sourcePos.z - this.getZ()
        );

        // 贴脸（攻击源和自己基本同一格）没有方向可言，直接算挡住
        if (lookH.lengthSqr() < 1.0E-6D || toSourceH.lengthSqr() < 1.0E-6D) {
            return true;
        }

        double dot = toSourceH.normalize().dot(lookH.normalize());

        return dot >= Math.cos(Math.toRadians(SHIELD_BLOCK_ARC_DEGREES));
    }

    /**
     * 盾被击中：扣耐久 + 出音效。
     *
     * 学自女仆模组 {@code EntityMaid#hurtCurrentlyUsedShield}：
     * 原版这里只处理「盾被打碎」的 SHIELD_BREAK 音效，
     * 而格挡成功的 item.shield.block 是玩家链路专属的，
     * 非玩家实体必须自己补，否则举盾挡下来一点声音都没有。
     *
     * 这里直接操作副手物品（而不是 useItem）：
     * 二阶段的盾是全程举着的，并没有真的走 startUsingItem，
     * useItem 会是空的，靠它判断会连盾都扣不掉。
     */
    @Override
    protected void hurtCurrentlyUsedShield(float damage) {

        ItemStack shield = this.getOffhandItem();

        if (!shield.canPerformAction(ToolActions.SHIELD_BLOCK)) {
            return;
        }

        if (damage >= 3.0F) {

            int amount = 1 + Mth.floor(damage);

            shield.hurtAndBreak(amount, this, entity -> {

                // 盾碎了：立刻同步给客户端（渲染和判定一起消失）
                entity.setOffhandShield(ItemStack.EMPTY);

                if (entity.isUsingItem()) {
                    entity.stopUsingItem();
                }
            });

            // hurtAndBreak 之后物品可能已经空了
            if (this.getOffhandItem().isEmpty()) {

                this.setOffhandShield(ItemStack.EMPTY);

                this.playSound(
                        SoundEvents.SHIELD_BREAK,
                        0.8F,
                        0.8F + this.level().random.nextFloat() * 0.4F
                );

                return;
            }
        }

        // 格挡成功的声音：原版对非玩家实体不会播，这里补上
        this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.0F);
    }

    /**
     * 客户端渲染（PhantomHeroModel 的 leftArmPose）会读这个方法来决定要不要摆举盾姿势。
     *
     * 这里**不再**自己拼条件，而是直接复用 {@link #isShieldBlockingState()}：
     * 客户端读的是同步字段 {@link #DATA_SHIELD}，服务端读的是真实副手物品，
     * 两边判定同源，就不会再出现「客户端认为没在举盾、服务端照样格挡」。
     *
     * 顺带去掉原版的「举起满 5 tick 才生效」限制（女仆 EntityMaid#isBlocking 同款）。
     */
    @Override
    public boolean isBlocking() {
        return this.isShieldBlockingState();
    }


    // ---------------------------------------------------------------
    // 二阶段（半血转化）
    // ---------------------------------------------------------------

    /**
     * 现在是不是二阶段。
     */
    public boolean isPhaseTwo() {
        return this.phase >= 2;
    }

    /**
     * 客户端可见的阶段号。
     */
    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    /**
     * 半血转化 —— 伤害结算前的门槛。
     *
     * 规则：如果这一下会把血量打到半血以下，
     *   - 先立刻进入转化阶段；
     *   - 伤害**照常全额结算**（不削、不减免、不无敌）。
     *
     * 这么做的原因：
     *   转化进入的那一瞬间会清空一阶段残留状态、把武器切回枪 / 弓 / 空手。
     *   一旦把伤害削到「正好半血」，Boss 会掉到 50% 并且完全不吃这一下，
     *   玩家看到的就是「打上去没伤害」= 无敌，观感很差。
     *   现在的口径是「让这一下成为转化触发器，而不是再走一遍伤害结算」，
     *   伤害数字和击退都照常，血线则通过 {@link #enterPhaseTwo()} 里的
     *   {@code setHealth(max(血线, 当前血 - 溢出))} 精确锁在 50%。
     *
     * 另外这里**不再要求 {@code transitionTicks <= 0}**：
     * 转化演出有 3 秒，如果演出期间玩家继续输出、血被打断到 50% 以下，
     * 旧写法会连血量都不拦（只有 phase 判定），Boss 就能在演出里被打死。
     *
     * @return 真正应该结算的伤害量；只有 {@code BYPASSES_INVULNERABILITY}
     *         这类特殊伤害会被放回来跟 {@code /kill}、虚空伤害一致处理
     */
    private float clampToPhaseThreshold(DamageSource source, float amount) {

        // 已经转化过（含转化演出期间），门槛不再生效
        if (this.isPhaseTwo()) {
            return amount;
        }

        // 无视无敌帧的特殊伤害不参与门槛
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }

        float threshold = this.getMaxHealth() * PHASE_TWO_HEALTH_RATIO;

        // 没打过半血线就照常结算
        if (this.getHealth() - amount >= threshold) {
            return amount;
        }

        // 触发转化。血线由 enterPhaseTwo(source, amount) 精确锁在半血
        enterPhaseTwo(source, amount);

        return amount;
    }

    /**
     * 进入转化阶段（不带伤害参数，仅演出用）。
     */
    public void enterPhaseTwo() {
        enterPhaseTwo(null, 0.0F);
    }

    /**
     * 进入转化阶段。
     *
     * 服务端调用一次，之后 {@link #tickPhase()} 负责推进演出，
     * {@link #tickShieldMaintenance()} 负责常驻盾与修复。
     *
     * @param source 触发转化的那次伤害来源，可为 null（演出调用）
     * @param amount 触发转化的那次伤害量，用来精确锁定血线；<= 0 表示不锁
     */
    public void enterPhaseTwo(DamageSource source, float amount) {

        if (this.isPhaseTwo() || this.level().isClientSide) {
            return;
        }

        this.phase = 2;
        this.entityData.set(DATA_PHASE, 2);

        this.transitionTicks = TRANSITION_TICKS;

        /*
         * 血线精确锁在 50%。
         *
         * 和玩家手里的武器 / 难度 / 主手物品都不挂钩：
         * 结算后的血量不允许低于 maxHealth * 0.5。
         * 若这次伤害没打过半血线（比如被别的机制提前触发），就只是补齐到半血。
         */
        float threshold = this.getMaxHealth() * PHASE_TWO_HEALTH_RATIO;

        if (amount > 0.0F) {

            float after = this.getHealth() - amount;

            // 溢出伤害部分全部作废，只扣到半血为止
            this.setHealth(Math.max(threshold, after));

        } else if (this.getHealth() > threshold) {

            // 演出触发（没有伤害来源）时，直接把血补到半血，观感更直观
            this.setHealth(threshold);
        }

        // 转化期间不接受任何攻击 goal 起手
        this.attackSelection = false;
        this.attackSelectionTick = 0;
        this.pendingAttacks = 0;

        // 盾牌先给满耐久
        this.shieldBreakCooldown = 0;

        // 清掉一阶段残留的技能状态
        this.getNavigation().stop();
        this.clearSpellAnimation();
        this.comboCooldown = 0;
        this.sniperCooldown = 0;
        this.shieldRushCooldown = 0;

        // 把一阶段掉在手上的武器实体清掉，免得转化瞬间地上多出一堆垃圾
        discardDroppedWeapons();

        // 转化演出：常驻剑盾 + 举盾，站定
        /*
         * 先手动发一面满耐久的盾。
         *
         * 不能指望 setWeapon(WEAPON_SHIELD) 去补 —— 二阶段下
         * applyWeaponItem 对「没有盾」的处理是「保持空着等修复」，
         * 免得每次回剑盾姿势都白送一面新盾，把修复机制架空。
         * 但转化这一刻是要给盾的，所以在这里显式补上。
         *
         * 必须走 setOffhandShield 而不是裸的 setItemInHand：
         * 后者只改服务端物品，DATA_SHIELD 不会跟着变，
         * 客户端就渲染不出这面盾（表现为「看不见盾但打上去被挡」）。
         */
        this.setOffhandShield(new ItemStack(Items.SHIELD));

        this.setWeapon(WEAPON_SHIELD);

        if (!this.isUsingItem()) {
            this.startUsingItem(InteractionHand.OFF_HAND);
        }

        // 转化时来一下「觉醒」的音效
        this.playSound(
                SoundEvents.TOTEM_USE,
                1.2F,
                0.8F
        );
    }

    /**
     * 清掉自己脚下刚掉落的手持武器实体。
     *
     * {@code setItemInHand} 换武器时，原版会把旧物品丢到地上。
     * 转化一瞬间要连换好几次武器（枪 / 弓 / 剑），地上就会堆一排掉落物，
     * 既卡观感也可能被玩家捡走。这里把它们收掉。
     */
    private void discardDroppedWeapons() {

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                this.getBoundingBox().inflate(3.0D, 2.0D, 3.0D),
                item -> true
        ).forEach(ItemEntity::discard);
    }

    /** 选招计时 */
    public int attackSelectionTick = 0;

    /**
     * 每 tick 推进转化演出 / 选招 / 轮次间隔。
     */
    private void tickPhase() {

        // ===== 转化演出 =====
        if (this.transitionTicks > 0) {

            this.transitionTicks--;

            this.getNavigation().stop();

            // 演出期间一直举着盾
            if (!this.isUsingItem() && this.canUseShield()) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }

            if (this.transitionTicks == 0) {
                this.attackSelection = true;
                this.pendingAttacks = ATTACKS_PER_PHASE_TWO_ROUND;
            }

            return;
        }

        // ===== 选招空档 =====
        if (this.attackSelection) {

            this.attackSelectionTick++;

            if (this.attackSelectionTick >= SELECTION_TICKS) {
                this.attackSelectionTick = 0;
                this.attackSelection = false;
            }
        }

        /*
         * ===== 轮次间隔 =====
         *
         * 本轮两个技能放完（pendingAttacks 归零）之后，
         * 由这里计时到点补满配额，开启下一轮随机 2 技能。
         *
         * 放在 tickPhase 里而不是 PhaseTwoGoal#tick 里：
         * 配额归零后 canContinueToUse 返回 false，GoalSelector
         * 不再 tick 那个 goal，计时根本走不动。
         */
        if (this.phaseTwoGoal != null) {
            this.phaseTwoGoal.tickRoundCooldown();
        }
    }

    /**
     * 二阶段常驻盾的维护：盾碎了就过 5 秒补一面新的。
     *
     * 二阶段的盾是常驻装备（{@link #applyWeaponItem} 里换武器也不会丢），
     * 所以这里不关心当前拿的是什么武器 —— 只看副手有没有盾。
     * 这样在放技能的间隙里倒计时也照常走，不会因为换了个武器就重置。
     */
    private void tickShieldMaintenance() {

        if (!this.isPhaseTwo() || this.level().isClientSide) {
            this.shieldBreakCooldown = 0;
            return;
        }

        // 副手还有盾，倒计时归零
        if (this.isShieldEquipped()) {
            this.shieldBreakCooldown = 0;
            return;
        }

        // 盾没了，开始（或继续）倒计时
        if (this.shieldBreakCooldown <= 0) {
            this.shieldBreakCooldown = SHIELD_REGEN_TICKS;
            return;
        }

        this.shieldBreakCooldown--;

        if (this.shieldBreakCooldown <= 0) {

            // 5 秒到，补一面新盾（走 setOffhandShield 才会同步给客户端渲染）
            this.setOffhandShield(new ItemStack(Items.SHIELD));

            // 只有回到剑盾姿势时才顺手把盾举起来；
            // 正在施法 / 射箭 / 开枪的时候就先塞在副手，等回剑盾再举
            if (this.entityData.get(DATA_WEAPON) == (byte) WEAPON_SHIELD
                    && !this.isUsingItem()) {
                this.startUsingItem(InteractionHand.OFF_HAND);
            }

            this.playSound(
                    SoundEvents.ANVIL_USE,
                    0.6F,
                    1.6F
            );
        }
    }

    /**
     * 回到二阶段的常驻剑盾姿势。
     *
     * 抽招空档、技能间隙里调用，保证 Boss 手上一直看得见剑和盾。
     *
     * 注意这里**不补盾**：盾碎了要老老实实等
     * {@link #SHIELD_REGEN_TICKS} 走完（见 {@link #tickShieldMaintenance()}）。
     * 否则每次回到剑盾姿势都会立刻发一面新盾，「盾碎 5 秒修复」就成了摆设。
     */
    public void restoreShieldStance() {

        if (!this.isPhaseTwo() || this.level().isClientSide) {
            return;
        }

        /*
         * 主手补剑。
         *
         * 直接改物品而不是 setWeapon()：setWeapon 会连副手一起重置
         * （见 applyWeaponItem），那样又会把盾的耐久重置掉。
         */
        if (!(this.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem)) {
            this.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(Items.DIAMOND_SWORD)
            );
        }

        // 副手有盾就举起来；没盾（已碎）就空着等修复
        if (this.canUseShield()
                && !this.isUsingItem()) {
            this.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    // ---------------------------------------------------------------
    // 「玩家是不是在主动拉开距离」
    // ---------------------------------------------------------------

    /**
     * 每 tick 比对一次和目标的距离，判断玩家是不是在主动远离。
     *
     * 只比对距离的变化量，不看距离本身：
     * 玩家站得远但不动，Boss 会自己走过去（近战是默认手段）；
     * 只有玩家真的在跑，距离持续变大，才切到远程手段。
     */
    private void updateFleeState() {

        LivingEntity target = this.getTarget();

        if (target == null || !target.isAlive()) {

            lastTargetDistance = -1.0D;
            fleeTicks = 0;

            return;
        }

        double distance = this.distanceTo(target);

        if (lastTargetDistance >= 0.0D) {

            if (distance - lastTargetDistance > FLEE_DELTA_PER_TICK) {

                // 玩家在拉开距离：远程模式快速进入，并保留一小段时间
                fleeTicks = Math.min(fleeTicks + 2, FLEE_HOLD_MAX);

            } else if (fleeTicks > 0) {

                // 没在拉开：慢慢退出远程模式
                fleeTicks--;
            }
        }

        lastTargetDistance = distance;
    }

    /**
     * 目标是否正在主动拉开距离。
     *
     * 远程手段（魔法三连击 / 弓箭）只在这个为 true 时才允许触发。
     */
    public boolean isTargetFleeing() {
        return fleeTicks > 0;
    }

    /**
     * 技能期间强制看向目标。
     *
     * 三连击的伤害判定是「身前 60 度锥形」，
     * 魔法三连击的弹射物是沿 getLookAngle() 发射，
     * 所以朝向必须准确，不能只依赖 LookControl 的平滑插值。
     */
    public void forceLookAtTarget(LivingEntity target) {
        if (target == null) {
            return;
        }

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getEyeY() - this.getEyeY();

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * (180F / (float) Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180F / (float) Math.PI)));

        this.setYRot(yaw % 360.0F);
        this.setXRot(pitch % 360.0F);
        this.setYHeadRot(yaw % 360.0F);
    }

    /**
     * 固定用右手。
     *
     * Mob#getMainArm() 的实现是 isLeftHanded() ? LEFT : RIGHT，
     * 而生物出生时有概率被随机成左撇子（LeftHanded 数据位）。
     * 一旦是左撇子，ItemInHandLayer 就会把主手物品渲染到左手，
     * 所以这里直接钉死成右手。
     */
    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    @Override
    public boolean isLeftHanded() {
        return false;
    }

    /**
     * 物理三连击的实际判定半径。
     *
     * TripleStrikeSpell 内部就是这么取范围的：
     *   range = 3，如果实体有 ENTITY_REACH 属性且 > 0 就用属性值。
     * 这里用同一个来源，红圈提示才能和真实判定严丝合缝。
     */
    public float getMeleeReach() {

        net.minecraft.world.entity.ai.attributes.AttributeInstance attr =
                this.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());

        if (attr != null && attr.getValue() > 0) {
            return (float) attr.getValue();
        }

        return 3.0F;
    }

    /**
     * 普通近战攻击距离的平方 —— 和原版僵尸完全同一个公式。
     *
     * 原版 {@link net.minecraft.world.entity.ai.goal.MeleeAttackGoal}：
     *
     * <pre>
     * (mob.getBbWidth() * 2) ^ 2 + target.getBbWidth()
     * </pre>
     *
     * 这是个「平方」值，直接和 {@code distanceToSqr} 比。
     * 本实体宽 0.6、玩家宽 0.6，算出来是 2.04，
     * 也就是中心到中心大约 1.43 格 —— 这就是「僵尸贴脸抡你」的手感，
     * 明显短于玩家 3.0 的攻击距离。
     *
     * 之前剑盾近战用的是硬编码的 3.0（中心距 3.0 格），
     * 足足是僵尸的两倍多，所以才会出现「它离我老远就能打我」。
     */
    public double getAttackReachSqr(LivingEntity target) {

        double width = this.getBbWidth() * 2.0D;

        return width * width + target.getBbWidth();
    }

    // ---------------------------------------------------------------
    // 普攻弓箭（与 Alex 完全一致）
    // ---------------------------------------------------------------

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {

        // 只在服务端生成箭
        if (this.level().isClientSide) {
            return;
        }

        double attackDamage =
                this.getAttribute(Attributes.ATTACK_DAMAGE).getValue();

        /*
         * 贴脸保护：
         * 玩家贴到 2 格以内时不再依赖箭的实体碰撞，直接造成伤害，
         * 避免出现「箭生成在身体内部 / 撞到自己 / delta 接近 0」导致的空射。
         */
        if (this.distanceToSqr(target) <= 2.0D * 2.0D) {

            target.hurt(
                    this.damageSources().mobAttack(this),
                    (float) attackDamage
            );

            return;
        }

        ItemStack ammo = new ItemStack(Items.ARROW);

        AbstractArrow arrow =
                ProjectileUtil.getMobArrow(this, ammo, 1.0F);

        Vec3 shooterPos = this.getEyePosition();
        Vec3 targetPos = target.getEyePosition();

        Vec3 delta = targetPos.subtract(shooterPos);

        if (delta.lengthSqr() < 0.0001D) {
            delta = this.getLookAngle();
        }

        Vec3 direction = delta.normalize();

        // 从眼睛前方一点生成，防止箭直接卡在脑袋里
        Vec3 spawnPos = shooterPos.add(direction.scale(0.1D));

        arrow.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        arrow.setBaseDamage(attackDamage);

        arrow.shoot(
                delta.x,
                delta.y,
                delta.z,
                2.0F,
                0.2F
        );

        this.level().addFreshEntity(arrow);
    }

    // ---------------------------------------------------------------
    // 属性
    // ---------------------------------------------------------------

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 800.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 26.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                // 物理三连击的判定半径就是读的这个属性
                // （TripleStrikeSpell 里 range = caster.getAttribute(ENTITY_REACH)），
                // 红圈提示也读同一个属性，保证提示和实际判定完全一致
                .add(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), 3.5D)
                // 附属 mod 的三连击伤害 = 攻击力 x 基础倍率，
                // 三连击的伤害来源就靠这几个属性
                .add(FastAttributeRegistry.MAGIC_ATTACK.get(), 18.0D)
                .add(FastAttributeRegistry.BASIC_PHYSICAL_DAMAGE.get(), 1.0D)
                .add(FastAttributeRegistry.BASIC_MAGIC_DAMAGE.get(), 1.0D);
    }
}
