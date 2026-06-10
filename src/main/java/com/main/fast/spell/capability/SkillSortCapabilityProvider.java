package com.main.fast.spell.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SkillSortCapabilityProvider
        implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<SkillSortCapability> CAPABILITY =
            CapabilityManager.get(
                    new CapabilityToken<>() {}
            );

    private final SkillSortCapability capability =
            new SkillSortCapability();

    private final LazyOptional<SkillSortCapability> optional =
            LazyOptional.of(() -> capability);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(
            @Nonnull Capability<T> cap,
            @Nullable Direction side
    ) {
        return cap == CAPABILITY
                ? optional.cast()
                : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {

        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();

        for (String skill : capability.getSkillOrder()) {
            list.add(StringTag.valueOf(skill));
        }

        tag.put("SkillOrder", list);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {

        capability.getSkillOrder().clear();

        ListTag list =
                tag.getList(
                        "SkillOrder",
                        Tag.TAG_STRING
                );

        for (Tag element : list) {
            capability.getSkillOrder().add(
                    element.getAsString()
            );
        }
    }
}