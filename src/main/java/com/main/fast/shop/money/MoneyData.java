package com.main.fast.shop.money;

import net.minecraft.nbt.CompoundTag;

/**
 * 金币数据对象
 */
public class MoneyData {

    private int money = 0;

    public int getMoney() {
        return money;
    }

    public void setMoney(int value) {
        this.money = Math.max(0, value);
    }

    public void addMoney(int value) {
        setMoney(money + value);
    }

    public boolean removeMoney(int value) {
        if (money < value) {
            return false;
        }

        money -= value;
        return true;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Money", money);
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        money = tag.getInt("Money");
    }
}
