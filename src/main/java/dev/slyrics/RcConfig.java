package dev.slyrics;

import com.teamresourceful.resourcefulconfig.api.annotations.*;

/**
 * The settings screen (Resourceful Config): one nested class per category, one static field per setting.
 * ConfigSync mirrors the values into Config by field name, so a field here needs a field of the same name there.
 */
@com.teamresourceful.resourcefulconfig.api.annotations.Config(value = "slyrics", version = 1, categories = {RcConfig.Connectivity.class, RcConfig.TextSettings.class, RcConfig.SizeCat.class, RcConfig.Modes.class, RcConfig.MediaCat.class, RcConfig.ProfilesTab.class, RcConfig.Cat10.class})
@ConfigInfo(title = "SLyrics", description = "Spotify lyrics as floating words in the world")
public final class RcConfig {
	private static final Config D = new Config();

	private static int hex(String s) {
		try { return Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF; } catch (NumberFormatException e) { return 0xFFFFFF; }
	}

	public enum TextAnim {
		NONE("None"), TIKTOK_POP("TikTok pop"), SLAM("Slam"), SHAKE("Shake"), WOBBLE("Wobble"), WAVE("Wave"), SPIN_IN("Spin in"), SLIDE_UP("Slide up");
		public final String label;
		TextAnim(String label) { this.label = label; }
		@Override public String toString() { return label; }
		public static TextAnim of(String s) { for (TextAnim a : values()) if (a.label.equals(s)) return a; return NONE; }
	}

	public enum MusicSource {
		AUTO("Auto"), SPOTIFY("Spotify"), OTHERS("Others");
		public final String label;
		MusicSource(String label) { this.label = label; }
		@Override public String toString() { return label; }
		public static MusicSource of(String s) { for (MusicSource m : values()) if (m.label.equals(s) || (m == OTHERS && "Apple Music".equals(s))) return m; return AUTO; }
	}

	@Category(value = "Connectivity", categories = {General.class, SpotifyTab.class, AppleMusicTab.class})
	public static final class Connectivity {
	}

	@Category(value = "General")
	public static final class General {
		@ConfigEntry(id = "musicSource", translation = "slyrics.config.musicSource")
		@Comment("Where the current song comes from. Auto: Spotify when it plays, otherwise any other player on this computer. Spotify: logs in with your Spotify account. Others: reads the song from any media player on this computer, e.g. Apple Music or YouTube Music in the browser, Cider, VLC (Linux only for now).")
		public static MusicSource musicSource = MusicSource.of(D.musicSource);

		@ConfigEntry(id = "enabled", translation = "slyrics.config.enabled")
		@Comment("Turn the mod on or off")
		public static boolean enabled = D.enabled;

		@ConfigEntry(id = "offsetMs", translation = "slyrics.config.offsetMs")
		@Comment("Negative values show lyrics (and the word highlighting) earlier, positive values later")
		@ConfigOption.Range(min = -3000, max = 3000)
		@ConfigOption.Slider
		public static int offsetMs = D.offsetMs;

	}

	@Category(value = "Spotify")
	public static final class SpotifyTab {
		@ConfigButton(title = "Connect to Spotify", text = "Connect")
		@Comment("Saves and starts the login. The link appears in chat.")
		public static final Runnable connect = ConfigSync::connectSpotify;

		@ConfigEntry(id = "clientId", translation = "slyrics.config.clientId")
		@Comment("Client ID of your app from developer.spotify.com. Redirect URI: http://127.0.0.1:8888/callback")
		public static String clientId = D.clientId;

	}

	@Category(value = "Text Settings", categories = {TextLook.class, Effects.class, Animations.class, Sweep.class})
	public static final class TextSettings {
	}

	@Category(value = "Text")
	public static final class TextLook {
		@ConfigButton(title = "Choose font", text = "Choose")
		@Comment("Opens a searchable list of Minecraft's fonts and every TrueType font installed on this computer.")
		public static final Runnable pickFont = dev.slyrics.font.FontPickerScreen::open;

		@ConfigEntry(id = "font", translation = "slyrics.config.font")
		@Comment("The current font. Use the Choose button above, or type a font id such as minecraft:uniform or one from a resource pack.")
		public static String font = D.font;

		@ConfigEntry(id = "color", translation = "slyrics.config.color")
		@Comment("Text color")
		@ConfigOption.Color
		public static int color = hex(D.color);

		@ConfigEntry(id = "opacity", translation = "slyrics.config.opacity")
		@ConfigOption.Range(min = 10, max = 100)
		@ConfigOption.Slider
		public static int opacity = D.opacity;

		@ConfigEntry(id = "uppercase", translation = "slyrics.config.uppercase")
		public static boolean uppercase = D.uppercase;

		@ConfigEntry(id = "bold", translation = "slyrics.config.bold")
		public static boolean bold = D.bold;

		@ConfigEntry(id = "shadow", translation = "slyrics.config.shadow")
		public static boolean shadow = D.shadow;

		@ConfigEntry(id = "background", translation = "slyrics.config.background")
		public static boolean background = D.background;

		@ConfigEntry(id = "typewriter", translation = "slyrics.config.typewriter")
		@Comment("Text is typed out letter by letter")
		public static boolean typewriter = D.typewriter;

	}

	@Category(value = "Effects")
	public static final class Effects {
		@ConfigEntry(id = "glow", translation = "slyrics.config.glow")
		@Comment("Soft halo around the words")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int glow = D.glow;

		@ConfigEntry(id = "glowColor", translation = "slyrics.config.glowColor")
		@Comment("Color of the soft halo around the words")
		@ConfigOption.Color
		public static int glowColor = hex(D.glowColor);

		@ConfigEntry(id = "outline", translation = "slyrics.config.outline")
		@Comment("Dark outline so words stay readable on bright backgrounds")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int outline = D.outline;

		@ConfigEntry(id = "depth3d", translation = "slyrics.config.depth3d")
		@Comment("Gives the words a body: stacked layers behind the front face, visible from the side")
		public static boolean depth3d = D.depth3d;

	}

	@Category(value = "Animations")
	public static final class Animations {
		@ConfigEntry(id = "textAnim", translation = "slyrics.config.textAnim")
		@Comment("Viral caption style for every sung word. TikTok pop: bouncy pop-in. Slam: drops in big and hits. Shake: jitters while sung. Wobble: tilts back and forth. Wave: words bob up and down. Spin in: spins into place. Slide up: slides up into place.")
		public static TextAnim textAnim = TextAnim.of(D.textAnim);

		@ConfigEntry(id = "animSpeed", translation = "slyrics.config.animSpeed")
		@Comment("How fast the word animation plays")
		@ConfigOption.Range(min = 50, max = 250)
		@ConfigOption.Slider
		public static int animSpeed = D.animSpeed;

		@ConfigEntry(id = "bounce", translation = "slyrics.config.bounce")
		@Comment("Words spring into place and float slightly")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int bounce = D.bounce;

		@ConfigEntry(id = "blur", translation = "slyrics.config.blur")
		@Comment("Smear while a word appears and fades")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int blur = D.blur;

		@ConfigEntry(id = "fadeMs", translation = "slyrics.config.fadeMs")
		@Comment("Fade in/out duration")
		@ConfigOption.Range(min = 0, max = 1000)
		@ConfigOption.Slider
		public static int fadeMs = D.fadeMs;

		@ConfigEntry(id = "wordHoldMs", translation = "slyrics.config.wordHoldMs")
		@Comment("How long a sung word stays before it fades out, counted from the moment it is sung. 0 = automatic (depends on the song speed). Works in all world modes and in the AFK camera.")
		@ConfigOption.Range(min = 0, max = 8000)
		@ConfigOption.Slider
		public static int wordHoldMs = D.wordHoldMs;

	}

	@Category(value = "Word sweep")
	public static final class Sweep {
		@ConfigEntry(id = "sweepMode", translation = "slyrics.config.sweepMode")
		@Comment("The word being sung is colored from left to right in time with the singing. When the next word starts, the color goes off again.")
		public static boolean sweepMode = D.sweepMode;

		@ConfigEntry(id = "sweepColor", translation = "slyrics.config.sweepColor")
		@Comment("Color of the sweep")
		@ConfigOption.Color
		public static int sweepColor = hex(D.sweepColor);

	}

	@Category(value = "Size")
	public static final class SizeCat {
		@ConfigEntry(id = "size", translation = "slyrics.config.size")
		@Comment("Size of the text in the world")
		@ConfigOption.Range(min = 50, max = 250)
		@ConfigOption.Slider
		public static int size = D.size;

		@ConfigEntry(id = "skyTextDeg", translation = "slyrics.config.skyTextDeg")
		@Comment("Height of the letters as seen from your view")
		@ConfigOption.Range(min = 3, max = 20)
		@ConfigOption.Slider
		public static float skyTextDeg = D.skyTextDeg;

		@ConfigEntry(id = "stickSize", translation = "slyrics.config.stickSize")
		@Comment("Size of the stuck words")
		@ConfigOption.Range(min = 30, max = 300)
		@ConfigOption.Slider
		public static int stickSize = D.stickSize;

	}

	@Category(value = "Modes", categories = {PickModes.class, AuraWords.class, SkyWords.class, StickWords.class, KaraokeCat.class, Cat8.class})
	public static final class Modes {
	}

	@Category(value = "Select modes")
	public static final class PickModes {
		@ConfigEntry(id = "throughWalls", translation = "slyrics.config.throughWalls")
		@Comment("On: lyrics can be seen through blocks and houses. Off: blocks hide them, so you only see words that are not behind terrain or buildings. Applies to all modes.")
		public static boolean throughWalls = D.throughWalls;

		@ConfigEntry(id = "floating", translation = "slyrics.config.floating")
		@Comment("Floating mode: words don't stay where they appear but keep drifting through the world, word by word, and fade out on the way. Works with Aura, Sky and the AFK camera (not with stick-on-blocks).")
		public static boolean floating = D.floating;

		@ConfigEntry(id = "floatSpeed", translation = "slyrics.config.floatSpeed")
		@Comment("How fast floating words travel across your view, in degrees per second")
		@ConfigOption.Range(min = 3, max = 60)
		@ConfigOption.Slider
		public static int floatSpeed = D.floatSpeed;

		@ConfigEntry(id = "hologram", translation = "slyrics.config.hologram")
		@Comment("Words float in front of you, scattered around the crosshair. If every world mode is off, the lyrics show as a plain HUD line")
		public static boolean hologram = D.hologram;

		@ConfigEntry(id = "skyMode", translation = "slyrics.config.skyMode")
		@Comment("Huge words far away in the sky that flash up and drift slowly like clouds")
		public static boolean skyMode = D.skyMode;

		@ConfigEntry(id = "stickMode", translation = "slyrics.config.stickMode")
		@Comment("Words stick flat onto the blocks you look at, like stickers on walls and floors")
		public static boolean stickMode = D.stickMode;

		@ConfigEntry(id = "afkEnabled", translation = "slyrics.config.afkEnabled")
		@Comment("AFK camera: after you have been idle for a while, the camera leaves first person and slowly orbits you like a cinematic shot, with the lyrics in view. Any input ends it. Settings are in Modes > AFK camera.")
		public static boolean afkEnabled = D.afkEnabled;

	}

	@Category(value = "Aura")
	public static final class AuraWords {
		@ConfigEntry(id = "near", translation = "slyrics.config.near")
		@Comment("Closest distance of the words in blocks")
		@ConfigOption.Range(min = 2, max = 12)
		@ConfigOption.Slider
		public static float near = D.near;

		@ConfigEntry(id = "far", translation = "slyrics.config.far")
		@Comment("Farthest distance of the words in blocks")
		@ConfigOption.Range(min = 2, max = 16)
		@ConfigOption.Slider
		public static float far = D.far;

		@ConfigEntry(id = "follow", translation = "slyrics.config.follow")
		@Comment("Words are carried along when you walk")
		public static boolean follow = D.follow;

		@ConfigEntry(id = "blockClipping", translation = "slyrics.config.blockClipping")
		@Comment("Keeps words out of blocks: they are moved in front of walls and terrain and never placed inside the ground")
		public static boolean blockClipping = D.blockClipping;

	}

	@Category(value = "Sky")
	public static final class SkyWords {
		@ConfigEntry(id = "skyDistance", translation = "slyrics.config.skyDistance")
		@Comment("How far away the sky words are, in blocks")
		@ConfigOption.Range(min = 30, max = 200)
		@ConfigOption.Slider
		public static float skyDistance = D.skyDistance;

		@ConfigEntry(id = "skyElevation", translation = "slyrics.config.skyElevation")
		@Comment("Preferred angle above the horizon")
		@ConfigOption.Range(min = 10, max = 80)
		@ConfigOption.Slider
		public static float skyElevation = D.skyElevation;

	}

	@Category(value = "Stick on blocks")
	public static final class StickWords {
		@ConfigEntry(id = "stickRange", translation = "slyrics.config.stickRange")
		@Comment("How far away blocks may be to get a word, in blocks")
		@ConfigOption.Range(min = 3, max = 40)
		@ConfigOption.Slider
		public static float stickRange = D.stickRange;

	}

	@Category("Karaoke mode")
	public static final class KaraokeCat {
		@ConfigEntry(id = "hudMode", translation = "slyrics.config.hudMode")
		@Comment("A small panel on the screen with the previous, the current and the next line. The current line fills in while it is sung, so you can sing along. Uses your font, colors and the word sweep color, and can be combined with the other modes.")
		public static boolean hudMode = D.hudMode;

		@ConfigButton(title = "Edit HUD layout", text = "Open")
		@Comment("Opens the HUD editor: drag the lyrics panel and the media player with the mouse, scroll over one to resize it. Also: /slyrics hud")
		public static final Runnable editHud = HudEditor::open;

	}

	@Category("AFK camera")
	public static final class Cat8 {
		@ConfigEntry(id = "afkTextNear", translation = "slyrics.config.afkTextNear")
		@Comment("In the AFK camera, the closest the lyric words stand to you, in blocks")
		@ConfigOption.Range(min = 1, max = 12)
		@ConfigOption.Slider
		public static float afkTextNear = D.afkTextNear;

		@ConfigEntry(id = "afkTextFar", translation = "slyrics.config.afkTextFar")
		@Comment("In the AFK camera, the farthest the lyric words stand from you, in blocks")
		@ConfigOption.Range(min = 1, max = 16)
		@ConfigOption.Slider
		public static float afkTextFar = D.afkTextFar;

		@ConfigEntry(id = "afkFollowWord", translation = "slyrics.config.afkFollowWord")
		@Comment("The camera swings toward the word that is being sung, so you always look at the current word. Smooth, not a cut.")
		public static boolean afkFollowWord = D.afkFollowWord;

		@ConfigEntry(id = "afkFollowStrength", translation = "slyrics.config.afkFollowStrength")
		@Comment("How far the camera pans toward the word: 100 puts the word in the middle, lower keeps more of the player in view.")
		@ConfigOption.Range(min = 10, max = 100)
		@ConfigOption.Slider
		public static int afkFollowStrength = D.afkFollowStrength;

		@ConfigEntry(id = "afkSeconds", translation = "slyrics.config.afkSeconds")
		@Comment("Idle time before the AFK camera starts")
		@ConfigOption.Range(min = 3, max = 600)
		@ConfigOption.Slider
		public static int afkSeconds = D.afkSeconds;

		@ConfigEntry(id = "afkOnlyWhilePlaying", translation = "slyrics.config.afkOnlyWhilePlaying")
		@Comment("Start only when music is playing (Spotify or another player)")
		public static boolean afkOnlyWhilePlaying = D.afkOnlyWhilePlaying;

		@ConfigEntry(id = "afkCutEveryLines", translation = "slyrics.config.afkCutEveryLines")
		@Comment("Hard cut to a new shot every this many lyric lines. Cuts only happen when a new line starts.")
		@ConfigOption.Range(min = 1, max = 12)
		@ConfigOption.Slider
		public static int afkCutEveryLines = D.afkCutEveryLines;

		@ConfigEntry(id = "afkShotSeconds", translation = "slyrics.config.afkShotSeconds")
		@Comment("Used only when the timing of the next cut is unknown")
		@ConfigOption.Range(min = 3, max = 60)
		@ConfigOption.Slider
		public static int afkShotSeconds = D.afkShotSeconds;

		@ConfigEntry(id = "afkMaxDistance", translation = "slyrics.config.afkMaxDistance")
		@Comment("The camera never gets farther from you than this, in blocks")
		@ConfigOption.Range(min = 2, max = 30)
		@ConfigOption.Slider
		public static float afkMaxDistance = D.afkMaxDistance;

		@ConfigEntry(id = "afkCloseUpPercent", translation = "slyrics.config.afkCloseUpPercent")
		@Comment("How often the next shot is a close-up")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int afkCloseUpPercent = D.afkCloseUpPercent;

		@ConfigEntry(id = "afkRandomOrder", translation = "slyrics.config.afkRandomOrder")
		@Comment("Pick the next shot at random (otherwise in the listed order)")
		public static boolean afkRandomOrder = D.afkRandomOrder;

		@ConfigEntry(id = "afkFadeBetween", translation = "slyrics.config.afkFadeBetween")
		@Comment("Dip through black at each cut instead of an instant cut")
		public static boolean afkFadeBetween = D.afkFadeBetween;

		@ConfigEntry(id = "afkRadius", translation = "slyrics.config.afkRadius")
		@Comment("Distance of the camera from you, in blocks")
		@ConfigOption.Range(min = 3, max = 20)
		@ConfigOption.Slider
		public static float afkRadius = D.afkRadius;

		@ConfigEntry(id = "afkHeight", translation = "slyrics.config.afkHeight")
		@Comment("Height above your eyes, in blocks")
		@ConfigOption.Range(min = 0, max = 8)
		@ConfigOption.Slider
		public static float afkHeight = D.afkHeight;

		@ConfigEntry(id = "afkSpeed", translation = "slyrics.config.afkSpeed")
		@Comment("How fast the camera circles you")
		@ConfigOption.Range(min = 2, max = 60)
		@ConfigOption.Slider
		public static float afkSpeed = D.afkSpeed;

	}

	@Category("Media player")
	public static final class MediaCat {
		@ConfigEntry(id = "mediaPlayer", translation = "slyrics.config.mediaPlayer")
		@Comment("A small player window on the screen with cover, title, progress and previous / play-pause / next buttons. Open chat or the pause menu to get the mouse cursor and click the buttons, or bind keys in Options > Controls > SLyrics. Works with Spotify (needs Premium and a new /slyrics login) and with the Others source.")
		public static boolean mediaPlayer = D.mediaPlayer;

		@ConfigButton(title = "Edit HUD layout", text = "Open")
		@Comment("Opens the HUD editor: drag the media player and the lyrics panel with the mouse, scroll over one to make it bigger or smaller. Also: /slyrics hud")
		public static final Runnable editHud = HudEditor::open;

		@ConfigEntry(id = "mediaOpacity", translation = "slyrics.config.mediaOpacity")
		@Comment("How opaque the window is")
		@ConfigOption.Range(min = 20, max = 100)
		@ConfigOption.Slider
		public static int mediaOpacity = D.mediaOpacity;

		@ConfigEntry(id = "mediaCover", translation = "slyrics.config.mediaCover")
		@Comment("Show the album cover (if the player provides one)")
		public static boolean mediaCover = D.mediaCover;
	}

	@Category("Profiles")
	public static final class ProfilesTab {
		@ConfigButton(title = "Manage profiles", text = "Open")
		@Comment("Save all your current settings under a name and load them again later. Profiles are files in config/slyrics-profiles. Your Spotify client ID is not part of them. Also: /slyrics profile save|load|delete|list <name>")
		public static final Runnable manage = dev.slyrics.profiles.ProfilesScreen::open;

	}

	@Category("HUD")
	public static final class Cat10 {
		@ConfigEntry(id = "scale", translation = "slyrics.config.scale")
		@Comment("Only used when holograms are off")
		@ConfigOption.Range(min = 0.5, max = 5)
		@ConfigOption.Slider
		public static float scale = D.scale;

		@ConfigEntry(id = "xPercent", translation = "slyrics.config.xPercent")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static float xPercent = D.xPercent;

		@ConfigEntry(id = "yPercent", translation = "slyrics.config.yPercent")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static float yPercent = D.yPercent;

		@ConfigEntry(id = "showNeighbors", translation = "slyrics.config.showNeighbors")
		@Comment("Show the previous/next line, dimmed")
		public static boolean showNeighbors = D.showNeighbors;

		@ConfigEntry(id = "showStatus", translation = "slyrics.config.showStatus")
		@Comment("Status message when nothing is playing or no lyrics were found")
		public static boolean showStatus = D.showStatus;

	}

	@Category("Others")
	public static final class AppleMusicTab {
		@ConfigEntry(id = "applePlayer", translation = "slyrics.config.applePlayer")
		@Comment("Only use a media player whose name contains this text, e.g. cider or firefox. Empty = any player except Spotify. Used when the source is Others.")
		public static String applePlayer = D.applePlayer;

	}

	private RcConfig() {}
}
