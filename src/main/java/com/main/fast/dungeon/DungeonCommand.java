package com.main.fast.dungeon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.main.fast.Fast;

/**
 * /fast instance message &lt;player&gt; &lt;true|false&gt; —— 开关副本聊天提示（迁移自 Instance/command.js）。
 */
@Mod.EventBusSubscriber(modid = Fast.MODID)
public final class DungeonCommand {

    private DungeonCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("fast")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("instance")
                                .then(Commands.literal("message")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.literal("true")
                                                        .executes(ctx -> setFlag(ctx, true)))
                                                .then(Commands.literal("false")
                                                        .executes(ctx -> setFlag(ctx, false)))))));
    }

    private static int setFlag(CommandContext<CommandSourceStack> ctx, boolean value) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DungeonManager.setChatLogEnabled(player, value);
        return 1;
    }
}
