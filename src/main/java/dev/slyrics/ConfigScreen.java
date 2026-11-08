package dev.slyrics;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Settings screen based on YACL. */
public final class ConfigScreen {
	private ConfigScreen() {}

	private static Component t(String s) {
		return Component.literal(s);
	}

	private static Option<Boolean> bool(String name, String desc, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder().name(t(name)).description(OptionDescription.of(t(desc)))
			.binding(def, get, set).controller(TickBoxControllerBuilder::create).build();
	}

	private static Option<Float> flt(String name, String desc, float def, float min, float max, float step, Supplier<Float> get, Consumer<Float> set) {
		return Option.<Float>createBuilder().name(t(name)).description(OptionDescription.of(t(desc)))
			.binding(def, get, set).controller(o -> FloatSliderControllerBuilder.create(o).range(min, max).step(step)).build();
	}

	private static Option<Integer> intg(String name, String desc, int def, int min, int max, int step, Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder().name(t(name)).description(OptionDescription.of(t(desc)))
			.binding(def, get, set).controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(step)).build();
	}

	public static Screen create(Screen parent) {
		Config c = Config.INSTANCE;
		Config d = new Config();

		var spotify = ConfigCategory.createBuilder().name(t("Spotify"))
			.option(Option.<String>createBuilder().name(t("Client ID"))
				.description(OptionDescription.of(t("Client ID of your app from developer.spotify.com. Redirect URI: " + Spotify.REDIRECT)))
				.binding(d.clientId, () -> c.clientId, v -> c.clientId = v.trim())
				.controller(StringControllerBuilder::create).build())
			.option(ButtonOption.createBuilder().name(t("Connect to Spotify"))
				.description(OptionDescription.of(t("Saves and starts the login. The link appears in chat.")))
				.text(t("Connect"))
				.action((screen, opt) -> {
					Config.save();
					Minecraft mc = Minecraft.getInstance();
					mc.gui.setScreen(null);
					if (mc.player != null) mc.player.connection.sendCommand("slyrics login");
				}).build())
			.option(bool("Enabled", "Turn the mod on or off", d.enabled, () -> c.enabled, v -> c.enabled = v))
			.option(intg("Offset (ms)", "Show lyrics earlier (negative) or later (positive)", d.offsetMs, -3000, 3000, 50, () -> c.offsetMs, v -> c.offsetMs = v))
			.build();

		var holo = ConfigCategory.createBuilder().name(t("World words"))
			.option(bool("Enable world words", "Words float in front of you, scattered around the crosshair. If every world mode is off, the lyrics show as a plain HUD line", d.hologram, () -> c.hologram, v -> c.hologram = v))
			.option(bool("Lines mode", "Only when World words is the only active mode. Off: each word appears when it is sung. On: the whole line appears and the sung word lights up", d.lineMode, () -> c.lineMode, v -> c.lineMode = v))
			.option(bool("Center layout", "Off: words are scattered around the crosshair. On: words in a row straight ahead", d.centerLayout, () -> c.centerLayout, v -> c.centerLayout = v))
			.option(flt("Closest distance", "Closest distance of the words in blocks", d.near, 2f, 12f, 0.5f, () -> c.near, v -> c.near = v))
			.option(flt("Farthest distance", "Farthest distance of the words in blocks", d.far, 2f, 16f, 0.5f, () -> c.far, v -> c.far = v))
			.option(bool("Follow me", "Words are carried along when you walk", d.follow, () -> c.follow, v -> c.follow = v))
			.option(bool("Prevent block clipping", "Keeps words out of blocks: they are moved in front of walls and terrain and never placed inside the ground", d.blockClipping, () -> c.blockClipping, v -> c.blockClipping = v))
			.option(bool("Visible through walls", "Text readable even behind blocks", d.throughWalls, () -> c.throughWalls, v -> c.throughWalls = v))
			.build();

		var sky = ConfigCategory.createBuilder().name(t("Sky"))
			.option(bool("Enable sky mode", "Huge words far away in the sky that flash up and drift slowly like clouds", d.skyMode, () -> c.skyMode, v -> c.skyMode = v))
			.option(flt("Sky distance", "How far away the sky words are, in blocks", d.skyDistance, 30f, 200f, 5f, () -> c.skyDistance, v -> c.skyDistance = v))
			.option(flt("Sky height (°)", "Preferred angle above the horizon", d.skyElevation, 10f, 80f, 1f, () -> c.skyElevation, v -> c.skyElevation = v))
			.option(flt("Sky letter size (°)", "Height of the letters as seen from your view", d.skyTextDeg, 3f, 20f, 0.5f, () -> c.skyTextDeg, v -> c.skyTextDeg = v))
			.build();

		var stick = ConfigCategory.createBuilder().name(t("Stick on blocks"))
			.option(bool("Enable stick-on-block mode", "Words stick flat onto the blocks you look at, like stickers on walls and floors", d.stickMode, () -> c.stickMode, v -> c.stickMode = v))
			.option(flt("Range", "How far away blocks may be to get a word, in blocks", d.stickRange, 3f, 40f, 1f, () -> c.stickRange, v -> c.stickRange = v))
			.option(intg("Size (%)", "Size of the stuck words", d.stickSize, 30, 300, 5, () -> c.stickSize, v -> c.stickSize = v))
			.build();

		var effects = ConfigCategory.createBuilder().name(t("Effects"))
			.option(intg("Size (%)", "Size of the text in the world", d.size, 50, 250, 5, () -> c.size, v -> c.size = v))
			.option(intg("Opacity (%)", "", d.opacity, 10, 100, 5, () -> c.opacity, v -> c.opacity = v))
			.option(intg("Glow (%)", "Soft halo around the words", d.glow, 0, 100, 5, () -> c.glow, v -> c.glow = v))
			.option(intg("Outline (%)", "Dark outline so words stay readable on bright backgrounds", d.outline, 0, 100, 5, () -> c.outline, v -> c.outline = v))
			.option(intg("Motion blur (%)", "Smear while a word appears and fades", d.blur, 0, 100, 5, () -> c.blur, v -> c.blur = v))
			.option(intg("Bounce (%)", "Words spring into place and float slightly", d.bounce, 0, 100, 5, () -> c.bounce, v -> c.bounce = v))
			.option(bool("Flicker", "Occasional glitch effect", d.flicker, () -> c.flicker, v -> c.flicker = v))
			.option(bool("3D text", "Gives the words a body: stacked layers behind the front face, visible from the side", d.depth3d, () -> c.depth3d, v -> c.depth3d = v))
			.option(flt("3D thickness", "Thickness of the 3D body in font pixels", d.depthSize, 1f, 8f, 0.5f, () -> c.depthSize, v -> c.depthSize = v))
			.build();

		var text = ConfigCategory.createBuilder().name(t("Text"))
			.option(Option.<String>createBuilder().name(t("Font"))
				.description(OptionDescription.of(t("e.g. minecraft:default, minecraft:uniform, minecraft:alt, minecraft:illageralt or a font from a resource pack")))
				.binding(d.font, () -> c.font, v -> c.font = v.trim())
				.controller(StringControllerBuilder::create).build())
			.option(Option.<Color>createBuilder().name(t("Color"))
				.binding(new Color(d.rgb()), () -> new Color(c.rgb()), v -> c.color = String.format("%06X", v.getRGB() & 0xFFFFFF))
				.controller(ColorControllerBuilder::create).build())
			.option(bool("Emojis", "Appends symbols after keywords (love \u2665, fight \u2694, ...). The word stays. You can edit the words in config/slyrics.json under emojiMap.", d.emojis, () -> c.emojis, v -> c.emojis = v))
			.option(Option.<Color>createBuilder().name(t("Glow color"))
				.binding(new Color(Integer.parseInt(d.glowColor, 16)), () -> new Color(Integer.parseInt(c.glowColor.replace("#", ""), 16)), v -> c.glowColor = String.format("%06X", v.getRGB() & 0xFFFFFF))
				.controller(ColorControllerBuilder::create).build())
			.option(bool("Minecraft icons", "Use real Minecraft icons for matching words: heart for love, diamond for diamond, sword for fight, and so on. Other words keep their symbols. Edit the words in config/slyrics.json under emojiSpriteMap.", d.emojiSprites, () -> c.emojiSprites, v -> c.emojiSprites = v))
			.option(bool("Colorful emojis", "Every emoji gets its own color (red heart, gold star, ...) instead of the text color", d.emojiColors, () -> c.emojiColors, v -> c.emojiColors = v))
			.option(bool("Uppercase", "", d.uppercase, () -> c.uppercase, v -> c.uppercase = v))
			.option(bool("Bold", "", d.bold, () -> c.bold, v -> c.bold = v))
			.option(bool("Shadow", "", d.shadow, () -> c.shadow, v -> c.shadow = v))
			.option(bool("Typewriter", "Text is typed out letter by letter", d.typewriter, () -> c.typewriter, v -> c.typewriter = v))
			.option(bool("Background", "", d.background, () -> c.background, v -> c.background = v))
			.option(intg("Fade (ms)", "Fade in/out duration", d.fadeMs, 0, 1000, 50, () -> c.fadeMs, v -> c.fadeMs = v))
			.build();

		var sweep = ConfigCategory.createBuilder().name(t("Karaoke sweep"))
			.option(bool("Enable karaoke sweep", "The word being sung is colored from left to right in time with the singing. When the next word starts, the color goes off again.", d.sweepMode, () -> c.sweepMode, v -> c.sweepMode = v))
			.option(Option.<Color>createBuilder().name(t("Sweep color"))
				.binding(new Color(Integer.parseInt(d.sweepColor, 16)), () -> new Color(Integer.parseInt(c.sweepColor.replace("#", ""), 16)), v -> c.sweepColor = String.format("%06X", v.getRGB() & 0xFFFFFF))
				.controller(ColorControllerBuilder::create).build())
			.option(bool("Color the whole word at once", "Instead of sweeping across the word, color it completely the moment it is sung", d.sweepWhole, () -> c.sweepWhole, v -> c.sweepWhole = v))
			.option(intg("Fade out (ms)", "How long the color takes to go off after the word is over (0 = instantly)", d.sweepFadeMs, 0, 600, 20, () -> c.sweepFadeMs, v -> c.sweepFadeMs = v))
			.option(bool("Glow", "A soft halo in the sweep color around the colored part", d.sweepGlow, () -> c.sweepGlow, v -> c.sweepGlow = v))
			.build();

		var afk = ConfigCategory.createBuilder().name(t("AFK camera"))
			.option(bool("AFK camera", "After you have been idle for a while, the camera leaves first person and slowly orbits you like a cinematic shot, with the lyrics in view. Any input ends it.", d.afkEnabled, () -> c.afkEnabled, v -> c.afkEnabled = v))
			.option(intg("Start after (seconds)", "Idle time before the AFK camera starts", d.afkSeconds, 3, 600, 1, () -> c.afkSeconds, v -> c.afkSeconds = v))
			.option(bool("Only while music plays", "Start only when Spotify is playing", d.afkOnlyWhilePlaying, () -> c.afkOnlyWhilePlaying, v -> c.afkOnlyWhilePlaying = v))
			.option(bool("Shot: Orbit", "Circle around you", d.afkOrbit, () -> c.afkOrbit, v -> c.afkOrbit = v))
			.option(bool("Shot: Rise", "Drone shot from low to high", d.afkRise, () -> c.afkRise, v -> c.afkRise = v))
			.option(bool("Shot: Descend", "Drone shot from high to low", d.afkDescend, () -> c.afkDescend, v -> c.afkDescend = v))
			.option(bool("Shot: Diagonal", "Sweep diagonally across while rising", d.afkDiagonal, () -> c.afkDiagonal, v -> c.afkDiagonal = v))
			.option(bool("Shot: Fly-by", "Straight pass beside you", d.afkFlyby, () -> c.afkFlyby, v -> c.afkFlyby = v))
			.option(bool("Shot: Push in", "Move toward you", d.afkPushIn, () -> c.afkPushIn, v -> c.afkPushIn = v))
			.option(bool("Shot: Pull out", "Move away from you", d.afkPullOut, () -> c.afkPullOut, v -> c.afkPullOut = v))
			.option(intg("Cut every N lines", "Hard cut to a new shot every this many lyric lines. Cuts only happen when a new line starts.", d.afkCutEveryLines, 1, 12, 1, () -> c.afkCutEveryLines, v -> c.afkCutEveryLines = v))
			.option(intg("Fallback shot length (s)", "Used only when the timing of the next cut is unknown", d.afkShotSeconds, 3, 60, 1, () -> c.afkShotSeconds, v -> c.afkShotSeconds = v))
			.option(flt("Max camera distance", "The camera never gets farther from you than this, in blocks", d.afkMaxDistance, 2f, 30f, 0.5f, () -> c.afkMaxDistance, v -> c.afkMaxDistance = v))
			.option(bool("Close-up shots", "Include close-up shots: the camera 2 to 3 blocks away at head height", d.afkCloseUp, () -> c.afkCloseUp, v -> c.afkCloseUp = v))
			.option(intg("Close-up chance (%)", "How often the next shot is a close-up", d.afkCloseUpPercent, 0, 100, 5, () -> c.afkCloseUpPercent, v -> c.afkCloseUpPercent = v))
			.option(bool("Random order", "Pick the next shot at random (otherwise in the listed order)", d.afkRandomOrder, () -> c.afkRandomOrder, v -> c.afkRandomOrder = v))
			.option(bool("Fade to black at cuts", "Dip through black at each cut instead of an instant cut", d.afkFadeBetween, () -> c.afkFadeBetween, v -> c.afkFadeBetween = v))
			.option(flt("Orbit radius", "Distance of the camera from you, in blocks", d.afkRadius, 3f, 20f, 0.5f, () -> c.afkRadius, v -> c.afkRadius = v))
			.option(flt("Camera height", "Height above your eyes, in blocks", d.afkHeight, 0f, 8f, 0.5f, () -> c.afkHeight, v -> c.afkHeight = v))
			.option(flt("Orbit speed (°/s)", "How fast the camera circles you", d.afkSpeed, 2f, 60f, 1f, () -> c.afkSpeed, v -> c.afkSpeed = v))
			.build();

		var hud = ConfigCategory.createBuilder().name(t("HUD"))
			.option(flt("Size", "Only used when holograms are off", d.scale, 0.5f, 5f, 0.1f, () -> c.scale, v -> c.scale = v))
			.option(flt("Position X (%)", "", d.xPercent, 0f, 100f, 1f, () -> c.xPercent, v -> c.xPercent = v))
			.option(flt("Position Y (%)", "", d.yPercent, 0f, 100f, 1f, () -> c.yPercent, v -> c.yPercent = v))
			.option(bool("Neighbor lines", "Show the previous/next line, dimmed", d.showNeighbors, () -> c.showNeighbors, v -> c.showNeighbors = v))
			.option(bool("Status message", "Status message when nothing is playing or no lyrics were found", d.showStatus, () -> c.showStatus, v -> c.showStatus = v))
			.build();

		return YetAnotherConfigLib.createBuilder()
			.title(t("SLyrics"))
			.category(spotify).category(holo).category(sky).category(stick).category(effects).category(sweep).category(text).category(afk).category(hud)
			.save(Config::save)
			.build().generateScreen(parent);
	}
}
