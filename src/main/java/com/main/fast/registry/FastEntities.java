package com.main.fast.registry;

import com.main.fast.Fast;
import com.main.fast.entity.FastSwordEntity;
import com.main.fast.entity.BossEntityAlex;
import com.main.fast.entity.FastBossEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class FastEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Fast.MODID);

    public static final RegistryObject<EntityType<FastSwordEntity>> FAST_SWORD =
            register("fast_sword", EntityType.Builder.of(FastSwordEntity::new, MobCategory.CREATURE), 0.18F, 0.18F);
            
    public static final RegistryObject<EntityType<FastBossEntity>> FAST_BOSS =
            register("fast_boss",
                    EntityType.Builder.of(FastBossEntity::new, MobCategory.MONSTER),
                    0.6F, 1.95F);
                    
    public static final RegistryObject<EntityType<BossEntityAlex>> BOSS_ALEX =
            register("alex",
                    EntityType.Builder.of(BossEntityAlex::new, MobCategory.MONSTER),
                    0.6F, 1.95F);
            
    private static <T extends Entity> RegistryObject<EntityType<T>> register(
            String registryName,
            EntityType.Builder<T> entityTypeBuilder
    ) {
        return ENTITIES.register(registryName,
                () -> entityTypeBuilder.build(Fast.MODID + ":" + registryName));
    }

    private static <T extends Entity> RegistryObject<EntityType<T>> register(
            String registryName,
            EntityType.Builder<T> entityTypeBuilder,
            float xz, float y
    ) {
        return ENTITIES.register(registryName,
                () -> entityTypeBuilder.sized(xz, y).build(Fast.MODID + ":" + registryName));
    }
}
