package com.main.fast.damage.api;

import com.main.fast.damage.ConversionListener;
import com.main.fast.damage.DamageTypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 伤害类型 API（KubeJS 直接 {@code Java.loadClass} 这个类）—— 只做两件事：
 * <b>读类型</b>和<b>注册转化事件</b>。
 *
 * <h3>注册</h3>
 * <pre>{@code
 * const $FastDamage = Java.loadClass("com.main.fast.damage.api.FastDamageType")
 *
 * // 数字就是 EventPriority 的序号，越大越先执行
 * $FastDamage.on(10, (event) => {
 *     event.convertIfOriginal("arrow", "fire_magic")
 * })
 * }</pre>
 *
 * <h3>读</h3>
 * <pre>{@code
 * $FastDamage.typeId(source)      // irons_spellbooks:fire_magic
 * $FastDamage.shortId(source)     // fire_magic（旧脚本口径）
 * $FastDamage.msgId(source)       // 旧的 source.getType()：arrow / tacz.bullet
 * }</pre>
 */
public final class FastDamageType {

    /** 本模组日志器 */
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private FastDamageType() {
    }

    // ==================================================================
    // 读
    // ==================================================================

    /** 完整类型 id，如 {@code irons_spellbooks:fire_magic}（注册表 id，不是 msgId） */
    @Nullable
    public static String typeId(@Nullable DamageSource source) {
        return DamageTypeManager.typeIdString(source);
    }

    /**
     * 旧的 {@code source.getType()}（即 {@code msgId}），如 {@code fire_magic} / {@code arrow} /
     * {@code tacz.bullet}。脚本里的 includes / 数组相等判断全部按这个口径，
     * 不要拿 {@link #typeId} 去做同类判断。
     */
    @Nullable
    public static String msgId(@Nullable DamageSource source) {
        return source == null ? null : source.getMsgId();
    }

    /**
     * 拿到这个伤害源「转化前」的 {@code getType()} 字符串。
     *
     * <p>这是脚本里唯一应该用来初始化伤害类型的入口：</p>
     * <ul>
     *   <li>源没被转化过 → 就是它自己的 {@code getType()}；</li>
     *   <li>源已被转化过 → 返回转化前的 {@code getType()}（即使连续转化多次也是最初那个）。</li>
     * </ul>
     *
     * <p>因为转化时 Java 侧已经记下了原值，脚本不需要再自己存一份字符串。</p>
     *
     * <pre>{@code
     * let damageType = $FastDamageType.originalMsgId(event.source)
     * }</pre>
     */
    @Nullable
    public static String originalMsgId(@Nullable DamageSource source) {
        return DamageTypeManager.originalMsgIdOf(source);
    }

    /** 这个类型 id 是否存在于当前注册表 */
    public static boolean exists(String typeId) {
        ResourceLocation id = DamageTypeManager.normalizeTypeId(typeId);
        return id != null;
    }

    // ==================================================================
    // 注册转化事件
    // ==================================================================

    /**
     * 注册一个伤害类型转化监听器。
     *
     * @param priority {@code EventPriority} 的序号：{@code LOWEST=0, LOW=1, NORMAL=2, HIGH=3,
     *                 HIGHEST=4}；也可以直接传更大的数排在更前面
     * @param handler  回调，参数 {@link DamageConversionEvent}
     * @return 句柄，{@code shutdown()} 可注销
     */
    public static ConversionListener on(int priority, Consumer<DamageConversionEvent> handler) {
        return on(priority, handler, null);
    }

    public static ConversionListener on(int priority, Consumer<DamageConversionEvent> handler,
                                        @Nullable Object owner) {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        var listener = new ConversionListener(bus, toPriority(priority), handler, owner);
        bus.addListener(listener.getPriority(), false, DamageConversionEvent.class, listener);
        DamageTypeManager.addListener();
        return listener;
    }

    /** 指定 {@code EventPriority} 枚举 */
    public static ConversionListener on(net.minecraftforge.eventbus.api.EventPriority priority,
                                        Consumer<DamageConversionEvent> handler) {
        return on(priority, handler, null);
    }

    public static ConversionListener on(net.minecraftforge.eventbus.api.EventPriority priority,
                                        Consumer<DamageConversionEvent> handler,
                                        @Nullable Object owner) {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        var listener = new ConversionListener(bus, priority, handler, owner);
        bus.addListener(priority, false, DamageConversionEvent.class, listener);
        DamageTypeManager.addListener();
        return listener;
    }

    /** 当前挂了多少条转化规则（0 = 完全关闭，连事件都不发） */
    public static int listenerCount() {
        return DamageTypeManager.listenerCount();
    }

    private static net.minecraftforge.eventbus.api.EventPriority toPriority(int n) {
        return switch (n) {
            case 0 -> net.minecraftforge.eventbus.api.EventPriority.LOWEST;
            case 1 -> net.minecraftforge.eventbus.api.EventPriority.LOW;
            case 3 -> net.minecraftforge.eventbus.api.EventPriority.HIGH;
            case 4 -> net.minecraftforge.eventbus.api.EventPriority.HIGHEST;
            default -> net.minecraftforge.eventbus.api.EventPriority.NORMAL;
        };
    }

    // ==================================================================
    // 造伤害（脚本里需要手动打伤害时用）
    // ==================================================================

    /**
     * 用指定类型造成一次伤害。
     *
     * @param attacker 造成伤害的实体，可为 null
     * @param target   受伤者
     * @param typeId   类型（支持旧写法）
     * @param amount   伤害值
     */
    public static boolean attack(@Nullable Entity attacker, LivingEntity target,
                                 String typeId, double amount) {
        if (target == null) return false;
        ResourceLocation id = DamageTypeManager.normalizeTypeId(typeId);
        if (id == null) return false;
        var registries = net.minecraft.core.registries.Registries.DAMAGE_TYPE;
        var holder = target.level().registryAccess().registryOrThrow(registries)
                .getHolder(net.minecraft.resources.ResourceKey.create(registries, id));
        if (holder.isEmpty()) {
            LOGGER.warn("[fast/damage] attack: 伤害类型不存在 {}", id);
            return false;
        }
        DamageSource ds = DamageTypeManager.buildSource(holder.get(), attacker, attacker);
        return target.hurt(ds, (float) amount);
    }
}
