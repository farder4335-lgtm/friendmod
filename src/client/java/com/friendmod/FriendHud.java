package com.friendmod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/** Shows an arrow and the distance in metres to every friend who is far away. */
public final class FriendHud {
	private static final double MIN_DISTANCE = 20.0;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private FriendHud() { }

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		int y = 20;
		for (AbstractClientPlayer friend : client.level.players()) {
			if (friend == client.player || !FriendList.contains(friend.getPlainTextName())) {
				continue;
			}
			double dx = friend.getX() - client.player.getX();
			double dz = friend.getZ() - client.player.getZ();
			double distance = client.player.distanceTo(friend);
			if (distance < MIN_DISTANCE) {
				continue;
			}

			// Same convention as the player's yaw: 0 = south (+Z), increases clockwise.
			double toFriend = Math.toDegrees(Math.atan2(-dx, dz));
			double relative = Mth.wrapDegrees(toFriend - client.player.getYRot());
			int index = Math.floorMod((int) Math.round(relative / 45.0), 8);

			String text = ARROWS[index] + " " + friend.getPlainTextName() + " " + Math.round(distance) + " м";
			graphics.centeredText(client.font, text, graphics.guiWidth() / 2, y, 0xFF000000 | FriendTracker.FRIEND_COLOR);
			y += 11;
		}
	}
}
