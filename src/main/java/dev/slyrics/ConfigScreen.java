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

		var holo = ConfigCategory.createBuilder().name(t("Holograms"))
			.option(bool("Holograms", "Show lines as floating text in the world instead of on the HUD", d.hologram, () -> c.hologram, v -> c.hologram = v))
			.option(bool("Sky mode", "Each word placed separately in the sky", d.skyMode, () -> c.skyMode, v -> c.skyMode = v))
			.option(flt("Sky tilt (°)", "Words are randomly tilted up to this angle", d.skyTilt, 0f, 45f, 1f, () -> c.skyTilt, v -> c.skyTilt = v))
			.option(flt("Sky height (°)", "Angle above the horizon", d.skyElevation, 10f, 80f, 1f, () -> c.skyElevation, v -> c.skyElevation = v))
			.option(flt("Sky distance", "Distance of the sky words in blocks", d.skyDistance, 15f, 120f, 5f, () -> c.skyDistance, v -> c.skyDistance = v))
			.option(flt("Radius", "Maximum distance from you in blocks", d.holoRadius, 3f, 30f, 0.5f, () -> c.holoRadius, v -> c.holoRadius = v))
			.option(flt("Size", "Size of the text in the world", d.holoScale, 0.3f, 3f, 0.1f, () -> c.holoScale, v -> c.holoScale = v))
			.option(intg("Max words per chunk", "Longer lines are split into chunks (20 = unlimited)", 6, 1, 20,
				1, () -> Math.min(c.maxWords, 20), v -> c.maxWords = v >= 20 ? 999 : v))
			.option(intg("Max simultaneous texts", "How many holograms are visible at once", d.holoMax, 1, 8, 1, () -> c.holoMax, v -> c.holoMax = v))
			.option(bool("Visible through walls", "Text readable even behind blocks", d.throughWalls, () -> c.throughWalls, v -> c.throughWalls = v))
			.build();

		var text = ConfigCategory.createBuilder().name(t("Text"))
			.option(Option.<String>createBuilder().name(t("Font"))
				.description(OptionDescription.of(t("e.g. minecraft:default, minecraft:uniform, minecraft:alt, minecraft:illageralt or a font from a resource pack")))
				.binding(d.font, () -> c.font, v -> c.font = v.trim())
				.controller(StringControllerBuilder::create).build())
			.option(Option.<Color>createBuilder().name(t("Color"))
				.binding(new Color(d.rgb()), () -> new Color(c.rgb()), v -> c.color = String.format("%06X", v.getRGB() & 0xFFFFFF))
				.controller(ColorControllerBuilder::create).build())
			.option(bool("Glow", "Outline around the hologram text", d.glow, () -> c.glow, v -> c.glow = v))
			.option(bool("Emojis", "Appends symbols after keywords (love \u2665, fight \u2694, ...). The word stays. You can edit the words in config/slyrics.json under emojiMap.", d.emojis, () -> c.emojis, v -> c.emojis = v))
			.option(bool("Bold", "", d.bold, () -> c.bold, v -> c.bold = v))
			.option(bool("Shadow", "", d.shadow, () -> c.shadow, v -> c.shadow = v))
			.option(bool("Typewriter", "Text is typed out letter by letter", d.typewriter, () -> c.typewriter, v -> c.typewriter = v))
			.option(bool("Background", "", d.background, () -> c.background, v -> c.background = v))
			.option(intg("Fade (ms)", "Fade in/out duration", d.fadeMs, 0, 1000, 50, () -> c.fadeMs, v -> c.fadeMs = v))
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
			.category(spotify).category(holo).category(text).category(hud)
			.save(Config::save)
			.build().generateScreen(parent);
	}
}
