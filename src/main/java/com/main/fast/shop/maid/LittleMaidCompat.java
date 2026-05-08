package com.main.fast.shop.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.main.fast.shop.item.MaidFoodAutoSellToken;
import com.main.fast.registry.FastItems;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        // 注册凭证物品为女仆饰品
        manager.bind(FastItems.MAID_FOOD_AUTO_SELL_TOKEN.get(), new MaidFoodAutoSellToken());
    }
}
