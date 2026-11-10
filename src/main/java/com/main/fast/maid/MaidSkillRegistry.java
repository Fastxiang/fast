package com.main.fast.maid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 女仆技能登记表（服务端）。
 *
 * <p>这里是「Java 侧唯一的数据来源」，全部内容都由 KubeJS 在脚本加载 / 刷新的时机推过来：</p>
 * <ul>
 *     <li>{@link #registerSpellType} —— 原 {@code init_magic.js} 的 {@code registerSpellType}，
 *         决定一个法术属于哪个职业池（magic / arrow / physical / buff / tank / teleport …）</li>
 *     <li>{@link #registerSkillTag} —— 原 {@code skill_tag.js} 的标签（aoe / heal / movement / melee …），
 *         决策器用它判断「这个技能该在什么情况下放」</li>
 *     <li>{@link #updateFromJson} —— 原 {@code MaidUpdateEnderBonusEvent} 末尾的技能表，
 *         外加「物理 / 魔法基础攻击用哪个法术」（末影饰品可能把 triple_strike 换成 six_strike 之类，
 *         所以这部分必须由 JS 决定，Java 不许写死）</li>
 * </ul>
 *
 * <p>为什么是 JS 推、Java 存：KubeJS 能拿到 Java 对象，Java 拿不到 KubeJS 的 global 表，
 * 只能由脚本侧单向写进来。写法沿用 {@code FastDungeon.registerJson} 的「JSON 字符串进 Java」先例。</p>
 */
public final class MaidSkillRegistry {

    /** 法术在职业里的定位，对应旧 JS 的 registerSpellType(type) */
    public enum Category {
        /** 法师的主动法术 */
        MAGIC,
        /** 法师的填充普攻法术 */
        BASIC_MAGIC,
        /** 减仇 / 脱身类 */
        SUBTRACT,
        /** 位移类 */
        TELEPORT,
        /** 守卫的仇恨类 */
        TANK,
        /** 战士的物理技能 */
        PHYSICAL,
        /** 游侠的箭类技能 */
        ARROW,
        /** 通用增益 */
        BUFF,
        /** 没有登记 */
        NONE;

        public static Category fromString(String type) {
            if (type == null) {
                return NONE;
            }
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "magic" -> MAGIC;
                case "basic_magic" -> BASIC_MAGIC;
                case "subtract" -> SUBTRACT;
                case "teleport" -> TELEPORT;
                case "tank" -> TANK;
                case "physical" -> PHYSICAL;
                case "arrow" -> ARROW;
                case "buff" -> BUFF;
                default -> NONE;
            };
        }
    }

    /** buff 技能的基础冷却（tick） */
    private static final Map<ResourceLocation, Integer> BUFF_COOLDOWN = new HashMap<>();
    /** buff 技能每级增加的冷却（tick） */
    private static final Map<ResourceLocation, Integer> BUFF_COOLDOWN_PER_LEVEL = new HashMap<>();

    private static final Map<ResourceLocation, Category> SPELL_CATEGORY = new HashMap<>();
    private static final Map<ResourceLocation, Set<String>> SPELL_TAGS = new HashMap<>();

    private static final Map<UUID, MaidSkillData> DATA = new HashMap<>();

    /** 旧 JS 里 buff 冷却会统一减掉 5 秒，这里保持一致，避免数值漂移 */
    private static final int BUFF_COOLDOWN_DISCOUNT = 20 * 5;

    private MaidSkillRegistry() {
    }

    // ---------------- 注册（KubeJS 调用） ----------------

    /**
     * 登记一个法术的职业定位与 buff 冷却
     *
     * @param spellId 法术 id
     * @param type    分类，见 {@link Category}
     * @param buffCooldown        buff 基础冷却（tick），非 buff 传 0
     * @param buffCooldownPerLevel buff 每级增加的冷却（tick），没有则传 0
     */
    public static void registerSpellType(String spellId, String type, int buffCooldown, int buffCooldownPerLevel) {
        ResourceLocation id = tryParse(spellId);
        if (id == null) {
            return;
        }
        Category category = Category.fromString(type);
        SPELL_CATEGORY.put(id, category);
        if (category == Category.BUFF) {
            BUFF_COOLDOWN.put(id, buffCooldown);
            BUFF_COOLDOWN_PER_LEVEL.put(id, buffCooldownPerLevel);
        }
    }

    /**
     * 登记一个法术的标签（aoe / heal / movement / melee / projectile / summon …）
     *
     * @param spellId 法术 id
     * @param tag     标签
     */
    public static void registerSkillTag(String spellId, String tag) {
        ResourceLocation id = tryParse(spellId);
        if (id == null || tag == null || tag.isEmpty()) {
            return;
        }
        SPELL_TAGS.computeIfAbsent(id, k -> new HashSet<>()).add(tag.toLowerCase(Locale.ROOT));
    }

    /**
     * 用 KubeJS 解析好的女仆数据覆盖快照
     *
     * <p>JSON 结构：</p>
     * <pre>{@code
     * {
     *   "skills": { "irons_spellbooks:fireball": 3 },
     *   "basic":  { "physical": "fast_irons_spellbooks_addition:triple_strike",
     *               "magic":    "fast_irons_spellbooks_addition:triple_magic_missile" }
     * }
     * }</pre>
     */
    public static void updateFromJson(Entity entity, String json) {
        if (entity == null || json == null || json.isBlank()) {
            return;
        }
        Map<ResourceLocation, Integer> skills = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> basic = new HashMap<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            JsonObject skillObject = object.getAsJsonObject("skills");
            if (skillObject != null) {
                for (Map.Entry<String, JsonElement> entry : skillObject.entrySet()) {
                    ResourceLocation id = tryParse(entry.getKey());
                    if (id == null) {
                        continue;
                    }
                    int level = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsInt() : 0;
                    if (level > 0) {
                        skills.put(id, level);
                    }
                }
            }
            JsonObject basicObject = object.getAsJsonObject("basic");
            if (basicObject != null) {
                for (Map.Entry<String, JsonElement> entry : basicObject.entrySet()) {
                    ResourceLocation spellId = tryParse(entry.getValue().getAsString());
                    if (spellId != null) {
                        basic.put(new ResourceLocation(entry.getKey()), spellId);
                    }
                }
            }
        } catch (RuntimeException exception) {
            // 脚本侧数据异常不应该让服务端崩，直接忽略这一次刷新
            return;
        }

        MaidSkillData data = DATA.computeIfAbsent(entity.getUUID(), k -> new MaidSkillData());
        data.replaceContent(skills, basic);
        data.setUpdatedAt(entity.level().getGameTime());
    }

    public static void clear(Entity entity) {
        if (entity != null) {
            DATA.remove(entity.getUUID());
        }
    }

    // ---------------- 查询 ----------------

    public static MaidSkillData getData(Entity entity) {
        return entity == null ? null : DATA.get(entity.getUUID());
    }

    /** 剑气三连击：近战扇形之外还会朝准星方向射一道剑气，所以按远程处理站位 */
    public static final ResourceLocation TRIPLE_SLASH =
            new ResourceLocation("fast_irons_spellbooks_addition", "triple_slash");

    /**
     * 这个女仆的基础攻击是不是「远程」形态。
     *
     * <p>由 KubeJS 推过来的 {@code basic.physical} 决定：带月牙之刃时 {@code getBasicAttackSpell}
     * 会返回 {@code triple_slash}（剑气三连击），它除了近战扇形还会射出剑气，
     * 所以走位要按法师那套「拉开距离打」而不是贴脸。</p>
     */
    public static boolean isRangedBasicAttack(Entity entity) {
        MaidSkillData data = getData(entity);
        if (data == null) {
            return false;
        }
        return TRIPLE_SLASH.equals(data.getBasicSpell("physical"));
    }

    public static Category getCategory(ResourceLocation spellId) {
        return SPELL_CATEGORY.getOrDefault(spellId, Category.NONE);
    }

    public static Set<String> getTags(ResourceLocation spellId) {
        Set<String> tags = SPELL_TAGS.get(spellId);
        return tags == null ? Collections.emptySet() : tags;
    }

    public static boolean hasTag(ResourceLocation spellId, String tag) {
        return getTags(spellId).contains(tag);
    }

    /**
     * buff 技能的实际冷却（tick）
     *
     * <p>与原 JS 的 {@code getBuffSkillCd} 完全一致：基础值减 5 秒，再按等级叠加。</p>
     */
    public static int getBuffCooldown(ResourceLocation spellId, int spellLevel) {
        Integer base = BUFF_COOLDOWN.get(spellId);
        if (base == null) {
            return 0;
        }
        int cooldown = base - BUFF_COOLDOWN_DISCOUNT;
        Integer perLevel = BUFF_COOLDOWN_PER_LEVEL.get(spellId);
        if (perLevel != null && perLevel != 0) {
            cooldown += perLevel * (spellLevel - 1);
        }
        return Math.max(cooldown, 0);
    }

    /** 清理长时间没有刷新的快照（女仆被删除 / 换维度后不再更新） */
    public static void prune(long gameTime, long ttlTicks) {
        DATA.entrySet().removeIf(entry -> gameTime - entry.getValue().getUpdatedAt() > ttlTicks);
    }

    private static ResourceLocation tryParse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }
}
