package com.main.fast.dungeon;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Random;

/**
 * fast:custom_spell 的随机 NBT（对应旧 JS 的 NewCustomSpellNbt）。
 *
 * <p>结构：{@code { spellConfig: { type: 'spell'|'level'|'delay', value: ... } }}</p>
 */
final class DungeonSpellNbt {

    private static final Random RANDOM = new Random();

    /** 法术池（与旧 JS 的 NewCustomSpellNbtList.spell 完全一致） */
    private static final List<String> SPELLS = List.of(
            "irons_spellbooks:fireball", "irons_spellbooks:heat_surge", "irons_spellbooks:wall_of_fire",
            "irons_spellbooks:fire_breath", "irons_spellbooks:magma_bomb", "irons_spellbooks:scorch",
            "irons_spellbooks:burning_dash", "irons_spellbooks:blaze_storm", "irons_spellbooks:firebolt",
            "irons_spellbooks:flaming_barrage", "irons_spellbooks:flaming_strike", "irons_spellbooks:poison_arrow",
            "irons_spellbooks:earthquake", "irons_spellbooks:poison_splash", "irons_spellbooks:spider_aspect",
            "irons_spellbooks:firefly_swarm", "irons_spellbooks:root", "irons_spellbooks:blight",
            "irons_spellbooks:stomp", "irons_spellbooks:acid_orb", "irons_spellbooks:poison_breath",
            "irons_spellbooks:gluttony", "irons_spellbooks:frostbite", "irons_spellbooks:ice_block",
            "irons_spellbooks:ray_of_frost", "irons_spellbooks:cone_of_cold", "irons_spellbooks:frost_step",
            "irons_spellbooks:frostwave", "irons_spellbooks:summon_polar_bear", "irons_spellbooks:icicle",
            "irons_spellbooks:arrow_volley", "irons_spellbooks:fang_ward", "irons_spellbooks:fang_strike",
            "irons_spellbooks:chain_creeper", "irons_spellbooks:gust", "irons_spellbooks:summon_vex",
            "irons_spellbooks:lob_creeper", "irons_spellbooks:wololo", "irons_spellbooks:shield",
            "irons_spellbooks:summon_horse", "irons_spellbooks:spectral_hammer", "irons_spellbooks:firecracker",
            "irons_spellbooks:portal", "irons_spellbooks:black_hole", "irons_spellbooks:recall",
            "irons_spellbooks:magic_missile", "irons_spellbooks:summon_ender_chest", "irons_spellbooks:magic_arrow",
            "irons_spellbooks:teleport", "irons_spellbooks:dragon_breath", "irons_spellbooks:counterspell",
            "irons_spellbooks:healing_circle", "irons_spellbooks:blessing_of_life", "irons_spellbooks:angel_wing",
            "irons_spellbooks:sunbeam", "irons_spellbooks:fortify", "irons_spellbooks:guiding_bolt",
            "irons_spellbooks:heal", "irons_spellbooks:wisp", "irons_spellbooks:divine_smite",
            "irons_spellbooks:greater_heal", "irons_spellbooks:raise_dead", "irons_spellbooks:blood_slash",
            "irons_spellbooks:blood_step", "irons_spellbooks:acupuncture", "irons_spellbooks:ray_of_siphoning",
            "irons_spellbooks:blood_needles", "irons_spellbooks:sacrifice", "irons_spellbooks:devour",
            "irons_spellbooks:wither_skull", "irons_spellbooks:thunderstorm", "irons_spellbooks:ascension",
            "irons_spellbooks:lightning_lance", "irons_spellbooks:shockwave", "irons_spellbooks:electrocute",
            "irons_spellbooks:lightning_bolt", "irons_spellbooks:ball_lightning", "irons_spellbooks:chain_lightning"
    );

    private static final int[] LEVEL_1 = {1};
    private static final int[] LEVEL_2 = {2};
    private static final int[] DELAY_1 = {10};
    private static final int[] DELAY_2 = {10, 5, 15, 20};

    private DungeonSpellNbt() {
    }

    /** 随机一条 spellConfig（NbtLevel 对应旧 JS 的 selectedReward.NbtLevel） */
    static CompoundTag random(int nbtLevel) {
        int level = nbtLevel <= 0 ? 1 : nbtLevel;
        // 旧 JS 的 types 是 ["spell","level","delay","spell","spell"] —— spell 权重 3/5
        String type;
        int roll = RANDOM.nextInt(5);
        if (roll == 1) {
            type = "level";
        } else if (roll == 2) {
            type = "delay";
        } else {
            type = "spell";
        }

        CompoundTag config = new CompoundTag();
        config.putString("type", type);
        switch (type) {
            case "spell" -> config.putString("value", SPELLS.get(RANDOM.nextInt(SPELLS.size())));
            case "level" -> {
                int[] pool = level >= 2 ? LEVEL_2 : LEVEL_1;
                config.putInt("value", pool[RANDOM.nextInt(pool.length)]);
            }
            default -> {
                int[] pool = level >= 2 ? DELAY_2 : DELAY_1;
                config.putInt("value", pool[RANDOM.nextInt(pool.length)]);
            }
        }
        return config;
    }
}
