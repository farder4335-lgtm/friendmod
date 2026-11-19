package com.friendmod;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class FriendMod implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("friendmod");

	@Override
	public void onInitializeClient() {
		FriendList.load();
		ClientTickEvents.END_CLIENT_TICK.register(FriendTracker::tick);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
			dispatcher.register(
				ClientCommands.literal("friend")
					.then(ClientCommands.literal("add")
						.then(ClientCommands.argument("nick", StringArgumentType.word())
							.executes(ctx -> {
								String nick = StringArgumentType.getString(ctx, "nick");
								if (FriendList.add(nick)) {
									ctx.getSource().sendFeedback(
										Component.literal(nick + " добавлен в друзья").withStyle(ChatFormatting.GREEN));
								} else {
									ctx.getSource().sendError(Component.literal(nick + " уже в друзьях"));
								}
								return 1;
							})))
					.then(ClientCommands.literal("remove")
						.then(ClientCommands.argument("nick", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FriendList.all(), builder))
							.executes(ctx -> {
								String nick = StringArgumentType.getString(ctx, "nick");
								if (FriendList.remove(nick)) {
									ctx.getSource().sendFeedback(
										Component.literal(nick + " удалён из друзей").withStyle(ChatFormatting.YELLOW));
								} else {
									ctx.getSource().sendError(Component.literal(nick + " не в списке друзей"));
								}
								return 1;
							})))
					.then(ClientCommands.literal("list")
						.executes(ctx -> {
							List<String> friends = FriendList.all();
							ctx.getSource().sendFeedback(Component.literal(friends.isEmpty()
								? "Список друзей пуст"
								: "Друзья (" + friends.size() + "): " + String.join(", ", friends)));
							return friends.size();
						}))));
	}
}
