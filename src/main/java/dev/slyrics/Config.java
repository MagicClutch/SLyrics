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
	/** Symbols after keywords (love -> love ♥). The word is kept. */
	public boolean emojis = true;
	/** Word (lowercase) -> symbol. Can be freely edited in slyrics.json. */
	public java.util.Map<String, String> emojiMap = Emojis.defaults();
	/** Internal: lets new default words be merged into an existing emojiMap once. */
	public int emojiVersion = 0;
	public boolean typewriter = true;
	/** Also show the previous/next line, small and dimmed. */
	public boolean showNeighbors = false;
	public boolean background = false;
	/** Lyrics timing offset in ms (positive = later). */
	public int offsetMs = 0;
	/** Show lines as floating holograms in the world instead of HUD text. */
	public int fadeMs = 250;
	public boolean hologram = true;
	/** false = Words (each word on its own), true = Lines (whole line, current word lights up). */
	public boolean lineMode = false;
	/** false = scattered around the crosshair, true = in a row straight ahead. */
	public boolean centerLayout = false;
	public boolean uppercase = false;
	/** Closest / farthest distance of the words in blocks. */
	public float near = 4f;
	public float far = 8f;
	/** Words follow you when you walk. */
	public boolean follow = false;
	/** AFK camera: after afkSeconds without input the camera orbits the player like a cinematic shot. */
	public boolean afkEnabled = false;
	public int afkSeconds = 10;
	public boolean afkOnlyWhilePlaying = true;
	/** Orbit radius in blocks, height above the eyes in blocks, speed in degrees per second. */
	public float afkRadius = 7f;
	public float afkHeight = 1.5f;
	public float afkSpeed = 12f;
	/** Which camera shots may be used. */
	public boolean afkOrbit = true;
	public boolean afkRise = true;
	public boolean afkDescend = true;
	public boolean afkDiagonal = true;
	public boolean afkFlyby = true;
	public boolean afkPushIn = true;
	public boolean afkPullOut = true;
	/** Fallback shot length in seconds (used when the lyric timing for the next cut is unknown). */
	public int afkShotSeconds = 12;
	/** Hard cut to a new shot every this many lyric lines (cuts only happen on line changes). */
	public int afkCutEveryLines = 3;
	/** Pick the next shot at random (otherwise in a fixed order). */
	public boolean afkRandomOrder = true;
	/** The camera never gets farther than this from the player, in blocks. */
	public float afkMaxDistance = 8f;
	/** Close-up shots (camera 2-3 blocks away at head height). */
	public boolean afkCloseUp = true;
	/** Chance in percent that the next shot is a close-up. */
	public int afkCloseUpPercent = 45;
	/** Fade to black between shots instead of gliding from one to the next. */
	public boolean afkFadeBetween = false;
	/** Keep words out of blocks: they are moved in front of walls/terrain and never placed inside the ground. */
	public boolean blockClipping = true;
	/** Text size in percent. */
	public int size = 100;
	public int opacity = 100;
	public int glow = 60;
	public int outline = 60;
	public int blur = 50;
	public int bounce = 100;
	public boolean flicker = false;
	/** 3D text: the words get a body, drawn as stacked layers behind the front face. */
	public boolean depth3d = false;
	/** Thickness of the 3D body in font pixels. */
	public float depthSize = 3f;
	/** Sky mode: huge words far away in the sky that flash up and drift slowly like clouds. */
	public boolean skyMode = false;
	/** Distance of the sky words in blocks. */
	public float skyDistance = 90f;
	/** Preferred height above the horizon in degrees. */
	public float skyElevation = 30f;
	/** Height of the letters in degrees of your view. */
	public float skyTextDeg = 8f;
	public String glowColor = "DDE9FF";
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
