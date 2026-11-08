package dev.slyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fetches synced lyrics from https://lrclib.net (free, no API key). */
public final class LrcLib {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Map<String, List<LyricLine>> CACHE = new ConcurrentHashMap<>();

	private LrcLib() {}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", "SLyrics Minecraft Mod (Fabric)")
			.timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		return res.statusCode() == 200 ? res.body() : null;
	}

	/** Returns an empty list if nothing was found. Blocking: only call from a background thread. */
	public static List<LyricLine> fetch(Track t) {
		List<LyricLine> cached = CACHE.get(t.id());
		if (cached != null) return cached;
		List<LyricLine> result = List.of();
		try {
			String body = get("https://lrclib.net/api/get?track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist())
				+ "&album_name=" + enc(t.album()) + "&duration=" + (t.durationMs() / 1000));
			String synced = body == null ? null : syncedOf(JsonParser.parseString(body).getAsJsonObject());
			if (synced == null) {
				String s = get("https://lrclib.net/api/search?track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist()));
				if (s != null) {
					JsonArray arr = JsonParser.parseString(s).getAsJsonArray();
					for (JsonElement e : arr) {
						JsonObject o = e.getAsJsonObject();
						double dur = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
						if (Math.abs(dur * 1000 - t.durationMs()) > 5000) continue;
						synced = syncedOf(o);
						if (synced != null) break;
					}
				}
			}
			if (synced != null) result = LyricLine.parseLrc(synced);
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("LRCLIB error", e);
			return result; // do not cache, retry later
		}
		CACHE.put(t.id(), result);
		return result;
	}

	private static String syncedOf(JsonObject o) {
		JsonElement e = o.get("syncedLyrics");
		return e == null || e.isJsonNull() || e.getAsString().isBlank() ? null : e.getAsString();
	}
}
