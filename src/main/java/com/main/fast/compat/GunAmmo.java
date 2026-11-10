package com.main.fast.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

/**
 * TACZ 弹药查询：只回答一个问题——女仆现在手上这把枪，背包里还剩多少发匹配的弹药。
 *
 * <p>写法照抄女仆模组 {@code compat/gun/tacz/TacInnerCompat#performGunAttack} 里
 * 「没子弹时去 {@code getAvailableInv} 里找 {@code IAmmo}」那一段，
 * 保证判定口径和女仆自己换弹时完全一致（不然会出现"AI 以为没子弹、实际还能换"）。</p>
 *
 * <p>和 {@link PhantomTacGun} 一样，TACZ 是可选依赖，所有 {@code com.tacz.*} 引用都关在这个类里，
 * 外面只认 {@link #isAvailable()}。</p>
 */
public final class GunAmmo {

    private GunAmmo() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded("tacz");
    }

    /**
     * 主手枪械在女仆可用背包里的弹药总数（不含弹匣里已压好的）。
     *
     * @return 主手不是枪、或没装 TACZ 时返回 0
     */
    public static int countForMainHand(EntityMaid maid) {
        if (!isAvailable()) {
            return 0;
        }
        ItemStack gun = maid.getMainHandItem();
        if (gun.isEmpty() || IGun.getIGunOrNull(gun) == null) {
            return 0;
        }
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        int count = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo != null && ammo.isAmmoOfGun(gun, stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
