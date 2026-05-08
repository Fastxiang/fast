package com.main.fast.shop.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 出售事件（服务端）
 * 在服务端执行出售前触发 Pre，可取消；出售完成后触发 Post。
 * Post 事件会提供实际出售的数量，可能因为库存不足而小于请求的数量。
 */
public abstract class ShopSellEvent extends Event {

    protected final Player player;
    protected final String shopId;
    protected final ItemStack stack;
    protected final int requestedAmount;
    protected final int actualAmount;
    protected final int totalEarned;

    public ShopSellEvent(Player player, String shopId, ItemStack stack, int requestedAmount, int actualAmount, int totalEarned) {
        this.player = player;
        this.shopId = shopId;
        this.stack = stack;
        this.requestedAmount = requestedAmount;
        this.actualAmount = actualAmount;
        this.totalEarned = totalEarned;
    }

    public Player getPlayer() { return player; }
    public String getShopId() { return shopId; }
    public ItemStack getStack() { return stack; }
    public int getRequestedAmount() { return requestedAmount; }
    public int getActualAmount() { return actualAmount; }
    public int getTotalEarned() { return totalEarned; }

    @net.minecraftforge.eventbus.api.Cancelable
    public static class Pre extends ShopSellEvent {
        public Pre(Player player, String shopId, ItemStack stack, int requestedAmount, int actualAmount, int totalEarned) {
            super(player, shopId, stack, requestedAmount, actualAmount, totalEarned);
        }
    }

    public static class Post extends ShopSellEvent {
        public Post(Player player, String shopId, ItemStack stack, int requestedAmount, int actualAmount, int totalEarned) {
            super(player, shopId, stack, requestedAmount, actualAmount, totalEarned);
        }
    }
}