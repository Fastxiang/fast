package com.main.fast.damage.api;

import com.main.fast.damage.DamageSourceCloner;
import com.main.fast.damage.DamageTypeManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * 伤害类型转化事件。
 *
 * <p>由 mixin 在 {@code LivingEntity#hurt} / {@code Player#hurt} 的最开头抛出，
 * 早于 {@code LivingAttackEvent}、早于无敌 / 免疫 / 火抗 / 护甲 / 抗性判定。
 * 监听器在这里改类型，改完之后原版接下来的所有逻辑（免疫、抗性、死亡消息、统计）
 * 看到的都是新类型。</p>
 *
 * <h3>事件持有伤害源，监听器直接改</h3>
 * <p>{@link #setDamageType(String)} 会立刻把 {@link #getSource()} 换成一个
 * 「同子类、只换类型」的新 {@link DamageSource}；也可以 {@link #setDamageSource(DamageSource)}
 * 完全自己给一个源。事件结束后，mixin 把 {@link #getSource()} 交回给 {@code hurt}。</p>
 *
 * <h3>KubeJS</h3>
 * <pre>{@code
 * const $FastDamage = Java.loadClass("com.main.fast.damage.api.FastDamageType")
 *
 * $FastDamage.on(10, (event) => {
 *     event.convertIfOriginal("arrow", "fire_magic")   // 弓箭伤害 -> 火焰伤害
 * })
 * }</pre>
 */
public class DamageConversionEvent extends Event {

    private final LivingEntity entity;
    /** 事件当前的伤害源（可能已被监听器换掉） */
    private DamageSource source;
    /** 进入事件时的原始源，永不改变 */
    private final DamageSource enteringSource;
    private final String originalTypeId;

    public DamageConversionEvent(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
        this.enteringSource = source;
        this.originalTypeId = DamageTypeManager.typeIdString(source);
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /** 受伤实体 */
    public LivingEntity getEntity() {
        return entity;
    }

    /** 当前的伤害源（可能已被本事件换掉） */
    public DamageSource getSource() {
        return source;
    }

    /** 进入事件时的原始伤害源（全程不变，用于对比） */
    public DamageSource getEnteringSource() {
        return enteringSource;
    }

    /** 造成伤害的实体（射箭的玩家、开枪的人），可能为 {@code null} */
    @Nullable
    public Entity getAttacker() {
        return source.getEntity();
    }

    /** 直接实体（箭、子弹本身），可能为 {@code null} */
    @Nullable
    public Entity getDirectEntity() {
        return source.getDirectEntity();
    }

    /** 当前类型对象 */
    public DamageType getDamageType() {
        return source.type();
    }

    /** 当前类型 id，如 {@code irons_spellbooks:fire_magic} */
    @Nullable
    public String getDamageTypeId() {
        return DamageTypeManager.typeIdString(source);
    }

    /** 进入事件时的类型 id（转化前） */
    @Nullable
    public String getOriginalTypeId() {
        return originalTypeId;
    }

    /** 进入事件时的 msgId（旧口径，如 {@code arrow} / {@code tacz.bullet} / {@code fire_magic}） */
    @Nullable
    public String getOriginalMsgId() {
        return enteringSource.getMsgId();
    }

    /** 当前类型的 msgId（旧口径） */
    @Nullable
    public String getDamageTypeMsgId() {
        return source.getMsgId();
    }

    /** 类型是否被本事件改过 */
    public boolean isConverted() {
        return source != enteringSource;
    }

    // ------------------------------------------------------------------
    // 便捷判定
    // ------------------------------------------------------------------

    /** 当前类型是否包含某段文字（忽略大小写），如 {@code is("arrow")} */
    public boolean is(@Nullable String keyword) {
        return DamageTypeManager.contains(getDamageTypeId(), keyword);
    }

    /** 进入时的类型是否包含某段文字，如 {@code isOriginal("tacz.bullet")} */
    public boolean isOriginal(@Nullable String keyword) {
        return DamageTypeManager.contains(originalTypeId, keyword);
    }

    /** 当前类型是否属于某标签，如 {@code minecraft:is_projectile} */
    public boolean isTag(String tagId) {
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        return id != null && isTag(id);
    }

    public boolean isTag(ResourceLocation tagId) {
        Holder<DamageType> holder = holderOf(source.type());
        if (holder == null) return false;
        try {
            return holder.is(net.minecraft.tags.TagKey.create(Registries.DAMAGE_TYPE, tagId));
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    /**
     * 换成另一个类型。会新建一个「同子类、只换类型」的源（原源不动）。
     *
     * <p>支持完整 id（{@code irons_spellbooks:fire_magic}）和旧写法
     * （{@code fire_magic} → 补 {@code irons_spellbooks:}；{@code tacz.bullet} → {@code tacz:bullet}）。</p>
     *
     * @return {@code true} = 已换成新类型；{@code false} = 类型不存在 / 解析失败 / 本来就是这个类型
     */
    public boolean setDamageType(@Nullable String typeId) {
        if (typeId == null) return false;
        ResourceLocation id = DamageTypeManager.normalizeTypeId(typeId);
        if (id == null) return false;
        Holder<DamageType> holder = holderOf(id);
        if (holder == null) {
            DamageTypeManager.reportMissingType(id);
            return false;
        }
        if (holder.value() == source.type()) return false;
        this.source = convert(source, holder);
        return true;
    }

    /** 直接指定一个伤害源（完全交给调用方决定） */
    public void setDamageSource(DamageSource newSource) {
        if (newSource != null) this.source = newSource;
    }

    /** 仅当当前类型包含 {@code keyword} 时才换成 {@code typeId} */
    public boolean convertIf(@Nullable String keyword, String typeId) {
        return is(keyword) && setDamageType(typeId);
    }

    /** 仅当进入时的类型包含 {@code keyword} 时才换成 {@code typeId} */
    public boolean convertIfOriginal(@Nullable String keyword, String typeId) {
        return isOriginal(keyword) && setDamageType(typeId);
    }

    /** 还原成进入时的源 */
    public void reset() {
        this.source = enteringSource;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 发事件，返回最终要交给 hurt 处理的源。
     *
     * <p><b>只在服务端发。</b>客户端也会跑 {@code LivingEntity#hurt}
     * （例如假人实体的客户端模拟），如果两端都发，脚本里的回调会执行两次，
     * 扣蓝、加 buff 这类有副作用的操作就会被重复执行。</p>
     *
     * <p>这里<b>不做</b>「没有监听器就跳过」的短路：KubeJS 用
     * {@code NativeEvents.onEvent} 订阅，Java 侧统计不到这种订阅，
     * 一旦按计数短路，事件就永远不会发出去。EventBus 在没有订阅者时
     * 的开销本身可以忽略。</p>
     */
    public static DamageSource fire(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide()) return source;
        DamageConversionEvent event = new DamageConversionEvent(target, source);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled() ? source : event.source;
    }

    @Nullable
    private Holder<DamageType> holderOf(ResourceLocation id) {
        var registry = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return registry.getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, id)).orElse(null);
    }

    @Nullable
    private Holder<DamageType> holderOf(DamageType type) {
        var registry = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return registry.getResourceKey(type)
                .flatMap(registry::getHolder)
                .orElse(null);
    }

    /**
     * 造一个换成 holder 类型的新源：子类优先走克隆，保证 instanceof 与附加行为不丢。
     *
     * <p>同时把「最初的 getType()」沿着转化链传递下去：如果 {@code original} 本身就是
     * 之前转化出来的源，就沿用它已记录的原值，这样连续转化多次也不会把最原始的类型弄丢。</p>
     */
    private static DamageSource convert(DamageSource original, Holder<DamageType> holder) {
        String originalMsgId = DamageTypeManager.originalMsgIdOf(original);

        if (DamageSourceCloner.isSpecial(original)) {
            DamageSource cloned = DamageSourceCloner.cloneWithType(original, holder);
            if (cloned != null) {
                DamageTypeManager.markConverted(cloned, originalMsgId);
                return cloned;
            }
        }
        DamageSource built = DamageTypeManager.buildSource(holder, original.getDirectEntity(), original.getEntity());
        DamageTypeManager.markConverted(built, originalMsgId);
        return built;
    }
}
