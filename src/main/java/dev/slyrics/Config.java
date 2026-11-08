package dev.slyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, stored in config/slyrics.json. */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("slyrics.json");
	public static Config INSTANCE = new Config();

	public boolean enabled = true;
	/** Spotify Client ID (from developer.spotify.com). */
	public String clientId = "";
	/** Font as identifier, e.g. minecraft:default, minecraft:uniform, minecraft:alt or a custom one from a resource pack. */
	public String font = "minecraft:default";
	public float scale = 1.5f;
	/** Text position in percent of screen width/height (center of the text). */
	public float xPercent = 50f;
	public float yPercent = 75f;
	/** Text color as RGB hex without alpha. */
	public String color = "FF5FB0";
	public boolean shadow = true;
	public boolean bold = true;
	/** Glowing outline around the text. */
	public boolean glow = true;
	/** Symbols after keywords (love -> love ♥). The word is kept. */
	public boolean emojis = true;
	/** Word (lowercase) -> symbol. Can be freely edited in slyrics.json. */
	public java.util.Map<String, String> emojiMap = Emojis.defaults();
	/** Internal: lets new default words be merged into an existing emojiMap once. */
	public int emojiVersion = 0;
	/** Sky mode: random tilt of the words in degrees (+-). */
	public float skyTilt = 12f;
	public boolean typewriter = true;
	/** Also show the previous/next line, small and dimmed. */
	public boolean showNeighbors = false;
	public boolean background = false;
	/** Lyrics timing offset in ms (positive = later). */
	public int offsetMs = 0;
	public int fadeMs = 250;
	/** Show lines as floating holograms in the world instead of HUD text. */
	public boolean hologram = true;
	/** Maximum hologram distance from the player in blocks. */
	public float holoRadius = 12f;
	public float holoScale = 0.8f;
	public int holoMax = 4;
	/** Sky mode: words scattered in the sky instead of in front of you. */
	public boolean skyMode = false;
	/** Sky height in degrees above the horizon. */
	public float skyElevation = 25f;
	public float skyDistance = 40f;
	/** Max words per hologram; longer lines are split into separate holograms shown one after another. */
	public int maxWords = 6;
	public boolean throughWalls = true;
	/** On-screen status message (e.g. when no lyrics were found). */
	public boolean showStatus = true;

	public int rgb() {
		try {
			return Integer.parseInt(color.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return 0xFFFFFF;
		}
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				Config c = GSON.fromJson(Files.readString(FILE), Config.class);
				if (c != null) {
					INSTANCE = c;
					if (c.emojiVersion < 2) { // add the multi-language defaults once, keep user entries
						Emojis.defaults().forEach(c.emojiMap::putIfAbsent);
						c.emojiVersion = 2;
					}
				}
			}
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not load config", e);
		}
		save();
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(INSTANCE));
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not save config", e);
		}
	}
}
