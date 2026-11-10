package com.main.fast.dungeon.api.event;

import com.main.fast.dungeon.DungeonInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 每生成一件副本奖励时触发，可以替换最终掉落的物品堆。
 *
 * <p>例如带 enderbonus 的奖励需要带上属性后缀名称，由 KubeJS 桥接脚本在这里用
 * createEnderBonusItem 换掉。</p>
 */
public class DungeonRewardEvent extends Event {

    private final DungeonInstance instance;
    private final String itemId;
    private final int count;
    private final String nbtJson;
    private ItemStack stack;

    public DungeonRewardEvent(DungeonInstance instance, String itemId, int count, String nbtJson, ItemStack stack) {
        this.instance = instance;
        this.itemId = itemId;
        this.count = count;
        this.nbtJson = nbtJson;
        this.stack = stack;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public String getType() {
        return instance.getType();
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    /** 奖励配置里的 nbt（JSON 字符串），没有则为 null */
    public String getNbtJson() {
        return nbtJson;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }
}
