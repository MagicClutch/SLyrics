package dev.slyrics;

import dev.slyrics.LayoutAnnotations.*;

/**
 * The layout of the OneConfig page: one nested class per tab or section, one static field per setting, in the order shown.
 * The values live in {@link Config}; an entry's id is the name of its Config field. The field types pick the widget (an enum
 * is a dropdown, an int with {@code @Color} a color picker); their initial values are not used.
 */
@Page(categories = {ConfigLayout.Connectivity.class, ConfigLayout.TextSettings.class, ConfigLayout.Modes.class, ConfigLayout.Hud.class})
public final class ConfigLayout {
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

	@Category(value = "Connectivity", categories = {General.class, SpotifyTab.class, OtherPlayersTab.class})
	public static final class Connectivity {
	}

	@Category(value = "General")
	public static final class General {
		@ConfigEntry(id = "musicSource")
		@Comment("Where the current song comes from. Auto: Spotify when it plays, otherwise any other player on this computer. Spotify: logs in with your Spotify account. Others: reads the song from any media player on this computer, e.g. Apple Music or YouTube Music in the browser, Cider, VLC (Linux and Windows).")
		public static MusicSource musicSource = MusicSource.of(D.musicSource);

		@ConfigEntry(id = "enabled")
		@Comment("Turn the mod on or off")
		public static boolean enabled = D.enabled;

		@ConfigEntry(id = "offsetMs")
		@Comment("Negative values show lyrics (and the word highlighting) earlier, positive values later")
		@Range(min = -3000, max = 3000)
		@Slider
		public static int offsetMs = D.offsetMs;

		@ConfigEntry(id = "devMode")
		@Comment("Developer mode: shows all the detailed settings (effects, distances, AFK camera, ...) in the OneConfig page. Off: only the easy, everyday settings.")
		public static boolean devMode = D.devMode;
	}

	@Category(value = "Spotify")
	public static final class SpotifyTab {
		@ConfigButton(title = "Connect to Spotify", text = "Connect")
		@Comment("Saves and starts the login. The link appears in chat.")
		public static final Runnable connect = Config::connectSpotify;

		@ConfigEntry(id = "clientId")
		@Comment("Client ID of your app from developer.spotify.com. Redirect URI: http://127.0.0.1:8888/callback")
		public static String clientId = D.clientId;
	}

	@Category(value = "Text Settings", categories = {TextLook.class, Effects.class, Animations.class, Sweep.class})
	public static final class TextSettings {
	}

	@Category(value = "Text")
	public static final class TextLook {
		@ConfigEntry(id = "size")
		@Comment("Size of the lyric words in the world (Aura, Stick on blocks, AFK camera; Sky has its own). Words that are placed farther ahead while you run or fly grow with the distance, so they look just as big.")
		@Range(min = 20, max = 400)
		@Slider
		public static int size = D.size;

		@ConfigEntry(id = "color")
		@Comment("Text color")
		@Color
		public static int color = hex(D.color);

		@ConfigEntry(id = "opacity")
		@Range(min = 10, max = 100)
		@Slider
		public static int opacity = D.opacity;

		@ConfigEntry(id = "uppercase")
		public static boolean uppercase = D.uppercase;

		@ConfigEntry(id = "bold")
		public static boolean bold = D.bold;

		@ConfigEntry(id = "shadow")
		public static boolean shadow = D.shadow;

		@ConfigEntry(id = "background")
		public static boolean background = D.background;

		@ConfigEntry(id = "typewriter")
		@Comment("Text is typed out letter by letter")
		public static boolean typewriter = D.typewriter;
	}

	@Category(value = "Effects")
	public static final class Effects {
		@ConfigEntry(id = "glow")
		@Comment("Soft halo around the words. 0 = off.")
		@Range(min = 0, max = 100)
		@Slider
		public static int glow = D.glow;

		@ConfigEntry(id = "glowColor")
		@Comment("Color of the soft halo around the words")
		@Color
		public static int glowColor = hex(D.glowColor);

		@ConfigEntry(id = "outline")
		@Comment("Dark outline so words stay readable on bright backgrounds")
		@Range(min = 0, max = 100)
		@Slider
		public static int outline = D.outline;

		@ConfigEntry(id = "depth3d")
		@Comment("Gives the words a body: stacked layers behind the front face, visible from the side")
		public static boolean depth3d = D.depth3d;
	}

	@Category(value = "Animations")
	public static final class Animations {
		@ConfigEntry(id = "textAnim")
		@Comment("Viral caption style for every sung word. TikTok pop: bouncy pop-in. Slam: drops in big and hits. Shake: jitters while sung. Wobble: tilts back and forth. Wave: words bob up and down. Spin in: spins into place. Slide up: slides up into place.")
		public static TextAnim textAnim = TextAnim.of(D.textAnim);

		@ConfigEntry(id = "animSpeed")
		@Comment("How fast the word animation plays")
		@Range(min = 50, max = 250)
		@Slider
		public static int animSpeed = D.animSpeed;

		@ConfigEntry(id = "bounce")
		@Comment("Words spring into place and float slightly")
		@Range(min = 0, max = 100)
		@Slider
		public static int bounce = D.bounce;

		@ConfigEntry(id = "blur")
		@Comment("Smear while a word appears and fades")
		@Range(min = 0, max = 100)
		@Slider
		public static int blur = D.blur;

		@ConfigEntry(id = "fadeMs")
		@Comment("Fade in/out duration")
		@Range(min = 0, max = 1000)
		@Slider
		public static int fadeMs = D.fadeMs;

		@ConfigEntry(id = "wordHoldMs")
		@Comment("How long a word stays after it has been sung before it fades out (a word that is sung longer stays that much longer). 0 = 2 seconds. Works in all world modes and in the AFK camera.")
		@Range(min = 0, max = 8000)
		@Slider
		public static int wordHoldMs = D.wordHoldMs;
	}

	@Category(value = "Word sweep")
	public static final class Sweep {
		@ConfigEntry(id = "sweepMode")
		@Comment("The word being sung is colored from left to right in time with the singing. When the next word starts, the color goes off again.")
		public static boolean sweepMode = D.sweepMode;

		@ConfigEntry(id = "sweepColor")
		@Comment("Color of the sweep")
		@Color
		public static int sweepColor = hex(D.sweepColor);
	}

	@Category(value = "Features", categories = {PickModes.class, AuraWords.class, SkyWords.class, StickWords.class, CommonCat.class, AfkCat.class})
	public static final class Modes {
	}

	/**
	 * Everything drawn flat on the screen (the lyric line, the karaoke panel and the media player) lives in OneConfig's HUD
	 * editor: add an element there to show it, and click it for its settings. This tab only opens the editor.
	 */
	@Category(value = "HUD", categories = {HudEditorCat.class})
	public static final class Hud {
	}

	@Category(value = "Select features")
	public static final class PickModes {
		@ConfigEntry(id = "hologram")
		@Comment("Words float in front of you, scattered around the crosshair.")
		public static boolean hologram = D.hologram;

		@ConfigEntry(id = "skyMode")
		@Comment("Huge words far away in the sky that flash up and drift slowly like clouds")
		public static boolean skyMode = D.skyMode;

		@ConfigEntry(id = "stickMode")
		@Comment("Words stick flat onto the blocks you look at, like stickers on walls and floors")
		public static boolean stickMode = D.stickMode;

		@ConfigEntry(id = "afkEnabled")
		@Comment("AFK camera: after you have been idle for a while, the camera leaves first person and slowly orbits you like a cinematic shot, with the lyrics in view. Any input ends it. Settings are in Features > AFK camera.")
		public static boolean afkEnabled = D.afkEnabled;

		@ConfigEntry(id = "floating")
		@Comment("Floating mode: words don't stay where they appear but keep drifting through the world, word by word, and fade out on the way. Works with Aura, Sky and the AFK camera (not with stick-on-blocks).")
		public static boolean floating = D.floating;
	}

	@Category("Aura")
	public static final class AuraWords {
		@ConfigEntry(id = "near")
		@Comment("Closest distance of the words in blocks")
		@Range(min = 2, max = 40)
		@Slider
		public static float near = D.near;

		@ConfigEntry(id = "far")
		@Comment("Farthest distance of the words in blocks")
		@Range(min = 2, max = 48)
		@Slider
		public static float far = D.far;

		@ConfigEntry(id = "auraSpread")
		@Comment("How far over the screen the words spread. Low: close around the crosshair. High: up to about 3/4 of the screen, still centred.")
		@Range(min = 0, max = 100)
		@Slider
		public static int auraSpread = D.auraSpread;

		@ConfigEntry(id = "follow")
		@Comment("Words are carried along when you walk")
		public static boolean follow = D.follow;

		@ConfigEntry(id = "predictSeconds")
		@Comment("Predict movement: how far ahead it looks, in seconds. More = the words are placed farther along your path.")
		@Range(min = 0.3, max = 5)
		@Slider
		public static float predictSeconds = D.predictSeconds;
	}

	@Category("Sky")
	public static final class SkyWords {
		@ConfigEntry(id = "skyTextDeg")
		@Comment("Size of the sky words: height of the letters as seen from your view")
		@Range(min = 2, max = 30)
		@Slider
		public static float skyTextDeg = D.skyTextDeg;

		@ConfigEntry(id = "skyDistance")
		@Comment("How far away the sky words are, in blocks")
		@Range(min = 30, max = 200)
		@Slider
		public static float skyDistance = D.skyDistance;

		@ConfigEntry(id = "skySpread")
		@Comment("How far over the screen the sky words spread. Low: close around the middle. High: up to about 3/4 of the screen, still centred.")
		@Range(min = 0, max = 100)
		@Slider
		public static int skySpread = D.skySpread;
	}

	@Category("Stick on blocks")
	public static final class StickWords {
		@ConfigEntry(id = "stickRange")
		@Comment("How far away blocks may be to get a word, in blocks")
		@Range(min = 3, max = 40)
		@Slider
		public static float stickRange = D.stickRange;
	}

	@Category("Floating & walls")
	public static final class CommonCat {
		@ConfigEntry(id = "floatSpeed")
		@Comment("How fast floating words travel across your view, in degrees per second")
		@Range(min = 3, max = 60)
		@Slider
		public static int floatSpeed = D.floatSpeed;
	}

	@Category("AFK camera")
	public static final class AfkCat {
		@ConfigEntry(id = "afkFollowWord")
		@Comment("The camera swings toward the word that is being sung, so you always look at the current word. Smooth, not a cut.")
		public static boolean afkFollowWord = D.afkFollowWord;

		@ConfigEntry(id = "afkSeconds")
		@Comment("Idle time before the AFK camera starts")
		@Range(min = 3, max = 600)
		@Slider
		public static int afkSeconds = D.afkSeconds;

		@ConfigEntry(id = "afkOnlyWhilePlaying")
		@Comment("Start only when music is playing (Spotify or another player)")
		public static boolean afkOnlyWhilePlaying = D.afkOnlyWhilePlaying;

		@ConfigEntry(id = "afkCutEveryLines")
		@Comment("Hard cut to a new shot every this many lyric lines. Cuts only happen when a new line starts.")
		@Range(min = 1, max = 12)
		@Slider
		public static int afkCutEveryLines = D.afkCutEveryLines;

		@ConfigEntry(id = "afkFadeBetween")
		@Comment("Dip through black at each cut instead of an instant cut")
		public static boolean afkFadeBetween = D.afkFadeBetween;

		@ConfigEntry(id = "afkSpeed")
		@Comment("How fast the camera circles you")
		@Range(min = 2, max = 60)
		@Slider
		public static float afkSpeed = D.afkSpeed;
	}

	@Category("HUD editor")
	public static final class HudEditorCat {
		@ConfigButton(title = "Open HUD editor", text = "Open")
		@Comment("Add the lyric line, the karaoke panel or the media player in OneConfig's HUD editor to show it; drag and scale it there and click it for its settings. Also: /slyrics hud")
		public static final Runnable editHud = dev.slyrics.compat.oneconfig.OneConfigBridge::openHudEditor;
	}

	@Category("Others")
	public static final class OtherPlayersTab {
		@ConfigEntry(id = "otherPlayer")
		@Comment("Only use a media player whose name contains this text, e.g. cider or firefox. Empty = any player except Spotify; type spotify to read the Spotify app on this computer directly (no Spotify login or rate limits). Used when the source is Others.")
		public static String otherPlayer = D.otherPlayer;
	}

	private ConfigLayout() {}
}
