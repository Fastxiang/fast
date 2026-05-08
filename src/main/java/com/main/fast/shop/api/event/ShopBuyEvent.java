package com.main.fast.shop.api.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 购买事件（服务端）
 * 在服务端执行购买前触发 Pre，可取消；购买完成后触发 Post。
 */
public abstract class ShopBuyEvent extends Event {

    protected final Player player;
    protected final String shopId;
    protected final ItemStack stack;
    protected final int amount;
    protected final int totalPrice;

    public ShopBuyEvent(Player player, String shopId, ItemStack stack, int amount, int totalPrice) {
        this.player = player;
        this.shopId = shopId;
        this.stack = stack;
        this.amount = amount;
        this.totalPrice = totalPrice;
    }

    public Player getPlayer() { return player; }
    public String getShopId() { return shopId; }
    public ItemStack getStack() { return stack; }
    public int getAmount() { return amount; }
    public int getTotalPrice() { return totalPrice; }

    /**
     * 购买前触发，可取消。若取消则交易不会发生。
     */
    @net.minecraftforge.eventbus.api.Cancelable
    public static class Pre extends ShopBuyEvent {
        public Pre(Player player, String shopId, ItemStack stack, int amount, int totalPrice) {
            super(player, shopId, stack, amount, totalPrice);
        }
    }

    /**
     * 购买完成后触发（不可取消）。
     */
    public static class Post extends ShopBuyEvent {
        public Post(Player player, String shopId, ItemStack stack, int amount, int totalPrice) {
            super(player, shopId, stack, amount, totalPrice);
        }
    }
}