package com.main.fast.spell.capability;

import java.util.ArrayList;
import java.util.List;

public class SkillSortCapability {

    private final List<String> skillOrder = new ArrayList<>();

    public List<String> getSkillOrder() {
        return skillOrder;
    }

    public void setSkillOrder(List<String> order) {
        skillOrder.clear();
        skillOrder.addAll(order);
    }
}