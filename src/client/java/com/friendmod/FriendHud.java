package com.friendmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

	/** Text lines for /friend debug: what the server sends and how it matches the friends. */
	public static List<String> debugLines() {
		List<String> lines = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			lines.add("not in a world");
			return lines;
		}
		ClientPacketListener connection = client.player.connection;

		Map<UUID, TrackedWaypoint> waypoints = new HashMap<>();
		int[] total = {0};
		connection.getWaypointManager().forEachWaypoint(client.player, wp -> {
			total[0]++;
			wp.id().left().ifPresent(id -> waypoints.put(id, wp));
		});
		lines.add("waypoints from server: " + total[0] + " (with player UUID: " + waypoints.size() + ")");
		lines.add("friends in list: " + FriendList.all().size() + ", players in Tab: " + connection.getListedOnlinePlayers().size());

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

		for (String friend : FriendList.all()) {
			PlayerInfo info = null;
			for (PlayerInfo candidate : connection.getListedOnlinePlayers()) {
				if (candidate.getProfile().name().equalsIgnoreCase(friend)) {
					info = candidate;
					break;
				}
			}
			if (info == null) {
				lines.add(friend + ": NOT in Tab list (offline, other server, or hidden from Tab)");
				continue;
			}
			UUID id = info.getProfile().id();
			StringBuilder sb = new StringBuilder(friend + ": in Tab");
			sb.append(client.level.getPlayerByUUID(id) != null ? ", loaded nearby" : ", NOT loaded");
			TrackedWaypoint wp = waypoints.get(id);
			if (wp == null) {
				sb.append(", NO waypoint from server");
			} else {
				double distanceSq = wp.distanceSquared(client.player);
				sb.append(", waypoint: ")
					.append(Double.isInfinite(distanceSq) ? "direction only" : Math.round(Math.sqrt(distanceSq)) + " m")
					.append(", angle ")
					.append(Math.round(wp.yawAngleToCamera(client.level, camera, entity -> 1.0F)));
			}
			lines.add(sb.toString());
		}
		return lines;
	}
}
