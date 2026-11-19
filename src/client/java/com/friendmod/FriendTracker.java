package com.friendmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Decides which nickname colour to use and detects "friend hits player" situations.
 * A player is marked red for a few seconds after he was hurt while a friend was swinging at him.
 */
public final class FriendTracker {
	public static final int FRIEND_COLOR = 0x7FD97F;  // slightly green
	public static final int TARGET_COLOR = 0xFF3030;  // red

	private static final int TARGET_TICKS = 100;      // 5 seconds
	private static final double MAX_DISTANCE = 6.0;
	private static final double MIN_AIM_DOT = 0.8;

	private static final Map<UUID, Integer> TARGET_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
	private static int tickCounter;

	private FriendTracker() { }

	/** @return colour for the nickname or null if it should stay as it is */
	public static Integer colorFor(String name, UUID id) {
		Integer until = TARGET_UNTIL.get(id);
		if (until != null && until > tickCounter) {
			return TARGET_COLOR;
		}
		if (FriendList.contains(name)) {
			return FRIEND_COLOR;
		}
		return null;
	}

	public static void tick(Minecraft client) {
		if (client.level == null) {
			TARGET_UNTIL.clear();
			LAST_HURT.clear();
			return;
		}
		tickCounter++;
		TARGET_UNTIL.values().removeIf(until -> until <= tickCounter);

		for (AbstractClientPlayer victim : client.level.players()) {
			int prev = LAST_HURT.getOrDefault(victim.getUUID(), 0);
			int now = victim.hurtTime;
			LAST_HURT.put(victim.getUUID(), now);
			if (now <= prev) {
				continue; // was not freshly hurt this tick
			}

			for (AbstractClientPlayer attacker : client.level.players()) {
				if (attacker == victim || !FriendList.contains(attacker.getPlainTextName()) || !attacker.swinging) {
					continue;
				}
				if (attacker.distanceTo(victim) > MAX_DISTANCE) {
					continue;
				}
				Vec3 toVictim = victim.position().add(0, victim.getBbHeight() / 2, 0).subtract(attacker.getEyePosition()).normalize();
				if (attacker.getLookAngle().dot(toVictim) >= MIN_AIM_DOT) {
					TARGET_UNTIL.put(victim.getUUID(), tickCounter + TARGET_TICKS);
					break;
				}
			}
		}
	}
}
