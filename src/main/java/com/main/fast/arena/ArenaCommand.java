package com.main.fast.arena;

import com.main.fast.Fast;
import com.main.fast.dungeon.DungeonDefinition;
import com.main.fast.dungeon.DungeonManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;

/**
 * /fast arena ... —— 调试与安全出口。
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class ArenaCommand {

    private ArenaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fast")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("arena")
                        .then(Commands.literal("list")
                                .executes(ArenaCommand::list))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("enter")
                                .then(Commands.argument("dungeon", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> enter(ctx.getSource().getPlayerOrException(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "dungeon")))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> reset(ctx, null))
                                        .then(Commands.argument("dungeon", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> reset(ctx,
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "dungeon"))))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var runs = ArenaManager.getActiveRuns();
        if (runs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§b[BOSS 战] §f当前没有进行中的挑战"), false);
            return 0;
        }
        for (ArenaRun run : runs) {
            ctx.getSource().sendSuccess(() -> Component.literal("§b[BOSS 战] §f" + run.getDungeonId()
                    + " §7slot=" + run.getSlot() + " §7玩家=" + run.getParticipantCount()), false);
        }
        return runs.size();
    }

    private static int status(ServerPlayer player) {
        ArenaRun run = ArenaManager.getRun(player);
        Set<String> cleared = ArenaManager.getCleared(player.getServer(), player.getUUID());
        player.sendSystemMessage(Component.literal("§b[BOSS 战] §f当前挑战："
                + (run == null ? "无" : run.getDungeonId())));
        player.sendSystemMessage(Component.literal("§b[BOSS 战] §f已首通：" + (cleared.isEmpty() ? "无" : String.join(", ", cleared))));
        Map<String, ArenaConfig> all = ArenaRegistry.getAll();
        player.sendSystemMessage(Component.literal("§b[BOSS 战] §f已注册 BOSS 副本：" + String.join(", ", all.keySet())));
        return 1;
    }

    private static int enter(ServerPlayer player, String dungeonId) {
        DungeonDefinition definition = DungeonManager.getDefinition(dungeonId);
        if (definition == null) {
            player.sendSystemMessage(Component.literal("§c没有这个副本：" + dungeonId));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        boolean ok = ArenaManager.startFirstClear(player, level, player.blockPosition(), definition, 1);
        if (!ok) {
            player.sendSystemMessage(Component.literal("§c进入失败（看日志 [Fast/Arena]）"));
            return 0;
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, String dungeonId) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int count = dungeonId == null
                ? ArenaManager.resetAll(target.getServer(), target.getUUID())
                : (ArenaManager.resetClear(target.getServer(), target.getUUID(), dungeonId) ? 1 : 0);
        ctx.getSource().sendSuccess(() -> Component.literal("§b[BOSS 战] §f已重置 " + count + " 条首通记录"), false);
        return count;
    }
}
