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
			SLyricsClient.LOGGER.debug("Other-player poll error", e);
		}
	}

	private static void poll() throws Exception {
		Config c = Config.INSTANCE;
		if (!c.enabled || !"Others".equals(c.musicSource)) return;
		if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			Spotify.status = "Others: only supported on Linux for now";
			return;
		}
		String filter = c.applePlayer == null ? "" : c.applePlayer.trim().toLowerCase();
		String chosen = null;
		String chosenState = "";
		int chosenScore = -1;
		for (String name : players()) {
			String lower = name.toLowerCase();
			if (lower.contains("spotify")) continue; // that one is the Spotify source
			if (!filter.isEmpty() && !lower.contains(filter)) continue;
			String state = prop(name, "PlaybackStatus");
			if (state == null) continue;
			// a playing player beats a paused one; among those, one that knows the artist (clean metadata) wins
			int score = ("Playing".equals(state) ? 2 : 0) + (hasArtist(name) ? 1 : 0);
			if (score > chosenScore) {
				chosen = name;
				chosenScore = score;
				chosenState = state;
			}
		}
		if (chosen == null) {
			Spotify.playing = false;
			boolean flatpak = java.nio.file.Files.exists(java.nio.file.Path.of("/.flatpak-info"));
			Spotify.status = flatpak && useGdbus
				? "Others: no media player found. Prism runs in Flatpak: run  flatpak override --user --talk-name='org.mpris.MediaPlayer2.*' org.prismlauncher.PrismLauncher  and restart it (see README)"
				: "Others: no media player found. Play something in a browser, Cider, VLC, ...";
			return;
		}
		JsonObject meta = obj(propJson(chosen, "Metadata"));
		if (meta == null || !meta.has("xesam:title")) {
			Spotify.playing = false;
			Spotify.status = "Others: nothing is playing";
			return;
		}
		String title = clean(str(meta.get("xesam:title")));
		String artist = clean(first(meta.get("xesam:artist"))).replaceAll("(?i)\\s*-\\s*topic$", "");
		String url = meta.has("xesam:url") ? str(meta.get("xesam:url")) : "";
		// plain YouTube: the "artist" is only the channel name; the real one is in the title as "Artist - Title"
		boolean plainYoutube = url.contains("://www.youtube.com/") || url.contains("://youtube.com/") || url.contains("://m.youtube.com/");
		if (artist.isEmpty() || plainYoutube) { // browser tabs often put "Artist - Title" into the title
			String[] parts = title.split("\\s+[-\u2013\u2014]\\s+", 2);
			if (parts.length == 2) {
				artist = parts[0].trim();
				title = parts[1].trim();
			}
		}
		String album = meta.has("xesam:album") ? str(meta.get("xesam:album")) : "";
		long lengthMs = meta.has("mpris:length") ? num(meta.get("mpris:length")) / 1000L : 0;
		long posMs = 0;
		String pos = propJson(chosen, "Position");
		if (pos != null) posMs = num(JsonParser.parseString(pos).getAsJsonObject().get("data")) / 1000L;

		Spotify.setProgress(posMs, "Playing".equals(chosenState));
		Spotify.setTrack(new Track(artist + " - " + title, title, artist, album, lengthMs));
	}

	private static boolean hasArtist(String bus) {
		try {
			JsonObject m = obj(propJson(bus, "Metadata"));
			return m != null && !first(m.get("xesam:artist")).isBlank();
		} catch (Exception e) {
			return false;
		}
	}

	/** Removes what web players add to titles: invisible marks, " - YouTube", " - Apple Music", "(Official Video)", "[Lyrics]". */
	static String clean(String s) {
		String t = s.replaceAll("[\\u200E\\u200F\\u202A-\\u202E]", "").trim();
		t = t.replaceAll("(?i)\\s*[-\u2013\u2014|]\\s*(youtube music|youtube|apple music|soundcloud|tidal|deezer)\\s*$", "");
		t = t.replaceAll("(?i)\\s*[\\(\\[](official[^\\)\\]]*|lyrics?[^\\)\\]]*|audio[^\\)\\]]*|video[^\\)\\]]*|hd|hq|4k)[\\)\\]]", "");
		return t.trim();
	}

	// ---------------------------------------------------------------- busctl helpers

	/** busctl is not inside a Flatpak sandbox (e.g. Prism from Bazaar); gdbus is, so it is the fallback. */
	private static volatile boolean useGdbus = false;

	private static List<String> players() throws Exception {
		List<String> out = new ArrayList<>();
		JsonElement names = null;
		if (!useGdbus) {
			try {
				String json = run("busctl", "--user", "--json=short", "list");
				if (json != null) {
					for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
						String n = e.getAsJsonObject().get("name").getAsString();
						if (n.startsWith(PREFIX)) out.add(n);
					}
				}
				return out;
			} catch (java.io.IOException noBusctl) {
				useGdbus = true;
			}
		}
		String g = run("gdbus", "call", "--session", "--dest", "org.freedesktop.DBus", "--object-path", "/org/freedesktop/DBus",
			"--method", "org.freedesktop.DBus.ListNames");
		if (g == null) return out;
		names = new GVariantText(g).parse();
		if (names.isJsonArray()) names = names.getAsJsonArray().get(0);
		for (JsonElement e : names.getAsJsonArray()) {
			String n = e.getAsString();
			if (n.startsWith(PREFIX)) out.add(n);
		}
		return out;
	}

	private static String propJson(String bus, String property) throws Exception {
		if (!useGdbus) {
			try {
				return run("busctl", "--user", "--json=short", "get-property", bus, "/org/mpris/MediaPlayer2", PLAYER_IF, property);
			} catch (java.io.IOException noBusctl) {
				useGdbus = true;
			}
		}
		String g = run("gdbus", "call", "--session", "--dest", bus, "--object-path", "/org/mpris/MediaPlayer2",
			"--method", "org.freedesktop.DBus.Properties.Get", PLAYER_IF, property);
		if (g == null) return null;
		JsonElement v = new GVariantText(g).parse(); // ( <value> , )  ->  [value]
		if (v.isJsonArray()) v = v.getAsJsonArray().get(0);
		JsonObject o = new JsonObject();
		o.add("data", v);
		return o.toString();
	}

	/** Minimal reader for gdbus' text output: tuples and arrays become JSON arrays, dictionaries objects, variants are unwrapped. */
	private static final class GVariantText {
		private final String t;
		private int i;

		GVariantText(String t) { this.t = t.trim(); }

		JsonElement parse() { return value(); }

		private void ws() { while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++; }

		private JsonElement value() {
			ws();
			char c = t.charAt(i);
			if (c == '(' || c == '[') {
				char close = c == '(' ? ')' : ']';
				i++;
				com.google.gson.JsonArray a = new com.google.gson.JsonArray();
				for (;;) {
					ws();
					if (t.charAt(i) == close) { i++; return a; }
					if (t.charAt(i) == ',') { i++; continue; }
					a.add(value());
				}
			}
			if (c == '{') {
				i++;
				JsonObject o = new JsonObject();
				for (;;) {
					ws();
					if (t.charAt(i) == '}') { i++; return o; }
					if (t.charAt(i) == ',') { i++; continue; }
					String k = value().getAsString();
					ws();
					if (t.charAt(i) == ':') i++;
					o.add(k, value());
				}
			}
			if (c == '<') {
				i++;
				JsonElement v = value();
				ws();
				if (t.charAt(i) == '>') i++;
				return v;
			}
			if (c == '\'' || c == '"') {
				i++;
				StringBuilder sb = new StringBuilder();
				while (t.charAt(i) != c) {
					char ch = t.charAt(i++);
					if (ch == '\\') {
						char n = t.charAt(i++);
						sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
					} else sb.append(ch);
				}
				i++;
				return new com.google.gson.JsonPrimitive(sb.toString());
			}
			if (c == '@') { // type annotation like @as [] or @a{sv} {}
				while (!Character.isWhitespace(t.charAt(i))) i++;
				return value();
			}
			int st = i;
			while (i < t.length() && ",)]}>:".indexOf(t.charAt(i)) < 0) i++;
			String word = t.substring(st, i).trim();
			int sp = word.lastIndexOf(' '); // "uint64 5" -> "5"
			if (sp >= 0) word = word.substring(sp + 1);
			if (word.equals("true") || word.equals("false")) return new com.google.gson.JsonPrimitive(Boolean.parseBoolean(word));
			try {
				return new com.google.gson.JsonPrimitive(Long.parseLong(word));
			} catch (NumberFormatException e) {
				try {
					return new com.google.gson.JsonPrimitive(Double.parseDouble(word));
				} catch (NumberFormatException e2) {
					return new com.google.gson.JsonPrimitive(word);
				}
			}
		}
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
