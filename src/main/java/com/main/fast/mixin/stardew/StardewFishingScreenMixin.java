package com.main.fast.mixin.stardew;

import com.main.fast.compat.stardew.StardewFishingAutoClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 星露谷钓鱼（{@code stardew_fishing}）—— 自动跟条。
 *
 * <p>{@code FishingScreen} 的 {@code tick()}（SRG {@code m_86600_}）里，
 * 状态机走到 {@code MINIGAME} 时会用 {@code this.inputDown} 推进
 * {@code FishingMinigame#tick(boolean)}。这里在 tick 头部先把 {@code inputDown}
 * 替换成「自动判定」的结果，界面与流程完全不变，只是玩家不用自己点。</p>
 *
 * <p>只在客户端生效：mixin 配置里放在 {@code client} 段。</p>
 */
@Mixin(targets = "com.bonker.stardewfishing.client.FishingScreen")
public class StardewFishingScreenMixin {

    @Inject(method = "m_86600_", at = @At("HEAD"), remap = false)
    private void fast$autoPlayMinigame(CallbackInfo ci) {
        StardewFishingAutoClient.tickScreen((Object) this);
    }
}
