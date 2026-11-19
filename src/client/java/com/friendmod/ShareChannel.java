package com.friendmod;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocketFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Shared "mod chat": every client with the same passphrase exchanges its position through public MQTT brokers.
 * The topic name and the encryption key are both derived from the passphrase, payloads are AES-GCM encrypted.
 */
public final class ShareChannel {
	private static final String[] HOSTS = {"broker.emqx.io", "broker.hivemq.com"};
	private static final int PORT = 8883;
	private static final int SEND_EVERY_TICKS = 100; // 5 seconds
	public static final long MAX_AGE_MS = 90_000;

	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("friendmod-share.json");
	private static final Gson GSON = new Gson();
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "friendmod-share-send");
		t.setDaemon(true);
		return t;
	});

	public record Position(String name, double x, double y, double z, String dimension, long time) { }

	private static final Map<String, Position> POSITIONS = new ConcurrentHashMap<>();
	private static final List<Conn> CONNS = new ArrayList<>();

	private static volatile int generation;
	private static volatile boolean active;
	private static volatile String topic;
	private static volatile SecretKeySpec key;
	private static volatile String selfName = "";
	private static int tickCounter;

	private ShareChannel() { }

	// ---------- public API ----------

	public static void loadAndStart() {
		try {
			if (Files.exists(CONFIG)) {
				JsonObject json = GSON.fromJson(Files.readString(CONFIG), JsonObject.class);
				if (json != null && json.has("phrase") && !json.get("phrase").getAsString().isBlank()) {
					start(json.get("phrase").getAsString(), false);
				}
			}
		} catch (IOException | RuntimeException e) {
			FriendMod.LOGGER.error("Could not read {}", CONFIG, e);
		}
	}

	public static synchronized void start(String phrase, boolean save) {
		stop(false);
		if (save) {
			JsonObject json = new JsonObject();
			json.addProperty("phrase", phrase);
			try {
				Files.writeString(CONFIG, GSON.toJson(json));
			} catch (IOException e) {
				FriendMod.LOGGER.error("Could not write {}", CONFIG, e);
			}
		}
		active = true;
		int gen = ++generation;
		Thread starter = new Thread(() -> {
			try {
				byte[] material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
					.generateSecret(new PBEKeySpec(phrase.toCharArray(), "friendmod-share-v1".getBytes(StandardCharsets.UTF_8), 200_000, 384))
					.getEncoded();
				if (gen != generation) {
					return;
				}
				key = new SecretKeySpec(Arrays.copyOfRange(material, 0, 32), "AES");
				topic = "friendmod/v1/" + HexFormat.of().formatHex(Arrays.copyOfRange(material, 32, 48));
				synchronized (CONNS) {
					CONNS.clear();
					for (String host : HOSTS) {
						Conn conn = new Conn(host, gen);
						CONNS.add(conn);
						Thread t = new Thread(conn::run, "friendmod-share-" + host);
						t.setDaemon(true);
						t.start();
					}
				}
			} catch (Exception e) {
				FriendMod.LOGGER.error("Share channel start failed", e);
			}
		}, "friendmod-share-init");
		starter.setDaemon(true);
		starter.start();
	}

	public static synchronized void stop(boolean deleteConfig) {
		if (active && topic != null) {
			SENDER.execute(() -> sendToAll(new byte[0])); // clear our retained position
		}
		active = false;
		generation++;
		synchronized (CONNS) {
			for (Conn c : CONNS) {
				c.close();
			}
			CONNS.clear();
		}
		POSITIONS.clear();
		if (deleteConfig) {
			try {
				Files.deleteIfExists(CONFIG);
			} catch (IOException e) {
				FriendMod.LOGGER.error("Could not delete {}", CONFIG, e);
			}
		}
	}

	public static boolean isActive() {
		return active;
	}

	public static String status() {
		if (!active) {
			return "off";
		}
		int connected = 0;
		synchronized (CONNS) {
			for (Conn c : CONNS) {
				if (c.connected) {
					connected++;
				}
			}
		}
		return "on, brokers connected: " + connected + "/" + HOSTS.length + ", positions received: " + POSITIONS.size();
	}

	public static List<Position> positions() {
		long now = System.currentTimeMillis();
		List<Position> list = new ArrayList<>();
		for (Position p : POSITIONS.values()) {
			if (Math.abs(now - p.time()) <= MAX_AGE_MS) {
				list.add(p);
			}
		}
		return list;
	}

	public static void tick(Minecraft client) {
		if (!active || client.player == null || client.level == null) {
			return;
		}
		selfName = client.player.getPlainTextName();
		if (++tickCounter < SEND_EVERY_TICKS) {
			return;
		}
		tickCounter = 0;
		if (key == null) {
			return;
		}
		JsonObject json = new JsonObject();
		json.addProperty("n", selfName);
		json.addProperty("x", client.player.getX());
		json.addProperty("y", client.player.getY());
		json.addProperty("z", client.player.getZ());
		json.addProperty("d", client.level.dimension().toString());
		json.addProperty("t", System.currentTimeMillis());
		byte[] payload = encrypt(json.toString().getBytes(StandardCharsets.UTF_8));
		SENDER.execute(() -> sendToAll(payload));
	}

	// ---------- crypto ----------

	private static byte[] encrypt(byte[] plain) {
		try {
			byte[] iv = new byte[12];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
			byte[] enc = cipher.doFinal(plain);
			byte[] out = new byte[iv.length + enc.length];
			System.arraycopy(iv, 0, out, 0, iv.length);
			System.arraycopy(enc, 0, out, iv.length, enc.length);
			return out;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] decrypt(byte[] data) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, 12));
		return cipher.doFinal(data, 12, data.length - 12);
	}

	private static void sendToAll(byte[] payload) {
		List<Conn> copy;
		synchronized (CONNS) {
			copy = new ArrayList<>(CONNS);
		}
		for (Conn c : copy) {
			c.publish(payload);
		}
	}

	private static void handleIncoming(byte[] payload) {
		if (payload.length <= 12 || key == null) {
			return;
		}
		try {
			JsonObject json = GSON.fromJson(new String(decrypt(payload), StandardCharsets.UTF_8), JsonObject.class);
			String name = json.get("n").getAsString();
			if (name.equalsIgnoreCase(selfName)) {
				return;
			}
			POSITIONS.put(name.toLowerCase(java.util.Locale.ROOT), new Position(name, json.get("x").getAsDouble(),
				json.get("y").getAsDouble(), json.get("z").getAsDouble(), json.get("d").getAsString(), json.get("t").getAsLong()));
		} catch (Exception ignored) {
			// wrong key or foreign data on the same topic: ignore
		}
	}

	// ---------- minimal MQTT 3.1.1 client ----------

	private static final class Conn {
		final String host;
		final int gen;
		volatile boolean connected;
		private volatile Socket socket;
		private volatile DataOutputStream out;

		Conn(String host, int gen) {
			this.host = host;
			this.gen = gen;
		}

		void close() {
			connected = false;
			try {
				Socket s = socket;
				if (s != null) {
					s.close();
				}
			} catch (IOException ignored) {
				// closing
			}
		}

		void run() {
			long backoff = 3000;
			while (gen == generation) {
				try {
					session();
					backoff = 3000;
				} catch (Exception e) {
					FriendMod.LOGGER.debug("MQTT {} error: {}", host, e.toString());
				}
				connected = false;
				if (gen != generation) {
					return;
				}
				try {
					Thread.sleep(backoff);
				} catch (InterruptedException e) {
					return;
				}
				backoff = Math.min(backoff * 2, 30_000);
			}
		}

		private void session() throws IOException {
			Socket s = SSLSocketFactory.getDefault().createSocket();
			s.connect(new InetSocketAddress(host, PORT), 8000);
			s.setSoTimeout(20_000);
			socket = s;
			DataInputStream in = new DataInputStream(s.getInputStream());
			DataOutputStream o = new DataOutputStream(s.getOutputStream());

			byte[] clientId = ("fm" + HexFormat.of().formatHex(randomBytes(8))).getBytes(StandardCharsets.UTF_8);
			byte[] connect = concat(utf("MQTT"), new byte[] {4, 0x02, 0, 60}, utf(clientId));
			o.write(packet(0x10, connect));
			o.flush();

			int type = in.readUnsignedByte();
			readVarInt(in);
			int sessionPresent = in.readUnsignedByte();
			int returnCode = in.readUnsignedByte();
			if ((type >> 4) != 2 || returnCode != 0 || sessionPresent > 1) {
				throw new IOException("MQTT connect refused");
			}

			byte[] subscribe = concat(new byte[] {0, 1}, utf(topic), new byte[] {0});
			o.write(packet(0x82, subscribe));
			o.flush();
			out = o;
			connected = true;

			long lastData = System.currentTimeMillis();
			while (gen == generation) {
				int header;
				try {
					header = in.readUnsignedByte();
				} catch (SocketTimeoutException timeout) {
					if (System.currentTimeMillis() - lastData > 70_000) {
						throw new IOException("MQTT timeout");
					}
					synchronized (o) {
						o.write(new byte[] {(byte) 0xC0, 0});
						o.flush();
					}
					continue;
				}
				lastData = System.currentTimeMillis();
				int length = readVarInt(in);
				byte[] body = new byte[length];
				in.readFully(body);
				if ((header >> 4) == 3) { // PUBLISH
					int topicLen = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
					int offset = 2 + topicLen + (((header >> 1) & 3) > 0 ? 2 : 0);
					handleIncoming(Arrays.copyOfRange(body, offset, body.length));
				}
			}
		}

		void publish(byte[] payload) {
			DataOutputStream o = out;
			if (!connected || o == null) {
				return;
			}
			try {
				synchronized (o) {
					o.write(packet(0x31, concat(utf(topic), payload))); // retained, QoS 0
					o.flush();
				}
			} catch (IOException e) {
				close();
			}
		}
	}

	private static byte[] randomBytes(int n) {
		byte[] b = new byte[n];
		RANDOM.nextBytes(b);
		return b;
	}

	private static byte[] utf(String s) {
		return utf(s.getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] utf(byte[] bytes) {
		byte[] out = new byte[bytes.length + 2];
		out[0] = (byte) (bytes.length >> 8);
		out[1] = (byte) bytes.length;
		System.arraycopy(bytes, 0, out, 2, bytes.length);
		return out;
	}

	private static byte[] concat(byte[]... parts) {
		int total = 0;
		for (byte[] p : parts) {
			total += p.length;
		}
		byte[] out = new byte[total];
		int pos = 0;
		for (byte[] p : parts) {
			System.arraycopy(p, 0, out, pos, p.length);
			pos += p.length;
		}
		return out;
	}

	private static byte[] packet(int header, byte[] body) {
		java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
		bos.write(header);
		int len = body.length;
		do {
			int digit = len % 128;
			len /= 128;
			if (len > 0) {
				digit |= 0x80;
			}
			bos.write(digit);
		} while (len > 0);
		bos.writeBytes(body);
		return bos.toByteArray();
	}

	private static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		int shift = 0;
		int b;
		do {
			b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			shift += 7;
		} while ((b & 0x80) != 0 && shift < 28);
		return value;
	}
}
