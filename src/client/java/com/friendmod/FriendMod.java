package com.friendmod;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

public class FriendMod implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("friendmod");

	@Override
	public void onInitializeClient() {
		FriendList.load();
		ShareChannel.loadAndStart();
		ClientTickEvents.END_CLIENT_TICK.register(ShareChannel::tick);
		ClientTickEvents.END_CLIENT_TICK.register(FriendTracker::tick);

		// Do not hit friends: cancel the attack on the client.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
			entity instanceof Player target && FriendList.contains(target.getPlainTextName())
				? InteractionResult.FAIL
				: InteractionResult.PASS);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("friendmod", "friend_distance"), FriendHud::render);

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
					.then(ClientCommands.literal("share")
						.then(ClientCommands.literal("off")
							.executes(ctx -> {
								ShareChannel.stop(true);
								ctx.getSource().sendFeedback(Component.literal("Общий канал выключен"));
								return 1;
							}))
						.then(ClientCommands.literal("status")
							.executes(ctx -> {
								ctx.getSource().sendFeedback(Component.literal("Общий канал: " + ShareChannel.status()));
								return 1;
							}))
						.then(ClientCommands.argument("phrase", StringArgumentType.greedyString())
							.executes(ctx -> {
								String phrase = StringArgumentType.getString(ctx, "phrase").trim();
								if (phrase.length() < 8) {
									ctx.getSource().sendError(Component.literal("Фраза слишком короткая: нужно минимум 8 символов"));
									return 0;
								}
								ShareChannel.start(phrase, true);
								ctx.getSource().sendFeedback(Component.literal("Общий канал включён. У друга должна быть та же фраза.")
									.withStyle(ChatFormatting.GREEN));
								return 1;
							})))
					.then(ClientCommands.literal("debug")
						.executes(ctx -> {
							for (String line : FriendHud.debugLines()) {
								ctx.getSource().sendFeedback(Component.literal(line));
							}
							return 1;
						}))
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
