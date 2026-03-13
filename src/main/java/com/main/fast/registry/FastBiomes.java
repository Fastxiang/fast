package com.main.fast.registry;

import com.main.fast.Fast;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class FastBiomes {
    public static final DeferredRegister<Biome> BIOMES =
            DeferredRegister.create(Registries.BIOME, Fast.MODID);

    public static final RegistryObject<Biome> EMPTY_PLAINS =
            BIOMES.register("empty_plains", FastBiomes::createEmptyPlains);

    public static final ResourceKey<Biome> EMPTY_PLAINS_KEY = ResourceKey.create(
            Registries.BIOME,
            Fast.id("empty_plains")
    );
    
    private static Biome createEmptyPlains() {
        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .fogColor(12638463)
                .waterColor(4159204)
                .waterFogColor(329011)
                .skyColor(7907327)
                .build();

        // 直接传 null 就行（Forge 内部会处理）
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(null, null);

        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        spawns.creatureGenerationProbability(0.0F);

        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8F)
                .downfall(0.0F)
                .specialEffects(effects)
                .mobSpawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }
}
