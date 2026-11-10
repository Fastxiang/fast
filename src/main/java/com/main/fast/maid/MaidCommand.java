package com.main.fast.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.main.fast.Fast;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.Map;

/**
 * /fast maid info —— 打印 nearest 女仆的战斗状态，用来排查「为什么没放技能 / 没走连段」。
 *
 * <p>一条链路三个环节，任何一环断了都能看出来：</p>
 * <ol>
 *     <li>职业 UID 是不是 fast:physical 等（任务没切对就不会进对应 AI）</li>
 *     <li>饰品栏里有没有勇者之剑 / 法杖（JS 用它决定 basic 槽位）</li>
 *     <li>Java 侧快照里 skills / basic 有没有值（JS 没推过来就是「无快照」）</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class MaidCommand {

    private MaidCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fast")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("maid")
                        .then(Commands.literal("info")
                                .executes(MaidCommand::info))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        EntityMaid maid = player.level()
                .getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(16.0D),
                        entity -> entity.isAlive())
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

        if (maid == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§b[女仆] §f16 格内没有女仆"), false);
            return 0;
        }

        String taskUid = maid.getTask() == null ? "null" : maid.getTask().getUid().toString();
        send(ctx, "§b[女仆] §f" + maid.getName().getString() + " §7(" + maid.getUUID() + ")");
        send(ctx, "§7职业: §f" + taskUid);
        send(ctx, "§7主手: §f" + itemId(maid.getMainHandItem()) + " §7副手: §f" + itemId(maid.getOffhandItem()));

        boolean heroSword = hasBauble(maid, "fast:the_hero_sword");
        boolean heroStaff = hasBauble(maid, "fast:the_hero_staff");
        send(ctx, "§7饰品栏: 勇者之剑 " + flag(heroSword) + " §7/ 勇者法杖 " + flag(heroStaff));

        MaidSkillData data = MaidSkillRegistry.getData(maid);
        if (data == null) {
            send(ctx, "§7Java 快照: §c无 §7（KubeJS 还没推过来）");
        } else {
            ResourceLocation physical = data.getBasicSpell("physical");
            ResourceLocation magic = data.getBasicSpell("magic");
            send(ctx, "§7Java 快照: §f" + data.getSkills().size() + " 个技能"
                    + " §7physical=" + (physical == null ? "§c空" : "§a" + physical)
                    + " §7magic=" + (magic == null ? "§c空" : "§a" + magic));
            StringBuilder builder = new StringBuilder();
            int count = 0;
            for (Map.Entry<ResourceLocation, Integer> entry : data.getSkills().entrySet()) {
                if (count++ >= 8) {
                    builder.append(" …");
                    break;
                }
                builder.append(' ').append(entry.getKey().getPath()).append('(').append(entry.getValue()).append(')');
            }
            if (!builder.isEmpty()) {
                send(ctx, "§7技能:§f" + builder);
            }
        }

        boolean casting = maid instanceof IMagicEntity magic && magic.isCasting();
        send(ctx, "§7状态: " + (casting ? "§a吟唱中" : "§f待机")
                + " §7血量: §f" + String.format("%.1f/%.1f", maid.getHealth(), maid.getMaxHealth()));
        return 1;
    }

    private static boolean hasBauble(EntityMaid maid, String itemId) {
        var handler = maid.getMaidBauble();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (itemId.equals(itemId(handler.getStackInSlot(i)))) {
                return true;
            }
        }
        return false;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "空";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? stack.getItem().toString() : id.toString();
    }

    private static String flag(boolean value) {
        return value ? "§a有" : "§c无";
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
