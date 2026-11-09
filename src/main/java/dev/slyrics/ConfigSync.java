package dev.slyrics;

import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.lang.reflect.Modifier;

/**
 * Resourceful Config stores the settings (RcConfig); the rest of the mod reads them from Config.INSTANCE.
 * This class copies the values between the two, matching fields by name.
 */
public final class ConfigSync {
	public static final Configurator CONFIGURATOR = new Configurator("slyrics");
	private static int ticks;
	private static boolean scaledDown;

	private ConfigSync() {}

	/** Registers the config with Resourceful Config and brings both sides in line. Call after Config.load(). */
	public static void init() {
		boolean hadFile = Files.exists(FabricLoader.getInstance().getConfigDir().resolve("slyrics.jsonc"));
		var old = hadFile ? readOldLayout() : null; // before anything touches the file: Resourceful Config rewrites it
		CONFIGURATOR.register(RcConfig.class);
		if (hadFile) {
			// register() does not read the file; without loading it the fields keep their defaults and would overwrite the real settings
			CONFIGURATOR.loadConfig(RcConfig.class);
			fromRc();
			if (old != null) {
				applyOld(old);
				toRc();
				CONFIGURATOR.saveConfig(RcConfig.class);
			}
		} else { // first run with Resourceful Config: take over the settings from the old slyrics.json
			toRc();
			CONFIGURATOR.saveConfig(RcConfig.class);
		}
	}

	/**
	 * The categories used to be laid out differently (Spotify, World words, Effects, ...). Settings are stored per category,
	 * so after the regrouping the old values would be lost; this reads them from the old file by name and keeps them.
	 */
	private static com.google.gson.JsonObject readOldLayout() {
		try (var in = Files.newBufferedReader(FabricLoader.getInstance().getConfigDir().resolve("slyrics.jsonc"))) {
			var reader = new com.google.gson.stream.JsonReader(in);
			reader.setStrictness(com.google.gson.Strictness.LENIENT);
			var root = com.google.gson.JsonParser.parseReader(reader);
			if (!root.isJsonObject() || !root.getAsJsonObject().has("Spotify") || root.getAsJsonObject().has("Connectivity")) return null;
			return root.getAsJsonObject();
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not migrate the old config layout", e);
			return null;
		}
	}

	private static void applyOld(com.google.gson.JsonObject o) {
		for (var e : o.entrySet()) {
			if (e.getValue().isJsonObject()) {
				applyOld(e.getValue().getAsJsonObject());
				continue;
			}
			if (!e.getValue().isJsonPrimitive()) continue;
			var v = e.getValue().getAsJsonPrimitive();
			try {
				Field cf = Config.class.getField(e.getKey());
				Class<?> t = cf.getType();
				if (t == boolean.class && v.isBoolean()) cf.setBoolean(Config.INSTANCE, v.getAsBoolean());
				else if (t == int.class && v.isNumber()) cf.setInt(Config.INSTANCE, v.getAsInt());
				else if (t == float.class && v.isNumber()) cf.setFloat(Config.INSTANCE, v.getAsFloat());
				else if (t == String.class) {
					if (v.isNumber()) cf.set(Config.INSTANCE, String.format("%06X", v.getAsInt() & 0xFFFFFF)); // colors
					else if (e.getKey().equals("textAnim")) cf.set(Config.INSTANCE, RcConfig.TextAnim.valueOf(v.getAsString()).label);
					else cf.set(Config.INSTANCE, v.getAsString());
				}
			} catch (NoSuchFieldException | IllegalArgumentException ignored) {
				// not a setting of ours
			} catch (IllegalAccessException ex) {
				SLyricsClient.LOGGER.warn("Migration failed for {}", e.getKey(), ex);
			}
		}
	}

	/** Called every client tick; picks up changes made in the config screen. */
	public static void tick() {
		if (++ticks % 5 == 0) fromRc();
		if (scaledDown) {
			Minecraft mc = Minecraft.getInstance();
			var s = mc.gui.screen();
			if (s == null || !s.getClass().getName().startsWith("com.teamresourceful")) { // settings closed: back to the normal GUI scale
				scaledDown = false;
				mc.resizeGui();
			}
		}
	}

	/** Saves a change made in code (commands) to both stores. */
	public static void saveAll() {
		toRc();
		CONFIGURATOR.saveConfig(RcConfig.class);
		Config.save();
	}

	/** Opens the settings screen, drawn a bit smaller than the normal GUI so more fits on screen. */
	public static void openScreen() {
		Minecraft mc = Minecraft.getInstance();
		var parent = mc.gui.screen();
		int down = Math.max(0, Config.INSTANCE.configScaleDown);
		var win = mc.getWindow();
		if (down > 0 && !scaledDown) {
			int s = Math.max(1, win.getGuiScale() - down);
			if (s != win.getGuiScale()) {
				win.setGuiScale(s);
				scaledDown = true;
			}
		}
		mc.gui.setScreen(com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigScreen.make(CONFIGURATOR, RcConfig.class).withParent(parent).build());
	}

	public static void connectSpotify() {
		fromRc();
		Config.save();
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			mc.gui.setScreen(null);
			if (mc.player != null) mc.player.connection.sendCommand("slyrics login");
		});
	}

	public static void fromRc() {
		copy(true);
	}

	public static void toRc() {
		copy(false);
	}

	private static void copy(boolean fromRc) {
		for (Class<?> cat : RcConfig.class.getDeclaredClasses()) {
			for (Field rf : cat.getDeclaredFields()) {
				if (!Modifier.isStatic(rf.getModifiers()) || Modifier.isFinal(rf.getModifiers())) continue;
				try {
					Field cf = Config.class.getField(rf.getName());
					rf.setAccessible(true);
					if (fromRc) cf.set(Config.INSTANCE, toConfig(rf.get(null), cf.getType()));
					else rf.set(null, toRc(cf.get(Config.INSTANCE), rf.getType()));
				} catch (NoSuchFieldException ignored) {
					// an entry that has no counterpart in Config
				} catch (ReflectiveOperationException | RuntimeException e) {
					SLyricsClient.LOGGER.warn("Config sync failed for {}", rf.getName(), e);
				}
			}
		}
	}

	private static Object toConfig(Object v, Class<?> target) {
		if (target == String.class && v instanceof Integer i) return String.format("%06X", i & 0xFFFFFF); // color
		if (target == String.class && v instanceof RcConfig.TextAnim a) return a.label;
		return v;
	}

	private static Object toRc(Object v, Class<?> target) {
		if (target == int.class && v instanceof String s) {
			try {
				return Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF;
			} catch (NumberFormatException e) {
				return 0xFFFFFF;
			}
		}
		if (target == RcConfig.TextAnim.class && v instanceof String s) return RcConfig.TextAnim.of(s);
		return v;
	}
}
