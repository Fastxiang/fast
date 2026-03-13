package com.main.fast.registry;

import com.main.fast.Fast;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FastAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Fast.MODID);

    // 飞剑上限 (最小1，最大100，默认8)
    public static final RegistryObject<Attribute> SWORD_LIMIT =
            ATTRIBUTES.register("sword_limit",
                    () -> new RangedAttribute("attribute.name.fast.sword_limit", 8.0D, 1.0D, 100.0D).setSyncable(true));

    // 飞剑悬浮半径 (最小0.5，最大20，默认2.5)
    public static final RegistryObject<Attribute> SWORD_HOVER_RADIUS =
            ATTRIBUTES.register("sword_hover_radius",
                    () -> new RangedAttribute("attribute.name.fast.sword_hover_radius", 2.5D, 0.5D, 20.0D).setSyncable(true));

    // 飞剑悬浮速度 (最小0.05，最大5，默认0.3)
    public static final RegistryObject<Attribute> SWORD_HOVER_SPEED =
            ATTRIBUTES.register("sword_hover_speed",
                    () -> new RangedAttribute("attribute.name.fast.sword_hover_speed", 0.3D, 0.05D, 5.0D).setSyncable(true));
                    
    // 玩家拉弓速度属性 (最小1，最大10，默认1)
    public static final RegistryObject<Attribute> BOW_DRAW_SPEED =
        ATTRIBUTES.register("bow_draw_speed",
                () -> new RangedAttribute(
                        "attribute.name.fast.bow_draw_speed",
                        1.0D,  // 默认值
                        1.0D,  // 最小值
                        100.0D  // 最大值
                ).setSyncable(true));

}