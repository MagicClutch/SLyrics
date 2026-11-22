package dev.slyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Runtime settings used by the whole mod, read as {@code Config.INSTANCE.xyz}.
 * <p>
 * Everything is stored in config/slyrics.json. The OneConfig page ({@link ConfigLayout}) edits these fields directly.
 */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path FILE = DIR.resolve("slyrics.json");
	/** Versions with Resourceful Config kept the settings in slyrics.jsonc and the rest in slyrics-extra.json; read once on upgrade. */
	private static final Path OLD_EXTRA = DIR.resolve("slyrics-extra.json"), OLD_RC = DIR.resolve("slyrics.jsonc");
	/** Bump when new default emoji words are added: they are merged into existing word lists once. */
	private static final int EMOJI_VERSION = 4;

	public static Config INSTANCE = new Config();

	// ------------------------------------------------------------------ general

	public boolean enabled = true;
	/** Spotify Client ID (from developer.spotify.com). */
	public String clientId = "";
	/** Lyrics timing offset in ms (negative = earlier, positive = later). */
	public int offsetMs = -139;
	/** Where the current song comes from: "Auto" (Spotify first, then any other player), "Spotify" or "Others". */
	public String musicSource = "Auto";
	/** Others: only use media players whose name contains this (empty = any except Spotify). */
	public String otherPlayer = "";
	/** On-screen status message (e.g. when no lyrics were found). */
	public boolean showStatus = true;
	/** Developer mode: shows all the detailed settings in the OneConfig page. Off = only the easy, everyday settings. */
	public boolean devMode = false;

	// ------------------------------------------------------------------ bookkeeping (only in slyrics.json)

	/** The profile that was last saved or loaded (empty = none). Not a setting: it is not part of any profile. */
	public String activeProfile = "";

	/** Which built-in profiles were already created (so deleted ones do not come back). */
	public int defaultProfilesVersion = 0;

	/** Bookkeeping: 1 = switched to the Auto music source and the new media player position once. */
	public int mediaVersion = 0;

	/** Which default emoji words were already merged into the word lists. */
	public int emojiVersion = 0;

	// ------------------------------------------------------------------ world words

	/** Words float in the world, scattered around the crosshair. */
	public boolean hologram = false;
	/** Closest / farthest distance of the words in blocks. */
	public float near = 7.5f;
	public float far = 9.5f;
	/** How far over the screen Aura words spread: 0 = close around the middle, 100 = about 3/4 of the screen. */
	public int auraSpread = 80;
	/** Words follow you when you walk. */
	public boolean follow = false;

	/** How far ahead the prediction looks, in seconds. */
	public float predictSeconds = 1.5f;

	/** Floating mode: words keep drifting through the world instead of staying where they appeared (not for stick-on-blocks). */
	public boolean floating = false;
	/** How fast floating words travel across your view, in degrees per second. */
	public int floatSpeed = 12;

	/** The current lyric line at the bottom of the screen (position in the HUD editor). */
	public boolean lineMode = false;
	/** Lyric line: scrolls through like a ticker, the word being sung stays in the middle. */
	public boolean lineScroll = false;
	/** Lyric line: how many words are shown at once (0 = the whole line). */
	public int lineMaxWords = 0;

	/** True if any of the in-world modes is on (otherwise the plain HUD line is shown). */
	public boolean anyWorld() {
		return hologram || skyMode || stickMode;
	}

	// ------------------------------------------------------------------ sky mode

	/** Huge words far away in the sky that flash up and drift slowly like clouds. */
	public boolean skyMode = true;
	/** Distance of the sky words in blocks. */
	public float skyDistance = 106.6204f;
	/** How far over the screen sky words spread: 0 = close around the middle, 100 = about 3/4 of the screen. */
	public int skySpread = 40;
	/** Height of the letters in degrees of your view. */
	public float skyTextDeg = 6.3174f;

	// ------------------------------------------------------------------ stick on blocks

	/** Words stick flat onto the blocks you look at. */
	public boolean stickMode = false;
	/** How far away blocks may be to get a word, in blocks. */
	public float stickRange = 14f;

	// ------------------------------------------------------------------ effects

	/** Text size in percent. */
	public int size = 100;
	public int opacity = 100;
	public int glow = 0;
	public int outline = 0;
	public int blur = 0;
	public int bounce = 0;
	public boolean flicker = false;
	/** 3D text: the words get a body, drawn as stacked layers behind the front face. */
	public boolean depth3d = false;
	/** Thickness of the 3D body in font pixels. */
	public float depthSize = 3f;

	// ------------------------------------------------------------------ text

	/** Font as identifier: minecraft:default or one of the fonts that come with the mod (see Fonts), e.g. slyrics:bebas_neue. */
	public String font = "minecraft:default";
	/** Colors as RGB hex without alpha. */
	public String color = "FFFFFF";
	public String glowColor = "DDE9FF";
	public boolean uppercase = true;
	public boolean bold = true;
	public boolean shadow = false;
	public boolean typewriter = true;
	public boolean background = false;
	/** Fade in/out time in ms. */
	public int fadeMs = 38;
	/** How long a word stays after it has been sung, in ms (0 = the default of 2 seconds). */
	public int wordHoldMs = 0;
	/** Viral-caption style animation of each sung word (see ConfigLayout.TextAnim). */
	public String textAnim = "None";
	public int animSpeed = 237;

	// ------------------------------------------------------------------ emojis

	/** Emojis after keywords (love -> love ♥, diamond -> a real diamond icon), each in its own color. The word is kept. */
	public boolean emojis = true;
	/** Word (lowercase) -> symbol. Can be freely edited in slyrics.json. */
	public Map<String, String> emojiMap = Emojis.defaults();
	/** Word (lowercase) -> sprite: "item:diamond", "block:poppy" or "gui:hud/heart/full". Can be edited in slyrics.json. */
	public Map<String, String> emojiSpriteMap = Emojis.spriteDefaults();

	// ------------------------------------------------------------------ word sweep

	/** The word being sung is colored from left to right, and the color goes off when the next word starts. */
	public boolean sweepMode = true;
	public String sweepColor = "FF4D4D";
	/** How long the sweep color takes to go off after the word, in ms. */
	public int sweepFadeMs = 76;
	/** Color the whole word at once instead of sweeping across it. */
	public boolean sweepWhole = false;
	public boolean sweepGlow = false;

	// ------------------------------------------------------------------ AFK camera

	/** After afkSeconds without input the camera leaves first person and plays cinematic shots around the player. */
	public boolean afkEnabled = false;
	public int afkSeconds = 10;
	public boolean afkOnlyWhilePlaying = true;
	/** Orbit radius in blocks, height above the eyes in blocks, speed in degrees per second. */
	public float afkRadius = 7f;
	public float afkHeight = 1.5f;
	public float afkSpeed = 12f;
	/** The camera never gets farther than this from the player, in blocks. */
	public float afkMaxDistance = 8f;
	/** In the AFK camera the lyric words stand this far from the player (min / max, in blocks). */
	public float afkTextNear = 3f;
	public float afkTextFar = 5f;
	/** Chance in percent that the next shot is a close-up. */
	public int afkCloseUpPercent = 45;
	/** Pick the next shot at random (otherwise in a fixed order). */
	public boolean afkRandomOrder = true;
	/** Hard cut to a new shot every this many lyric lines (cuts only happen on line changes). */
	public int afkCutEveryLines = 3;
	/** Fallback shot length in seconds (used when the lyric timing for the next cut is unknown). */
	public int afkShotSeconds = 12;
	/** Fade to black between shots instead of cutting instantly. */
	public boolean afkFadeBetween = false;
	/** The AFK camera pans toward the word that is being sung. */
	public boolean afkFollowWord = false;
	/** How far (percent) it pans toward the word: 100 = word centered, lower keeps the player more in view. */
	public int afkFollowStrength = 70;

	// ------------------------------------------------------------------ experimental


	// ------------------------------------------------------------------ HUD line (when no world mode is on)

	public float scale = 2.9202f;
	/** Text position in percent of screen width/height (center of the text). */
	public float xPercent = 50f;
	public float yPercent = 75f;
	/** Also show the previous/next line, small and dimmed. */
	public boolean showNeighbors = false;

	// ------------------------------------------------------------------ karaoke mode (lyrics panel)

	/** A small panel with the previous, current and next line; the current line fills in while it is sung. */
	public boolean hudMode = false;
	/** Top-left corner of the panel in percent of screen width/height. */
	public float hudX = 1.5f;
	public float hudY = 2f;
	/** Panel text size in percent. */
	public int hudScale = 100;

	// ------------------------------------------------------------------ media player widget

	/** A small player window on the screen: cover, title, progress and play / pause / previous / next buttons. */
	public boolean mediaPlayer = true;
	/** Top-left corner in percent of screen width/height. */
	public float mediaX = 0.4f;
	public float mediaY = 0.6f;
	/** Size in percent. */
	public int mediaScale = 100;
	public int mediaOpacity = 85;
	public boolean mediaCover = true;

	// ------------------------------------------------------------------ helpers

	public int rgb() {
		try {
			return Integer.parseInt(color.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return 0xFFFFFF;
		}
	}

	// ------------------------------------------------------------------ file

	public static void load() {
		boolean upgrade = Files.exists(OLD_EXTRA) || Files.exists(OLD_RC);
		try {
			Path source = Files.exists(OLD_EXTRA) ? OLD_EXTRA : FILE; // with the old files around, slyrics.json is an even older leftover
			if (Files.exists(source)) {
				Config c = GSON.fromJson(Files.readString(source), Config.class);
				if (c != null) {
					INSTANCE = c;
					if (c.emojiVersion < EMOJI_VERSION) { // add new default words once, keep user entries
						Emojis.defaults().forEach(c.emojiMap::putIfAbsent);
						Emojis.spriteDefaults().forEach(c.emojiSpriteMap::putIfAbsent);
						c.emojiVersion = EMOJI_VERSION;
					}
				}
			}
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not load config", e);
		}
		if (upgrade) upgradeFromResourcefulConfig();
		migrate();
		if (!Fonts.isKnown(INSTANCE.font)) INSTANCE.font = "minecraft:default"; // fonts from this computer are no longer supported
		save();
	}

	/** Takes the settings over from the Resourceful Config files once, then moves those files out of the way. */
	private static void upgradeFromResourcefulConfig() {
		if (Files.exists(OLD_RC)) {
			try (var in = Files.newBufferedReader(OLD_RC)) {
				var reader = new com.google.gson.stream.JsonReader(in);
				reader.setStrictness(com.google.gson.Strictness.LENIENT);
				var root = com.google.gson.JsonParser.parseReader(reader);
				if (root.isJsonObject()) applyOld(root.getAsJsonObject());
			} catch (Exception e) {
				SLyricsClient.LOGGER.warn("Could not read the old slyrics.jsonc", e);
			}
		}
		for (Path old : new Path[]{OLD_RC, OLD_EXTRA}) {
			try {
				if (Files.exists(old)) Files.move(old, old.resolveSibling(old.getFileName() + ".old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				SLyricsClient.LOGGER.warn("Could not move {} away", old, e);
			}
		}
		SLyricsClient.LOGGER.info("Settings moved from Resourceful Config to config/slyrics.json");
	}

	/** Every value in the old file, matched to a field here by name; sections are only folders, so their layout does not matter. */
	private static void applyOld(com.google.gson.JsonObject o) {
		for (var e : o.entrySet()) {
			if (e.getValue().isJsonObject()) {
				applyOld(e.getValue().getAsJsonObject());
				continue;
			}
			if (!e.getValue().isJsonPrimitive()) continue;
			var v = e.getValue().getAsJsonPrimitive();
			try {
				var cf = Config.class.getField(e.getKey());
				Class<?> t = cf.getType();
				if (t == boolean.class && v.isBoolean()) cf.setBoolean(INSTANCE, v.getAsBoolean());
				else if (t == int.class && v.isNumber()) cf.setInt(INSTANCE, v.getAsInt());
				else if (t == float.class && v.isNumber()) cf.setFloat(INSTANCE, v.getAsFloat());
				else if (t == String.class) {
					if (v.isNumber()) cf.set(INSTANCE, String.format("%06X", v.getAsInt() & 0xFFFFFF)); // colors were stored as numbers
					else if (e.getKey().equals("textAnim")) cf.set(INSTANCE, ConfigLayout.TextAnim.valueOf(v.getAsString()).label);
					else if (e.getKey().equals("musicSource")) cf.set(INSTANCE, ConfigLayout.MusicSource.valueOf(v.getAsString()).label);
					else cf.set(INSTANCE, v.getAsString());
				}
			} catch (NoSuchFieldException | IllegalArgumentException ignored) {
				// not a setting (any more)
			} catch (IllegalAccessException ex) {
				SLyricsClient.LOGGER.warn("Could not take over {}", e.getKey(), ex);
			}
		}
	}

	/** Once: switch to the Auto music source (Spotify first) and move the media player from its old default spot into the corner. */
	private static void migrate() {
		Config c = INSTANCE;
		if (c.mediaVersion >= 7) return;
		if (c.mediaVersion < 1) {
			c.musicSource = "Auto";
			if (c.mediaX == 1.5f && c.mediaY == 2f) {
				c.mediaX = 0.4f;
				c.mediaY = 0.6f;
			}
		}
		if (c.mediaVersion < 5) c.lineMode = !c.anyWorld(); // the lyric line used to show only while no world mode was on
		c.mediaVersion = 7;
	}

	/** Saves and starts the Spotify login (the link appears in chat). */
	public static void connectSpotify() {
		save();
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		mc.execute(() -> {
			mc.gui.setScreen(null);
			if (mc.player != null) mc.player.connection.sendCommand("slyrics login");
		});
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(INSTANCE));
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not save config", e);
		}
	}
}
