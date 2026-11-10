package com.main.fast.mixin.cataclysm;

/**
 * 灾变（L_Ender's Cataclysm）项目混用注入点的字符串常量。
 *
 * <p>这个类<b>不能</b>加 {@code @Mixin}：它只是被各 mixin 引用的普通工具类。
 * 本模组的 mixin 全是 {@code remap = false}（灾变的方法名在混淆映射下对不上），
 * 所以这里的字符串必须是<b>运行时（SRG/混淆）名字</b>。</p>
 */
public final class CataclysmProjectiles {

    /** {@code LivingEntity#hurt(DamageSource, float)} */
    public static final String HURT = "m_6469_";

    /** {@code DamageSource#getDirectEntity()} —— 注意 1.20.1 里它是 {@code m_7640_}，不是 {@code m_7639_} */
    public static final String GET_DIRECT_ENTITY = "m_7640_";

    private CataclysmProjectiles() {
    }
}
