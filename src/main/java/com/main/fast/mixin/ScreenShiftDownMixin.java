package com.main.fast.mixin;

import com.main.fast.compat.apricity.FastTooltipFlags;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 配合 {@link FastTooltipFlags}：开关打开的那一瞬，让 {@code Screen.hasShiftDown()} 返回 true。
 *
 * <p>用途只有一个 —— 商店详情里要把「按住 Shift 才展开」的那部分说明也算出来，
 * 好接在普通说明下面一起显示（见 {@code FastShopUIBridge#pinnedTooltipLinesFor}）。
 * 各模组那句折叠说明是查 {@code Screen.hasShiftDown()} 的，除此之外没有别的办法
 * 让它们把内容吐出来。
 *
 * <p>窗口极短（就一次 {@code getTooltipLines} 调用），而且外面套了 try/finally，
 * 所以不会影响到别处对 Shift 的判断。
 */
@Mixin(Screen.class)
public class ScreenShiftDownMixin {

    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void fast$forceShiftForTooltip(CallbackInfoReturnable<Boolean> cir) {

        if (FastTooltipFlags.isForcingShift()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
