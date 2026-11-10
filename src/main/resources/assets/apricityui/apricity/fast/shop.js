/* 森林杂货铺 —— 页面逻辑
 *
 * 数据来源：Java 侧的 FastShopUIBridge（@KJSBindings 注入成全局对象 FastShopUI）。
 * 这里只做「渲染 + 转发交易请求」，扣钱发货全在服务端。
 *
 * ── 收藏分类的持久化 ─────────────────────────────────────────────
 * 落盘走 Java 桥：FastShopUI.getFavorites() / setFavorites(String)，
 * 存到 <config>/fast/shop_favorites.txt（换行分隔，一行一个分类 key）。
 *
 * 曾经用晴雪UI 自带的 localStorage，结论是**在 Forge 上存不下来**：
 *   · global.js:4 注入裸 localStorage，setItem 确实会立刻 save()；
 *   · 但 LocalStorage.save() 是用 Mojang 方法名反射调
 *     CompoundTag.putString(String,String)，Forge 生产环境里方法名是 SRG
 *     （m_128381_），于是每次都抛 NoSuchMethodException，
 *     日志刷一片 "Failed to reflectively persist LocalStorage"，
 *     而 config/apricityui/localStorage.nbt 永远是空的。
 * 所以现在只把 localStorage 当降级兜底（Java 桥没同步到位时，至少当次会话有效）。
 *
 * 值用「换行分隔」而不是 JSON：Rhino 上 JSON 不保证存在，而 key 由我们自己控制，
 * 只要屏蔽换行就绝不会歧义。
 *
 * ── Rhino / 晴雪UI 约束（踩过的坑，别改回去）──────────────────────
 * 1. window 是 Java 对象（com.sighs.apricityui.init.Window），往上面挂任意属性
 *    会抛 EvaluatorException，整个脚本直接死掉。状态一律用 var 存。
 * 2. 只用 var + function：不用 const（循环体里的 const 在 KubeJS 的 Rhino 上有
 *    已知 bug）、不用箭头函数、不用模板字符串、不用可选链。
 * 3. document.createElement 造不出 <item> 这类扩展元素（它只做大写化），
 *    真正的构造在 HTML 解析器里 —— 所以商品格子全部走 innerHTML 生成。
 * 4. 页面脚本跑在 KubeJS 客户端的共享作用域里，会跨页面存活：
 *    所有全局都加 fs 前缀，且定时器必须在 unload 里清掉。
 */

var fsRows = [];
var fsMode = "buy";
var fsCategory = "";
var fsKeyword = "";
var fsSelected = null;
var fsQty = 1;
var fsMoney = 0;
var fsMax = 1;
var fsMenuOpen = false;
var fsDialogOpen = false;
var fsPoll = null;
var fsSettle = null;
var fsPinPoll = null;
var fsCategories = [];
var fsGridCount = 0;
var fsGridSig = "";
var fsPage = 0;
var fsPageCount = 1;
var fsTipRow = null;

/**
 * 当前「钉」在窗口右侧的那一条（详情弹窗打开时）。
 *
 * 必须和 fsTipRow 分开记：详情弹窗一开，货架被 display:none 收起，
 * AUI 的悬停跟踪发现光标下的元素变了，会立刻给刚收起的卡片补一发 mouseleave，
 * 于是 fsTipHide() 会被调到。如果两者共用一个状态，钉住的那份就会被这一发顺手清掉，
 * 表现是「详情弹窗里的 tooltip 一瞬间就消失了」。
 */
var fsPinRow = null;

/**
 * 右侧那份是不是「详情弹窗」钉的。
 *
 * 悬停已经不再往这个槽里塞东西，所以 fsPinRow 非空就一定是弹窗钉的。
 * 留这个标记只为 fsTipShow 开头那一句 —— 弹窗开着时别再弹跟鼠标的那份。
 */
var fsDialogPin = false;

/** 只在第一次悬停成功时往日志里报一次，用来定位「事件 → 页面 → Java」链路。 */
var fsTipLogged = false;

/** 每页条目数：195 个条目全渲染会把首屏排版拖到秒级，分页后才跑得动。
 *  页面是 mode=gui（CSS 像素 ≡ GUI 像素），货架一屏只放得下 3 行，
 *  8 列 × 3 = 24。 */
var FS_PAGE_SIZE = 24;

var FS_ALL = "";
var FS_NONE = "__none__";

/**
 * DOM 属性 data-c 里代表「全部分类」的占位值。
 *
 * 为什么不能直接把 FS_ALL（空串）写进 data-c：AUI 的 getAttribute 对空属性
 * 和「没有这个属性」返回的东西没法区分，而绑事件那边必须能一眼看出「这个节点
 * 不是分类项」。之前 `if (!key) return;` 就把全部分类当成无效节点直接跳过了 ——
 * 表现是「切到别的分类之后，全部分类点不回去」（切走之前它本来就是选中态，
 * 所以一开始看不出来）。
 */
var FS_ALL_DOM = "__all__";

/** 收藏分类在 localStorage 里的键名（改了就等于清空所有人的收藏，别乱改）。 */
var FS_FAV_KEY = "fast_shop_favorite_categories_v1";

/** 真分类 key 与 DOM data-c 值的互转。 */
function fsDomKey(key) {
    return (key === FS_ALL) ? FS_ALL_DOM : key;
}

function fsKeyFromDom(value) {
    var v = (value === null || value === undefined) ? "" : String(value);
    return (v === FS_ALL_DOM || v === "") ? FS_ALL : v;
}

/** 收藏的分类 key 列表，顺序即展示顺序。 */
var fsFavs = [];

/**
 * 分类顺序：两套，必须分开存。
 *
 *   · fsDefaultOrder —— 整合包作者用 Java API（{@code ShopManager.setCategoryOrder}）
 *     定的默认顺序，随商店数据一起下发。作者的东西，玩家改不动。
 *   · fsPersonalOrder —— 这个玩家自己拖动出来的顺序，按 UUID 落到
 *     {@code <config>/fast/shop_order/<uuid>.txt}，别人看不到。
 *
 * 渲染时合成一张排名表：玩家排过的排前面，剩下按作者顺序，再剩下按注册顺序。
 * 这样「玩家删掉的分类」「新加的分类」都不会因为顺序表里没有就消失或跑到最前。
 */
var fsDefaultOrder = [];
var fsPersonalOrder = [];

/** 拖动排序的实时状态。fsDragNode 为空表示没在拖。 */
var fsDragNode = null;

/** 被拎起来那一项原来位置上的空壳，占着坑给玩家看「会落到这里」。 */
var fsDragSlot = null;

/** 被拖那一项的原始 inline style，松手时原样写回。 */
var fsDragStyle = "";

/** 拎起来那一刻的光标位置：虚影的位置 = 原位 + (当前光标 − 这个点)，用 translate 表达。 */
var fsDragBaseX = 0;
var fsDragBaseY = 0;

/** 上一次写进 transform 的值，同值不重复写（写一次就是一次重排）。 */
var fsDragShift = "";

/**
 * 上一次光标压到的那一项。压到同一项就不动 DOM —— 空档每挪一次都是一次列表重排，
 * 每帧都挪会明显发卡。
 */
var fsDragHoverEl = null;

/** 「拖到所有分类之后」的判定线（列表最后一格的底边），拖动开始时量一次。 */
var fsDragListBottom = 0;

var fsDragKey = "";
var fsDragX = 0;
var fsDragY = 0;
var fsDragActive = false;

/**
 * 吞掉拖动结束后的那一次 click。
 *
 * 松手时列表会重排 / 重渲染，光标底下已经换了个按钮，那一下 click 会落到
 * 新按钮上 —— 表现就是「拖完分类，顺手切到了一个没想去的分类」。
 */
var fsDragSuppress = false;

/** 拖动死区（px）：位移小于它当成点击，不进入拖动。 */
var FS_DRAG_DEADZONE = 3;

/** 分类顺序没能落盘只报一次，别每次拖都刷日志。 */
var fsOrderWarned = false;

/** 「收藏没能落盘」只往日志里报一次，别每次右键都刷屏。 */
var fsFavWarned = false;

/** 搜索框的防抖定时器：打一个字就重建一次货架太贵，攒一下再渲染。 */
var fsSearchTimer = null;

/** 防抖间隔（ms）。太短挡不住连击，太长会觉得输入发涩。 */
var FS_SEARCH_DELAY = 180;

/** 上一次搜索输入的时间戳，只用于把「输入间隔」写进日志。 */
var fsLastInput = 0;

/** fsText 的写值缓存：同一个值不重复写 textContent，省掉一次样式 / 布局失效。 */
var fsTextCache = {};

/** 显隐 / 页码这类 inline style 的上次写值，避免同值反复写触发重排。 */
var fsApplied = {};

/** 图标放大：槽位逻辑尺寸 = 倍数 × 16，两个常量必须和 shop.css 里的
 *  --aui-slot-size / --aui-slot-icon-scale 以及盒子宽高保持一致，否则会偏心。
 *  页面已切成 mode=gui（CSS 像素 ≡ 原版 GUI 像素），货架卡只有 48px 宽，
 *  图标 32（scale 2，原版的 2 倍）正合适；详情弹窗 40（scale 2.5）。 */
var FS_ICON_SCALE = 2;
var FS_ICON_SIZE = 32;      // 2 × 16
var FS_PREVIEW_SIZE = 40;   // 2.5 × 16
var FS_PREVIEW_SCALE = 2.5;

// ============================================================
// 小工具
// ============================================================

function fsEl(id) {
    try {
        return document.getElementById(id);
    } catch (e) {
        return null;
    }
}

function fsText(id, value) {
    var el = fsEl(id);
    if (!el) return;

    var text = (value === null || value === undefined) ? "" : String(value);

    // 同一个值不重复写：textContent 每写一次都会让这个元素的样式 / 布局失效，
    // 而货架每次重渲染都要刷新「件数 / 页码 / 标题」这几个字段，绝大多数时候
    // 值根本没变 —— 白写一次就白排一次版。
    if (fsTextCache[id] === text) return;

    fsTextCache[id] = text;

    try {
        el.textContent = text;
    } catch (e) {
        try { el.setTextContent(text); } catch (x) {}
    }
}

/** 直接写过 textContent 的地方，要把缓存作废，否则 fsText 会以为「已经是这个值」而跳过。 */
function fsForgetText(id) {
    fsTextCache[id] = null;
}

/** 只在值真的变了才写 inline style —— 同值反复写会白白触发一次重排。 */
function fsSetDisplay(el, slot, value) {
    if (!el) return;
    if (fsApplied[slot] === value) return;

    fsApplied[slot] = value;

    try {
        el.style.display = value;
    } catch (e) {}
}

/** 只认千分位，不用 toLocaleString（Rhino 上没有可靠的 locale 数据）。 */
function fsNum(value) {
    var n = Math.floor(Number(value));
    if (isNaN(n)) return "0";

    var neg = n < 0;
    if (neg) n = -n;

    var s = String(n);
    var out = "";

    while (s.length > 3) {
        out = "," + s.substring(s.length - 3) + out;
        s = s.substring(0, s.length - 3);
    }

    return (neg ? "-" : "") + s + out;
}

/** 无千分位版本：货架卡片只有 48px 宽，「● 100,000」放不下会被裁，
 *  「● 100000」刚好。大数字的千分位只在详情弹窗里展示。 */
function fsNumPlain(value) {
    var n = Math.floor(Number(value));
    if (isNaN(n)) return "0";
    return String(n);
}

/** AUI 的 HTML 解析器只认 6 个实体，转义范围就按它来。 */
function fsEsc(value) {
    if (value === null || value === undefined) return "";

    var s = String(value);

    s = s.split("&").join("&amp;");
    s = s.split("<").join("&lt;");
    s = s.split(">").join("&gt;");
    s = s.split('"').join("&quot;");

    return s;
}

function fsRowAt(index) {
    if (typeof index !== "number" || isNaN(index)) return null;
    if (index < 0 || index >= fsRows.length) return null;
    return fsRows[index];
}

/** 毫秒时间戳。Rhino 上没有 performance.now，Date 最稳。 */
function fsNow() {
    try {
        return new Date().getTime();
    } catch (e) {
        return 0;
    }
}

function fsIsBuy() {
    return fsMode === "buy";
}

// ============================================================
// 收藏分类
// ============================================================

/**
 * 读收藏列表。
 *
 * 首选 Java 桥那支笔（FastShopUI.getFavorites），它把列表写进
 * <config>/fast/shop_favorites.txt，重启后一定还在。
 *
 * 为什么不用 AUI 自带的 localStorage：它的 save() 是用 **Mojang 方法名**反射
 * 调 net.minecraft.nbt.CompoundTag.putString(...)，而 Forge 生产环境里方法名是
 * SRG（m_128381_），于是每次 setItem 都抛
 *     NoSuchMethodException: CompoundTag.putString(String,String)
 * 日志里刷一片 ERROR，而 config/apricityui/localStorage.nbt 永远是空的 ——
 * 也就是「收藏压根存不下来」。这里保留 localStorage 只是降级兜底：
 * 万一 Java 桥没同步到位，至少当次会话内还能记住。
 */
function fsLoadFavs() {
    var raw = "";

    try {
        raw = String(FastShopUI.getFavorites() || "");
    } catch (e) {
        raw = "";
    }

    if (!raw) {
        try {
            raw = String(localStorage.getItem(FS_FAV_KEY) || "");
        } catch (e) {
            raw = "";
        }
    }

    fsFavs = [];

    if (!raw) return;

    var parts = raw.split(/\r?\n/);

    for (var i = 0; i < parts.length; i++) {
        var key = String(parts[i]).replace(/[\r\n]/g, "");

        if (!key) continue;
        if (key === FS_ALL) continue;   // 「全部」不是真分类，不参与收藏

        var dup = false;
        for (var j = 0; j < fsFavs.length; j++) {
            if (fsFavs[j] === key) dup = true;
        }

        if (!dup) fsFavs.push(key);
    }
}

/** 写收藏列表：主写 Java 桥的文件持久化；桥不在才退回 localStorage。 */
function fsSaveFavs() {
    var text = fsFavs.join("\n");
    var ok = false;

    try {
        ok = !!FastShopUI.setFavorites(text);
    } catch (e) {
        ok = false;
    }

    if (ok) return;

    if (!fsFavWarned) {
        fsFavWarned = true;
        fsPageLog("收藏没写进文件（Java 桥未同步？），退回 localStorage；本次会话内仍然有效");
    }

    try {
        localStorage.setItem(FS_FAV_KEY, text);
    } catch (e) {
        fsPageLog("收藏写入失败: " + e);
    }
}

function fsIsFav(key) {
    for (var i = 0; i < fsFavs.length; i++) {
        if (fsFavs[i] === key) return true;
    }
    return false;
}

/**
 * 右键分类 = 收藏 / 取消收藏。
 *
 * 只动 localStorage，不改动任何服务端数据；分类被阶段限制掉（AStages 没到）
 * 时这里也不会把它删掉 —— 只是暂时不显示，阶段到了自己就回来。
 */
function fsToggleFav(key) {
    if (!key || key === FS_ALL) return;

    var out = [];
    var removed = false;

    for (var i = 0; i < fsFavs.length; i++) {
        if (fsFavs[i] === key) {
            removed = true;
            continue;
        }
        out.push(fsFavs[i]);
    }

    if (!removed) out.push(key);

    fsFavs = out;
    fsSaveFavs();

    var stats = fsBuildCategories();
    fsRenderCategories(stats);
    fsRenderFavorites(stats);
}

/**
 * 分类图标。
 *
 * 只允许用「微软雅黑确定覆盖」的几何符号（U+25C6 U+25A0 U+25CF）。
 * 本页字体栈是 "MinecraftPixel","MCPixelJar","Microsoft YaHei"：前两个像素字体
 * 分别只有 156 / 99 个 ASCII 字形，中文和符号全靠雅黑兜底。实测雅黑覆盖：
 *   · 有 —— U+2605 U+2606 U+25CF U+25C6 U+25C7 U+25CB U+25A1 U+25A0 U+25AA U+25AB
 *             U+25B3 U+25B2 U+25BC U+00D7 U+2212 U+2039 U+203A U+2192 U+2460 U+00B7
 *             U+00A7 U+25CE U+203B U+3013
 *   · 没有 —— U+2661 U+2726 U+2715 U+273F U+2618 U+2315 U+25A3 U+2665 U+2660 U+2663，
 *             以及全部 emoji（U+1F331 / U+1F343 / U+1F33C 在 BMP 之外的补充平面，
 *             雅黑没有、系统也不保证 fallback，必定显示成豆腐块）
 * 之前这里用的三个图标恰好在「没有」那一批，已全部换掉。
 */
function fsCategoryIcon(key) {
    if (key === FS_ALL) return "◆";
    if (key === FS_NONE) return "■";
    return "●";
}

// ============================================================
// 悬停 tooltip
// ============================================================

/**
 * 说明文字不再由页面渲染，改成让引擎画一个「真正的原版 tooltip」。
 *
 * 好处：
 *   1. 样式就是原版的暗紫底 + 描边 + 像素字体，天然比手搓的 HTML 好看；
 *   2. 内容直接是带 Style 的 Component，颜色 / 加粗 / 斜体全部原生还原，
 *      不需要页面再解析 § 码（以前那句「只有符号和字，没有颜色」就是这么来的）；
 *   3. 白字会画在原版深色底纹上，不会再隐没在详情弹窗的浅色背景里。
 *
 * 页面只负责喊「悬停哪一条 / 钉住哪一条 / 收起」，坐标由 Java 侧自己算，
 * 避免页面逻辑像素和 GUI 缩放坐标对不上。
 */

/** 只收掉「跟鼠标」的那一份；钉住的那份由 fsPinHide() 负责。 */
function fsTipHide() {
    fsTipRow = null;

    try {
        FastShopUI.hideTooltip();
    } catch (e) {
        fsPageLog("hideTooltip 调用失败: " + e);
    }
}

/** 详情弹窗打开：把这一条的 tooltip 钉在窗口上（坐标由 Java 侧算，位置 / 拖动交给 Pin Tooltips）。 */
function fsPinShow(row) {
    if (!row) return;

    fsDialogPin = true;
    fsPinRow = row;

    try {
        FastShopUI.showPinnedTooltip(row.i);
    } catch (e) {
        fsPageLog("showPinnedTooltip(" + row.i + ") 调用失败: " + e);
    }
}

/**
 * 收起钉住的那份：关详情弹窗 / 关页面时调用。
 *
 * 这里刻意不做「fsPinRow 为空就直接 return」那种提前返回：
 * 页面状态（fsPinRow / fsDialogPin）和 Java 侧那份是两份独立的状态，
 * 一旦有哪条路径漏掉一次同步，提前返回就会让 Java 侧那份永远收不掉。
 * 反正 ShopTooltipState.clearPinned() 在没东西的时候自己会返回，这里每次都喊一遍最省心。
 */
function fsPinHide() {
    fsPinRow = null;
    fsDialogPin = false;

    try {
        FastShopUI.hidePinnedTooltip();
    } catch (e) {
        fsPageLog("hidePinnedTooltip 调用失败: " + e);
    }
}

/**
 * 鼠标移到商品格子上：只显示光标旁那份跟鼠标的原版 tooltip。
 *
 * 右侧「钉住」的那一份只属于详情弹窗，悬停时不再钉 ——
 * 以前两者一起弹，看起来就是「鼠标划过物品会多冒出一份说明」，没必要。
 */
function fsTipShow(row) {
    if (!row) return;

    // 详情弹窗钉住时不显示跟鼠标的那份（这时货架也收起了，正常到不了这里）
    if (fsDialogPin) return;

    // mousemove 触发得非常频繁，同一条目不要重复过桥
    if (fsTipRow === row) return;

    fsTipRow = row;

    try {
        FastShopUI.showTooltip(row.i);

        // 只在第一次成功时汇报一次：日志里出现这行说明「鼠标事件 → 页面 → Java」
        // 这条链路是通的，问题就只可能在渲染那一步了。
        if (!fsTipLogged) {
            fsTipLogged = true;
            fsPageLog("首次悬停成功 → showTooltip(" + row.i + ")");
        }
    } catch (e) {
        fsPageLog("showTooltip(" + row.i + ") 调用失败: " + e);
    }
}

/**
 * 把页面里被 catch 掉的错误送到游戏日志。
 *
 * 页面脚本里到处是 try/catch（防止一个方法抛异常把整页带崩），
 * 但那样出错时日志里完全没有痕迹，界面上只表现为「点了没反应」。
 */
function fsPageLog(message) {
    try {
        FastShopUI.log(String(message));
    } catch (e) {}
}

// ============================================================
// 读数据
// ============================================================

function fsLoadRows() {
    fsRows = [];

    var total = 0;

    try {
        total = FastShopUI.getCount();
    } catch (e) {
        return false;
    }

    for (var i = 0; i < total; i++) {
        fsRows.push(fsBuildRow(i));
    }

    return true;
}

/**
 * 一条目的展示数据。
 *
 * 每个字段单独 try：桥接对象里任意一个方法抛异常，最多丢掉这一个字段，
 * 不会把整个脚本带崩（上一版就是这么全页空白的）。
 */
function fsBuildRow(index) {
    var row = {
        i: index,
        e: "",
        n: "",
        p: 0,
        b: true,
        c: FS_NONE,
        cn: "",
        have: 0
    };

    try { row.e = String(FastShopUI.getExpression(index)); } catch (e) {}
    try { row.n = String(FastShopUI.getName(index)); } catch (e) {}
    // 注意：tooltip 内容不在这里取。条目有 190+ 个，开屏时全算一遍会卡住渲染线程，
    // Java 侧改成按需懒算并缓存（悬停 / 打开详情时才第一次算，见 showTooltip）。
    try { row.p = Number(FastShopUI.getPrice(index)) || 0; } catch (e) {}
    try { row.b = !!FastShopUI.isBuy(index); } catch (e) {}
    try { row.c = String(FastShopUI.getCategory(index)); } catch (e) {}
    try { row.cn = String(FastShopUI.getCategoryName(index)); } catch (e) {}
    try { row.have = Number(FastShopUI.getHave(index)) || 0; } catch (e) {}

    if (!row.c) row.c = FS_NONE;

    return row;
}

function fsLoadMoney() {
    try {
        fsMoney = Number(FastShopUI.getMoney()) || 0;
    } catch (e) {
        fsMoney = 0;
    }
}

/** 背包持有数变化后重新拉一次（买卖之后调）。 */
function fsResync() {
    try {
        FastShopUI.refresh();
    } catch (e) {}

    for (var i = 0; i < fsRows.length; i++) {
        try {
            fsRows[i].have = Number(FastShopUI.getHave(fsRows[i].i)) || 0;
        } catch (e) {}
    }
}

// ============================================================
// 分类顺序
// ============================================================

/** 玩家排序的兜底键（Java 桥写不进去时才用，见 fsSavePersonalOrder）。 */
var FS_ORDER_KEY = "fast_shop_category_order_v1";

/** "a\nb\nc" → ["a","b","c"]；空行、重复项都丢掉，顺序保持原样。 */
function fsSplitKeys(raw) {
    var out = [];

    if (!raw) return out;

    var parts = String(raw).split(/\r?\n/);

    for (var i = 0; i < parts.length; i++) {
        var key = String(parts[i]).replace(/[\r\n]/g, "");

        if (!key) continue;
        if (key === FS_ALL) continue;   // 「全部」不是真分类，不进顺序表

        var dup = false;
        for (var j = 0; j < out.length; j++) {
            if (out[j] === key) dup = true;
        }

        if (!dup) out.push(key);
    }

    return out;
}

/**
 * 读两套顺序。
 *
 * 都在开页面时读一次：默认顺序来自 Java（服务端下发的那份），玩家顺序来自
 * 玩家自己的文件。两个读取都失败也不影响商店能用，只是退回注册顺序。
 */
function fsLoadOrders() {
    var rawDefault = "";
    var rawPersonal = "";

    try {
        rawDefault = String(FastShopUI.getDefaultOrder() || "");
    } catch (e) {
        rawDefault = "";
    }

    try {
        rawPersonal = String(FastShopUI.getPersonalOrder() || "");
    } catch (e) {
        rawPersonal = "";
    }

    if (!rawPersonal) {
        try {
            rawPersonal = String(localStorage.getItem(FS_ORDER_KEY) || "");
        } catch (e) {
            rawPersonal = "";
        }
    }

    fsDefaultOrder = fsSplitKeys(rawDefault);
    fsPersonalOrder = fsSplitKeys(rawPersonal);

    // 一行诊断：分类顺序出问题时，先看这两串是不是空的 / 是不是对得上，
    // 就不用去猜「是服务端没下发，还是本地文件没读到」。
    fsPageLog("[perf] 分类顺序：默认 " + (fsDefaultOrder.length > 0 ? fsDefaultOrder.join(",") : "（无）")
        + " / 玩家 " + (fsPersonalOrder.length > 0 ? fsPersonalOrder.join(",") : "（无）"));
}

/** 写玩家顺序：主写 Java 桥（按 UUID 落盘），桥不在才退回 localStorage。 */
function fsSavePersonalOrder() {
    var text = fsPersonalOrder.join("\n");
    var ok = false;

    try {
        ok = !!FastShopUI.setPersonalOrder(text);
    } catch (e) {
        ok = false;
    }

    if (ok) return;

    if (!fsOrderWarned) {
        fsOrderWarned = true;
        fsPageLog("分类顺序没写进文件（Java 桥未同步？），退回 localStorage；本次会话内仍然有效");
    }

    try {
        localStorage.setItem(FS_ORDER_KEY, text);
    } catch (e) {
        fsPageLog("分类顺序写入失败: " + e);
    }
}

/**
 * 按「玩家顺序 → 作者顺序 → 注册顺序」排 fsCategories（不含「全部」，它由调用方插到最前）。
 *
 * 排序必须自己带次级键：Rhino 的 Array#sort 不保证稳定，同一个档位里的分类
 * 如果只返回 0，几次排序下来的先后可能不一样（界面会「自己乱跳」）。
 */
function fsSortCategories() {
    var rank = {};
    var next = 0;
    var i;

    for (i = 0; i < fsPersonalOrder.length; i++) {
        if (rank[fsPersonalOrder[i]] === undefined) {
            rank[fsPersonalOrder[i]] = next;
            next++;
        }
    }

    for (i = 0; i < fsDefaultOrder.length; i++) {
        if (rank[fsDefaultOrder[i]] === undefined) {
            rank[fsDefaultOrder[i]] = next;
            next++;
        }
    }

    var origin = {};
    for (i = 0; i < fsCategories.length; i++) {
        origin[fsCategories[i]] = i;
    }

    fsCategories.sort(function (a, b) {
        var ra = (rank[a] === undefined) ? 9999 : rank[a];
        var rb = (rank[b] === undefined) ? 9999 : rank[b];

        if (ra !== rb) return ra - rb;

        return origin[a] - origin[b];
    });
}

// ============================================================
// 分类
// ============================================================

/**
 * 从「当前这一档（买 / 卖）真实存在的条目」反推分类表。
 *
 * 这里天然就满足「空分类不显示」：服务端已经按阶段（AStages）过滤过条目，
 * 玩家阶段没到的商品压根不会下发，于是那个分类在这一档里个数为 0，
 * 下游渲染直接跳过它 —— 不需要客户端再单独判一次阶段。
 *
 * 返回 { counts, labels }，同时刷新 fsCategories（含开头的「全部」）。
 */
function fsBuildCategories() {
    fsCategories = [];

    var wantBuy = fsIsBuy();
    var seen = {};
    var counts = {};
    var labels = {};

    for (var i = 0; i < fsRows.length; i++) {
        var row = fsRows[i];

        if (row.b !== wantBuy) continue;

        var key = row.c;

        if (!seen[key]) {
            seen[key] = true;
            counts[key] = 0;
            labels[key] = (key === FS_NONE) ? "未分类" : (row.cn || key);
            fsCategories.push(key);
        }

        counts[key] = counts[key] + 1;
    }

    // 顺序走「玩家拖的 → 作者用 API 定的 → 注册顺序」，见 fsSortCategories
    fsSortCategories();

    // 「全部」永远排在最前
    fsCategories.unshift(FS_ALL);

    return { counts: counts, labels: labels };
}

function fsCategoryLabel(key, stats) {
    if (key === FS_ALL) return "全部分类";
    if (key === FS_NONE) return "未分类";

    if (stats && stats.labels && stats.labels[key]) return stats.labels[key];

    for (var i = 0; i < fsRows.length; i++) {
        if (fsRows[i].c === key) {
            return fsRows[i].cn || key;
        }
    }

    return key;
}

// ============================================================
// 过滤
// ============================================================

function fsMatches(row, keyword) {
    if (row.b !== fsIsBuy()) return false;

    if (fsCategory !== FS_ALL && row.c !== fsCategory) return false;

    if (!keyword) return true;

    var haystack = (row.n + " " + row.e + " " + row.cn).toLowerCase();

    return haystack.indexOf(keyword) >= 0;
}

function fsVisible(keyword) {
    var out = [];

    for (var i = 0; i < fsRows.length; i++) {
        if (fsMatches(fsRows[i], keyword)) out.push(fsRows[i]);
    }

    return out;
}

// ============================================================
// 渲染
// ============================================================

function fsRenderMoney() {
    fsLoadMoney();
    fsText("money", fsNum(fsMoney));
}

function fsRenderTabs() {
    var buy = fsEl("tab-buy");
    var sell = fsEl("tab-sell");

    if (buy) buy.className = "trade-tab" + (fsIsBuy() ? " active" : "");
    if (sell) sell.className = "trade-tab" + (fsIsBuy() ? "" : " active");
}

/**
 * 货架的显隐。<item> 的物品图标走 AUI 的 body 渲染层单独绘制，
 * 不受 DOM 层级和弹窗遮挡控制 —— 弹窗 / 分类菜单打开时必须把货架整个藏起来，
 * 否则货架上的物品贴图会透到二级菜单上面。
 */
function fsApplyGridDisplay() {
    var grid = fsEl("product-grid");
    if (!grid) return;

    var covered = fsDialogOpen || fsMenuOpen;

    // 货架收起来的时候 tooltip 也必须跟着收，否则会浮在弹窗上面
    if (covered || fsGridCount === 0) fsTipHide();

    // 值没变就别写：inline style 同值反复写，AUI 也会重新走一遍样式 / 布局
    fsSetDisplay(grid, "grid", (covered || fsGridCount === 0) ? "none" : "grid");

    var empty = fsEl("empty-state");

    if (empty) {
        var emptyCls = "empty-state" + (fsGridCount === 0 && !covered ? " show" : "");

        // className 同值反复写也会让 AUI 重新算一遍样式（可能整份文档重算），
        // 搜索时这里每敲一次都会走到，所以必须挡住
        if (fsApplied.emptyCls !== emptyCls) {
            fsApplied.emptyCls = emptyCls;
            empty.className = emptyCls;
        }
    }

    // 分页条里没有 <item>，但弹窗盖住时一起收掉更干净
    var pagerBox = fsEl("pager");

    if (pagerBox) {
        fsSetDisplay(pagerBox, "pagerBox",
            (covered || fsGridCount === 0 || fsPageCount <= 1) ? "none" : "flex");
    }
}

/** 切出当前页，并把页码夹到合法范围。 */
function fsSlicePage(list) {
    var pages = Math.max(1, Math.ceil(list.length / FS_PAGE_SIZE));

    if (fsPage >= pages) fsPage = pages - 1;
    if (fsPage < 0) fsPage = 0;

    var start = fsPage * FS_PAGE_SIZE;
    var end = start + FS_PAGE_SIZE;

    if (end > list.length) end = list.length;

    var out = [];

    for (var i = start; i < end; i++) {
        out.push(list[i]);
    }

    return { list: out, pages: pages };
}

function fsRenderPager() {
    var pager = fsEl("pager");
    if (!pager) return;

    if (fsPageCount <= 1) {
        fsSetDisplay(pager, "pager", "none");
        return;
    }

    fsSetDisplay(pager, "pager", "flex");
    fsText("page-label", (fsPage + 1) + " / " + fsPageCount + " 页");

    // disabled 属性在 AUI 的元素上不保证可写，失败就当没这回事
    try {
        var pv = fsEl("page-prev");
        if (pv) {
            var pvOff = (fsPage === 0);
            if (fsApplied.prevOff !== pvOff) {
                fsApplied.prevOff = pvOff;
                pv.disabled = pvOff;
            }
        }

        var nx = fsEl("page-next");
        if (nx) {
            var nxOff = (fsPage >= fsPageCount - 1);
            if (fsApplied.nextOff !== nxOff) {
                fsApplied.nextOff = nxOff;
                nx.disabled = nxOff;
            }
        }
    } catch (e) {}
}

function fsSetPage(next) {
    fsPage = next;
    fsRenderGrid(true);
}

function fsRenderGrid(force) {
    var grid = fsEl("product-grid");
    if (!grid) return;

    var keyword = fsKeyword.toLowerCase();
    var list = fsVisible(keyword);
    var slice = fsSlicePage(list);

    fsGridCount = slice.list.length;
    fsPageCount = slice.pages;

    // 列表没变就别碰 DOM：innerHTML 重建会把所有 <item> 表达式重新解析一遍
    // 并触发一次全局重排，搜索框每敲一个字都重建会卡。
    var sig = "";

    for (var i = 0; i < slice.list.length; i++) {
        sig += slice.list[i].i + ",";
    }

    var changed = force === true || sig !== fsGridSig;
    fsGridSig = sig;

    // 货架内容变了（搜索 / 切分类 / 翻页 / 交易结算）之后，悬停联动的那份可能已经
    // 不在列表里了，收掉免得显示一条过期信息；详情弹窗钉住的那份不受影响。
    // fsTipRow 也一起清：它必须只指向「当前这一页上真实存在的卡片」，
    // 否则对账逻辑会拿着一个过期 row 把 tooltip 又钉回来。
    if (changed && !fsDialogPin) {
        fsTipRow = null;
        fsPinHide();
    }

    if (changed) {
        var tRebuild = fsNow();
        var html = "";

        for (var n = 0; n < slice.list.length; n++) {
            var row = slice.list[n];

            // 图标放大走引擎原生的 <slot size iconScale>，不用 CSS transform：
            // transform 会把物推进另一条图层绘制路径，贴图会被压暗。
            // render="item" + --aui-slot-render-bg:0 + background:none 三层挡掉
            // global.css 给 slot 的深灰底纹 area.png。
            html += '<button type="button" class="product"'
                + ' data-i="' + row.i + '">'
                + '<span class="product-icon">'
                + '<slot render="item"'
                + ' size="' + FS_ICON_SIZE + '"'
                + ' iconScale="' + FS_ICON_SCALE + '"'
                + ' interactive="0" pointer="0">'
                + "<item>" + fsEsc(row.e) + "</item>"
                + "</slot>"
                + "</span>"
                + '<span class="product-price' + (row.b ? "" : " sell-price") + '">'
                + "● " + fsNumPlain(row.p)
                + "</span>"
                + "</button>";
        }

        grid.innerHTML = html;

        // innerHTML 生成的节点是全新的，事件必须重新绑
        var cards = grid.children;

        for (var k = 0; k < cards.length; k++) {
            fsBindCard(cards[k]);
        }

        // 这一段是「解析 innerHTML + 造 32 个 slot/item」的纯 JS 侧耗时，
        // 不含 AUI 那边的样式重算和排版（那些在渲染线程下一帧才跑）。
        // 排查「搜索卡顿」就靠它：正常情况下 32 格只有几十毫秒，
        // 如果这里莫名其妙几百毫秒，或者日志里一次输入刷出一堆这行，就是重建风暴。
        fsPageLog("[perf] 重建货架 " + slice.list.length + " 格 "
            + (fsNow() - tRebuild) + "ms（关键字=「" + fsKeyword + "」分类=「" + fsCategory + "」）");
    }

    fsApplyGridDisplay();
    fsRenderPager();

    fsText("shelf-count", list.length + " 件好物");
    fsText("shelf-title", "◆ " + (fsIsBuy() ? "今日上架" : "收购清单"));
}

function fsBindCard(node) {
    node.addEventListener("click", function () {
        var raw = node.getAttribute("data-i");
        fsTipHide();
        fsOpenDetail(parseInt(raw, 10));
    });

    // 悬停出原版 tooltip。mousemove 会随光标持续刷新，所以要 stopPropagation。
    node.addEventListener("mousemove", function (event) {
        var row = fsRowAt(parseInt(node.getAttribute("data-i"), 10));

        if (!row) return;

        fsTipShow(row);

        try {
            if (event) event.stopPropagation();
        } catch (e) {}
    });

    // 必须用 mouseleave 而不是 mouseout：mouseout 会冒泡，
    // 光标从按钮挪到它自己的子元素（span / slot）上时也会触发，
    // tooltip 就会一闪一闪。mouseleave 只在真正离开按钮时触发。
    node.addEventListener("mouseleave", function () {
        fsTipHide();
    });
}

/**
 * 分类菜单。
 *
 * 找不到 / 被阶段限制掉的分类直接不出现 —— count 是「这一档里真实存在的条目数」，
 * 服务端下发前已经过 AStages 过滤，所以个数为 0 就等于「玩家还看不到这个分类」。
 */
function fsRenderCategories(stats) {
    var list = fsEl("category-list");
    if (!list) return;

    stats = stats || fsBuildCategories();

    var total = 0;

    for (var i = 0; i < fsRows.length; i++) {
        if (fsRows[i].b === fsIsBuy()) total++;
    }

    // 当前分类在切换模式后不存在了就退回全部
    if (fsCategory !== FS_ALL) {
        var found = false;
        for (var j = 0; j < fsCategories.length; j++) {
            if (fsCategories[j] === fsCategory) found = true;
        }
        if (!found) fsCategory = FS_ALL;
    }

    var html = "";

    for (var n = 0; n < fsCategories.length; n++) {
        var key = fsCategories[n];
        var label = (key === FS_ALL) ? "全部分类" : stats.labels[key];
        var count = (key === FS_ALL) ? total : stats.counts[key];

        // 空分类不显示（阶段没到的分类到这里个数就是 0）
        if (count <= 0 && key !== FS_ALL) continue;

        html += '<button type="button" class="category-option'
            + (fsCategory === key ? " active" : "") + '"'
            + ' data-c="' + fsEsc(fsDomKey(key)) + '">'
            + '<span class="category-option-main">'
            + '<span class="category-option-icon">' + fsCategoryIcon(key) + "</span>"
            + '<span class="category-option-name">' + fsEsc(label) + "</span>"
            + "</span>"
            + '<span class="option-count">' + count + " 件</span>";

        if (key !== FS_ALL && fsIsFav(key)) {
            html += '<span class="favorite-mark">★</span>';
        }

        html += "</button>";
    }

    list.innerHTML = html;

    var options = list.children;

    for (var m = 0; m < options.length; m++) {
        fsBindCategory(options[m]);
    }

    fsText("category-label", fsCategoryLabel(fsCategory, stats));
}

function fsBindCategory(node) {
    // 注意：这一句不能用 `if (!key) return;` —— 「全部分类」的 data-c 以前就是空串，
    // 会被当成无效节点直接跳过，于是「切了分类就点不回全部分类」。
    // 现在全部分类在 DOM 里是 FS_ALL_DOM，只有「属性真的缺失」才会为空。
    var raw = node.getAttribute("data-c");
    if (raw === null || raw === undefined || raw === "") return;

    var key = fsKeyFromDom(raw);

    // 右键 = 收藏 / 取消收藏，所以要单独留住原值（全部分类不能收藏）
    node.addEventListener("click", function () {
        // 刚拖完的那一下 click 要丢掉：松手时列表已经重排，这一下会落到别的分类上
        if (fsDragSuppress) {
            fsDragSuppress = false;
            return;
        }

        fsSetCategory(key);
    });

    // 按住拖动 = 排序。这一条对「全部」也绑：它自己不参与拖动（见 fsDragBegin），
    // 但按一下必须能把上一轮留下的 fsDragSuppress 清掉，否则点它会被吞。
    node.addEventListener("mousedown", function (event) {
        fsDragBegin(node, key, event);
    });

    // AUI 支持 contextmenu（见 docs/en/guide/web-api.md 事件表），
    // preventDefault 只是表明意图 —— AUI 本来就没有原生右键菜单。
    if (key !== FS_ALL) {
        node.addEventListener("contextmenu", function (event) {
            try { if (event) event.preventDefault(); } catch (e) {}
            fsToggleFav(key);
        });
    }
}

// ============================================================
// 拖动排序
// ============================================================
//
// 手感照「拿起一件物品」来做，而不是把列表里的格子一格一格挪：
//   ① 越过死区 → 这一项被「拎起来」：挂到 body 上、position:fixed、跟着光标走，
//      原本的位置留一个同样大小的空壳（fsDragSlot）当占位；
//   ② 拖动过程中移动的是那个**空壳**，所以列表的排版一直是稳定的；
//   ③ 松手 → 把真身插回空壳的位置、还原样式、把顺序落盘。
//
// 为什么必须挂到 body：分类菜单位于 .shop 里，而 .shop 有 overflow:hidden，
// 留在列表里的元素出不了面板边框，拖到边上就会被裁掉 —— 那样根本不像「拎起来」。
//
// 监听挂在 document 而不是列表上：拖动时光标会离开列表甚至离开菜单，
// 挂在列表上就只能等鼠标移回来才继续跟。位移小于 FS_DRAG_DEADZONE 当成点击，
// 所以「点一下进分类」不受影响。

/** 拿元素在页面里的矩形（CSS 逻辑像素）。量不到（display:none / 还没排版）返回 null。 */
function fsRect(el) {
    if (!el) return null;

    try {
        var r = el.getBoundingClientRect();

        if (!r || !r.width || !r.height) return null;

        return r;
    } catch (e) {
        return null;
    }
}

/** 元素是不是某个父节点的孩子（比引用，不依赖 sibling 这类属性）。 */
function fsHasChild(parent, node) {
    if (!parent || !node) return false;

    var kids = parent.children;

    for (var i = 0; i < kids.length; i++) {
        if (kids[i] === node) return true;
    }

    return false;
}

/** 元素的 class 里有没有这一项（按空格切开逐个比，不能用 indexOf —— 
 *  `.category-option-name` 这种内层 span 也含有 "category-option" 字样）。 */
function fsHasClass(node, name) {
    if (!node) return false;

    var cls = "";

    try {
        cls = String(node.className || "");
    } catch (e) {
        return false;
    }

    var parts = cls.split(" ");

    for (var i = 0; i < parts.length; i++) {
        if (parts[i] === name) return true;
    }

    return false;
}

/**
 * 光标现在压在哪一项上（null = 没压在分类项上）。
 *
 * <p>直接问事件的目标元素、再沿父链往上找 `.category-option` —— 比「量一堆矩形、
 * 自己算格子」稳得多：flex-wrap 怎么换行、每行几列、视口怎么缩放，这里都不用跟着改。
 * 被拖的那一项已经挂到 body 上（不在列表里），空壳要从结果里排掉。
 */
function fsDragHover(target) {
    var node = target;

    for (var i = 0; i < 8 && node; i++) {

        if (node === fsDragSlot) return null;

        if (fsHasClass(node, "category-option")) {
            return (node === fsDragNode) ? null : node;
        }

        try {
            node = node.parentElement;
        } catch (e) {
            node = null;
        }
    }

    return null;
}

function fsDragBegin(node, key, event) {
    var button = 0;

    try {
        button = Number(event.button);
    } catch (e) {
        button = 0;
    }

    // 上一轮拖动吞掉的是「上一次」的点击，这里清掉 —— 任何一项按下都清，
    // 否则吞过一次之后，下一个点到「全部」上的点击会被莫名其妙吃掉。
    fsDragSuppress = false;

    // 「全部」永远排最前，不参与拖动；右键是收藏，也不拖
    if (key === FS_ALL || button === 2) {
        fsDragNode = null;
        fsDragKey = "";
        fsDragActive = false;
        return;
    }

    fsDragNode = node;
    fsDragKey = key;
    fsDragActive = false;

    try {
        fsDragX = Number(event.clientX) || 0;
        fsDragY = Number(event.clientY) || 0;
    } catch (e) {
        fsDragX = 0;
        fsDragY = 0;
    }
}

/**
 * 把这一项「拎起来」：挂到 body 上跟着光标，原位留一个空壳。
 *
 * @return true 表示成功进入「浮空拖动」；false 表示这一项现在量不到（被收起 / 还没排版），
 *         调用方直接放弃这次拖动，不要退化成挪格子 —— 两种手感混在一起更难受。
 */
function fsDragLift(x, y) {
    var node = fsDragNode;
    var list = fsEl("category-list");

    if (!node || !list) return false;

    var rect = fsRect(node);
    if (!rect) return false;

    // 「拖到所有分类之后」的判定线：列表最后一格的底边。整个拖动过程中行数不变，
    // 所以量一次就够，不用每帧再问引擎。
    var kids = list.children;
    var lastRect = kids.length > 0 ? fsRect(kids[kids.length - 1]) : null;

    fsDragListBottom = lastRect ? lastRect.bottom : 0;

    // 空壳：同一个 class 的空按钮，尺寸跟原项一致，占住那个坑
    var slot = null;

    try {
        slot = node.cloneNode(false);
        slot.className = "category-option drop-slot";
        slot.innerHTML = "";
        slot.style.width = rect.width + "px";
        slot.style.height = rect.height + "px";
        list.insertBefore(slot, node);
    } catch (e) {
        return false;
    }

    // 真身挪到 body：脱离 .shop 的 overflow 裁剪，才能拖出面板边框
    try {
        fsDragStyle = String(node.getAttribute("style") || "");
        document.body.appendChild(node);

        var style = node.style;

        style.position = "fixed";
        style.left = rect.left + "px";
        style.top = rect.top + "px";
        style.width = rect.width + "px";
        style.height = rect.height + "px";
        style.margin = "0";
        style.zIndex = "200";
        style.opacity = "0.92";
        style.pointerEvents = "none";

        node.className = "category-option dragging";
    } catch (e) {
        // 挂不上去就退回原位，别留一个半残状态
        fsDragDropSlot(slot);
        return false;
    }

    fsDragSlot = slot;

    // 虚影的位置不再每帧改 left/top（那是布局属性，改一次整页重排一次，
    // 拖起来会又卡又追不上手），改成从原位出发的 translate 位移。
    fsDragBaseX = x;
    fsDragBaseY = y;
    fsDragShift = "";
    fsDragHoverEl = null;

    return true;
}

/** 把空壳从列表里摘掉（能摘就摘，摘不掉也没关系）。 */
function fsDragDropSlot(slot) {
    try {
        if (slot) slot.remove();
    } catch (e) {}
}

function fsDragMove(event) {
    if (!fsDragNode) return;

    var x = 0;
    var y = 0;

    try {
        x = Number(event.clientX) || 0;
        y = Number(event.clientY) || 0;
    } catch (e) {
        return;
    }

    if (!fsDragActive) {
        var dx = x - fsDragX;
        var dy = y - fsDragY;

        if (dx * dx + dy * dy < FS_DRAG_DEADZONE * FS_DRAG_DEADZONE) return;

        // 拎不起来就直接作废这次拖动（不进入「挪格子」的降级路径）
        if (!fsDragLift(x, y)) {
            fsDragNode = null;
            fsDragKey = "";
            return;
        }

        fsDragActive = true;
    }

    // 虚影跟着光标：只写 transform（纯位移，不进布局），而且值没变就不写
    try {
        var shift = "translate(" + (x - fsDragBaseX) + "px," + (y - fsDragBaseY) + "px)";

        if (shift !== fsDragShift) {
            fsDragShift = shift;
            fsDragNode.style.transform = shift;
        }
    } catch (e) {}

    var list = fsEl("category-list");
    if (!list || !fsDragSlot) return;

    // 光标压在哪一项上，空档就插到那一项前面（= 被拖的这项占掉它的位置，它往后挤一格）。
    // 压到列表下方的空白就当成「放到最后」。
    var hovered = fsDragHover(event ? event.target : null);

    if (hovered !== fsDragHoverEl) {
        fsDragHoverEl = hovered;

        try {
            if (hovered) {
                list.insertBefore(fsDragSlot, hovered);
            } else if (y > fsDragListBottom) {
                list.appendChild(fsDragSlot);
            }
        } catch (e) {
            // 空档挪不动就算了：松手时按它当前的位置落地，最差也只是位置没跟着走
        }
    }

    // 拖的时候别让下面的按钮继续 hover / 起 tooltip
    try { if (event.preventDefault) event.preventDefault(); } catch (e) {}
}

/**
 * 把真身放回空壳的位置，并还原内联样式。
 *
 * @param adopt true = 保留在列表里（正常松手）；false = 只收拾干净不回到列表
 *               （页面/菜单被关掉时用，列表可能马上就被重建了）
 */
function fsDragPutBack(adopt) {
    var node = fsDragNode;
    var list = fsEl("category-list");

    if (!node) return;

    if (adopt && list && fsDragSlot && fsHasChild(list, fsDragSlot)) {
        try {
            list.insertBefore(node, fsDragSlot);
        } catch (e) {
            try { list.appendChild(node); } catch (x) {}
        }
    } else if (adopt && list && !fsHasChild(list, node)) {
        // 空壳没了（比如中途重渲染过），至少别把这一项弄丢
        try { list.appendChild(node); } catch (e) {}
    }

    fsDragDropSlot(fsDragSlot);

    try {
        node.setAttribute("style", fsDragStyle || "");
        node.className = "category-option";
    } catch (e) {}
}

function fsDragCancel() {
    if (fsDragNode) fsDragPutBack(false);

    fsDragNode = null;
    fsDragSlot = null;
    fsDragStyle = "";
    fsDragKey = "";
    fsDragActive = false;
    fsDragShift = "";
    fsDragHoverEl = null;
    fsDragListBottom = 0;
}

/**
 * 松手：把「拖完的顺序」落盘成这个玩家自己的顺序。
 *
 * 顺序以拖动后的 DOM 为准 —— 那正是玩家眼睛看到的先后。当前这一档没渲染出来的
 * 分类（阶段还没解锁 / 这一档没货）不会丢：它们按原来的相对顺序接在后面，
 * 等解锁了自然回到它们该在的位置。
 */
function fsDragEnd() {
    var moved = fsDragActive;

    if (!fsDragNode) return;

    if (!moved) {
        // 只是点了一下：把按下时记的状态清掉，什么都不改
        fsDragNode = null;
        fsDragKey = "";
        return;
    }

    fsDragPutBack(true);

    fsDragNode = null;
    fsDragSlot = null;
    fsDragStyle = "";
    fsDragKey = "";
    fsDragActive = false;
    fsDragShift = "";
    fsDragHoverEl = null;
    fsDragListBottom = 0;

    fsDragSuppress = true;

    var list = fsEl("category-list");
    var merged = [];

    if (list) {
        var kids = list.children;

        for (var i = 0; i < kids.length; i++) {
            var key = fsKeyFromDom(kids[i].getAttribute("data-c"));

            if (!key || key === FS_ALL) continue;

            merged.push(key);
        }
    }

    // 把「这一档没渲染出来」的分类按「上一份玩家顺序 → 作者顺序 → 这一档的分类表」
    // 依次补在后面：卖/买两档的分类不重叠，只按当前这一档存会把另一档的分类弄丢。
    var pools = [fsPersonalOrder, fsDefaultOrder, fsCategories];

    for (var p = 0; p < pools.length; p++) {
        var pool = pools[p];

        for (var n = 0; n < pool.length; n++) {
            var cat = pool[n];

            if (cat === FS_ALL) continue;

            var seen = false;

            for (var m = 0; m < merged.length; m++) {
                if (merged[m] === cat) seen = true;
            }

            if (!seen) merged.push(cat);
        }
    }

    if (merged.length <= 0) return;

    fsPersonalOrder = merged;
    fsSavePersonalOrder();

    var stats = fsBuildCategories();

    fsRenderCategories(stats);
    fsRenderFavorites(stats);

    fsPageLog("[perf] 分类顺序已保存：" + merged.join(","));
}

/**
 * 左栏收藏面板。
 *
 * 只列「当前这一档真的有货」的收藏分类：分类被阶段限制掉时服务端不下发条目，
 * 于是它在这里暂时消失 —— 但收藏记录保留着，阶段到了自己就会回来。
 */
function fsRenderFavorites(stats) {
    var list = fsEl("favorites-list");
    if (!list) return;

    stats = stats || fsBuildCategories();

    var html = "";
    var shown = 0;

    for (var i = 0; i < fsFavs.length; i++) {
        var key = fsFavs[i];
        var count = stats.counts[key];

        if (count === undefined || count <= 0) continue;

        shown++;

        html += '<button type="button" class="favorite-category'
            + (fsCategory === key ? " active" : "") + '"'
            + ' data-c="' + fsEsc(fsDomKey(key)) + '"'
            + ' title="左键进入，右键取消收藏">'
            + '<span class="favorite-category-icon">' + fsCategoryIcon(key) + "</span>"
            + '<span class="favorite-category-name">' + fsEsc(fsCategoryLabel(key, stats)) + "</span>"
            + '<span class="favorite-category-star">★</span>'
            + "</button>";
    }

    if (shown === 0) {
        html = '<div class="favorites-empty">'
            + "<div>还没有收藏分类</div>"
            + "<div>打开分类菜单，右键喜欢的分类吧！</div>"
            + "</div>";
    }

    list.innerHTML = html;

    var nodes = list.children;

    for (var n = 0; n < nodes.length; n++) {
        fsBindFavorite(nodes[n]);
    }
}

function fsBindFavorite(node) {
    var raw = node.getAttribute("data-c");
    if (raw === null || raw === undefined || raw === "") return;

    var key = fsKeyFromDom(raw);

    node.addEventListener("click", function () {
        fsSetCategory(key);
    });

    node.addEventListener("contextmenu", function (event) {
        try { if (event) event.preventDefault(); } catch (e) {}
        fsToggleFav(key);
    });
}

function fsRenderAll() {
    fsRenderMoney();
    fsRenderTabs();

    // 分类表只算一次，菜单和收藏面板共用同一份计数
    var stats = fsBuildCategories();

    fsRenderCategories(stats);
    fsRenderFavorites(stats);
    fsRenderGrid();
}

// ============================================================
// 交互
// ============================================================

/** 攒一次搜索渲染。连着打几个字只会触发最后一次。 */
function fsQueueSearch() {
    if (fsSearchTimer !== null) clearTimeout(fsSearchTimer);

    fsSearchTimer = setTimeout(fsApplySearch, FS_SEARCH_DELAY);
}

/**
 * 防抖到点：值真的变了才重建货架。
 *
 * 这一句「值没变就 return」是搜索卡顿的关键防线之一：
 * 输入法组字、连击、AUI 把同一次输入派发两遍之类的情况都会推着
 * fsQueueSearch 反复走一遍，值一样就没必要动 DOM。
 */
function fsApplySearch() {
    fsSearchTimer = null;

    var input = fsEl("search-input");
    var kw = input ? String(input.value || "").trim() : "";

    if (kw === fsKeyword) return;

    fsKeyword = kw;
    fsPage = 0;

    fsRenderGrid();
}

function fsSetMode(mode) {
    if (fsMode === mode) return;

    fsMode = mode;
    fsCategory = FS_ALL;
    fsSelected = null;
    fsPage = 0;
    fsTipHide();
    fsCloseMenu();
    fsCloseDialog();

    fsRenderAll();
}

function fsSetCategory(key) {
    fsCategory = key;
    fsPage = 0;
    fsTipHide();
    fsCloseMenu();

    var stats = fsBuildCategories();

    fsRenderCategories(stats);
    fsRenderFavorites(stats);
    fsRenderGrid();
}

function fsOpenMenu() {
    fsMenuOpen = true;
    var menu = fsEl("category-menu");
    if (menu) menu.className = "category-menu open";
    fsApplyGridDisplay();
    fsSyncOverlay();
}

function fsCloseMenu() {
    fsMenuOpen = false;

    // 菜单都关了，拖动状态和「吞掉下一次点击」的标记一起作废
    fsDragCancel();
    fsDragSuppress = false;

    var menu = fsEl("category-menu");
    if (menu) menu.className = "category-menu";
    fsApplyGridDisplay();
    fsSyncOverlay();
}

function fsToggleMenu() {
    if (fsMenuOpen) fsCloseMenu();
    else fsOpenMenu();
}

function fsOpenDetail(index) {
    var row = fsRowAt(index);
    if (!row) return;

    fsTipHide();

    fsSelected = row;
    fsQty = 1;
    fsDialogOpen = true;

    // 立刻上报「弹窗开着」：不等下一拍对账，按 E / ESC 才不会误判成关整个商店
    fsSyncOverlay();

    // 先把这一条钉到右侧，再往下填内容。顺序很关键：
    //   ① 钉住之后 fsTipHide()（卡片的 mouseleave、货架收起时都会被调到）碰不到它；
    //   ② 下面填内容的步骤万一有哪一句抛异常，右侧那份也已经钉上了 ——
    //      以前 fsPinShow 放在最后，中间一抛就变成「弹窗开了、说明没钉上」，
    //      更糟的是 fsDialogPin 停在 false，之后悬停还会继续往右槽里塞东西，
    //      关弹窗时那些残留就一起冒出问题。
    fsPinShow(row);

    try {
        // 大图标同样走 <slot iconScale>（不要用 CSS transform，会压暗贴图）
        var icon = fsEl("preview-icon");

        if (icon) {
            icon.innerHTML = '<slot render="item"'
                + ' size="' + FS_PREVIEW_SIZE + '"'
                + ' iconScale="' + FS_PREVIEW_SCALE + '"'
                + ' interactive="0" pointer="0">'
                + "<item>" + fsEsc(row.e) + "</item>"
                + "</slot>";
        }

        var buy = row.b;

        fsText("dialog-title", buy ? "商品详情" : "出售详情");
        fsText("preview-name", row.n);
        fsText("preview-category", (row.cn && row.cn !== FS_NONE) ? row.cn : "未分类");
        fsText("preview-have", buy ? ("背包中：" + fsNum(row.have) + " 个") : "");

        // 说明文字不放弹窗里 —— 右侧钉住的卡片本来就有完整描述，
        // 弹窗里再放一遍既占高度还会把购买区挤出去。

        fsText("quantity-title", buy ? "想要买多少个呢？" : "想要卖多少个呢？");
        fsText("unit-label", buy ? "单价" : "收购价");
        fsText("quantity-label", buy ? "购买数量" : "出售数量");
        fsText("total-label", buy ? "合计花费" : "合计收入");
        fsText("remain-label", buy ? "购买后余额" : "出售后余额");
        fsText("unit-price", fsNum(row.p));
        fsText("notice", "");

        var input = fsEl("quantity-input");
        if (input) input.value = "1";

        try {
            fsMax = Math.max(1, Number(FastShopUI.getMaxAmount(row.i)) || 1);
        } catch (e) {
            fsMax = 1;
        }

        if (!buy) fsMax = Math.max(1, Math.min(fsMax, row.have));

        var backdrop = fsEl("backdrop");
        if (backdrop) backdrop.className = "backdrop open";

        // 弹窗盖不住 <item> 的 body 渲染层，货架必须先藏起来
        fsApplyGridDisplay();

        fsUpdateSummary();

    } catch (e) {
        fsPageLog("fsOpenDetail 出错: " + e);
    }
}

function fsCloseDialog() {
    fsDialogOpen = false;

    var backdrop = fsEl("backdrop");
    if (backdrop) backdrop.className = "backdrop";

    fsTipHide();
    fsPinHide();
    fsApplyGridDisplay();
    fsSyncOverlay();
}

/**
 * 立刻把「页面盖着哪一层」报给 Java。
 *
 * 原来只有 fsReconcilePinned 每 150ms 报一次：Java 侧按 ESC / E 判断该关哪一层时，
 * 有可能正好落在空档里 —— 刚点开详情弹窗就按 E，会被判成「没有弹窗」，
 * 结果把整个商店关掉。开关菜单这几处顺手推一次，判层就永远是最新的。
 * （周期对账保留，防的是「某条路径漏推」。）
 */
function fsSyncOverlay() {
    try {
        FastShopUI.setOverlayState(fsDialogOpen, fsMenuOpen);
    } catch (e) {}
}

/**
 * 按 GUI 视口实时钳制面板与弹窗的高度。
 *
 * 为什么不交给 CSS：反编译 1.2.7 证实，CSS 的 `vh` 走 `Size.getWindowHeight()`，
 * 在 mode=gui 下给的可能是窗口物理高度而不是 GUI 视口高度 ——
 * `max-height: calc(100vh - 12px)` 算出来比视口还大，弹窗直接撑出屏幕
 * （23:58 截图：二级菜单上下都出界）。
 * 而 `window.innerHeight` 走的是 `Size.getWindowSize()`：文档活跃时返回
 * `viewport.layoutWidth/Height()`，就是真正的 GUI 视口（如 640x330），可信。
 *
 * 所以这里用内联 maxHeight 钳住两块（Style 有 setProperty/maxHeight，可靠），
 * CSS 里的 max/height 只做 JS 失效时的兜底。值没变不写 DOM（省一次重排）。
 */
var fsLastClamp = "";

function fsLayoutClamp() {
    var w = 0;
    var h = 0;

    try {
        w = Math.round(Number(window.innerWidth) || 0);
        h = Math.round(Number(window.innerHeight) || 0);
    } catch (e) {
        return;
    }

    if (w <= 0 || h <= 0) return;

    var sig = w + "x" + h;
    if (sig === fsLastClamp) return;
    fsLastClamp = sig;

    try {
        var shop = fsEl("shop-root");
        if (shop) shop.style.maxHeight = (h - 8) + "px";

        var dialog = fsEl("detail-dialog");
        if (dialog) dialog.style.maxHeight = (h - 16) + "px";
    } catch (e) {}
}

/**
 * 右侧那份 tooltip 的兜底对账 —— 每 150ms 跑一次。
 *
 * 为什么需要它：右侧那份存在 Java 侧，而「它还该不该在」这件事只有页面知道。
 * 一旦有哪条关闭路径漏调了 fsPinHide（弹窗的 ×、切买卖模式、关整个界面几条路），
 * Java 侧那份就会一直挂在屏幕上。
 *
 * 这里不试图穷举关闭路径，而是每次直接把状态对齐：
 *   详情弹窗开着 → 钉的就是选中那条；
 *   没开弹窗     → 收掉（悬停不再往这个槽里塞东西）。
 *
 * 顺带做两件事：
 *   ① 把层级状态报给 Java，ESC / E 才知道该关弹窗而不是关整个界面；
 *   ② 取走 Java 那边的「关掉二级菜单」请求（就是玩家按了 ESC / E）。
 */
function fsReconcilePinned() {

    // 顺手做视口钳制：F11 / 拖窗口 / 改 GUI Scale 后 150ms 内跟上
    fsLayoutClamp();

    try {
        FastShopUI.setOverlayState(fsDialogOpen, fsMenuOpen);
    } catch (e) {}

    var wantClose = false;

    try {
        wantClose = !!FastShopUI.consumeCloseOverlayRequest();
    } catch (e) {}

    if (wantClose) {
        // 关弹窗里面就会同步收掉右侧那份 tooltip
        if (fsDialogOpen) {
            fsCloseDialog();
            return;
        }
        if (fsMenuOpen) {
            fsCloseMenu();
            return;
        }
    }

    // 只有详情弹窗打开时才钉；悬停不再钉右侧那份。
    var want = fsDialogOpen ? fsSelected : null;

    if (!want) {
        fsPinHide();
        return;
    }

    if (fsPinRow !== want || !fsDialogPin) {
        fsPinShow(want);
    }
}

function fsQuantity() {
    var input = fsEl("quantity-input");
    if (!input) return 1;

    var parsed = parseInt(String(input.value), 10);

    if (isNaN(parsed) || parsed < 1) parsed = 1;
    if (parsed > 2304) parsed = 2304;

    return parsed;
}

function fsSetQuantity(value) {
    var input = fsEl("quantity-input");
    if (!input) return;

    var parsed = parseInt(String(value), 10);

    if (isNaN(parsed) || parsed < 1) parsed = 1;
    if (parsed > 2304) parsed = 2304;

    input.value = String(parsed);
    fsUpdateSummary();
}

function fsUpdateSummary() {
    var row = fsSelected;
    if (!row) return;

    var qty = fsQuantity();
    var total = row.p * qty;
    var buy = row.b;

    var remaining = buy ? (fsMoney - total) : (fsMoney + total);
    var ok = buy ? (remaining >= 0) : (qty <= row.have);

    fsQty = qty;

    fsText("quantity-summary", fsNum(qty) + " 个");
    fsText("total-price", fsNum(total));

    var remainEl = fsEl("remaining-money");
    if (remainEl) {
        remainEl.textContent = fsNum(remaining);
        remainEl.className = ok ? "balance-ok" : "balance-low";
    }

    var button = fsEl("buy-button");

    if (button) {
        button.disabled = !ok;

        if (!ok) {
            button.textContent = buy ? "金币不够啦！" : "背包里没有这么多！";
        } else {
            button.textContent = (buy ? "购买 " : "出售 ") + fsNum(qty) + " 个 · 带回家！★";
        }
    }

    // notice 这里读原文再决定留不留，所以直接写 textContent；写完要把 fsText 的
    // 写值缓存作废，否则「同一个提示」第二次会被 fsText 当成已写过而跳过。
    var notice = fsEl("notice");

    if (notice && !ok) {
        notice.textContent = buy ? "再去冒险赚一些金币吧！" : "背包里的存货不够啦！";
        fsForgetText("notice");
    } else if (notice && notice.textContent) {
        var keep = String(notice.textContent);
        if (keep.indexOf("成功") < 0 && keep.indexOf("发货") < 0) {
            notice.textContent = "";
            fsForgetText("notice");
        }
    }
}

function fsTrade() {
    var row = fsSelected;
    if (!row) return;

    var qty = fsQuantity();
    var sent = false;

    try {
        sent = FastShopUI.trade(row.i, qty);
    } catch (e) {
        sent = false;
    }

    if (!sent) {
        fsText("notice", "交易请求没能发出去，再试一次吧！");
        return;
    }

    fsText("notice", row.b ? "正在打包ing……稍等一下！" : "正在清点货物……稍等一下！");

    var button = fsEl("buy-button");
    if (button) button.disabled = true;

    try {
        FastShopUI.requestMoney();
    } catch (e) {}

    if (fsSettle !== null) clearTimeout(fsSettle);

    fsSettle = setTimeout(function () {
        fsSettle = null;

        try {
            fsResync();
            fsRenderMoney();
            fsRenderGrid();
            fsUpdateSummary();
        } catch (e) {}
    }, 400);
}

// ============================================================
// 初始化
// ============================================================

function fsBind(id, event, handler) {
    var el = fsEl(id);
    if (!el) return;

    try {
        el.addEventListener(event, handler);
    } catch (e) {}
}

function fsInit() {
    // 作用域是共享的：先清掉上一份页面可能遗留的定时器
    if (fsPoll !== null) {
        clearInterval(fsPoll);
        fsPoll = null;
    }

    if (fsSettle !== null) {
        clearTimeout(fsSettle);
        fsSettle = null;
    }

    if (fsPinPoll !== null) {
        clearInterval(fsPinPoll);
        fsPinPoll = null;
    }

    if (fsSearchTimer !== null) {
        clearTimeout(fsSearchTimer);
        fsSearchTimer = null;
    }

    // 页面是新建的 DOM，上一份页面记的「某 id 已经写过这个文本 / 这个 display」
    // 全部作废 —— 不清的话新元素会被当成「值没变」而永远拿不到文本。
    fsTextCache = {};
    fsApplied = {};
    fsLastInput = 0;

    fsMode = "buy";
    fsCategory = FS_ALL;
    fsKeyword = "";
    fsSelected = null;
    fsMenuOpen = false;
    fsDialogOpen = false;
    fsPage = 0;

    // 页面重开时层级归零，同步一次，免得 Java 侧还记着上一份页面的「有弹窗」
    fsSyncOverlay();

    // 这几个是「上一份页面」留下的悬停 / 钉住状态，重开页面必须一起清：
    // 作用域跨页面存活，脚本被重新求值时 var 那几行的初值会重置，
    // 但 fsInit 里的赋值才是权威 —— 漏掉任何一个，新页面就可能停在
    // 「fsDialogPin 还是 true」的状态，于是悬停再也不出 tooltip。
    fsTipRow = null;
    fsPinRow = null;
    fsDialogPin = false;
    fsTipLogged = false;

    // 收藏是持久化的（Java 桥 → config/fast/shop_favorites.txt），每次开页面读一遍
    fsLoadFavs();

    // 分类顺序也读一遍：默认顺序来自服务端下发的 API 表，玩家顺序来自本地文件
    fsLoadOrders();

    // 上一份页面如果还没来得及收掉右侧那份，Java 侧可能还挂着，这里直接抹掉
    try {
        FastShopUI.hideTooltip();
        FastShopUI.hidePinnedTooltip();
    } catch (e) {}

    if (typeof FastShopUI === "undefined") {
        var grid = fsEl("product-grid");
        if (grid) {
            grid.innerHTML = '<div class="bridge-error">'
                + "<div>拿不到商店数据桥 FastShopUI。</div>"
                + "<div>检查：① 装了 KubeJS + Rhino；② 日志里搜 Fast/AUI 是否有注册失败。</div>"
                + "</div>";
            grid.style.display = "block";
        }
        return;
    }

    if (!fsLoadRows()) {
        fsText("shelf-count", "读取失败");
        return;
    }

    fsRenderAll();

    // 首帧就把面板/弹窗钳到视口内（别等 150ms 后的第一拍对账）
    fsLayoutClamp();

    fsBind("shop-close", "click", function () {
        try { FastShopUI.close(); } catch (e) {}
    });

    fsBind("tab-buy", "click", function () { fsSetMode("buy"); });
    fsBind("tab-sell", "click", function () { fsSetMode("sell"); });

    fsBind("category-button", "click", fsToggleMenu);
    fsBind("close-category-menu", "click", fsCloseMenu);

    fsBind("clear-search", "click", function () {
        var input = fsEl("search-input");
        if (input) input.value = "";

        // 清空是明确的一次动作，取消挂起的防抖，立刻渲染
        if (fsSearchTimer !== null) {
            clearTimeout(fsSearchTimer);
            fsSearchTimer = null;
        }

        fsKeyword = "";
        fsPage = 0;
        fsRenderGrid();
    });

    // 搜索：输入事件会来得很密（连打 / 输入法组字），每次都重建 32 格货架
    // 会把渲染线程顶住。这里只记值 + 攒一下，隔 FS_SEARCH_DELAY 毫秒
    // 才真正重建一次；真正重建时值没变就直接跳过（见 fsApplySearch）。
    fsBind("search-input", "input", function () {
        var input = fsEl("search-input");
        var kw = input ? String(input.value || "").trim() : "";

        // 「距上一次输入」的间隔：这两行日志放在一起看就能定位卡顿落在哪一段
        //   · 输入 → 输入 之间隔了好几秒 = 上一拍的渲染把渲染线程顶住了（AUI 内部）
        //   · 输入 → 重建货架 之间隔了好几秒 = 我们自己的重建慢
        var now = fsNow();
        var gap = (fsLastInput > 0) ? (now - fsLastInput) : -1;
        fsLastInput = now;

        fsPageLog("[perf] 搜索框输入 " + (gap >= 0 ? "+" + gap + "ms " : "") + "→ 「" + kw + "」");

        fsQueueSearch();
    });

    fsBind("page-prev", "click", function () { fsSetPage(fsPage - 1); });
    fsBind("page-next", "click", function () { fsSetPage(fsPage + 1); });

    fsBind("close-dialog", "click", fsCloseDialog);

    // 遮罩不绑点击：二级菜单只由 × 和 E（或 ESC）关，点到屏幕外不关。
    // （以前这里还兼着「吞掉拖 tooltip 时落到遮罩上的那一下 click」——
    //   现在 tooltip 的拖动交给 Pin Tooltips，它自己会取消点击，不用我们代劳。）

    fsBind("quantity-minus", "click", function () {
        fsSetQuantity(fsQuantity() - 1);
    });

    fsBind("quantity-plus", "click", function () {
        fsSetQuantity(fsQuantity() + 1);
    });

    fsBind("quantity-input", "input", fsUpdateSummary);

    fsBind("quantity-input", "change", function () {
        var input = fsEl("quantity-input");
        fsSetQuantity(input ? input.value : "1");
    });

    fsBind("quick-1", "click", function () { fsSetQuantity(1); });
    fsBind("quick-8", "click", function () { fsSetQuantity(8); });
    fsBind("quick-16", "click", function () { fsSetQuantity(16); });
    fsBind("quick-64", "click", function () { fsSetQuantity(64); });

    fsBind("quick-max", "click", function () { fsSetQuantity(fsMax); });

    fsBind("buy-button", "click", fsTrade);

    // 余额会因为服务端回包变化，低频跟一次就够
    fsPoll = setInterval(function () {
        try {
            var before = fsMoney;
            fsLoadMoney();
            if (before !== fsMoney) {
                fsRenderMoney();
                fsUpdateSummary();
            }
        } catch (e) {}
    }, 1000);

    // 右侧那份 tooltip 的兜底对账 + 层级状态上报 + ESC/E 请求，150ms 一拍。
    // 这是「关掉二级菜单后那份 tooltip 必须跟着消失」的最后一道保险：
    // 无论玩家用哪条路径关的弹窗，最多 150ms 之后它就会被清掉。
    fsPinPoll = setInterval(fsReconcilePinned, 150);

    try {
        FastShopUI.requestMoney();
    } catch (e) {}
}

function fsUnload() {
    // 页面卸载了就把 tooltip 状态清掉，免得残留在别的界面上
    fsTipHide();
    fsPinHide();

    // 拖动状态也一起清：页面都没了，留着只会在下一次开页面时误吞一下点击
    fsDragCancel();
    fsDragSuppress = false;

    if (fsPoll !== null) {
        clearInterval(fsPoll);
        fsPoll = null;
    }

    if (fsPinPoll !== null) {
        clearInterval(fsPinPoll);
        fsPinPoll = null;
    }

    if (fsSettle !== null) {
        clearTimeout(fsSettle);
        fsSettle = null;
    }

    if (fsSearchTimer !== null) {
        clearTimeout(fsSearchTimer);
        fsSearchTimer = null;
    }
}

try {
    if (document.body) {
        document.body.addEventListener("unload", fsUnload);
    }
} catch (e) {}

// 拖动排序的两个监听挂在 document 上，脚本加载时绑一次就够
// （跟上面 unload 同一个位置：页面刷新会重建 DOM，但脚本也会重新求值）。
try {
    document.addEventListener("mousemove", fsDragMove);
    document.addEventListener("mouseup", fsDragEnd);
} catch (e) {
    fsPageLog("拖动排序的鼠标监听没绑上: " + e);
}

try {
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", fsInit);
    } else {
        fsInit();
    }
} catch (e) {
    fsInit();
}
