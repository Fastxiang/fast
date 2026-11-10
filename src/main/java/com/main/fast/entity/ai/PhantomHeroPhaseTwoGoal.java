package com.main.fast.entity.ai;

import com.main.fast.compat.PhantomTacGun;
import com.main.fast.entity.BossPhantomHero;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 昔日勇者的幻影 - 二阶段随机选招调度。
 *
 * 一阶段的行为是「贴脸就复读物理三连击」，二阶段改掉这一点：
 *
 *   每隔一段时间，从所有技能里随机抽两个 -> 依次放完 -> 回到剑盾状态
 *
 * 这里的「所有技能」= 物理三连击 / 魔法三连击 / AWM 狙击 / 弓箭普攻。
 * 剑盾肉搏自己不在抽签池里 —— 它在二阶段本身就是常驻状态（半个常驻），
 * 所以两个技能放完就自然回落到它。
 *
 * 注意：二阶段**完全不看距离**（远近一视同仁）。
 * 抽到技能就把目标当作靶子打，该走过去就走过去 —— 走位由技能 goal 自己负责。
 *
 * 调度流程：
 *   1. 转化演出结束（{@link BossPhantomHero#transitionTicks} 归零）时
 *      Boss 会拿到 pendingAttacks = 2；
 *   2. 本 goal 每 tick 看有没有技能正在放（靠各自的冷却 / isUsingItem 判断）。
 *      空闲就抽一个技能、把它强制起手、然后立刻进入选招空档；
 *   3. 技能打完会扣掉一次 pendingAttacks 并清掉选招空档，
 *      本 goal 再抽第二个；
 *   4. 两个都放完，pendingAttacks 归零，本 goal 会补满配额并进入
 *      {@link #ROUND_INTERVAL} 的间隔，然后开启下一轮 —— 也就是
 *      「隔一段时间随机 2 个技能」的循环。
 */
public class PhantomHeroPhaseTwoGoal extends Goal {

    private final BossPhantomHero boss;

    /**
     * 抽招之后的等待时间。
     *
     * 技能 stop() 之后会有一段 {@link BossPhantomHero#SELECTION_TICKS}
     * 的选招空档，这里多等几 tick 是为了让「换武器 -> 举盾」看得清，
     * 不然技能和技能之间会显得太连贯、没有喘气的画面。
     */
    private static final int PICK_DELAY = 6;

    /**
     * 一轮打完（两个技能都放完）之后，回剑盾肉搏多久再开下一轮。
     *
     * 这就是「隔一段时间随机 2 个技能」的那个「一段时间」。
     * 这段时间里剑盾突进 / 追击可以自由发挥，让二阶段不至于变成
     * 「站桩读技能条」的死板循环。
     */
    private static final int ROUND_INTERVAL = 20 * 6;

    /** 每轮随机技能的个数 */
    private static final int ATTACKS_PER_ROUND =
            BossPhantomHero.ATTACKS_PER_PHASE_TWO_ROUND;

    /** 上一个技能放完之后要等多久才能抽下一个 */
    private int delay;

    /** 距离下一轮开始的剩余 tick */
    private int roundCooldown;

    public PhantomHeroPhaseTwoGoal(BossPhantomHero boss) {
        this.boss = boss;

        // 不抢 MOVE / LOOK：具体技能自己会带上这两个 flag，
        // 调度器跟着抢反而会在选招空档里把朝向控制顶掉。
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {

        if (!boss.isPhaseTwo()) {
            return false;
        }

        // 转化演出还没播完
        if (boss.transitionTicks > 0) {
            return false;
        }

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        /*
         * 配额已经发下去了，就交给下面的 tick 去抽招。
         *
         * 注意这里不再有距离判断 —— 二阶段远近一视同仁，
         * 全局周期性随机 2 个技能，抽到什么打什么。
         */
        return boss.pendingAttacks > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        delay = PICK_DELAY;
    }

    @Override
    public void stop() {

        delay = PICK_DELAY;

        /*
         * 本轮打完（配额归零）之后：
         *   1. 确认手上是剑盾姿势；
         *   2. 开始计下一轮的间隔。
         *
         * 配额由 tick() 里的 {@link #startNextRound()} 在间隔结束时重新补满，
         * 形成「剑盾打一会儿 -> 随机 2 个技能 -> 剑盾打一会儿」的循环。
         */
        if (boss.isPhaseTwo() && boss.pendingAttacks <= 0) {
            boss.restoreShieldStance();
        }
    }

    @Override
    public void tick() {

        LivingEntity target = boss.getTarget();

        if (target == null || !target.isAlive()) {
            return;
        }

        boss.getLookControl().setLookAt(target, 30.0F, 30.0F);

        /*
         * 配额归零 = 本轮技能已经打完，交由 tickRoundCooldown() 计时。
         */
        if (boss.pendingAttacks <= 0) {
            return;
        }

        /*
         * 有技能正在放就等着，不要打断。
         *
         * 判断依据是「占用中」的三条线索：
         *   - comboCooldown  三连击起手后立刻置位
         *   - sniperCooldown 狙击起手后立刻置位
         *   - isUsingItem    弓箭正在拉弓
         */
        if (boss.comboCooldown > 0
                || boss.sniperCooldown > 0
                || boss.isUsingItem()) {
            return;
        }

        // 技能刚打完的选招空档，让它过完
        if (boss.attackSelection) {
            return;
        }

        if (delay > 0) {
            delay--;
            return;
        }

        pickAndStart();
    }

    /**
     * 本轮技能都放完了 —— 由 {@link BossPhantomHero} 每 tick 调用，
     * 负责「隔一段时间」的那段计时，到点补满配额开下一轮。
     *
     * 单独一个方法而不是塞进 tick()，是因为配额归零之后
     * {@link #canContinueToUse()} 会立刻返回 false，GoalSelector
     * 就不再 tick 本 goal 了，计时根本走不动。
     *
     * @return 是否刚刚开了一轮新的
     */
    public boolean tickRoundCooldown() {

        // 转化演出期间不排下一轮
        if (!boss.isPhaseTwo() || boss.transitionTicks > 0) {
            roundCooldown = 0;
            return false;
        }

        /*
         * 本轮还在进行（手上还有技能没放完），不计时。
         *
         * 关键：这里**只是清零计时器**，不补配额 ——
         * 等配额真的归零（两个技能都放完）时，下一 tick 会走到下面
         * 「刚开始计时」的分支，把 roundCooldown 设成 ROUND_INTERVAL，
         * 于是剑盾肉搏会老老实实打满 6 秒，再开下一轮。
         */
        if (boss.pendingAttacks > 0) {
            roundCooldown = 0;
            return false;
        }

        // 本轮刚结束：开始计时（roundCooldown 从 0 起步）
        if (roundCooldown <= 0) {
            roundCooldown = ROUND_INTERVAL;
            return false;
        }

        roundCooldown--;

        if (roundCooldown > 0) {
            return false;
        }

        // 间隔走完：补满配额，开始下一轮随机 2 技能
        roundCooldown = ROUND_INTERVAL;
        boss.pendingAttacks = ATTACKS_PER_ROUND;
        boss.attackSelection = false;
        boss.attackSelectionTick = 0;

        return true;
    }

    /**
     * 从技能池里随机抽一个并强制起手。
     *
     * 抽到「起不来」的技能（比如没装 TACZ 时的狙击）就顺延到物理三连击，
     * 保证每次抽签都真能打出一个技能，不会空抽。
     */
    private void pickAndStart() {

        int pick = boss.getRandom().nextInt(4);

        boolean started = switch (pick) {

            // -------- 狙击（没装 TACZ 时回落到三连击） --------
            case 0 -> PhantomTacGun.isAvailable()
                    ? boss.getSniperGoal() != null && startSniper()
                    : startPhysics();

            // -------- 弓箭 --------
            case 1 -> startBow();

            // -------- 魔法三连击 --------
            case 2 -> startMagic();

            // -------- 物理三连击 --------
            default -> startPhysics();
        };

        // 兜底：抽到什么都起不来时，起码抡一刀
        if (!started) {
            startPhysics();
        }

        // 起手后立刻进选招空档，防止同一 tick 被重复抽签
        boss.attackSelection = true;
        boss.attackSelectionTick = 0;

        delay = PICK_DELAY;
    }

    /**
     * 物理三连击。
     *
     * 形态用 forceStart(false) 钉死，免得距离判断又把它掰成魔法形态。
     * 二阶段物理三连击自己会往目标压（见 PhantomHeroComboGoal#movingCast）。
     */
    private boolean startPhysics() {

        PhantomHeroComboGoal goal = boss.getComboGoal();

        if (goal == null) {
            return false;
        }

        boss.comboCooldown = 0;

        goal.forceStart(false);

        return true;
    }

    /**
     * 魔法三连击。
     *
     * 和物理共用一个 goal，靠 forceStart(true) 强制成魔法形态 ——
     * 否则近战距离下它会自己判成物理。
     * 二阶段魔法三连击同样允许移动释放（见 PhantomHeroComboGoal#keepMagicSpacing）。
     */
    private boolean startMagic() {

        PhantomHeroComboGoal goal = boss.getComboGoal();

        if (goal == null) {
            return false;
        }

        boss.comboCooldown = 0;

        goal.forceStart(true);

        return true;
    }

    private boolean startSniper() {

        PhantomHeroSniperGoal goal = boss.getSniperGoal();

        if (goal == null) {
            return false;
        }

        boss.sniperCooldown = 0;

        goal.forceStart();

        return true;
    }

    private boolean startBow() {

        PhantomHeroBowAttackGoal goal = boss.getBowGoal();

        if (goal == null) {
            return false;
        }

        goal.forceStart();

        return true;
    }
}
