package dev.slyrics.profiles;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.slyrics.Config;
import dev.slyrics.ConfigSync;
import dev.slyrics.RcConfig;
import dev.slyrics.SLyricsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Named copies of all settings in config/slyrics-profiles/<name>.json. Your Spotify client ID is never stored in them. */
public final class Profiles {
	private static final String SKIP = "clientId";

	private Profiles() {}

	public static Path folder() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("slyrics-profiles");
		try {
			Files.createDirectories(dir);
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not create the profiles folder", e);
		}
		return dir;
	}

	/** File-system safe version of a name. */
	public static String clean(String name) {
		String n = name == null ? "" : name.trim().replaceAll("[^\\p{L}\\p{N} _.-]", "").replaceAll("^\\.+", "");
		return n.length() > 40 ? n.substring(0, 40).trim() : n;
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(folder())) {
			s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
		} catch (Exception ignored) {
			// no folder yet
		}
		return out;
	}

	/** The names of all settings the config screen knows. */
	private static List<Field> settingFields() {
		List<Field> out = new ArrayList<>();
		for (Class<?> cat : RcConfig.class.getDeclaredClasses()) {
			for (Field rf : cat.getDeclaredFields()) {
				if (!Modifier.isStatic(rf.getModifiers()) || Modifier.isFinal(rf.getModifiers()) || rf.getName().equals(SKIP)) continue;
				try {
					out.add(Config.class.getField(rf.getName()));
				} catch (NoSuchFieldException ignored) {
					// a button or an entry that has no counterpart in Config
				}
			}
		}
		return out;
	}

	public static boolean exists(String rawName) {
		String n = clean(rawName);
		return !n.isEmpty() && Files.isRegularFile(folder().resolve(n + ".json"));
	}

	public static String active() {
		return Config.INSTANCE.activeProfile == null ? "" : Config.INSTANCE.activeProfile;
	}

	private static void setActive(String name) {
		Config.INSTANCE.activeProfile = name;
		Config.save();
	}

	private static JsonObject snapshot() throws Exception {
		JsonObject o = new JsonObject();
		for (Field f : settingFields()) {
			Object v = f.get(Config.INSTANCE);
			if (v instanceof Boolean b) o.addProperty(f.getName(), b);
			else if (v instanceof Number n) o.addProperty(f.getName(), n);
			else if (v instanceof String s) o.addProperty(f.getName(), s);
		}
		return o;
	}

	/** True if a profile is active and the current settings differ from what is saved in it. */
	public static boolean modified() {
		String name = active();
		if (name.isEmpty() || !exists(name)) return false;
		try {
			JsonObject now = snapshot();
			JsonObject saved = JsonParser.parseString(Files.readString(folder().resolve(clean(name) + ".json"))).getAsJsonObject();
			for (var e : now.entrySet()) {
				JsonElement other = saved.get(e.getKey());
				if (other == null || !other.isJsonPrimitive() || !other.getAsString().equals(e.getValue().getAsString())) return true;
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	/** Saves the current settings under this name (replacing a profile of the same name) and makes it the active profile. Null if the name is empty. */
	public static String save(String rawName) throws Exception {
		String name = clean(rawName);
		if (name.isEmpty()) return null;
		Files.writeString(folder().resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()));
		setActive(name);
		return name;
	}

	/** Applies a saved profile to the running settings. Returns false if it does not exist. */
	public static boolean load(String rawName) throws Exception {
		Path file = folder().resolve(clean(rawName) + ".json");
		if (!Files.isRegularFile(file)) return false;
		JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		for (Field f : settingFields()) {
			JsonElement e = o.get(f.getName());
			if (e == null || !e.isJsonPrimitive()) continue;
			var v = e.getAsJsonPrimitive();
			Class<?> t = f.getType();
			if (t == boolean.class && v.isBoolean()) f.setBoolean(Config.INSTANCE, v.getAsBoolean());
			else if (t == int.class && v.isNumber()) f.setInt(Config.INSTANCE, v.getAsInt());
			else if (t == float.class && v.isNumber()) f.setFloat(Config.INSTANCE, v.getAsFloat());
			else if (t == String.class && v.isString()) f.set(Config.INSTANCE, v.getAsString());
		}
		Config.settingsChanged();
		setActive(clean(rawName));
		ConfigSync.saveAll();
		return true;
	}

	/** The profile as text that can be shared (file or clipboard). */
	public static String exportText(String rawName, boolean pretty) throws Exception {
		Path file = folder().resolve(clean(rawName) + ".json");
		if (!Files.isRegularFile(file)) throw new IllegalArgumentException("No profile named '" + rawName + "'");
		JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		JsonObject out = new JsonObject();
		out.addProperty("_profile", clean(rawName));
		for (var e : o.entrySet()) out.add(e.getKey(), e.getValue());
		return (pretty ? new GsonBuilder().setPrettyPrinting().create() : new GsonBuilder().create()).toJson(out);
	}

	/**
	 * Saves shared profile text as a new profile. The name comes from rawName, or from the text itself if empty.
	 * Only known settings are kept (never a client ID). Refuses to replace an existing profile.
	 */
	public static String importText(String text, String rawName) throws Exception {
		return importText(text, rawName, "imported");
	}

	/** If rawName is empty the name is taken from the text (or fallback) and made unique ("name-2") instead of refused. */
	public static String importText(String text, String rawName, String fallback) throws Exception {
		JsonObject in;
		try {
			in = JsonParser.parseString(text.trim()).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalArgumentException("That is not a profile");
		}
		String name = clean(rawName);
		if (name.isEmpty()) {
			if (in.has("_profile") && in.get("_profile").isJsonPrimitive()) name = clean(in.get("_profile").getAsString());
			if (name.isEmpty()) name = clean(fallback);
			if (name.isEmpty()) name = "imported";
			String base = name;
			for (int i = 2; exists(name); i++) name = base + "-" + i;
		} else if (exists(name)) {
			throw new IllegalArgumentException("'" + name + "' already exists. Type another name");
		}
		JsonObject kept = new JsonObject();
		for (Field f : settingFields()) {
			JsonElement e = in.get(f.getName());
			if (e != null && e.isJsonPrimitive()) kept.add(f.getName(), e);
		}
		if (kept.size() == 0) throw new IllegalArgumentException("That is not a profile");
		Files.writeString(folder().resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(kept));
		return name;
	}

	public static Path exportsFolder() {
		Path dir = folder().resolve("exports");
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
			// reported when writing
		}
		return dir;
	}

	public static boolean delete(String rawName) {
		try {
			if (clean(rawName).equals(active())) setActive("");
			return Files.deleteIfExists(folder().resolve(clean(rawName) + ".json"));
		} catch (Exception e) {
			return false;
		}
	}
}
