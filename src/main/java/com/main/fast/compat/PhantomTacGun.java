package com.main.fast.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.Optional;

/**
 * TACZ 兼容层：把幻影勇者开枪需要的所有 TACZ 调用集中在这里。
 *
 * 逻辑全部学自女仆模组（TouhouLittleMaid）的
 * compat/gun/tacz/TacInnerCompat#performGunAttack：
 *
 *   1. 主手必须是枪（IGun.getIGunOrNull(stack) 拿得到东西）
 *   2. 用 IGunOperator.fromLivingEntity(shooter) 拿到施法者
 *   3. 自己算 yaw / pitch（枪械不走原版的 performRangedAttack），
 *      再 op.shoot(() -> pitch, () -> yaw)
 *   4. 狙击枪要先 op.aim(true)，等 gunData.getAimTime() 秒开镜完成才打得准
 *   5. 按 ShootResult 逐个处理：没拔枪就 draw、需要拉栓就 bolt、没子弹就 reload
 *
 * 女仆那边的弹药是从背包里找的（ItemsUtil.findStackSlot + reload），
 * 但 Boss 没有背包，所以这里改成用 TACZ 自带的「dummy ammo」：
 * 每次开枪前把弹匣直接填满，不需要任何物品栏。
 *
 * 为什么单独封装成一个类：
 * TACZ 是可选依赖。把 com.tacz.* 的引用全部关在这个文件里，
 * 别的类只认 {@link #isAvailable()} 和自定义的 {@link ShotResult}，
 * 没装 TACZ 时只要不进入这个类就不会触发 NoClassDefFoundError。
 */
public final class PhantomTacGun {

    /** TACZ 的 modid，用来判断有没有装 */
    private static final String TACZ_MODID = "tacz";

    /**
     * AWM 在 TACZ 默认枪包里的 id。
     *
     * 注意不是 awm：默认枪包的文件名是 ai_awp_data.json，
     * en_us.json 里 "tacz.gun.ai_awp.name" = "Accuracy International AWM"。
     */
    private static final ResourceLocation AWM_ID =
            ResourceLocation.fromNamespaceAndPath(TACZ_MODID, "ai_awp");

    private static final String SNIPER_TYPE = "sniper";

    /** 缓存解析出来的枪 id，避免每帧遍历全部枪械索引 */
    private static ResourceLocation cachedGunId;

    /**
     * 开枪的结果。
     *
     * 刻意不直接用 TACZ 的 ShootResult，这样外面的 AI 完全不依赖 TACZ 的类。
     */
    public enum ShotResult {
        /** 打出去了 */
        SUCCESS,
        /** 需要拉栓（AWM 是手动枪机） */
        NEED_BOLT,
        /** 枪还没拔出来 */
        NOT_DRAW,
        /** 没子弹 */
        NO_AMMO,
        /** 其它失败（冷却、正在装填、不是枪……） */
        FAIL
    }

    private PhantomTacGun() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(TACZ_MODID);
    }

    // ---------------------------------------------------------------
    // 枪械物品
    // ---------------------------------------------------------------

    /**
     * 造一把装好子弹的狙击枪。
     *
     * TACZ 的枪不是一枪一个物品，而是统一用 modern_kinetic_gun 这一个物品，
     * 具体是哪把枪写在 NBT 里，所以流程是：
     *   new ItemStack(ModItems.MODERN_KINETIC_GUN) -> IGun#setGunId(枪id)
     */
    public static ItemStack createSniperStack() {

        ItemStack stack = new ItemStack(ModItems.MODERN_KINETIC_GUN.get());

        IGun iGun = IGun.getIGunOrNull(stack);

        if (iGun == null) {
            return stack;
        }

        iGun.setGunId(stack, resolveGunId());

        refill(stack);

        return stack;
    }

    /**
     * 解析要用的枪：优先 AWM，没装默认枪包就退而求其次找任意一把狙击枪。
     */
    private static ResourceLocation resolveGunId() {

        if (cachedGunId != null) {
            return cachedGunId;
        }

        if (TimelessAPI.getCommonGunIndex(AWM_ID).isPresent()) {
            cachedGunId = AWM_ID;
            return cachedGunId;
        }

        for (Map.Entry<ResourceLocation, CommonGunIndex> entry
                : TimelessAPI.getAllCommonGunIndex()) {

            if (SNIPER_TYPE.equalsIgnoreCase(entry.getValue().getType())) {
                cachedGunId = entry.getKey();
                return cachedGunId;
            }
        }

        cachedGunId = AWM_ID;

        return cachedGunId;
    }

    /**
     * 把弹匣填满。
     *
     * Boss 没有物品栏，走不了女仆那种「从背包找弹药 + reload」的路子，
     * 所以直接用 TACZ 的 dummy ammo 把枪内弹药写满。
     * 两条路（dummy / 弹匣计数）都写一遍，兼容不同的服务器配置。
     */
    public static void refill(ItemStack gunStack) {

        IGun iGun = IGun.getIGunOrNull(gunStack);

        if (iGun == null) {
            return;
        }

        try {
            int max = iGun.getMaxDummyAmmoAmount(gunStack);

            iGun.setDummyAmmoAmount(gunStack, max);
            iGun.setCurrentAmmoCount(gunStack, max);
            iGun.setBulletInBarrel(gunStack, true);

        } catch (Exception ignored) {
            // 枪械索引缺失（比如枪包被删了）时这几个方法会抛异常，
            // 不能让它把 Boss 的 AI tick 炸掉
        }
    }

    // ---------------------------------------------------------------
    // 枪械参数（全部取自枪械本体，保证前摇和真实开镜时间一致）
    // ---------------------------------------------------------------

    private static Optional<GunData> getGunData(ItemStack gunStack) {

        IGun iGun = IGun.getIGunOrNull(gunStack);

        if (iGun == null) {
            return Optional.empty();
        }

        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack))
                .map(CommonGunIndex::getGunData);
    }

    /** 开镜时间（tick）。狙击枪必须等开镜完成才打得准。 */
    public static int getAimTicks(ItemStack gunStack) {
        return secondsToTicks(getGunData(gunStack).map(GunData::getAimTime).orElse(0.25F));
    }

    /** 拔枪时间（tick） */
    public static int getDrawTicks(ItemStack gunStack) {
        return secondsToTicks(getGunData(gunStack).map(GunData::getDrawTime).orElse(0.35F));
    }

    /** 拉栓时间（tick）。AWM 是手动枪机，打完一发要拉栓。 */
    public static int getBoltTicks(ItemStack gunStack) {
        return secondsToTicks(getGunData(gunStack).map(GunData::getBoltActionTime).orElse(0.9F));
    }

    private static int secondsToTicks(float seconds) {
        return Math.max(1, Math.round(seconds * 20.0F));
    }

    // ---------------------------------------------------------------
    // 动作
    // ---------------------------------------------------------------

    public static void aim(LivingEntity shooter, boolean aiming) {
        IGunOperator.fromLivingEntity(shooter).aim(aiming);
    }

    public static void draw(LivingEntity shooter) {
        IGunOperator.fromLivingEntity(shooter).draw(shooter::getMainHandItem);
    }

    public static void bolt(LivingEntity shooter) {
        IGunOperator.fromLivingEntity(shooter).bolt();
    }

    public static void reload(LivingEntity shooter) {
        IGunOperator.fromLivingEntity(shooter).reload();
    }

    /**
     * 开一枪。
     *
     * yaw / pitch 的算法和女仆完全一致：
     *   yaw   = -atan2(dx, dz)
     *   pitch = -atan2(dy, sqrt(dx^2 + dz^2))
     * 枪械不走原版的 performRangedAttack，方向必须由我们自己给。
     */
    public static ShotResult shoot(LivingEntity shooter, LivingEntity target) {

        ItemStack gunStack = shooter.getMainHandItem();

        if (IGun.getIGunOrNull(gunStack) == null) {
            return ShotResult.FAIL;
        }

        refill(gunStack);

        double dx = target.getX() - shooter.getX();
        double dy = target.getEyeY() - shooter.getEyeY();
        double dz = target.getZ() - shooter.getZ();

        float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);

        ShootResult result = operator.shoot(() -> pitch, () -> yaw);

        return switch (result) {
            case SUCCESS -> ShotResult.SUCCESS;
            case NEED_BOLT -> ShotResult.NEED_BOLT;
            case NOT_DRAW -> ShotResult.NOT_DRAW;
            case NO_AMMO -> ShotResult.NO_AMMO;
            default -> ShotResult.FAIL;
        };
    }
}
