package dev.slyrics;

import com.teamresourceful.resourcefulconfig.api.annotations.*;

/**
 * The settings screen (Resourceful Config): one nested class per category, one static field per setting.
 * ConfigSync mirrors the values into Config by field name, so a field here needs a field of the same name there.
 */
@com.teamresourceful.resourcefulconfig.api.annotations.Config(value = "slyrics", version = 1, categories = {RcConfig.Cat1.class, RcConfig.Cat2.class, RcConfig.Cat3.class, RcConfig.Cat4.class, RcConfig.Cat5.class, RcConfig.Cat6.class, RcConfig.Cat7.class, RcConfig.Cat8.class, RcConfig.Cat9.class, RcConfig.Cat10.class})
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

	@Category("Spotify")
	public static final class Cat1 {
		@ConfigButton(title = "Connect to Spotify", text = "Connect")
		@Comment("Saves and starts the login. The link appears in chat.")
		public static final Runnable connect = ConfigSync::connectSpotify;

		@ConfigEntry(id = "configScaleDown", translation = "slyrics.config.configScaleDown")
		@Comment("Draws this settings screen smaller: how many GUI scale steps below your normal GUI scale (0 = normal size). Takes effect the next time you open it.")
		@ConfigOption.Range(min = 0, max = 3)
		@ConfigOption.Slider
		public static int configScaleDown = D.configScaleDown;

		@ConfigEntry(id = "clientId", translation = "slyrics.config.clientId")
		@Comment("Client ID of your app from developer.spotify.com. Redirect URI: http://127.0.0.1:8888/callback")
		public static String clientId = D.clientId;

		@ConfigEntry(id = "enabled", translation = "slyrics.config.enabled")
		@Comment("Turn the mod on or off")
		public static boolean enabled = D.enabled;

		@ConfigEntry(id = "offsetMs", translation = "slyrics.config.offsetMs")
		@Comment("Show lyrics earlier (negative) or later (positive)")
		@ConfigOption.Range(min = -3000, max = 3000)
		@ConfigOption.Slider
		public static int offsetMs = D.offsetMs;

	}

	@Category("World words")
	public static final class Cat2 {
		@ConfigEntry(id = "hologram", translation = "slyrics.config.hologram")
		@Comment("Words float in front of you, scattered around the crosshair. If every world mode is off, the lyrics show as a plain HUD line")
		public static boolean hologram = D.hologram;

		@ConfigEntry(id = "lineMode", translation = "slyrics.config.lineMode")
		@Comment("Only when World words is the only active mode. Off: each word appears when it is sung. On: the whole line appears and the sung word lights up")
		public static boolean lineMode = D.lineMode;

		@ConfigEntry(id = "centerLayout", translation = "slyrics.config.centerLayout")
		@Comment("Off: words are scattered around the crosshair. On: words in a row straight ahead")
		public static boolean centerLayout = D.centerLayout;

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

		@ConfigEntry(id = "throughWalls", translation = "slyrics.config.throughWalls")
		@Comment("Text readable even behind blocks")
		public static boolean throughWalls = D.throughWalls;

	}

	@Category("Sky")
	public static final class Cat3 {
		@ConfigEntry(id = "skyMode", translation = "slyrics.config.skyMode")
		@Comment("Huge words far away in the sky that flash up and drift slowly like clouds")
		public static boolean skyMode = D.skyMode;

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

		@ConfigEntry(id = "skyTextDeg", translation = "slyrics.config.skyTextDeg")
		@Comment("Height of the letters as seen from your view")
		@ConfigOption.Range(min = 3, max = 20)
		@ConfigOption.Slider
		public static float skyTextDeg = D.skyTextDeg;

	}

	@Category("Stick on blocks")
	public static final class Cat4 {
		@ConfigEntry(id = "stickMode", translation = "slyrics.config.stickMode")
		@Comment("Words stick flat onto the blocks you look at, like stickers on walls and floors")
		public static boolean stickMode = D.stickMode;

		@ConfigEntry(id = "stickRange", translation = "slyrics.config.stickRange")
		@Comment("How far away blocks may be to get a word, in blocks")
		@ConfigOption.Range(min = 3, max = 40)
		@ConfigOption.Slider
		public static float stickRange = D.stickRange;

		@ConfigEntry(id = "stickSize", translation = "slyrics.config.stickSize")
		@Comment("Size of the stuck words")
		@ConfigOption.Range(min = 30, max = 300)
		@ConfigOption.Slider
		public static int stickSize = D.stickSize;

	}

	@Category("Effects")
	public static final class Cat5 {
		@ConfigEntry(id = "size", translation = "slyrics.config.size")
		@Comment("Size of the text in the world")
		@ConfigOption.Range(min = 50, max = 250)
		@ConfigOption.Slider
		public static int size = D.size;

		@ConfigEntry(id = "opacity", translation = "slyrics.config.opacity")
		@ConfigOption.Range(min = 10, max = 100)
		@ConfigOption.Slider
		public static int opacity = D.opacity;

		@ConfigEntry(id = "glow", translation = "slyrics.config.glow")
		@Comment("Soft halo around the words")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int glow = D.glow;

		@ConfigEntry(id = "outline", translation = "slyrics.config.outline")
		@Comment("Dark outline so words stay readable on bright backgrounds")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int outline = D.outline;

		@ConfigEntry(id = "blur", translation = "slyrics.config.blur")
		@Comment("Smear while a word appears and fades")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int blur = D.blur;

		@ConfigEntry(id = "bounce", translation = "slyrics.config.bounce")
		@Comment("Words spring into place and float slightly")
		@ConfigOption.Range(min = 0, max = 100)
		@ConfigOption.Slider
		public static int bounce = D.bounce;

		@ConfigEntry(id = "flicker", translation = "slyrics.config.flicker")
		@Comment("Occasional glitch effect")
		public static boolean flicker = D.flicker;

		@ConfigEntry(id = "depth3d", translation = "slyrics.config.depth3d")
		@Comment("Gives the words a body: stacked layers behind the front face, visible from the side")
		public static boolean depth3d = D.depth3d;

		@ConfigEntry(id = "depthSize", translation = "slyrics.config.depthSize")
		@Comment("Thickness of the 3D body in font pixels")
		@ConfigOption.Range(min = 1, max = 8)
		@ConfigOption.Slider
		public static float depthSize = D.depthSize;

	}

	@Category("Text")
	public static final class Cat6 {
		@ConfigEntry(id = "font", translation = "slyrics.config.font")
		@Comment("e.g. minecraft:default, minecraft:uniform, minecraft:alt, minecraft:illageralt or a font from a resource pack")
		public static String font = D.font;

		@ConfigEntry(id = "color", translation = "slyrics.config.color")
		@Comment("Text color")
		@ConfigOption.Color
		public static int color = hex(D.color);

		@ConfigEntry(id = "glowColor", translation = "slyrics.config.glowColor")
		@Comment("Color of the soft halo around the words")
		@ConfigOption.Color
		public static int glowColor = hex(D.glowColor);

		@ConfigEntry(id = "textAnim", translation = "slyrics.config.textAnim")
		@Comment("Viral caption style for every sung word. TikTok pop: bouncy pop-in. Slam: drops in big and hits. Shake: jitters while sung. Wobble: tilts back and forth. Wave: words bob up and down. Spin in: spins into place. Slide up: slides up into place.")
		public static TextAnim textAnim = TextAnim.of(D.textAnim);

		@ConfigEntry(id = "emojis", translation = "slyrics.config.emojis")
		@Comment("Appends symbols after keywords (love ♥, fight ⚔, ...). The word stays. You can edit the words in config/slyrics.json under emojiMap.")
		public static boolean emojis = D.emojis;

		@ConfigEntry(id = "emojiSprites", translation = "slyrics.config.emojiSprites")
		@Comment("Use real Minecraft icons for matching words: heart for love, diamond for diamond, sword for fight, and so on. Other words keep their symbols. Edit the words in config/slyrics.json under emojiSpriteMap.")
		public static boolean emojiSprites = D.emojiSprites;

		@ConfigEntry(id = "emojiColors", translation = "slyrics.config.emojiColors")
		@Comment("Every emoji gets its own color (red heart, gold star, ...) instead of the text color")
		public static boolean emojiColors = D.emojiColors;

		@ConfigEntry(id = "uppercase", translation = "slyrics.config.uppercase")
		public static boolean uppercase = D.uppercase;

		@ConfigEntry(id = "bold", translation = "slyrics.config.bold")
		public static boolean bold = D.bold;

		@ConfigEntry(id = "shadow", translation = "slyrics.config.shadow")
		public static boolean shadow = D.shadow;

		@ConfigEntry(id = "typewriter", translation = "slyrics.config.typewriter")
		@Comment("Text is typed out letter by letter")
		public static boolean typewriter = D.typewriter;

		@ConfigEntry(id = "animSpeed", translation = "slyrics.config.animSpeed")
		@Comment("How fast the word animation plays")
		@ConfigOption.Range(min = 50, max = 250)
		@ConfigOption.Slider
		public static int animSpeed = D.animSpeed;

		@ConfigEntry(id = "background", translation = "slyrics.config.background")
		public static boolean background = D.background;

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

	@Category("Karaoke sweep")
	public static final class Cat7 {
		@ConfigEntry(id = "sweepColor", translation = "slyrics.config.sweepColor")
		@Comment("Color of the sweep")
		@ConfigOption.Color
		public static int sweepColor = hex(D.sweepColor);

		@ConfigEntry(id = "sweepMode", translation = "slyrics.config.sweepMode")
		@Comment("The word being sung is colored from left to right in time with the singing. When the next word starts, the color goes off again.")
		public static boolean sweepMode = D.sweepMode;

		@ConfigEntry(id = "sweepWhole", translation = "slyrics.config.sweepWhole")
		@Comment("Instead of sweeping across the word, color it completely the moment it is sung")
		public static boolean sweepWhole = D.sweepWhole;

		@ConfigEntry(id = "sweepFadeMs", translation = "slyrics.config.sweepFadeMs")
		@Comment("How long the color takes to go off after the word is over (0 = instantly)")
		@ConfigOption.Range(min = 0, max = 600)
		@ConfigOption.Slider
		public static int sweepFadeMs = D.sweepFadeMs;

		@ConfigEntry(id = "sweepGlow", translation = "slyrics.config.sweepGlow")
		@Comment("A soft halo in the sweep color around the colored part")
		public static boolean sweepGlow = D.sweepGlow;

	}

	@Category("AFK camera")
	public static final class Cat8 {
		@ConfigEntry(id = "afkFollowWord", translation = "slyrics.config.afkFollowWord")
		@Comment("The camera swings toward the word that is being sung, so you always look at the current word. Smooth, not a cut.")
		public static boolean afkFollowWord = D.afkFollowWord;

		@ConfigEntry(id = "afkFollowStrength", translation = "slyrics.config.afkFollowStrength")
		@Comment("How far the camera pans toward the word: 100 puts the word in the middle, lower keeps more of the player in view.")
		@ConfigOption.Range(min = 10, max = 100)
		@ConfigOption.Slider
		public static int afkFollowStrength = D.afkFollowStrength;

		@ConfigEntry(id = "afkEnabled", translation = "slyrics.config.afkEnabled")
		@Comment("After you have been idle for a while, the camera leaves first person and slowly orbits you like a cinematic shot, with the lyrics in view. Any input ends it.")
		public static boolean afkEnabled = D.afkEnabled;

		@ConfigEntry(id = "afkSeconds", translation = "slyrics.config.afkSeconds")
		@Comment("Idle time before the AFK camera starts")
		@ConfigOption.Range(min = 3, max = 600)
		@ConfigOption.Slider
		public static int afkSeconds = D.afkSeconds;

		@ConfigEntry(id = "afkOnlyWhilePlaying", translation = "slyrics.config.afkOnlyWhilePlaying")
		@Comment("Start only when Spotify is playing")
		public static boolean afkOnlyWhilePlaying = D.afkOnlyWhilePlaying;

		@ConfigEntry(id = "afkOrbit", translation = "slyrics.config.afkOrbit")
		@Comment("Circle around you")
		public static boolean afkOrbit = D.afkOrbit;

		@ConfigEntry(id = "afkRise", translation = "slyrics.config.afkRise")
		@Comment("Drone shot from low to high")
		public static boolean afkRise = D.afkRise;

		@ConfigEntry(id = "afkDescend", translation = "slyrics.config.afkDescend")
		@Comment("Drone shot from high to low")
		public static boolean afkDescend = D.afkDescend;

		@ConfigEntry(id = "afkDiagonal", translation = "slyrics.config.afkDiagonal")
		@Comment("Sweep diagonally across while rising")
		public static boolean afkDiagonal = D.afkDiagonal;

		@ConfigEntry(id = "afkFlyby", translation = "slyrics.config.afkFlyby")
		@Comment("Straight pass beside you")
		public static boolean afkFlyby = D.afkFlyby;

		@ConfigEntry(id = "afkPushIn", translation = "slyrics.config.afkPushIn")
		@Comment("Move toward you")
		public static boolean afkPushIn = D.afkPushIn;

		@ConfigEntry(id = "afkPullOut", translation = "slyrics.config.afkPullOut")
		@Comment("Move away from you")
		public static boolean afkPullOut = D.afkPullOut;

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

		@ConfigEntry(id = "afkCloseUp", translation = "slyrics.config.afkCloseUp")
		@Comment("Include close-up shots: face close-ups, tilts from feet to head and tight orbits around the head")
		public static boolean afkCloseUp = D.afkCloseUp;

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

	@Category("Experimental")
	public static final class Cat9 {
		@ConfigEntry(id = "beatShake", translation = "slyrics.config.beatShake")
		@Comment("Experimental. The AFK camera shakes in time with the lyrics: at line starts, long held words and emphasized words (CAPS or !). It follows the lyrics, not the real beat of the song.")
		public static boolean beatShake = D.beatShake;

		@ConfigEntry(id = "shakeStrength", translation = "slyrics.config.shakeStrength")
		@Comment("How strong the shakes are")
		@ConfigOption.Range(min = 10, max = 300)
		@ConfigOption.Slider
		public static int shakeStrength = D.shakeStrength;

		@ConfigEntry(id = "shakeOnLines", translation = "slyrics.config.shakeOnLines")
		@Comment("A light shake whenever a new lyric line begins")
		public static boolean shakeOnLines = D.shakeOnLines;

		@ConfigEntry(id = "shakeOnLongWords", translation = "slyrics.config.shakeOnLongWords")
		@Comment("A strong shake when a long held word starts")
		public static boolean shakeOnLongWords = D.shakeOnLongWords;

		@ConfigEntry(id = "shakeOnEmphasis", translation = "slyrics.config.shakeOnEmphasis")
		@Comment("A shake on words in CAPS or ending with !")
		public static boolean shakeOnEmphasis = D.shakeOnEmphasis;

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

	private RcConfig() {}
}
