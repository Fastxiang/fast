package com.main.fast.compat.jei;

import com.main.fast.compat.jei.api.FastJei;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * JEI 插件：把脚本推过来的「获取方式」条目渲染成一个独立分类。
 *
 * <p>数据来源是 {@link FastJei}（KubeJS 客户端脚本在加载阶段填好），
 * JEI 启动时会读一份快照，所以这里必须在 {@link #registerRecipes} 之前
 * 就拿到数据 —— 客户端脚本的加载时机比 JEI 启动早得多，满足这个条件。</p>
 */
@JeiPlugin
public class FastJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation UID = new ResourceLocation("fast", "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(@NotNull IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FastJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        List<FastJeiRecipe> recipes = FastJeiRecipe.buildAll();
        LOGGER.info("[Fast/JEI] 「整合包物品的获取方式」注册 {} 条", recipes.size());

        if (!recipes.isEmpty()) {
            registration.addRecipes(FastJeiCategory.TYPE, recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(@NotNull IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Items.ENDER_CHEST), FastJeiCategory.TYPE);
    }

    /**
     * JEI 启动后逐项回读自己注册的东西。
     *
     * <p>JEI 对「取不到原料的配方」是静默丢弃的（不报错），所以只能这样反查。
     * 每一项单独 try/catch，某一项炸了不影响其它项出数据。</p>
     */
    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime runtime) {
        IRecipeManager manager = runtime.getRecipeManager();
        IFocusFactory factory = runtime.getJeiHelpers().getFocusFactory();

        probe("配方总数", () -> manager.createRecipeLookup(FastJeiCategory.TYPE).includeHidden().get().count());

        probe("分类可见", () -> manager.createRecipeCategoryLookup().get()
                .anyMatch(c -> c.getRecipeType().getUid().equals(FastJeiCategory.TYPE.getUid())));

        probe("物品在JEI", () -> "heroSword=" + inJei(runtime, "fast:the_hero_sword")
                + " key1=" + inJei(runtime, "fast:key1"));

        probe("焦点heroSword", () -> "输出=" + focusHits(manager, factory, "fast:the_hero_sword", RecipeIngredientRole.OUTPUT)
                + " 输入=" + focusHits(manager, factory, "fast:the_hero_sword", RecipeIngredientRole.INPUT));

        probe("焦点key1", () -> focusHits(manager, factory, "fast:key1", RecipeIngredientRole.INPUT));

        probe("原版对照", () -> vanillaControl(manager, factory));

        probe("布局", () -> layoutProbe(manager, factory));

        probe("逐条索引", () -> dumpIndex(runtime, manager, factory));
    }

    /** 把 21 条配方逐条打出来：输出物品、索引键、按输出/输入反查的命中数 */
    private static Object dumpIndex(IJeiRuntime runtime, IRecipeManager manager, IFocusFactory factory) {
        List<FastJeiRecipe> all = manager.createRecipeLookup(FastJeiCategory.TYPE).includeHidden().get().toList();

        StringBuilder sb = new StringBuilder();
        sb.append(all.size()).append(" 条");

        int index = 0;
        for (FastJeiRecipe recipe : all) {
            ItemStack out = recipe.getOutput();
            ItemStack in = recipe.getInput();

            sb.append('\n').append("  #").append(index++)
                    .append(' ').append(out.getItem())
                    .append(" tag=").append(out.getTag())
                    .append(" key=").append(indexKey(runtime, out))
                    .append(" out=").append(hitsByStack(manager, factory, out, RecipeIngredientRole.OUTPUT))
                    .append(" in=").append(in == null ? "-" : hitsByStack(manager, factory, in, RecipeIngredientRole.INPUT));
        }
        return sb.toString();
    }

    /**
     * 算出 JEI 给这个物品堆用的索引键。
     *
     * <p>JEI 的 {@code RecipeMap#getIngredientUid} 是这么分支的：有子类型就用
     * {@code getBase()} 得到的 {@code Item} 对象当键，否则用注册名字符串。
     * 两边算法必须一致，否则永远对不上。</p>
     */
    @SuppressWarnings("unchecked")
    private static String indexKey(IJeiRuntime runtime, ItemStack stack) {
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);

        boolean subtypes = helper.hasSubtypes(stack);
        Object key = subtypes
                ? ((IIngredientTypeWithSubtypes<Item, ItemStack>) VanillaTypes.ITEM_STACK).getBase(stack)
                : helper.getUniqueId(stack, UidContext.Recipe);

        return (subtypes ? "Item:" : "Str:") + key;
    }

    private static long hitsByStack(IRecipeManager manager, IFocusFactory factory, ItemStack stack, RecipeIngredientRole role) {
        IFocus<ItemStack> focus = factory.createFocus(role, VanillaTypes.ITEM_STACK, stack);
        return manager.createRecipeLookup(FastJeiCategory.TYPE)
                .limitFocus(List.of(focus))
                .get()
                .count();
    }

    private static void probe(String name, Supplier<Object> action) {
        try {
            LOGGER.info("[Fast/JEI] 自检·{} = {}", name, action.get());
        } catch (Throwable t) {
            LOGGER.error("[Fast/JEI] 自检·{} 失败", name, t);
        }
    }

    /** 槽位探针：能不能建出布局、里面几个槽几个带料（不碰需要客户端配置的显示接口） */
    private static Object layoutProbe(IRecipeManager manager, IFocusFactory factory) {
        IRecipeCategory<FastJeiRecipe> category = manager.getRecipeCategory(FastJeiCategory.TYPE);
        if (category == null) return "无分类";

        List<FastJeiRecipe> all = manager.createRecipeLookup(FastJeiCategory.TYPE).includeHidden().get().toList();
        if (all.isEmpty()) return "无配方";

        Optional<IRecipeLayoutDrawable<FastJeiRecipe>> drawable =
                manager.createRecipeLayoutDrawable(category, all.get(0), factory.getEmptyFocusGroup());

        return drawable.map(d -> {
            List<? extends IRecipeSlotView> slots = d.getRecipeSlotsView().getSlotViews();
            long filled = slots.stream().filter(s -> !s.isEmpty()).count();
            return slots.size() + "槽/" + filled + "带料";
        }).orElse("空");
    }

    /** 这个物品在不在 JEI 的物品列表里（不在的话焦点查询必然为空） */
    private static boolean inJei(IJeiRuntime runtime, String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return false;

        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return false;

        return runtime.getIngredientManager()
                .getAllIngredients(VanillaTypes.ITEM_STACK)
                .stream()
                .anyMatch(stack -> !stack.isEmpty() && stack.getItem() == item);
    }

    /** 某个物品在某个角色下能查到几条本分类的配方 */
    private static long focusHits(IRecipeManager manager, IFocusFactory factory, String itemId, RecipeIngredientRole role) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return -1;

        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return -2;

        IFocus<ItemStack> focus = factory.createFocus(role, VanillaTypes.ITEM_STACK, new ItemStack(item));
        return manager.createRecipeLookup(FastJeiCategory.TYPE)
                .limitFocus(List.of(focus))
                .get()
                .count();
    }

    /** 对照组：原版工作台配方 + 铁锭（正常应该 > 0，用来确认探针写法没问题） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static long vanillaControl(IRecipeManager manager, IFocusFactory factory) {
        Optional<RecipeType<?>> crafting = manager.getRecipeType(new ResourceLocation("minecraft", "crafting"));
        if (crafting.isEmpty()) return -3;

        IFocus<ItemStack> focus = factory.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(Items.IRON_INGOT));

        return manager.createRecipeLookup((RecipeType) crafting.get())
                .limitFocus(List.of(focus))
                .get()
                .count();
    }
}
