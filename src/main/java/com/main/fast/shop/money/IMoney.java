package com.main.fast.shop.money;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/**
 * 注册 Capability 类型
 */
@AutoRegisterCapability
public interface IMoney {

    int getMoney();

    void setMoney(int value);

    void addMoney(int value);

    boolean removeMoney(int value);
}
