package com.main.fast.compat.jei.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JEI「整合包物品的获取方式」数据入口（KubeJS 侧唯一入口）。
 *
 * <p>渲染在 Java 侧（{@code com.main.fast.compat.jei.FastJeiPlugin}），脚本只负责把
 * 「哪个物品、从哪来、怎么描述」这层数据推过来，这样：
 * <ul>
 *   <li>不再依赖 kubejsadditions 的 {@code JEIAddedEvents}；</li>
 *   <li>描述里的翻译键在<b>渲染时</b>才翻译，脚本加载阶段（语言表还没加载）不会拿到裸键名；</li>
 *   <li>换语言、改排版只需要动 Java，不用重进存档。</li>
 * </ul>
 *
 * <pre>{@code
 * const $FastJei = Java.loadClass("com.main.fast.compat.jei.api.FastJei")
 *
 * $FastJei.clear()
 * $FastJei.registerJson(JSON.stringify([
 *     {
 *         item: "fast:the_hero_sword",       // 产出物品 id
 *         nbt: "",                           // 可选，SNBT 字符串
 *         key: "fast:key1",                  // 可选，获得它需要的副本钥匙（画成输入槽）
 *         pass: "fast:instance_pass1",       // 可选，额外让这个物品也能查到这里
 *         desc: [{ t: "jei.generic.hero.description" }],   // 描述行
 *         source: { t: "instance.fast1.name" }             // 可选，来源名（显示在物品名下面）
 *     }
 * ]))
 * }</pre>
 *
 * <p>文本节点有三种写法：</p>
 * <ul>
 *   <li>{@code "字面量"}</li>
 *   <li>{@code { t: "翻译键" }} —— 支持 {@code { t: "键", f: "找不到键时的兜底文本" }}</li>
 *   <li>{@code { t: "键", a: [ 子文本... ] }} —— 参数本身也可以是文本节点</li>
 * </ul>
 *
 * <p>这个类不能出现任何 JEI 的类型：没装 JEI 时脚本照样能调，只是数据没人消费。</p>
 */
public final class FastJei {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 解析后的条目，按注册顺序 */
    private static final List<Entry> ENTRIES = new ArrayList<>();

    /** 去重用（客户端脚本 /reload 会再跑一遍，避免条目翻倍） */
    private static final Set<String> SEEN = new LinkedHashSet<>();

    private FastJei() {
    }

    // ---------------- 脚本接口 ----------------

    /** 清空全部条目。脚本每次重新推送前先调用一次 */
    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
            SEEN.clear();
        }
    }

    /**
     * 注册一批条目。
     *
     * @param json JSON 数组（也接受单个对象）
     */
    public static void registerJson(String json) {
        if (json == null || json.isEmpty()) return;

        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            LOGGER.error("[Fast/JEI] 条目 JSON 解析失败", e);
            return;
        }

        List<Entry> parsed = new ArrayList<>();
        if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                Entry entry = Entry.fromJson(element);
                if (entry != null) parsed.add(entry);
            }
        } else {
            Entry entry = Entry.fromJson(root);
            if (entry != null) parsed.add(entry);
        }

        synchronized (ENTRIES) {
            for (Entry entry : parsed) {
                if (SEEN.add(entry.identity())) {
                    ENTRIES.add(entry);
                }
            }
        }
    }

    /** 已注册条目数（调试用） */
    public static int size() {
        synchronized (ENTRIES) {
            return ENTRIES.size();
        }
    }

    /** 当前条目快照（JEI 插件读取） */
    public static List<Entry> getEntries() {
        synchronized (ENTRIES) {
            return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
        }
    }

    // ---------------- 数据结构 ----------------

    /** 一条「获取方式」 */
    public static final class Entry {

        private final String itemId;
        private final JsonElement nbt;
        private final String keyItemId;
        private final String passItemId;
        private final List<String> extraLookups;
        private final List<Text> description;
        private final Text source;

        private Entry(String itemId, JsonElement nbt, String keyItemId, String passItemId,
                      List<String> extraLookups, List<Text> description, Text source) {
            this.itemId = itemId;
            this.nbt = nbt;
            this.keyItemId = keyItemId;
            this.passItemId = passItemId;
            this.extraLookups = extraLookups;
            this.description = description;
            this.source = source;
        }

        static Entry fromJson(JsonElement element) {
            if (element == null || !element.isJsonObject()) return null;

            JsonObject object = element.getAsJsonObject();
            String itemId = string(object, "item");
            if (itemId == null) return null;

            List<Text> description = new ArrayList<>();
            if (object.has("desc") && object.get("desc").isJsonArray()) {
                for (JsonElement line : object.getAsJsonArray("desc")) {
                    Text text = Text.fromJson(line);
                    if (text != null) description.add(text);
                }
            } else if (object.has("desc")) {
                Text text = Text.fromJson(object.get("desc"));
                if (text != null) description.add(text);
            }

            // nbt 两种写法都收：SNBT 字符串（脚本里 `${stack.nbt}`），或者 JSON 对象（脚本里直接传数据）
            JsonElement nbt = object.has("nbt") && !object.get("nbt").isJsonNull()
                    ? object.get("nbt") : null;

            // 额外反查物品：通关凭证 / 无限钥匙之类，左右键都要能翻到这条
            List<String> lookups = new ArrayList<>();
            if (object.has("lookup") && object.get("lookup").isJsonArray()) {
                for (JsonElement lookup : object.getAsJsonArray("lookup")) {
                    if (lookup.isJsonPrimitive()) {
                        String id = lookup.getAsString();
                        if (!id.isEmpty()) lookups.add(id);
                    }
                }
            }

            return new Entry(itemId,
                    nbt,
                    string(object, "key"),
                    string(object, "pass"),
                    lookups,
                    description,
                    Text.fromJson(object.get("source")));
        }

        private static String string(JsonObject object, String name) {
            if (!object.has(name) || object.get(name).isJsonNull()) return null;
            JsonElement value = object.get(name);
            if (!value.isJsonPrimitive()) return null;
            String text = value.getAsString();
            return text.isEmpty() ? null : text;
        }

        /** 去重标识 */
        String identity() {
            return itemId + '|' + (nbt == null ? "" : nbt)
                    + '|' + (keyItemId == null ? "" : keyItemId)
                    + '|' + (passItemId == null ? "" : passItemId)
                    + '|' + extraLookups
                    + '|' + description
                    + '|' + source;
        }

        public String getItemId() {
            return itemId;
        }

        /** 物品 NBT：SNBT 字符串或 JSON 对象，可能为 null */
        public JsonElement getNbt() {
            return nbt;
        }

        /** 获得它需要的副本钥匙（可能为 null） */
        public String getKeyItemId() {
            return keyItemId;
        }

        /** 额外可反查的物品 id（可能为 null） */
        public String getPassItemId() {
            return passItemId;
        }

        /** 额外反查物品 id 列表（通关凭证 / 无限钥匙等），左右键都要能翻到 */
        public List<String> getExtraLookups() {
            return extraLookups;
        }

        /** 描述（已按行拼好，渲染时再翻译） */
        public Component getDescription() {
            if (description.isEmpty()) return Component.empty();

            MutableComponent out = Component.empty();
            for (int i = 0; i < description.size(); i++) {
                if (i > 0) out.append("\n");
                out.append(description.get(i).toComponent());
            }
            return out;
        }

        /** 来源名（可能为 null） */
        public Component getSource() {
            return source == null ? null : source.toComponent();
        }
    }

    /** 一段可翻译文本节点 */
    public static final class Text {

        private final String literal;
        private final String key;
        private final String fallback;
        private final List<Text> args;

        private Text(String literal, String key, String fallback, List<Text> args) {
            this.literal = literal;
            this.key = key;
            this.fallback = fallback;
            this.args = args;
        }

        static Text fromJson(JsonElement element) {
            if (element == null || element.isJsonNull()) return null;

            if (element.isJsonPrimitive()) {
                String literal = element.getAsString();
                // 空串当成「没写」，否则会画出「来源：」这种半截前缀
                return literal.isEmpty() ? null : new Text(literal, null, null, Collections.emptyList());
            }
            if (!element.isJsonObject()) return null;

            JsonObject object = element.getAsJsonObject();
            if (!object.has("t")) {
                // 没写键，当成字面量
                return new Text(object.toString(), null, null, Collections.emptyList());
            }

            List<Text> args = new ArrayList<>();
            if (object.has("a") && object.get("a").isJsonArray()) {
                for (JsonElement arg : object.getAsJsonArray("a")) {
                    Text text = fromJson(arg);
                    if (text != null) args.add(text);
                }
            }

            String fallback = object.has("f") && !object.get("f").isJsonNull()
                    ? object.get("f").getAsString() : null;

            return new Text(null, object.get("t").getAsString(), fallback, args);
        }

        Component toComponent() {
            if (key == null) {
                return literal == null ? Component.empty() : Component.literal(literal);
            }

            Object[] resolved = new Object[args.size()];
            for (int i = 0; i < resolved.length; i++) {
                resolved[i] = args.get(i).toComponent();
            }

            if (fallback != null) {
                return Component.translatableWithFallback(key, fallback, resolved);
            }
            return Component.translatable(key, resolved);
        }

        @Override
        public String toString() {
            return key == null ? String.valueOf(literal) : key + args;
        }
    }
}
