package com.main.fast.damage;

import com.main.fast.damage.api.DamageConversionEvent;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 一条伤害类型转化规则的句柄（由 {@link com.main.fast.damage.api.FastDamageType#on} 返回）。
 *
 * <p>本身就是监听器实现，{@link #accept} 被 EventBus 调用；{@link #shutdown()} 用来注销
 * （KubeJS 的 server 脚本重载时配合使用）。</p>
 */
public class ConversionListener implements Consumer<DamageConversionEvent> {

    private final net.minecraftforge.eventbus.api.IEventBus bus;
    private final net.minecraftforge.eventbus.api.EventPriority priority;
    private final Consumer<DamageConversionEvent> handler;
    @Nullable
    private final Object owner;
    private volatile boolean alive = true;

    public ConversionListener(net.minecraftforge.eventbus.api.IEventBus bus,
                              net.minecraftforge.eventbus.api.EventPriority priority,
                              Consumer<DamageConversionEvent> handler,
                              @Nullable Object owner) {
        this.bus = bus;
        this.priority = priority;
        this.handler = handler;
        this.owner = owner;
    }

    @Override
    public void accept(DamageConversionEvent event) {
        if (!alive) return;
        handler.accept(event);
    }

    public void shutdown() {
        if (!alive) return;
        alive = false;
        try {
            bus.unregister(this);
        } catch (Throwable ignored) {
            // EventBus 重载 / 关服时会抛，忽略即可
        }
        DamageTypeManager.removeListener();
    }

    public boolean isAlive() {
        return alive;
    }

    public net.minecraftforge.eventbus.api.EventPriority getPriority() {
        return priority;
    }

    @Nullable
    public Object getOwner() {
        return owner;
    }
}
