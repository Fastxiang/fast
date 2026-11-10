package com.main.fast.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤害类型转化的工具集合。
 *
 * <p>这里<b>没有任何状态机</b>：不压栈、不缓存上下文、不记录「这次伤害的原类型」。
 * 转化决策与原始类型全部由 {@link com.main.fast.damage.api.DamageConversionEvent} 持有。</p>
 *
 * <p>唯一的状态是「挂了几条监听器」——用来在一条都没有时彻底跳过事件。</p>
 */
public final class DamageTypeManager {

    private DamageTypeManager() {
    }

    // ------------------------------------------------------------------
    // 监听器计数
    //
    // 只统计通过 FastDamageType.on() 注册的「Java 侧」监听器，用于诊断。
    // 不要拿它决定要不要发事件：KubeJS 用 NativeEvents.onEvent 订阅，
    // 计数看不到这些订阅，一短路就会把脚本的事件全部挡掉。
    // ------------------------------------------------------------------

    private static volatile int LISTENERS;

    public static int listenerCount() {
        return LISTENERS;
    }

    public static void addListener() {
        LISTENERS++;
    }

    public static void removeListener() {
        int v = LISTENERS;
        if (v > 0) LISTENERS = v - 1;
    }

    // ------------------------------------------------------------------
    // 造源
    // ------------------------------------------------------------------

    /** 从实体 / 位置构造一个指定类型的新源（1.20.1 没有「任意类型」工厂，只能直接调构造器） */
    public static DamageSource buildSource(Holder<DamageType> holder,
                                           @Nullable Entity direct,
                                           @Nullable Entity causing,
                                           @Nullable Vec3 pos) {
        if (direct != null && causing != null && direct != causing) {
            return new DamageSource(holder, direct, causing);
        }
        if (causing != null) return new DamageSource(holder, causing);
        if (direct != null) return new DamageSource(holder, direct);
        return pos != null ? new DamageSource(holder, pos) : new DamageSource(holder);
    }

    public static DamageSource buildSource(Holder<DamageType> holder,
                                           @Nullable Entity direct, @Nullable Entity causing) {
        return buildSource(holder, direct, causing, null);
    }

    // ------------------------------------------------------------------
    // 字符串
    // ------------------------------------------------------------------

    /** 伤害类型的注册表 id；取不到 key 时回落到 {@code msgId} */
    @Nullable
    public static String typeIdString(DamageSource source) {
        if (source == null) return null;
        return source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElseGet(source::getMsgId);
    }

    /** 忽略大小写的包含判断 */
    public static boolean contains(@Nullable String haystack, @Nullable String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * 归一化类型 id，兼容旧写法：
     * {@code tacz.bullet}→{@code tacz:bullet}；{@code fire_magic}→{@code irons_spellbooks:fire_magic}
     */
    @Nullable
    public static ResourceLocation normalizeTypeId(@Nullable String raw) {
        if (raw == null) return null;
        String id = raw.trim();
        if (id.isEmpty()) return null;
        if (!id.contains(":")) {
            id = id.contains(".") ? id.replace('.', ':') : "irons_spellbooks:" + id;
        }
        return ResourceLocation.tryParse(id);
    }

    // ------------------------------------------------------------------
    // 日志（同一个问题只报一次）
    // ------------------------------------------------------------------

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    static void warnOnce(String msg) {
        if (!WARNED.add(msg)) return;
        com.main.fast.damage.api.FastDamageType.LOGGER.warn(msg);
    }

    static void warnMissing(ResourceLocation id) {
        warnOnce("[fast/damage] 伤害类型不存在，转化已忽略: " + id);
    }

    /** 公共入口：事件层遇到缺失类型时调用 */
    public static void reportMissingType(ResourceLocation id) {
        warnMissing(id);
    }

    /**
     * 这个源是不是本模组造出来的（用于 mixin 层防重复处理）
     */
    public static boolean isConverted(@Nullable DamageSource source) {
        return source != null && CONVERTED.containsKey(source);
    }

    /**
     * 登记「这个源是本模组造的」，同时记住它转化前的 {@code getType()} 字符串。
     *
     * <p>只存一次的 msgId 快照：即使同一次伤害被连续转化多次，读到的永远是最初那个值。</p>
     */
    public static void markConverted(DamageSource source, @Nullable String originalMsgId) {
        if (source != null) CONVERTED.put(source, originalMsgId);
    }

    /**
     * 拿到这个伤害源「转化前」的 {@code getType()} 字符串。
     *
     * <ul>
     *   <li>本模组造出来的源 → 转化前的 msgId（如 {@code arrow} / {@code tacz.bullet}）；</li>
     *   <li>普通源 → 它自己的 {@code getType()}，也就是没被转化过的原值。</li>
     * </ul>
     *
     * <p>所以调用方在任何时点、任何事件里拿到的都是「这次伤害刚进来时的类型」，不用自己维护。</p>
     */
    @Nullable
    public static String originalMsgIdOf(@Nullable DamageSource source) {
        if (source == null) return null;
        String recorded = CONVERTED.get(source);
        return recorded != null ? recorded : source.getMsgId();
    }

    /** 新源 → 转化前的 msgId（弱引用，避免泄漏长生命周期的源） */
    private static final java.util.Map<DamageSource, String> CONVERTED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
}
