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
 * The settings screen and the user-facing values live in Resourceful Config ({@link RcConfig}, config/slyrics.jsonc);
 * {@link ConfigSync} copies them into this class by field name. The file config/slyrics-extra.json keeps what
 * Resourceful Config cannot store: the emoji word lists.
 */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Not "slyrics.json": Resourceful Config treats that name as an old-format file of its own config and deletes it. */
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("slyrics-extra.json");
	/** Where earlier versions kept everything; read once on upgrade. */
	private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("slyrics.json");
	/** Bump when new default emoji words are added: they are merged into existing word lists once. */
	private static final int EMOJI_VERSION = 4;

	public static Config INSTANCE = new Config();

	// ------------------------------------------------------------------ general

	public boolean enabled = true;
	/** Spotify Client ID (from developer.spotify.com). */
	public String clientId = "";
	/** Lyrics timing offset in ms (negative = earlier, positive = later). */
	public int offsetMs = -139;
	/** Where the current song comes from: "Spotify" or "Others". */
	public String musicSource = "Spotify";
	/** Others: only use media players whose name contains this (empty = any except Spotify). */
	public String applePlayer = "";
	/** On-screen status message (e.g. when no lyrics were found). */
	public boolean showStatus = true;

	// ------------------------------------------------------------------ bookkeeping (only in slyrics.json)

	/** The profile that was last saved or loaded (empty = none). Not a setting: it is not part of any profile. */
	public String activeProfile = "";

	/** Which built-in profiles were already created (so deleted ones do not come back). */
	public int defaultProfilesVersion = 0;

	/** Which default emoji words were already merged into the word lists. */
	public int emojiVersion = 0;

	// ------------------------------------------------------------------ world words

	/** Words float in the world, scattered around the crosshair. */
	public boolean hologram = false;
	/** Closest / farthest distance of the words in blocks. */
	public float near = 7.5f;
	public float far = 9.5f;
	/** Words follow you when you walk. */
	public boolean follow = false;
	/** Keep words out of blocks: they are moved in front of walls/terrain and never placed inside the ground. */
	public boolean blockClipping = true;
	public boolean throughWalls = true;

	/** Floating mode: words keep drifting through the world instead of staying where they appeared (not for stick-on-blocks). */
	public boolean floating = false;
	/** How fast floating words travel across your view, in degrees per second. */
	public int floatSpeed = 12;

	/** True if any of the in-world modes is on (otherwise the plain HUD line is shown). */
	public boolean anyWorld() {
		return hologram || skyMode || stickMode;
	}

	// ------------------------------------------------------------------ sky mode

	/** Huge words far away in the sky that flash up and drift slowly like clouds. */
	public boolean skyMode = true;
	/** Distance of the sky words in blocks. */
	public float skyDistance = 106.6204f;
	/** Preferred height above the horizon in degrees. */
	public float skyElevation = 30f;
	/** Height of the letters in degrees of your view. */
	public float skyTextDeg = 6.3174f;

	// ------------------------------------------------------------------ stick on blocks

	/** Words stick flat onto the blocks you look at. */
	public boolean stickMode = false;
	/** How far away blocks may be to get a word, in blocks. */
	public float stickRange = 14f;
	/** Size of the stuck words in percent. */
	public int stickSize = 100;

	// ------------------------------------------------------------------ effects

	/** Text size in percent. */
	public int size = 100;
	public int opacity = 100;
	public int glow = 0;
	public int outline = 60;
	public int blur = 50;
	public int bounce = 100;
	public boolean flicker = false;
	/** 3D text: the words get a body, drawn as stacked layers behind the front face. */
	public boolean depth3d = false;
	/** Thickness of the 3D body in font pixels. */
	public float depthSize = 3f;

	// ------------------------------------------------------------------ text

	/** Font as identifier, e.g. minecraft:default, minecraft:uniform, minecraft:alt or a custom one from a resource pack. */
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
	/** How long a sung word stays before it fades out, in ms (0 = automatic, depends on the song speed). */
	public int wordHoldMs = 0;
	/** Viral-caption style animation of each sung word (see RcConfig.TextAnim). */
	public String textAnim = "TikTok pop";
	public int animSpeed = 237;

	// ------------------------------------------------------------------ emojis

	/** Symbols after keywords (love -> love ♥). The word is kept. */
	public boolean emojis = true;
	/** Give every emoji its own color instead of the text color. */
	public boolean emojiColors = true;
	/** Use real Minecraft icons (items, blocks, hearts, ...) for words that have one; other words still use the symbols. */
	public boolean emojiSprites = true;
	/** Word (lowercase) -> symbol. Can be freely edited in slyrics.json. */
	public Map<String, String> emojiMap = Emojis.defaults();
	/** Word (lowercase) -> sprite: "item:diamond", "block:poppy" or "gui:hud/heart/full". Can be edited in slyrics.json. */
	public Map<String, String> emojiSpriteMap = Emojis.spriteDefaults();

	// ------------------------------------------------------------------ karaoke sweep

	/** The word being sung is colored from left to right, and the color goes off when the next word starts. */
	public boolean sweepMode = true;
	public String sweepColor = "FF4D4D";
	/** Color the whole word at once instead of sweeping across it. */
	public boolean sweepWhole = false;
	/** How long the color takes to fade out once the word is over (0 = instantly). */
	public int sweepFadeMs = 76;
	public boolean sweepGlow = false;

	// ------------------------------------------------------------------ AFK camera

	/** After afkSeconds without input the camera leaves first person and plays cinematic shots around the player. */
	public boolean afkEnabled = true;
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

	// ------------------------------------------------------------------ HUD mode (karaoke panel)

	/** A small panel with the previous, current and next line; the current line fills in while it is sung. */
	public boolean hudMode = false;
	/** Top-left corner of the panel in percent of screen width/height. */
	public float hudX = 1.5f;
	public float hudY = 2f;
	/** Panel text size in percent. */
	public int hudScale = 100;

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
		try {
			Path source = Files.exists(FILE) ? FILE : LEGACY_FILE;
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
