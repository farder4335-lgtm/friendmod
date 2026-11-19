package com.friendmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;

/**
 * Shows an arrow and the distance in metres to every friend who is far away.
 * Friends that are loaded on the client use their real position. Friends outside the render distance
 * are located through the server's locator-bar waypoints (if the server sends them).
 */
public final class FriendHud {
	private static final double MIN_DISTANCE = 20.0;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private FriendHud() { }

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}
		ClientPacketListener connection = client.player.connection;

		// Waypoints sent by the server (direction, sometimes position) keyed by player UUID.
		Map<UUID, TrackedWaypoint> waypoints = new HashMap<>();
		connection.getWaypointManager().forEachWaypoint(client.player, wp -> wp.id().left().ifPresent(id -> waypoints.put(id, wp)));

		TrackedWaypoint.Camera camera = new TrackedWaypoint.Camera() {
			@Override
			public float yaw() {
				return client.player.getYRot();
			}

			@Override
			public Vec3 position() {
				return client.player.getEyePosition();
			}
		};

		int y = 20;
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			String name = info.getProfile().name();
			UUID id = info.getProfile().id();
			if (id.equals(client.player.getUUID()) || !FriendList.contains(name)) {
				continue;
			}

			double distance;   // -1 = unknown
			double relative;   // degrees, 0 = straight ahead, positive = to the right

			Player loaded = client.level.getPlayerByUUID(id);
			if (loaded != null) {
				double dx = loaded.getX() - client.player.getX();
				double dz = loaded.getZ() - client.player.getZ();
				distance = client.player.distanceTo(loaded);
				relative = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - client.player.getYRot());
			} else {
				TrackedWaypoint waypoint = waypoints.get(id);
				if (waypoint == null) {
					continue; // the server does not tell us where this friend is
				}
				relative = waypoint.yawAngleToCamera(client.level, camera, entity -> 1.0F);
				if (Double.isNaN(relative)) {
					continue;
				}
				double distanceSq = waypoint.distanceSquared(client.player);
				distance = Double.isInfinite(distanceSq) ? -1 : Math.sqrt(distanceSq);
			}

			if (distance >= 0 && distance < MIN_DISTANCE) {
				continue;
			}

			int index = Math.floorMod((int) Math.round(relative / 45.0), 8);
			String meters = distance < 0 ? "?" : String.valueOf(Math.round(distance));
			String text = ARROWS[index] + " " + name + " " + meters + " м";
			graphics.centeredText(client.font, text, graphics.guiWidth() / 2, y, 0xFF000000 | FriendTracker.FRIEND_COLOR);
			y += 11;
		}
	}
}
