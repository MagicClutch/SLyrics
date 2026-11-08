package dev.slyrics;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Einstellungsmenü auf Basis von YACL. */
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
				.description(OptionDescription.of(t("Client ID deiner App von developer.spotify.com. Redirect URI: " + Spotify.REDIRECT)))
				.binding(d.clientId, () -> c.clientId, v -> c.clientId = v.trim())
				.controller(StringControllerBuilder::create).build())
			.option(ButtonOption.createBuilder().name(t("Mit Spotify verbinden"))
				.description(OptionDescription.of(t("Speichert und startet den Login. Link erscheint im Chat.")))
				.text(t("Verbinden"))
				.action((screen, opt) -> {
					Config.save();
					Minecraft mc = Minecraft.getInstance();
					mc.gui.setScreen(null);
					if (mc.player != null) mc.player.connection.sendCommand("slyrics login");
				}).build())
			.option(bool("Aktiv", "Mod ein- oder ausschalten", d.enabled, () -> c.enabled, v -> c.enabled = v))
			.option(intg("Offset (ms)", "Lyrics früher (negativ) oder später (positiv) anzeigen", d.offsetMs, -3000, 3000, 50, () -> c.offsetMs, v -> c.offsetMs = v))
			.build();

		var holo = ConfigCategory.createBuilder().name(t("Hologramme"))
			.option(bool("Hologramme", "Zeilen als schwebenden Text in der Welt statt als HUD anzeigen", d.hologram, () -> c.hologram, v -> c.hologram = v))
			.option(bool("Sky-Modus", "Jedes Wort einzeln verteilt am Himmel", d.skyMode, () -> c.skyMode, v -> c.skyMode = v))
			.option(flt("Sky-Schräglage (°)", "Wörter werden zufällig bis zu diesem Winkel schräg gestellt", d.skyTilt, 0f, 45f, 1f, () -> c.skyTilt, v -> c.skyTilt = v))
			.option(flt("Sky-Höhe (°)", "Winkel über dem Horizont", d.skyElevation, 10f, 80f, 1f, () -> c.skyElevation, v -> c.skyElevation = v))
			.option(flt("Sky-Entfernung", "Entfernung der Himmelstexte in Blöcken", d.skyDistance, 15f, 120f, 5f, () -> c.skyDistance, v -> c.skyDistance = v))
			.option(flt("Radius", "Maximale Entfernung zu dir in Blöcken", d.holoRadius, 3f, 30f, 0.5f, () -> c.holoRadius, v -> c.holoRadius = v))
			.option(flt("Größe", "Größe des Textes in der Welt", d.holoScale, 0.3f, 3f, 0.1f, () -> c.holoScale, v -> c.holoScale = v))
			.option(intg("Max. Wörter pro Zeile", "Längere Zeilen werden umbrochen (20 = unbegrenzt)", 6, 1, 20,
				1, () -> Math.min(c.maxWords, 20), v -> c.maxWords = v >= 20 ? 999 : v))
			.option(intg("Max. gleichzeitige Texte", "Wie viele Hologramme gleichzeitig sichtbar sind", d.holoMax, 1, 8, 1, () -> c.holoMax, v -> c.holoMax = v))
			.option(bool("Durch Wände sichtbar", "Text auch hinter Blöcken lesbar", d.throughWalls, () -> c.throughWalls, v -> c.throughWalls = v))
			.build();

		var text = ConfigCategory.createBuilder().name(t("Text"))
			.option(Option.<String>createBuilder().name(t("Schriftart"))
				.description(OptionDescription.of(t("z.B. minecraft:default, minecraft:uniform, minecraft:alt, minecraft:illageralt oder eine Schrift aus einem Resourcepack")))
				.binding(d.font, () -> c.font, v -> c.font = v.trim())
				.controller(StringControllerBuilder::create).build())
			.option(Option.<Color>createBuilder().name(t("Farbe"))
				.binding(new Color(d.rgb()), () -> new Color(c.rgb()), v -> c.color = String.format("%06X", v.getRGB() & 0xFFFFFF))
				.controller(ColorControllerBuilder::create).build())
			.option(bool("Glow", "Leuchtender Rand um die Hologramm-Texte", d.glow, () -> c.glow, v -> c.glow = v))
			.option(bool("Emojis", "Hängt Symbole hinter Schlüsselwörter (love \u2665, fight \u2694, ...). Das Wort bleibt stehen. Die Wörter kannst du in config/slyrics.json unter emojiMap ändern.", d.emojis, () -> c.emojis, v -> c.emojis = v))
			.option(bool("Fett", "", d.bold, () -> c.bold, v -> c.bold = v))
			.option(bool("Schatten", "", d.shadow, () -> c.shadow, v -> c.shadow = v))
			.option(bool("Schreibmaschine", "Text baut sich Buchstabe für Buchstabe auf", d.typewriter, () -> c.typewriter, v -> c.typewriter = v))
			.option(bool("Hintergrund", "", d.background, () -> c.background, v -> c.background = v))
			.option(intg("Fade (ms)", "Ein-/Ausblenddauer", d.fadeMs, 0, 1000, 50, () -> c.fadeMs, v -> c.fadeMs = v))
			.build();

		var hud = ConfigCategory.createBuilder().name(t("HUD"))
			.option(flt("Größe", "Nur wenn Hologramme aus sind", d.scale, 0.5f, 5f, 0.1f, () -> c.scale, v -> c.scale = v))
			.option(flt("Position X (%)", "", d.xPercent, 0f, 100f, 1f, () -> c.xPercent, v -> c.xPercent = v))
			.option(flt("Position Y (%)", "", d.yPercent, 0f, 100f, 1f, () -> c.yPercent, v -> c.yPercent = v))
			.option(bool("Nachbarzeilen", "Vorherige/nächste Zeile gedimmt anzeigen", d.showNeighbors, () -> c.showNeighbors, v -> c.showNeighbors = v))
			.option(bool("Statusmeldung", "Hinweis wenn nichts läuft oder keine Lyrics gefunden wurden", d.showStatus, () -> c.showStatus, v -> c.showStatus = v))
			.build();

		return YetAnotherConfigLib.createBuilder()
			.title(t("SLyrics"))
			.category(spotify).category(holo).category(text).category(hud)
			.save(Config::save)
			.build().generateScreen(parent);
	}
}
