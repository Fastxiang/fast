package com.main.fast.shop.money;

public class MoneyCapability implements IMoney {

    private final MoneyData data = new MoneyData();

    @Override
    public int getMoney() {
        return data.getMoney();
    }

    @Override
    public void setMoney(int value) {
        data.setMoney(value);
    }

    @Override
    public void addMoney(int value) {
        data.addMoney(value);
    }

    @Override
    public boolean removeMoney(int value) {
        return data.removeMoney(value);
    }

    public MoneyData getData() {
        return data;
    }
}
