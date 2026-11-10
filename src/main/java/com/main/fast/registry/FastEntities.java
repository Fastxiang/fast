package com.main.fast.registry;

import com.main.fast.Fast;
import com.main.fast.entity.FastSwordEntity;
import com.main.fast.entity.BossEntityAlex;
import com.main.fast.entity.BossMysteriousBlackCloakedSwordsman;
import com.main.fast.entity.BossPhantomHero;
import com.main.fast.entity.FastBossEntity;
import com.main.fast.entity.projectile.HomingArrow;
import com.main.fast.entity.projectile.MagicArrow;
import com.main.fast.entity.projectile.PiercingArrow;
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

    public static final RegistryObject<EntityType<BossMysteriousBlackCloakedSwordsman>> MYSTERIOUS_BLACK_CLOAKED_SWORDSMAN =
            register("mysterious_black_cloaked_swordsman",
                    EntityType.Builder.of(BossMysteriousBlackCloakedSwordsman::new, MobCategory.MONSTER),
                    0.6F, 1.95F);

    // 昔日勇者的幻影
    public static final RegistryObject<EntityType<BossPhantomHero>> PHANTOM_HERO =
            register("phantom_hero",
                    EntityType.Builder.of(BossPhantomHero::new, MobCategory.MONSTER),
                    0.6F, 1.95F);

    public static final RegistryObject<EntityType<PiercingArrow>> PIERCING_ARROW =
            registerProjectile("piercing_arrow",
                    EntityType.Builder.<PiercingArrow>of(PiercingArrow::new, MobCategory.MISC),
                    0.5F, 0.5F);

    public static final RegistryObject<EntityType<HomingArrow>> HOMING_ARROW =
            registerProjectile("homing_arrow",
                    EntityType.Builder.<HomingArrow>of(HomingArrow::new, MobCategory.MISC),
                    0.5F, 0.5F);

    public static final RegistryObject<EntityType<MagicArrow>> MAGIC_ARROW =
            registerProjectile("magic_arrow",
                    EntityType.Builder.<MagicArrow>of(MagicArrow::new, MobCategory.MISC),
                    0.5F, 0.5F);

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

    private static <T extends Entity> RegistryObject<EntityType<T>> registerProjectile(
            String registryName,
            EntityType.Builder<T> entityTypeBuilder,
            float xz, float y
    ) {
        return ENTITIES.register(registryName,
                () -> entityTypeBuilder.sized(xz, y)
                        .clientTrackingRange(4)
                        .updateInterval(20)
                        .build(Fast.MODID + ":" + registryName));
    }

}
