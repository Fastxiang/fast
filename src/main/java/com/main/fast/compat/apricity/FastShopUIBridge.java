package com.main.fast.compat.apricity;

import com.main.fast.shop.ShopManager;
import com.main.fast.shop.client.ClientShopCache;
import com.main.fast.shop.money.IMoney;
import com.main.fast.shop.money.MoneyProvider;
import com.sighs.apricityui.slot.ItemStackExpressionCompiler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 商店页面与 Java 侧之间的桥接对象。
 *
 * 页面脚本跑在 KubeJS 的客户端 Rhino 作用域里，因此这里只需要一个静态工具类，
 * 加 {@code @KJSBindings} 后就能在页面里直接用 {@code FastShopUI.xxx()} 调用。
 *
 * 这一层只负责「读数据 / 转发交易请求」，真正的扣钱、发货、出售结算逻辑
 * 仍然完全由 {@link com.main.fast.shop.api.FastShop} 和服务端处理，不做任何改动。
 *
 * 接口一律用标量 getter，不用 JSON：Rhino 里 JSON 解析不可靠，而且每次渲染
 * 都要跑一遍序列化。页面端自己把标量拼成对象数组。
 */
@com.sighs.apricityui.registry.annotation.KJSBindings(
        value = "FastShopUI",
        modId = "fast",
        isClient = true
)
public final class FastShopUIBridge {

    /**
     * 详情弹窗里「Shift 展开内容」那一段的小标题。
     *
     * <p>正文里本来只有一句「按住 Shift 查看详情」，展开后的内容在详情里被直接摊开，
     * 不加个标题的话会被当成普通说明的一部分。想换措辞改这里就行；
     * <b>颜色不写死</b> —— see {@link #hintStyle}，直接抄那一行提示的样式。
     */
    private static final String SHIFT_SECTION_TITLE = "Shift 展开后：";

    private FastShopUIBridge() {
    }

    /** 打开界面时的条目快照，保证页面看到的索引和交易时用的索引是同一份，不会中途串位。 */
    private static final List<ShopManager.ShopEntry> SNAPSHOT = new ArrayList<>();

    /** 与 SNAPSHOT 一一对应、开屏时一次性算好的展示数据。 */
    private static final List<Row> ROWS = new ArrayList<>();

    private static String currentShopId = "";

    /** 一行的展示数据。 */
    private static final class Row {

        private final ItemStack stack;
        private final String expression;
        private final String name;
        private final int price;
        private final boolean buy;
        private final String category;
        private final String categoryName;

        private int have;

        /**
         * 懒计算：原版 tooltip 的所有行，只在第一次需要时算一次。
         *
         * <p>存的是 {@link Component} 原样而不是字符串：原版 tooltip 渲染器认得
         * Style 树，颜色 / 加粗 / 斜体全都会自动带上，不需要我们再去解析颜色码。
         *
         * <p>但缓存不能一直吃死 —— 原版 tooltip 的正文是<b>按当前的 Shift / Ctrl 状态现算</b>
         * 的（模组那套「按住 Shift 查看详情」），所以按键状态一变就得重算，
         * 由 {@link #tooltipMods} 记着上次是哪个状态。
         */
        private List<Component> tooltip;

        /** 详情弹窗那份：普通说明 + 「Shift 展开版」多出来的部分 + 价格（永远最后）。 */
        private List<Component> pinnedTooltip;

        /** {@link #tooltip} 是在哪个修饰键状态下算出来的；-1 = 还没算过。 */
        private int tooltipMods = -1;

        /** 同上，给 {@link #pinnedTooltip} 用。 */
        private int pinnedMods = -1;

        private Row(ShopManager.ShopEntry entry) {

            this.stack = entry.stack;
            this.expression = safeSerialize(entry.stack);
            this.name = nameOf(entry.stack);
            this.price = entry.price;
            this.buy = entry.buy;
            this.category = entry.category == null ? "" : entry.category;
            this.categoryName = categoryName(entry.category);
            this.have = 0;
        }
    }

    // =================================================================
    // 开屏：准备数据
    // =================================================================

    /**
     * 由 {@link ApricityCompat#openShop(String)} 在打开界面前调用。
     */
    public static void prepare(String shopId) {

        currentShopId = shopId == null ? "" : shopId;

        SNAPSHOT.clear();
        ROWS.clear();

        // 换商店 / 重开界面，上一轮记的展示状态全部作废
        hoverIndex = -1;
        pinnedIndex = -1;
        lastModifiers = -1;
        pageDialogOpen = false;
        pageMenuOpen = false;
        closeOverlayRequest = false;

        long start = System.currentTimeMillis();

        List<ShopManager.ShopEntry> cache = ClientShopCache.get(currentShopId);

        for (ShopManager.ShopEntry entry : cache) {
            SNAPSHOT.add(entry);
            ROWS.add(new Row(entry));
        }

        refresh();

        // 开屏卡顿排查用的耗时点：这里只做表达式序列化和名字，tooltip 已经改成懒计算
        com.mojang.logging.LogUtils.getLogger()
                .info("[Fast/AUI] prepare {} entries in {} ms", ROWS.size(), System.currentTimeMillis() - start);
    }

    /** 重新扫一遍背包，更新「持有数量」。买卖之后页面会调一次。 */
    public static void refresh() {

        Player player = Minecraft.getInstance().player;

        for (int i = 0; i < ROWS.size(); i++) {
            ROWS.get(i).have = countInInventory(player, SNAPSHOT.get(i).stack);
        }
    }

    public static String getShopId() {
        return currentShopId;
    }

    public static int getCount() {
        return ROWS.size();
    }

    // =================================================================
    // 逐条读取（页面端拼成数组）
    // =================================================================

    /** 物品表达式，页面直接塞进 {@code <item>} 标签里。 */
    public static String getExpression(int index) {
        Row row = rowAt(index);
        return row == null ? "" : row.expression;
    }

    public static String getName(int index) {
        Row row = rowAt(index);
        return row == null ? "" : row.name;
    }

    public static int getPrice(int index) {
        Row row = rowAt(index);
        return row == null ? 0 : row.price;
    }

    /** true = 购买条目，false = 出售条目。 */
    public static boolean isBuy(int index) {
        Row row = rowAt(index);
        return row != null && row.buy;
    }

    public static String getCategory(int index) {
        Row row = rowAt(index);
        return row == null ? "" : row.category;
    }

    public static String getCategoryName(int index) {
        Row row = rowAt(index);
        return row == null ? "" : row.categoryName;
    }

    /** 背包里有多少个（出售上限用它）。 */
    public static int getHave(int index) {
        Row row = rowAt(index);
        return row == null ? 0 : row.have;
    }

    // =================================================================
    // 余额
    // =================================================================

    public static int getMoney() {

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }

        return player.getCapability(MoneyProvider.MONEY)
                .map(IMoney::getMoney)
                .orElse(0);
    }

    /** 向服务端要一次最新余额，服务端会用现有的 PacketSyncMoney 回包。 */
    public static void requestMoney() {

        if (Minecraft.getInstance().player == null) {
            return;
        }

        com.main.fast.shop.network.ShopNetwork.CHANNEL.sendToServer(
                new com.main.fast.shop.network.PacketRequestMoneySync()
        );
    }

    // =================================================================
    // 交易
    // =================================================================

    /**
     * 单次交易最多能买 / 能卖多少个。
     * 购买按余额折算，出售按背包持有数折算；与原版的取值口径保持一致。
     */
    public static int getMaxAmount(int index) {

        Row row = rowAt(index);
        if (row == null) {
            return 1;
        }

        if (row.buy) {
            int money = getMoney();
            return Math.max(1, row.price <= 0 ? 1 : money / row.price);
        }

        return Math.max(1, row.have);
    }

    /**
     * 发起一次交易：把请求丢给服务端，其余什么都不做。
     *
     * @return 请求是否成功发出；成功不代表交易一定成功（服务端还会二次校验）
     */
    public static boolean trade(int index, int amount) {

        if (index < 0 || index >= SNAPSHOT.size() || amount <= 0) {
            return false;
        }

        if (index >= ROWS.size()) {
            return false;
        }

        if (Minecraft.getInstance().player == null) {
            return false;
        }

        ShopManager.ShopEntry entry = SNAPSHOT.get(index);

        com.main.fast.shop.network.ShopNetwork.CHANNEL.sendToServer(
                new com.main.fast.shop.network.PacketShopTradeRequest(
                        currentShopId,
                        entry.stack,
                        amount,
                        entry.buy
                )
        );

        return true;
    }

    // =================================================================
    // 界面
    // =================================================================

    /** 关闭商店界面（页面里的 ✕ 按钮用）。 */
    public static void close() {

        Minecraft mc = Minecraft.getInstance();

        mc.execute(() -> {
            if (mc.screen != null) {
                mc.setScreen(null);
            }
        });
    }

    // =================================================================
    // 原版 tooltip
    // =================================================================

    /** 当前正在显示的是哪一条（-1 = 没在显示）。Shift 状态变化时要靠它重建。 */
    private static int hoverIndex = -1;
    private static int pinnedIndex = -1;

    /** 上一次见到的修饰键状态，见 {@link #modifierState()}。 */
    private static int lastModifiers = -1;

    /**
     * 鼠标悬停在商品卡片上：显示跟随鼠标的原版 tooltip。
     *
     * <p>页面不需要传坐标 —— 真正画的时候由 {@link FastShopScreen} 直接用
     * {@code Screen#render} 收到的那对 mouseX/mouseY，天然就是原版坐标系，
     * 不存在「页面逻辑像素 vs GUI 缩放坐标」的换算问题。
     */
    public static void showTooltip(int index) {
        hoverIndex = index;
        ShopTooltipState.showHover(tooltipLinesFor(index));
    }

    /**
     * 把某一条的 tooltip 钉在窗口上（<b>只在详情弹窗打开时调用</b>）。
     *
     * <p>这样说明文字永远画在原版那个暗紫底纹上，白色 / 浅色文本不会再隐没在
     * 弹窗的浅色背景里。和 {@link #showTooltip(int)} 是两个独立槽位，可以同时存在。
     *
     * <p>这一份会<b>连物品一起</b>交给 Pin Tooltips 钉住（见 {@link PinTooltipsCompat}）——
     * 只有这样才有它那套「悬停附魔 / 效果词条 → 弹出描述」的能力，顺带白拿 z 抬升、
     * 拖动、右键关闭。
     *
     * <p>另外这一份比悬停那份多一段：物品若有「按住 Shift 才展开」的说明，
     * 展开后的内容会接在下面单独成段显示（详情里不用玩家真的按着 Shift）。
     */
    public static void showPinnedTooltip(int index) {

        pinnedIndex = index;

        ShopTooltipState.showPinned(pinnedTooltipLinesFor(index), stackAt(index));
    }

    /** 鼠标离开卡片时调用：<b>只</b>收掉跟随鼠标的那一份，钉住的不动。 */
    public static void hideTooltip() {
        hoverIndex = -1;
        ShopTooltipState.clearHover();
    }

    /**
     * 收掉钉在右侧的那一份。
     *
     * <p>和 {@link #hideTooltip()} 分开是必须的：详情弹窗一开货架就被收起，
     * AUI 会给刚被收起的卡片补一发 {@code mouseleave}，那一发只会调到 hideTooltip()。
     * 两者若共用一个 clear，钉住的 tooltip 会在弹窗刚打开的一瞬间被那一发清掉。
     *
     * <p>交给 Pin Tooltips 的那一份也是在这里被撤掉的（见 {@code ShopTooltipState}）。
     */
    public static void hidePinnedTooltip() {
        pinnedIndex = -1;
        ShopTooltipState.clearPinned();
    }

    /**
     * 每帧调一次：Shift / Ctrl 状态变了就把正在显示的那两份重建一遍。
     *
     * <p>原版 tooltip 的正文是「按当前按键状态现算」的，而页面只会在鼠标动 /
     * 点东西的时候才喊我们，所以玩家单独按住或松开 Shift 时，得由这里补一刀
     * ——否则那套折叠说明永远停在同一个状态，看起来就是「没反应」。
     */
    public static void tickModifiers() {

        int modifiers = modifierState();

        if (modifiers == lastModifiers) {
            return;
        }

        lastModifiers = modifiers;

        if (hoverIndex >= 0 && ShopTooltipState.isHoverActive()) {
            ShopTooltipState.showHover(tooltipLinesFor(hoverIndex));
        }

        if (pinnedIndex >= 0 && ShopTooltipState.isPinnedActive()) {
            ShopTooltipState.showPinned(pinnedTooltipLinesFor(pinnedIndex), stackAt(pinnedIndex));
        }
    }

    // =================================================================
    // 页面层级状态 / ESC 让给二级菜单
    // =================================================================

    /**
     * 页面现在盖着哪一层。由页面每 150ms 上报一次（见 shop.js 的 fsReconcilePinned）。
     *
     * <p>Java 侧需要这个信息，只为一件事：玩家按 ESC 时该关的是「二级菜单」，
     * 而不是把整个商店界面一起关掉。
     */
    private static boolean pageDialogOpen = false;
    private static boolean pageMenuOpen = false;

    /** Java 侧请求页面关掉最上面那一层；页面下一拍取走并执行。 */
    private static volatile boolean closeOverlayRequest = false;

    public static void setOverlayState(boolean dialogOpen, boolean menuOpen) {
        pageDialogOpen = dialogOpen;
        pageMenuOpen = menuOpen;
    }

    /** 页面上有没有盖着「二级菜单」（详情弹窗 / 分类菜单）。 */
    public static boolean isOverlayOpen() {
        return pageDialogOpen || pageMenuOpen;
    }

    public static void requestCloseOverlay() {
        closeOverlayRequest = true;
    }

    /** 页面取走「关掉二级菜单」的请求；取一次就清掉。 */
    public static boolean consumeCloseOverlayRequest() {

        if (!closeOverlayRequest) {
            return false;
        }

        closeOverlayRequest = false;

        return true;
    }

    /**
     * 页面诊断通道：把页面脚本里被 {@code catch} 吞掉的异常写进游戏日志。
     *
     * <p>页面跑在 Rhino 上，所有过桥调用都套了 try/catch 防止把整页脚本带崩 ——
     * 代价是出错时日志里一点痕迹都没有，界面上只会表现为「点了没反应 / 啥都没显示」。
     * 排查这类问题只能靠页面自己汇报。
     */
    public static void log(String message) {
        com.mojang.logging.LogUtils.getLogger().info("[Fast/AUI][page] {}", message == null ? "" : message);
    }

    // =================================================================
    // 收藏分类的持久化
    // =================================================================

    /**
     * 收藏分类列表的落盘位置：{@code <游戏目录>/config/fast/shop_favorites.txt}。
     *
     * <p>为什么不直接用页面里的 localStorage：晴雪UI 那支笔在 Forge 上是坏的 ——
     * {@code LocalStorage.save()} 用 Mojang 方法名反射调
     * {@code CompoundTag.putString(String,String)}，而 Forge 生产环境里方法名是 SRG
     * （{@code m_128381_}），于是每次都抛 {@code NoSuchMethodException}，
     * {@code config/apricityui/localStorage.nbt} 永远是空的。
     * 这里沿用页面那一层「一行一个 key」的纯文本格式，读写都简单且能直接手改。
     */
    private static java.nio.file.Path favoritesFile() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("fast")
                .resolve("shop_favorites.txt");
    }

    /**
     * 读收藏分类列表（换行分隔，一行一个 key）。
     *
     * <p>读不到 / 文件不存在一律返回空串：页面那边会退回 localStorage 兜底，
     * 拿不到收藏不该把整页脚本带崩。
     */
    public static String getFavorites() {

        try {
            java.nio.file.Path file = favoritesFile();

            if (!java.nio.file.Files.exists(file)) {
                return "";
            }

            return new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 写收藏分类列表，返回是否写成功 —— 页面据此决定要不要退回 localStorage。
     *
     * <p>列表就几个分类 key，直接整文件覆写，不做增量：省得出现写了一半的文件。
     */
    public static boolean setFavorites(String text) {

        try {
            java.nio.file.Path file = favoritesFile();

            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file,
                    (text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =================================================================
    // 分类顺序
    // =================================================================

    /**
     * 服务端用 {@code ShopManager.setCategoryOrder} 指定的分类顺序（换行分隔，一行一个 key）。
     *
     * <p>这份是「整合包作者定的默认顺序」，和玩家自己拖出来的那份分开：
     * 读不到就返回空串，页面退回「按注册顺序 + 全部排最前」的老行为。
     */
    public static String getDefaultOrder() {

        StringBuilder sb = new StringBuilder();

        for (String key : ClientShopCache.getCategoryOrder(currentShopId)) {

            if (key == null || key.isEmpty()) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append('\n');
            }

            sb.append(key);
        }

        return sb.toString();
    }

    /**
     * 该玩家自己拖出来的分类顺序（换行分隔，一行一个 key）。
     *
     * <p>按玩家 UUID 分开存：<b>同一个人</b>在哪个存档、哪个服务器看到的都是自己那份，
     * 别人的界面不受影响。这也是「玩家自定义」和「整合包默认」必须分开存的原因 ——
     * 默认顺序是作者的东西，不该被某个玩家的拖动改掉。
     */
    public static String getPersonalOrder() {

        try {
            java.nio.file.Path file = personalOrderFile();

            if (file == null || !java.nio.file.Files.exists(file)) {
                return "";
            }

            return new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 写该玩家的分类顺序，返回是否写成功 —— 页面据此决定要不要退回 localStorage。
     *
     * <p>顺手把玩家 UUID 写进文件名，一眼就能看出这是谁的排序。
     */
    public static boolean setPersonalOrder(String text) {

        try {
            java.nio.file.Path file = personalOrderFile();

            if (file == null) {
                return false;
            }

            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file,
                    (text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 当前玩家的分类顺序文件：{@code <config>/fast/shop_order/<uuid>.txt}。
     *
     * <p>取不到玩家（界面还没绑到具体玩家）时返回 null，读写都按失败处理 ——
     * 与其把顺序写到一个「不知道是谁」的文件里，不如让页面这一轮先不落盘。
     */
    private static java.nio.file.Path personalOrderFile() {

        Player player = Minecraft.getInstance().player;

        if (player == null) {
            return null;
        }

        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("fast")
                .resolve("shop_order")
                .resolve(player.getUUID().toString() + ".txt");
    }

    // =================================================================
    // 内部工具
    // =================================================================

    private static Row rowAt(int index) {

        if (index < 0 || index >= ROWS.size()) {
            return null;
        }

        return ROWS.get(index);
    }

    /** 某一条对应的物品；没有就给空栈（Pin Tooltips 那边拿到空栈也不会出问题）。 */
    private static ItemStack stackAt(int index) {

        Row row = rowAt(index);

        return row == null || row.stack == null ? ItemStack.EMPTY : row.stack;
    }

    private static String nameOf(ItemStack stack) {
        return stack == null ? "" : stack.getHoverName().getString();
    }

    private static String safeSerialize(ItemStack stack) {

        if (stack == null || stack.isEmpty()) {
            return "";
        }

        try {
            return ItemStackExpressionCompiler.serialize(stack);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String categoryName(String category) {

        if (category == null || category.isEmpty()) {
            return "";
        }

        String key = "category." + category;
        Component translated = Component.translatable(key);

        String text = translated.getString();

        // 没有对应翻译时会原样返回 key，这时候退回原始分类名，别把 "category.xxx" 显示出去。
        if (key.equals(text)) {
            return category;
        }

        return text;
    }

    private static int countInInventory(Player player, ItemStack stack) {

        if (player == null || stack == null || stack.isEmpty()) {
            return 0;
        }

        NonNullList<ItemStack> items = player.getInventory().items;

        int count = 0;

        for (ItemStack invStack : items) {

            if (!invStack.isEmpty() && ItemStack.isSameItemSameTags(stack, invStack)) {
                count += invStack.getCount();
            }
        }

        return count;
    }

    /** 当前按住的是哪些修饰键。原版 tooltip 的正文会跟着这个变，所以得参与缓存键。 */
    private static int modifierState() {

        int state = 0;

        if (Screen.hasShiftDown()) {
            state |= 1;
        }

        if (Screen.hasControlDown()) {
            state |= 2;
        }

        if (Screen.hasAltDown()) {
            state |= 4;
        }

        return state;
    }

    /**
     * 跟随鼠标那一份的行。
     *
     * <p>缓存以「修饰键状态」为键：玩家按住 Shift 时，原版 tooltip 会多出
     * 「按住 Shift 才展开」的那部分内容，一直吃旧缓存的话就永远看不到变化。
     */
    private static List<Component> tooltipLinesFor(int index) {

        Row row = rowAt(index);

        if (row == null) {
            return Collections.emptyList();
        }

        int modifiers = modifierState();

        if (row.tooltip == null || row.tooltipMods != modifiers) {

            row.tooltip = buildHoverTooltip(row);
            row.tooltipMods = modifiers;
        }

        return row.tooltip;
    }

    /**
     * 详情弹窗右侧那份的行。
     *
     * <p>顺序是固定的，别再调：
     * <ol>
     *   <li>物品名 + 普通说明（里面本来就有那句「按住 Shift 查看详情」的提示）；</li>
     *   <li>空一行；</li>
     *   <li>一段小标题，说明下面这块是 Shift 展开的内容；</li>
     *   <li>Shift 展开后多出来的正文；</li>
     *   <li>空一行；</li>
     *   <li><b>价格（永远最后一行）</b>。</li>
     * </ol>
     *
     * <p>这样点进物品就能一次看全，不用玩家先按住 Shift 才知道还有后半段，
     * 也不会让价格被挤到中间去。
     */
    private static List<Component> pinnedTooltipLinesFor(int index) {

        Row row = rowAt(index);

        if (row == null) {
            return Collections.emptyList();
        }

        int modifiers = modifierState();

        if (row.pinnedTooltip == null || row.pinnedMods != modifiers) {

            row.pinnedTooltip = buildPinnedTooltip(row);
            row.pinnedMods = modifiers;
        }

        return row.pinnedTooltip;
    }

    /**
     * 详情那份：普通说明 → 空行 → Shift 段标题 → Shift 展开内容 → 空行 → 价格。
     *
     * <p>玩家自己正按着 Shift 时，普通版本身已经是展开版，差集自然为空，
     * 于是不会有重复的第二段，价格照样在最后。
     */
    private static List<Component> buildPinnedTooltip(Row row) {

        List<Component> normal = bodyLines(row, false);

        if (normal.isEmpty()) {
            return normal;
        }

        List<Component> out = new ArrayList<>(normal);

        List<Component> extra = shiftExtras(row, normal);

        if (!extra.isEmpty()) {

            out.add(Component.empty());
            out.add(Component.literal(SHIFT_SECTION_TITLE).withStyle(hintStyle(normal)));
            out.addAll(extra);
        }

        out.add(Component.empty());
        out.add(priceLine(row));

        return out;
    }

    /**
     * 「Shift 展开后：」那一行的样式 —— 直接抄正文里「按住 Shift 查看详情」那一行。
     *
     * <p>不写死颜色是有原因的：每句话在 tooltip 里长什么样是物品 / 模组自己定的
     * （有的用 §7 灰、有的用 §8 深灰、有的还带斜体），写死任何一种都可能跟它撞不上。
     * 这里在普通版正文里找那一行提示，把它的 {@link Style} 原样拿来用；
     * 万一没找到（或者那一行本身没带颜色），再退回灰色。
     */
    private static Style hintStyle(List<Component> normal) {

        for (Component line : normal) {

            String text = line.getString().toLowerCase();

            boolean looksLikeHint = text.contains("shift")
                    || text.contains("查看详情")
                    || text.contains("按住");

            if (!looksLikeHint) {
                continue;
            }

            Style style = line.getStyle();

            if (style.getColor() != null) {
                return style;
            }
        }

        return Style.EMPTY.applyFormat(ChatFormatting.GRAY);
    }

    /**
     * 把「Shift 展开版」里普通版没有的行挑出来。
     *
     * <p>用行文本做差集（而不是比对 Component 对象）：两次 {@code getTooltipLines}
     * 出来的是两批新对象，引用对不上，但同一行的明文是一样的。
     * 那行「按住 Shift 查看详情」的提示本来就在普通版里，自然不会被当成新增内容。
     */
    private static List<Component> shiftExtras(Row row, List<Component> normal) {

        List<Component> shifted = bodyLines(row, true);

        if (shifted.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalTexts = new ArrayList<>(normal.size());

        for (Component line : normal) {
            normalTexts.add(line.getString());
        }

        List<Component> extra = new ArrayList<>();

        for (Component line : shifted) {

            if (!normalTexts.contains(line.getString())) {
                extra.add(line);
            }
        }

        return extra;
    }

    /** 悬停那份的行：名字 + 正文 + 空行 + 价格。 */
    private static List<Component> buildHoverTooltip(Row row) {

        List<Component> out = bodyLines(row, false);

        // 名字之外还有正文时，留一个空行和价格分隔开
        if (out.size() > 1) {
            out.add(Component.empty());
        }

        out.add(priceLine(row));

        return out;
    }

    /**
     * 物品名 + tooltip 正文，<b>不含价格</b>（价格由调用方决定放在哪儿）。
     *
     * <p>第一行是物品名（{@code getHoverName()} 本身就带稀有度 / 自定义颜色），
     * 后面直接接 {@code ItemStack} 自己的 tooltip 正文 —— 这里刻意保留
     * {@link Component} 原样、不做任何字符串化：原版 tooltip 渲染器认得 Style 树，
     * 颜色 / 加粗 / 斜体都会自动还原，所以不会再出现「只有符号和字、没有颜色」
     * 那种把 {@code §9} 原样画出来的情况。
     *
     * @param forceShift 这一遍假装玩家按住 Shift —— 模组那套折叠说明才会把内容吐出来。
     *                   假的开关见 {@link FastTooltipFlags}，用完立刻还原。
     */
    private static List<Component> bodyLines(Row row, boolean forceShift) {

        List<Component> out = new ArrayList<>();

        if (row.stack == null || row.stack.isEmpty()) {
            return out;
        }

        boolean previous = FastTooltipFlags.isForcingShift();
        FastTooltipFlags.setForcingShift(forceShift);

        try {

            out.add(row.stack.getHoverName());

            LocalPlayer player = Minecraft.getInstance().player;

            try {

                List<Component> lines = row.stack.getTooltipLines(player, TooltipFlag.NORMAL);

                // 跳过第 0 行（那就是物品名），其余原样搬过来
                for (int i = 1; i < lines.size(); i++) {

                    Component line = lines.get(i);

                    // 空行丢掉：原版 tooltip 里的空行只作分隔，段落间距由上面自己控制
                    if (line.getString().trim().isEmpty()) {
                        continue;
                    }

                    out.add(line);
                }

            } catch (Throwable ignored) {
                // 个别模组的 tooltip 会在极端情况下抛异常，别让它连累整个页面
            }

        } finally {
            // 一定要还原：这个标志骗的是全局的 Screen.hasShiftDown()
            FastTooltipFlags.setForcingShift(previous);
        }

        return out;
    }

    /** 价格行：那是商店自己的概念，原版物品 tooltip 里没有。 */
    private static Component priceLine(Row row) {
        return Component.literal((row.buy ? "价格：" : "回收价：") + row.price)
                .withStyle(ChatFormatting.GOLD);
    }
}
