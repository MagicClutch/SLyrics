package dev.slyrics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Spotify Login (PKCE, kein Client Secret nötig) und Abfrage des aktuell laufenden Songs. */
public final class Spotify {
	public static final int PORT = 8888;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPE = "user-read-currently-playing user-read-playback-state";
	private static final Path AUTH_FILE = FabricLoader.getInstance().getConfigDir().resolve("slyrics-auth.json");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Gson GSON = new Gson();

	private static final ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(2, r -> {
		Thread t = new Thread(r, "SLyrics-Worker");
		t.setDaemon(true);
		return t;
	});

	private static volatile String accessToken;
	private static volatile long accessExpiresAt;
	private static volatile String refreshToken;
	private static volatile long pausedUntil;
	private static String verifier;

	// ---- öffentlicher Zustand für das HUD ----
	public static volatile Track track;
	public static volatile List<LyricLine> lyrics = List.of();
	public static volatile boolean lyricsLoading;
	public static volatile boolean playing;
	private static volatile long progressAtFetch;
	private static volatile long fetchedAtNanos;
	public static volatile String status = "Nicht verbunden. Nutze /slyrics login";

	private Spotify() {}

	public static boolean isLoggedIn() {
		return refreshToken != null;
	}

	/** Aktuelle Songposition in ms (interpoliert zwischen den Abfragen). */
	public static long positionMs() {
		long p = progressAtFetch;
		if (playing) p += (System.nanoTime() - fetchedAtNanos) / 1_000_000L;
		return p;
	}

	public static void start() {
		loadAuth();
		EXEC.scheduleWithFixedDelay(Spotify::pollSafe, 1, 1, TimeUnit.SECONDS);
	}

	// ---------------- Login ----------------

	public static String beginLogin(Consumer<String> onDone) throws Exception {
		String clientId = Config.INSTANCE.clientId.trim();
		if (clientId.isEmpty()) throw new IllegalStateException("Keine Client ID gesetzt");
		byte[] rnd = new byte[64];
		new SecureRandom().nextBytes(rnd);
		verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
		String challenge = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		String state = Long.toHexString(new SecureRandom().nextLong());

		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
		server.createContext("/callback", ex -> {
			String q = ex.getRequestURI().getQuery();
			String code = param(q, "code");
			boolean ok = code != null && state.equals(param(q, "state"));
			byte[] body = (ok ? "<h2>SLyrics verbunden. Du kannst dieses Fenster schliessen.</h2>"
				: "<h2>Login fehlgeschlagen.</h2>").getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body);
			ex.close();
			server.stop(1);
			EXEC.execute(() -> {
				try {
					if (!ok) throw new IllegalStateException("Ungültige Antwort von Spotify");
					exchangeCode(code);
					onDone.accept("Spotify verbunden!");
				} catch (Exception e) {
					SLyricsClient.LOGGER.warn("Login Fehler", e);
					onDone.accept("Login fehlgeschlagen: " + e.getMessage());
				}
			});
		});
		server.setExecutor(EXEC);
		server.start();
		EXEC.schedule(() -> server.stop(0), 5, TimeUnit.MINUTES);

		return "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId)
			+ "&scope=" + enc(SCOPE) + "&redirect_uri=" + enc(REDIRECT)
			+ "&code_challenge_method=S256&code_challenge=" + challenge + "&state=" + state;
	}

	public static void logout() {
		accessToken = null;
		refreshToken = null;
		track = null;
		lyrics = List.of();
		playing = false;
		status = "Nicht verbunden. Nutze /slyrics login";
		try {
			Files.deleteIfExists(AUTH_FILE);
		} catch (Exception ignored) {}
	}

	private static void exchangeCode(String code) throws Exception {
		JsonObject o = tokenRequest("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
			+ "&client_id=" + enc(Config.INSTANCE.clientId.trim()) + "&code_verifier=" + enc(verifier));
		applyToken(o);
	}

	private static void refresh() throws Exception {
		JsonObject o = tokenRequest("grant_type=refresh_token&refresh_token=" + enc(refreshToken)
			+ "&client_id=" + enc(Config.INSTANCE.clientId.trim()));
		applyToken(o);
	}

	private static JsonObject tokenRequest(String form) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form)).build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new IllegalStateException("Token HTTP " + res.statusCode() + ": " + res.body());
		return JsonParser.parseString(res.body()).getAsJsonObject();
	}

	private static void applyToken(JsonObject o) {
		accessToken = o.get("access_token").getAsString();
		accessExpiresAt = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L - 60_000L;
		if (o.has("refresh_token")) refreshToken = o.get("refresh_token").getAsString();
		saveAuth();
		status = "Verbunden. Warte auf Wiedergabe...";
	}

	private static void saveAuth() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("refresh_token", refreshToken);
			Files.writeString(AUTH_FILE, GSON.toJson(o));
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Konnte Auth nicht speichern", e);
		}
	}

	private static void loadAuth() {
		try {
			if (Files.exists(AUTH_FILE)) {
				JsonObject o = JsonParser.parseString(Files.readString(AUTH_FILE)).getAsJsonObject();
				refreshToken = o.get("refresh_token").getAsString();
				status = "Verbunden. Warte auf Wiedergabe...";
			}
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Konnte Auth nicht laden", e);
		}
	}

	// ---------------- Polling ----------------

	private static void pollSafe() {
		try {
			poll();
		} catch (Exception e) {
			SLyricsClient.LOGGER.debug("Spotify Poll Fehler", e);
			status = "Spotify nicht erreichbar";
		}
	}

	private static void poll() throws Exception {
		if (refreshToken == null || !Config.INSTANCE.enabled) return;
		if (System.currentTimeMillis() < pausedUntil) return;
		if (accessToken == null || System.currentTimeMillis() > accessExpiresAt) refresh();

		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
			.header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8)).GET().build();
		long sent = System.nanoTime();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		int code = res.statusCode();
		if (code == 429) {
			pausedUntil = System.currentTimeMillis() + res.headers().firstValueAsLong("Retry-After").orElse(5) * 1000L;
			return;
		}
		if (code == 401) {
			accessToken = null;
			return;
		}
		if (code == 204 || code != 200) {
			playing = false;
			if (code == 204) status = "Spotify spielt gerade nichts";
			return;
		}
		JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
		if (!root.has("item") || root.get("item").isJsonNull()
			|| !"track".equals(root.has("currently_playing_type") ? root.get("currently_playing_type").getAsString() : "track")) {
			playing = false;
			status = "Kein Song (Podcast/Werbung)";
			return;
		}
		JsonObject item = root.getAsJsonObject("item");
		String id = item.get("id").isJsonNull() ? item.get("name").getAsString() : item.get("id").getAsString();
		Track t = new Track(id, item.get("name").getAsString(),
			item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString(),
			item.getAsJsonObject("album").get("name").getAsString(), item.get("duration_ms").getAsLong());

		// Latenz grob ausgleichen: halbe Request-Zeit
		long latencyMs = (System.nanoTime() - sent) / 2_000_000L;
		boolean isPlaying = root.get("is_playing").getAsBoolean();
		progressAtFetch = root.get("progress_ms").getAsLong() + (isPlaying ? latencyMs : 0);
		fetchedAtNanos = System.nanoTime();
		playing = isPlaying;

		Track old = track;
		if (old == null || !old.id().equals(t.id())) {
			track = t;
			lyrics = List.of();
			lyricsLoading = true;
			status = t.artist() + " - " + t.title() + ": suche Lyrics...";
			EXEC.execute(() -> {
				List<LyricLine> l = LrcLib.fetch(t);
				if (track != null && track.id().equals(t.id())) {
					lyrics = l;
					lyricsLoading = false;
					status = l.isEmpty() ? t.artist() + " - " + t.title() + ": keine Lyrics gefunden" : "";
				}
			});
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String param(String query, String key) {
		if (query == null) return null;
		for (String p : query.split("&")) {
			int i = p.indexOf('=');
			if (i > 0 && p.substring(0, i).equals(key)) return java.net.URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
		}
		return null;
	}
}
