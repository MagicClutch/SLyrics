package dev.slyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen, gespeichert in config/slyrics.json. */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("slyrics.json");
	public static Config INSTANCE = new Config();

	public boolean enabled = true;
	/** Spotify Client ID (aus developer.spotify.com). */
	public String clientId = "";
	/** Schriftart als Identifier, z.B. minecraft:default, minecraft:uniform, minecraft:alt oder eine eigene aus einem Resourcepack. */
	public String font = "minecraft:default";
	public float scale = 1.5f;
	/** Position des Textes in Prozent der Bildschirmbreite/-höhe (Mitte des Textes). */
	public float xPercent = 50f;
	public float yPercent = 75f;
	/** Textfarbe als RGB-Hex ohne Alpha. */
	public String color = "FF5FB0";
	public boolean shadow = true;
	public boolean bold = true;
	/** Leuchtender Rand um den Text (Glow). */
	public boolean glow = true;
	/** Symbole hinter Schlüsselwörtern (love -> love ♥). Wort bleibt erhalten. */
	public boolean emojis = true;
	/** Wort (klein geschrieben) -> Symbol. Kann in slyrics.json frei angepasst werden. */
	public java.util.Map<String, String> emojiMap = Emojis.defaults();
	/** Sky-Modus: zufällige Schräglage der Wörter in Grad (+-). */
	public float skyTilt = 12f;
	public boolean typewriter = true;
	/** Vorherige/nächste Zeile klein und gedimmt mit anzeigen. */
	public boolean showNeighbors = false;
	public boolean background = false;
	/** Verschiebung der Lyrics in ms (positiv = später). */
	public int offsetMs = 0;
	public int fadeMs = 250;
	/** Zeilen als schwebende Hologramme in der Welt statt als HUD-Text. */
	public boolean hologram = true;
	/** Maximale Entfernung der Hologramme zum Spieler in Blöcken. */
	public float holoRadius = 12f;
	public float holoScale = 0.8f;
	public int holoMax = 4;
	/** Sky-Modus: Lyrics riesig am Himmel statt vor dir. */
	public boolean skyMode = false;
	/** Höhe am Himmel in Grad über dem Horizont. */
	public float skyElevation = 25f;
	public float skyDistance = 40f;
	/** Max. Wörter pro Textzeile; längere Zeilen werden umbrochen und untereinander gestapelt. */
	public int maxWords = 6;
	public boolean throughWalls = true;
	/** Hinweis auf dem Bildschirm wenn keine Lyrics gefunden wurden. */
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
				if (c != null) INSTANCE = c;
			}
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Konnte Config nicht laden", e);
		}
		save();
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(INSTANCE));
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Konnte Config nicht speichern", e);
		}
	}
}
