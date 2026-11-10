# 晴雪UI（ApricityUI / AUI）细则

> 主文件 `MEMORY.md` 只留索引，这里放改动 UI 三件套时需要逐条对照的细节。

## 晴雪UI 页面「必须两处」，只更新 jar 等于没生效
AUI 分层 `loadFromResourcePack → loadFromLocalFolder → loadFromDevFolders`，
实例 `<实例>/apricity/fast/shop.*` 会盖掉 jar 内 `assets/apricityui/apricity/fast/shop.*`。
```bash
DST="D:/Minecraft/异界随行诗/.minecraft/versions/otherworld_traveling_poems"
cp -v build/libs/fast-1.20.1-forge-0.0.1.jar "$DST/mods/fast-1.20.1-forge-0.0.1.jar"
unzip -o -j build/libs/fast-1.20.1-forge-0.0.1.jar "assets/apricityui/apricity/fast/shop.*" -d "$DST/apricity/fast"
```
⚠️ 实例 `config/apricityui-client.toml` 的 `autoReload`/`frameTimingHud`/`remoteDebug` 全 false → 改三件套必须重启；
热更可开 `autoReload`（用完关），或让用户按 **END** 手动重载。日志看 `<实例>/logs/debug.log`，前缀 `[Fast/*]`。

### 特性锻造台 UI：只在实例里，不进 jar（别去 src 找）
`apricity/fast/trait_forge.{html,css}` 只在实例目录，改它不用编译（改前 cp 备份）。
驱动是 KubeJS：`client_scripts/trait_forge_ui.js` + `server_scripts/ender_chest/trait_forge_ui.js`。
页面 `mode=browser` + `aui-mouse-events=intercept`（商店是 gui + none），钩子加在 `tfPageInit`。
2026-10-14 修超屏：`.window` 的 `max-width:100%`；`.content/.panel-body/.center-body/.list` 加 `overflow-y:auto`；
`.workspace` 加 `min-height:360px`。**不要用 JS 钳尺寸**。

## ⚠️ 缩放模型：页面 mode=gui（18 轮，2026-10-13）
`<meta name="aui-viewport" content="mode=gui,user-scalable=false">`：CSS 视口 = MC GUI 分辨率（实测 640x331），
**CSS 像素 ≡ 原版 GUI 像素**，随 GUI Scale 放大。mode=browser 已弃用。
- 基准：面板 600x322 居中；货架 8 列×48px、卡高 46、gap 2；`FS_PAGE_SIZE=24`；图标 slot 32/iconScale 2（弹窗 48/3），
  slot item left/top=(槽位-16)/2，换档联动 `FS_ICON_*`。小视口 @media 降级（≤560→6 列，≤460→4 列）。
- Java 换算 `gui = css / (viewport.layoutWidth()/Screen#width)`（`FastShopScreen#cssToGuiScale`）。
- **每帧实测 DOM**（`getBoundingClientRect` 在 `super.render()` 之后；display:none 时 rect 全 0→-1）。
  钉住 tooltip 锚 = 弹窗右缘 + `PINNED_GAP=+6`。
- **字号纪律**：可能含 ASCII 的文本字号一律 10 的倍数；纯中文/雅黑符号才用 12/13/14/16。
- **尺寸纪律**：AUI `CssLength` 只可靠支持 `min(A,B)` 与 `min(A,calc(...))`；❌ `min(100% - 8px,600px)` 解析不了；
  ❌ `calc(100% - Npx)` 在无高度 body 上塌 0；❌ `vh/vw` 不可靠 → **视口相关尺寸一律 JS 内联钳制**（`fsLayoutClamp`）。
- ⚠️ `window.innerWidth/innerHeight` 只在「本页就是上下文文档」时可信；锻造页（browser）用它会「几秒后突然压扁」。

## 文字被裁：两处必须让出空间（2026-11-02 实测修过）
AUI 的绘制是「后画的盖前画的」，**绝对定位的伪元素会盖住文字**，而文字不会因为父级 padding 不够就自动缩。
- 招牌标题「森林杂货铺」：`.sign::before` 的草方块条总高 10px（内容 6 + 描边 2×2），
  `.sign` 的 `padding-top` 必须 ≥ 10px，现在 11px。改回 5px 立刻字顶被削平。
- 左上「今天也要好好冒险呀！」：左栏 156px → 内容宽仅 138px，一行放不下「提示语 + ● 5,678」，
  flex 会把提示语压窄（表现是最后一个「！」被金币图标盖住）。现在 `.wallet` 允许折行、
  `.wallet-tip` 独占一行、`.wallet-money` 第二行右对齐。
  ⚠️ 折行后的 flex 行里 `margin-left:auto` AUI 不认，靠 `width:100% + justify-content:flex-end`。
- 排查手法：开 `aiAutoScreenshot`，从 `screenshots/aui/` 拿图后**按像素量**（白字行的 min-y、
  金币色块的 x 范围），比肉眼判断可靠。注意该目录只留 20 张、每秒覆盖，先复制出来再裁。

## 分类拖动排序（分类菜单）
- 命中判定**只用 `event.target`**（AUI 无 `document.elementFromPoint`）：沿 `parentElement` 上找
  `.category-option`，空档 `insertBefore(slot, hovered)`。class 必须按空格切词精确比，
  `indexOf("category-option")` 会命中 `.category-option-name` 这类内层 span → 会把空档插进按钮里。
- 被拖的那项要 `appendChild(document.body)` + `position:fixed`（`.shop` 有 `overflow:hidden`，
  留在列表里出不了面板边框）；原位留 `cloneNode(false)` 的空壳，尺寸内联成原矩形。
- 跟手只用 `transform: translate(dx,dy)`，**别每帧写 left/top**（布局属性，卡且追不上手）；
  同值不重复写。
- 松手后按 DOM 顺序落盘，并且要把「当前买卖档没渲染出来」的分类按旧顺序补回去。

## 商店 tooltip / 交互约束（别推翻）
- 单轨交给 **PinTooltips 的 `pin()`**（`PinTooltipsCompat` 反射桥）：词条描述只在它那，自绘=词条全没；
  unpin 后清 `focused`（否则卡死玩家 Alt）。`<meta aui-mouse-events>` **必须 `content="none"`**（intercept 会 consumeNative）。
- 悬停槽只跟鼠标；钉住槽只在详情弹窗开时存在：`want = fsDialogOpen ? fsSelected : null`。
- 二级菜单只能 ×/E 关；ESC 与 E 等价；「玩家拖过」判定用 `movedByUser`（偏离>2px）。

## UI/布局约定
- `<button>` 文本不垂直居中 → 全局 `button{display:flex;align-items:center;justify-content:center}`。
- body 保持 `background:transparent`（用户明确不要背景）。
- **别用 grid 搭骨架**（`grid-column:1/-1` 不通栏、`minmax(0,1fr)` 不收缩）；外壳 flex + `flex:1` + `min-height:0`。
- 字体只准用雅黑确定覆盖的字符：✅ `★☆●◆◇○□■▪▫△▲▼×−‹›→①·§◎※〓`；❌ `♡♦✦✕✿☘⌕▣♥♠♣` 及 emoji（AUI 自定义字体上限 4）。
- ⚠️ browser 视口下 normal 字重中文必花字 → browser 页面写 `body{font-weight:bold}`（gui 无此问题）。
- 收藏走自建 Java 桥 `FastShopUIBridge.getFavorites()/setFavorites()` → `config/fast/shop_favorites.txt`
  （AUI `LocalStorage.save()` 在 Forge 上 SRG 反射必抛异常）。
- `data-c` 不能有空串（`FS_ALL=""` 曾致「切分类后点不回全部」，用占位 `__all__`）。
- 搜索：防抖 180ms、同值 return、`fsTextCache`/`fsApplied` 防重复写 DOM。`[AUI Font] blank text draw` 无害。

## 改完三件套的编译前自检
1. 禁区扫描（只许注释命中）：text-shadow/sticky/inset:/dashed/overflow-wrap/outline/负 margin/calc 乘除/radial|conic-gradient/grid-template-areas。
2. ES5：const/let/箭头/模板串/?./??/JSON 只许注释里。3. HTML id ↔ JS 引用齐全、slot 常量 JS↔CSS 一致。
4. 字体缺字=0；`node --check shop.js`。
