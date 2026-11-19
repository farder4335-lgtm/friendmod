package com.friendmod;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/** Friend list stored in config/friends.json. Nicknames are compared case-insensitively. */
public final class FriendList {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<String>>() { }.getType();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("friends.json");

	/** lowercase nick -> nick as typed */
	private static final Map<String, String> FRIENDS = new LinkedHashMap<>();

	private FriendList() { }

	public static void load() {
		FRIENDS.clear();
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			List<String> names = GSON.fromJson(Files.readString(FILE), LIST_TYPE);
			if (names != null) {
				for (String name : names) {
					FRIENDS.put(key(name), name);
				}
			}
		} catch (IOException | RuntimeException e) {
			FriendMod.LOGGER.error("Could not read {}", FILE, e);
		}
	}

	private static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(new ArrayList<>(FRIENDS.values()), LIST_TYPE));
		} catch (IOException e) {
			FriendMod.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	/** @return false if the player was already a friend */
	public static boolean add(String name) {
		if (FRIENDS.putIfAbsent(key(name), name) != null) {
			return false;
		}
		save();
		return true;
	}

	/** @return false if the player was not a friend */
	public static boolean remove(String name) {
		if (FRIENDS.remove(key(name)) == null) {
			return false;
		}
		save();
		return true;
	}

	public static boolean contains(String name) {
		return FRIENDS.containsKey(key(name));
	}

	public static List<String> all() {
		return new ArrayList<>(FRIENDS.values());
	}

	private static String key(String name) {
		return name.toLowerCase(java.util.Locale.ROOT);
	}
}
