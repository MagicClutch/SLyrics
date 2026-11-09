package dev.slyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Apple Music has no public "what is playing right now" API, so the song is read from the media player that plays it
 * on this computer (the Apple Music web player in a browser, Cider, ...). On Linux every such player announces itself
 * on the session D-Bus as an MPRIS player; this class asks it for title, artist, length, position and play state.
 */
public final class AppleMusic {
	private static final String PREFIX = "org.mpris.MediaPlayer2.";
	private static final String PLAYER_IF = "org.mpris.MediaPlayer2.Player";

	private AppleMusic() {}

	public static void start() {
		Spotify.executor().scheduleWithFixedDelay(AppleMusic::pollSafe, 1, 1, TimeUnit.SECONDS);
	}

	private static void pollSafe() {
		try {
			poll();
		} catch (Exception e) {
			SLyricsClient.LOGGER.debug("Apple Music poll error", e);
		}
	}

	private static void poll() throws Exception {
		Config c = Config.INSTANCE;
		if (!c.enabled || !"Apple Music".equals(c.musicSource)) return;
		if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			Spotify.status = "Apple Music: only supported on Linux for now";
			return;
		}
		String filter = c.applePlayer == null ? "" : c.applePlayer.trim().toLowerCase();
		String chosen = null;
		String chosenState = "";
		for (String name : players()) {
			String lower = name.toLowerCase();
			if (lower.contains("spotify")) continue; // that one is the Spotify source
			if (!filter.isEmpty() && !lower.contains(filter)) continue;
			String state = prop(name, "PlaybackStatus");
			if (state == null) continue;
			if (chosen == null || ("Playing".equals(state) && !"Playing".equals(chosenState))) {
				chosen = name;
				chosenState = state;
			}
		}
		if (chosen == null) {
			Spotify.playing = false;
			Spotify.status = "Apple Music: no player found. Start Apple Music (web player or Cider)";
			return;
		}
		JsonObject meta = obj(propJson(chosen, "Metadata"));
		if (meta == null || !meta.has("xesam:title")) {
			Spotify.playing = false;
			Spotify.status = "Apple Music: nothing is playing";
			return;
		}
		String title = str(meta.get("xesam:title"));
		String artist = first(meta.get("xesam:artist"));
		String album = meta.has("xesam:album") ? str(meta.get("xesam:album")) : "";
		long lengthMs = meta.has("mpris:length") ? num(meta.get("mpris:length")) / 1000L : 0;
		long posMs = 0;
		String pos = propJson(chosen, "Position");
		if (pos != null) posMs = num(JsonParser.parseString(pos).getAsJsonObject().get("data")) / 1000L;

		Spotify.setProgress(posMs, "Playing".equals(chosenState));
		Spotify.setTrack(new Track(artist + " - " + title, title, artist, album, lengthMs));
	}

	// ---------------------------------------------------------------- busctl helpers

	private static List<String> players() throws Exception {
		List<String> out = new ArrayList<>();
		String json = run("busctl", "--user", "--json=short", "list");
		if (json == null) return out;
		for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
			String n = e.getAsJsonObject().get("name").getAsString();
			if (n.startsWith(PREFIX)) out.add(n);
		}
		return out;
	}

	private static String propJson(String bus, String property) throws Exception {
		return run("busctl", "--user", "--json=short", "get-property", bus, "/org/mpris/MediaPlayer2", PLAYER_IF, property);
	}

	private static String prop(String bus, String property) throws Exception {
		String j = propJson(bus, property);
		return j == null ? null : JsonParser.parseString(j).getAsJsonObject().get("data").getAsString();
	}

	private static JsonObject obj(String json) {
		if (json == null) return null;
		JsonElement d = JsonParser.parseString(json).getAsJsonObject().get("data");
		return d.isJsonObject() ? d.getAsJsonObject() : null;
	}

	/** A D-Bus variant in busctl's JSON form is {"type":..,"data":..}; this unwraps it. */
	private static JsonElement unwrap(JsonElement v) {
		return v != null && v.isJsonObject() && v.getAsJsonObject().has("data") ? v.getAsJsonObject().get("data") : v;
	}

	private static String str(JsonElement v) {
		JsonElement d = unwrap(v);
		return d == null || d.isJsonNull() ? "" : d.getAsString();
	}

	private static long num(JsonElement v) {
		JsonElement d = unwrap(v);
		return d == null || d.isJsonNull() ? 0 : d.getAsLong();
	}

	private static String first(JsonElement v) {
		JsonElement d = unwrap(v);
		if (d == null || d.isJsonNull()) return "";
		return d.isJsonArray() ? (d.getAsJsonArray().isEmpty() ? "" : d.getAsJsonArray().get(0).getAsString()) : d.getAsString();
	}

	private static String run(String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		String out = new String(p.getInputStream().readAllBytes());
		if (!p.waitFor(3, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			return null;
		}
		return p.exitValue() == 0 && !out.isBlank() ? out : null;
	}
}
