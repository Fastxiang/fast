package com.main.fast.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.Holder;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特殊伤害源兼容层：把一个 {@link DamageSource} 子类实例复制成「同子类、同字段、只换类型」的新实例。
 *
 * <h3>为什么需要它</h3>
 * 铁魔法（Iron's Spells）判定法术伤害用的是 {@code instanceof SpellDamageSource}：
 * <pre>
 * DamageSources.applyDamage   → instanceof SpellDamageSource → 发 SpellDamageEvent
 * DamageSources.postHitEffects → instanceof SpellDamageSource → 吸血 / 冰冻 / 着火 / iFrame
 * </pre>
 * 如果我们用 {@code new DamageSource(holder, ...)} 造新源，这些分支全部走不到，法术伤害会失去
 * 吸血、冰冻、着火等效果。灾变（Cataclysm）也有子类 {@code EntityExcludedDamageSource}
 * （靠覆写死亡消息隐藏受害实体）。
 *
 * <h3>做法</h3>
 * 反射套一条「不走构造器」的复制路径：
 * <ol>
 *   <li>用 {@link sun.misc.Unsafe}（或 {@code ReflectionFactory}）分配一个同类的空壳实例，
 *       <b>不执行任何构造器</b>，所以不需要知道子类构造器签名，也不会触发副作用；</li>
 *   <li>反射遍历从子类到 {@link DamageSource} 的所有实例字段，逐个复制值；</li>
 *   <li>只把类型字段（{@code DamageSource.type}）改写成目标类型。</li>
 * </ol>
 * 这样新实例是「同一子类、同一批字段值、只有类型不同」，{@code instanceof} 判定全部照旧成立。
 *
 * <p>任何一步失败都返回 {@code null}，调用方回落到普通的 {@code new DamageSource(...)}，
 * 绝不会因为兼容层出问题而导致伤害丢失。</p>
 */
public final class DamageSourceCloner {

    private DamageSourceCloner() {
    }

    private static volatile boolean UNAVAILABLE;

    /** 每个子类只解析一次字段表 */
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();

    /** 类型字段在 {@link DamageSource} 上，名字固定 */
    private static Field typeField;

    private static Object unsafe;

    private static java.lang.reflect.Method allocateInstance;

    /**
     * 复制成「同子类、换类型」的新实例。
     *
     * @return 新实例；原源就是基类 {@link DamageSource}、或反射不可用时返回 {@code null}
     */
    @Nullable
    public static DamageSource cloneWithType(DamageSource original, Holder<DamageType> holder) {
        if (original == null || holder == null) return null;
        Class<?> cls = original.getClass();
        if (cls == DamageSource.class || UNAVAILABLE) return null;

        try {
            Object instance = newInstanceWithoutConstructor(cls);
            if (instance == null) return null;

            for (Field f : fieldsOf(cls)) {
                Object value = f.get(original);
                if (f.equals(typeOf())) {
                    value = holder;
                }
                f.set(instance, value);
            }
            return (DamageSource) instance;
        } catch (Throwable t) {
            DamageTypeManager.warnOnce("[fast/damage] 特殊伤害源复制失败，回落到普通伤害源: "
                    + cls.getName() + " (" + t.getClass().getSimpleName() + ")");
            return null;
        }
    }

    // ------------------------------------------------------------------

    private static Field typeOf() throws NoSuchFieldException {
        Field f = typeField;
        if (f == null) {
            f = DamageSource.class.getDeclaredField("type");
            f.setAccessible(true);
            typeField = f;
        }
        return f;
    }

    private static Field[] fieldsOf(Class<?> cls) {
        return FIELD_CACHE.computeIfAbsent(cls, c -> {
            java.util.List<Field> list = new java.util.ArrayList<>();
            for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                for (Field f : cur.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (f.isSynthetic()) continue;
                    try {
                        f.setAccessible(true);
                        list.add(f);
                    } catch (Throwable ignored) {
                        // 拿不到就跳过；如果正好是类型字段会由下面的校验兜住
                    }
                }
            }
            return list.toArray(new Field[0]);
        });
    }

    /**
     * 不执行构造器直接分配实例。
     *
     * <p>优先 {@code sun.misc.Unsafe.allocateInstance}；退而求其次用
     * {@code sun.reflect.ReflectionFactory} 拿一个「无参构造」再调用它——后者不会跑子类逻辑。</p>
     */
    @Nullable
    private static Object newInstanceWithoutConstructor(Class<?> cls) {
        Object instance = null;
        try {
            if (unsafe == null) unsafe = findUnsafe();
            if (unsafe != null) {
                if (allocateInstance == null) {
                    allocateInstance = unsafe.getClass()
                            .getMethod("allocateInstance", Class.class);
                }
                instance = allocateInstance.invoke(unsafe, cls);
            }
        } catch (Throwable ignored) {
            instance = null;
        }

        if (instance == null) {
            instance = viaReflectionFactory(cls);
        }
        return instance;
    }

    /** 兜底：ReflectionFactory 造一个「只调 Object.<init>」的构造器 */
    @Nullable
    private static Object viaReflectionFactory(Class<?> cls) {
        try {
            Class<?> rfClass = Class.forName("sun.reflect.ReflectionFactory");
            Object rf = rfClass.getMethod("getReflectionFactory").invoke(null);
            Constructor<?> objectCtor = Object.class.getDeclaredConstructor();
            Constructor<?> ctor = (Constructor<?>) rfClass
                    .getMethod("newConstructorForSerialization", Class.class, Constructor.class)
                    .invoke(rf, cls, objectCtor);
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable t) {
            UNAVAILABLE = true;
            DamageTypeManager.warnOnce("[fast/damage] 无法免构造分配实例，特殊伤害源将回落到普通源");
            return null;
        }
    }

    @Nullable
    private static Object findUnsafe() {
        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            Field f = uc.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 这个类是不是 {@link DamageSource} 的特殊子类（需要走复制路径） */
    public static boolean isSpecial(@Nullable DamageSource source) {
        return source != null && source.getClass() != DamageSource.class;
    }
}
