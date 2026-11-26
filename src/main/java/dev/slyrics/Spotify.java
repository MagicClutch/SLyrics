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

/** Spotify login (PKCE, no client secret needed) and polling of the currently playing song. */
public final class Spotify {
	public static final int PORT = 8888;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPE = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
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
	/** Permissions of the current login (from Spotify's token answer); empty = not known yet. */
	private static volatile String grantedScope = "";
	private static boolean loginHinted;
	private static volatile long pausedUntil;
	private static String verifier;
	/** The local server that waits for Spotify's answer during a login; null when no login is waiting. */
	private static HttpServer loginServer;
	/** The Client ID the current login (or waiting login) belongs to; a different or empty ID in the config ends it. */
	private static volatile String boundClientId = "";

	// ---- public state for the HUD ----
	public static volatile Track track;
	public static volatile List<LyricLine> lyrics = List.of();
	public static volatile boolean lyricsLoading;
	public static volatile boolean playing;
	/** Cover image of the current song (URL, or file:// from a local player); empty if unknown. */
	public static volatile String coverUrl = "";
	/** Auto source: true while the song on screen comes from Spotify (false: from another player). */
	public static volatile boolean fromSpotify;
	/** Auto source: another player is playing right now (Spotify then only takes over when it plays itself). */
	static volatile boolean othersActive;
	/** When the lyrics search for the current song came back empty (ms), for the short "No lyrics found" message. */
	private static volatile long noLyricsSince;
	private static volatile long progressAtFetch;
	private static volatile long fetchedAtNanos;
	public static volatile String status = "Not connected. Use /slyrics login";

	private Spotify() {}

	/** Current song position in ms (interpolated between polls). */
	public static long positionMs() {
		long p = progressAtFetch;
		if (playing) p += (System.nanoTime() - fetchedAtNanos) / 1_000_000L;
		return p;
	}

	public static void start() {
		loadAuth();
		checkClientId();
		EXEC.scheduleWithFixedDelay(Spotify::pollSafe, 1, 1, TimeUnit.SECONDS);
	}

	/**
	 * A login only belongs to the Client ID it was made with. When the ID in the config is emptied (or replaced by
	 * another one), everything of the old login goes: the saved token, the session and a login that is still waiting.
	 */
	public static void checkClientId() {
		String now = Config.INSTANCE.clientId.trim();
		boolean changed = !now.equals(boundClientId);
		boolean hadLogin = refreshToken != null || accessToken != null || loginServer != null;
		boundClientId = now;
		if (hadLogin && (changed || now.isEmpty())) {
			SLyricsClient.LOGGER.info("Spotify Client ID {}: the old login was removed", now.isEmpty() ? "removed" : "changed");
			logout();
		}
	}

	// ---------------- Login ----------------

	public static String beginLogin(Consumer<String> onDone) throws Exception {
		String clientId = Config.INSTANCE.clientId.trim();
		if (clientId.isEmpty()) throw new IllegalStateException("No client ID set");
		byte[] rnd = new byte[64];
		new SecureRandom().nextBytes(rnd);
		verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
		String challenge = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		String state = Long.toHexString(new SecureRandom().nextLong());

		stopLoginServer(); // a login that is still waiting would keep the port busy ("Address already in use")
		boundClientId = clientId;
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
		server.createContext("/callback", ex -> {
			String q = ex.getRequestURI().getQuery();
			String code = param(q, "code");
			boolean ok = code != null && state.equals(param(q, "state"));
			byte[] body = (ok ? "<h2>SLyrics connected. You can close this window.</h2>"
				: "<h2>Login failed.</h2>").getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body);
			ex.close();
			stopLoginServer(server, false);
			EXEC.execute(() -> {
				try {
					if (!ok) throw new IllegalStateException("Invalid response from Spotify");
					exchangeCode(code);
					onDone.accept("Spotify connected!");
				} catch (Exception e) {
					SLyricsClient.LOGGER.warn("Login error", e);
					onDone.accept("Login failed: " + e.getMessage());
				}
			});
		});
		server.setExecutor(EXEC);
		server.start();
		synchronized (Spotify.class) {
			loginServer = server;
		}
		EXEC.schedule(() -> stopLoginServer(server, true), 5, TimeUnit.MINUTES);

		return "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId)
			+ "&scope=" + enc(SCOPE) + "&redirect_uri=" + enc(REDIRECT)
			+ "&code_challenge_method=S256&code_challenge=" + challenge + "&state=" + state;
	}

	/** Stops the login server if one is waiting, and frees the port before returning. */
	private static synchronized void stopLoginServer() {
		if (loginServer != null) stopLoginServer(loginServer, true);
	}

	/**
	 * Stops this login server (the field is only cleared if it is still the current one). now = false from inside the
	 * server's own request: stop() waits for running requests, so it then has to happen afterwards on another thread.
	 */
	private static synchronized void stopLoginServer(HttpServer server, boolean now) {
		if (loginServer == server) loginServer = null;
		if (now) server.stop(0);
		else EXEC.execute(() -> server.stop(0));
	}

	/** Forgets the Spotify login completely: token file, session, permissions and a login that is still waiting. */
	public static void logout() {
		stopLoginServer();
		accessToken = null;
		accessExpiresAt = 0;
		refreshToken = null;
		grantedScope = "";
		verifier = null;
		loginHinted = false;
		pausedUntil = 0;
		if (fromSpotify) {
			track = null;
			lyrics = List.of();
			playing = false;
			coverUrl = "";
			fromSpotify = false;
		}
		status = "Not connected. Use /slyrics login";
		try {
			Files.deleteIfExists(AUTH_FILE);
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not delete the saved Spotify login", e);
		}
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
		if (o.has("scope")) grantedScope = o.get("scope").getAsString();
		saveAuth();
		status = "Connected. Waiting for playback...";
	}

	private static void saveAuth() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("refresh_token", refreshToken);
			Files.writeString(AUTH_FILE, GSON.toJson(o));
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not save auth", e);
		}
	}

	private static void loadAuth() {
		try {
			if (Files.exists(AUTH_FILE)) {
				JsonObject o = JsonParser.parseString(Files.readString(AUTH_FILE)).getAsJsonObject();
				refreshToken = o.get("refresh_token").getAsString();
				boundClientId = Config.INSTANCE.clientId.trim();
				status = "Connected. Waiting for playback...";
			}
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not load auth", e);
		}
	}

	// ---------------- Polling ----------------

	private static String loggedStatus = "", loggedError = "";
	/**
	 * Spotify limits how often an app may ask (many clients asking every second at once got it blocked for hours). So:
	 * every second while a song plays, every 5 s when nothing plays, right at the end of a song so the next one is picked
	 * up at once, and not at all while no world is open.
	 */
	private static final long POLL_PLAYING_MS = 1000, POLL_IDLE_MS = 5000;
	private static volatile long nextPollAt;

	/** Asks Spotify on the next tick regardless of the usual pace (after the player buttons). */
	private static void pollNow() {
		nextPollAt = 0;
		pollSafe();
	}

	private static void pollSafe() {
		try {
			checkClientId(); // also catches an ID emptied in slyrics.json or by a reset
		} catch (Throwable t) {
			SLyricsClient.LOGGER.warn("Client ID check failed", t);
		}
		long t0 = System.currentTimeMillis();
		if (t0 < nextPollAt) return;
		if (net.minecraft.client.Minecraft.getInstance().player == null && !LyricsWorld.lobbyWanted()) return; // nothing to show yet (unless lyrics show in the OneClient lobby)
		nextPollAt = t0 + POLL_IDLE_MS;
		try {
			poll();
		} catch (Exception e) {
			String err = e.getClass().getSimpleName() + ": " + e.getMessage();
			if (!err.equals(loggedError)) { // once per kind of error, not every second
				loggedError = err;
				SLyricsClient.LOGGER.warn("Spotify request failed: {}", err);
			}
			status = "Spotify unreachable";
		} catch (Throwable t) { // must not escape: the scheduler would silently stop polling for good
			SLyricsClient.LOGGER.error("Spotify polling hit an unexpected error", t);
			status = "Spotify error: " + t;
		}
		if (playing && track != null) { // next poll: the usual pace, or just after this song ends
			long left = track.durationMs() - positionMs();
			nextPollAt = System.currentTimeMillis() + Math.max(500, Math.min(POLL_PLAYING_MS, left + 300));
		}
		String now = status;
		if (now != null && !now.equals(loggedStatus)) { // every change of state once, so a log shows what happened
			loggedStatus = now;
			SLyricsClient.LOGGER.info("Spotify: {} (source {}, logged in {}, playing {}, lyrics {})",
				now.isEmpty() ? "lyrics ready" : now, Config.INSTANCE.musicSource, refreshToken != null, playing, lyrics.size());
		}
	}

	private static void poll() throws Exception {
		String source = Config.INSTANCE.musicSource;
		if (refreshToken == null || !Config.INSTANCE.enabled || !("Spotify".equals(source) || "Auto".equals(source))) return;
		if (System.currentTimeMillis() < pausedUntil) return;
		if (accessToken == null || System.currentTimeMillis() > accessExpiresAt) refresh();

		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
			.header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8)).GET().build();
		long sent = System.nanoTime();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		int code = res.statusCode();
		if (code == 429) {
			long wait = res.headers().firstValueAsLong("Retry-After").orElse(5);
			pausedUntil = System.currentTimeMillis() + wait * 1000L;
			status = "Spotify rate limit: asked to wait " + wait + " s";
			if (wait >= 60) chat("SLyrics: Spotify is limiting this app for about " + Math.round(wait / 60.0) + " min (too many requests). Lyrics come back by themselves after that.");
			return;
		}
		if (code == 401) {
			accessToken = null;
			status = "Spotify login expired, renewing...";
			return;
		}
		if (code == 204 || code != 200) {
			if (yieldToOthers()) return;
			playing = false;
			if (code == 204) status = "Spotify is not playing anything";
			else status = "Spotify answered " + code + ": " + res.body().replaceAll("\\s+", " ").trim(); // e.g. 403 when the account may not use this app
			return;
		}
		JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
		if (!root.has("item") || root.get("item").isJsonNull()
			|| !"track".equals(root.has("currently_playing_type") ? root.get("currently_playing_type").getAsString() : "track")) {
			if (yieldToOthers()) return;
			playing = false;
			status = "No song (podcast/ad)";
			return;
		}
		JsonObject item = root.getAsJsonObject("item");
		String id = item.get("id").isJsonNull() ? item.get("name").getAsString() : item.get("id").getAsString();
		Track t = new Track(id, item.get("name").getAsString(),
			item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString(),
			item.getAsJsonObject("album").get("name").getAsString(), item.get("duration_ms").getAsLong());

		// roughly compensate latency: half the request time
		long latencyMs = (System.nanoTime() - sent) / 2_000_000L;
		boolean isPlaying = root.get("is_playing").getAsBoolean();
		if (!isPlaying && yieldToOthers()) return;
		fromSpotify = true;
		progressAtFetch = root.get("progress_ms").getAsLong() + (isPlaying ? latencyMs : 0);
		fetchedAtNanos = System.nanoTime();
		playing = isPlaying;

		JsonObject album = item.getAsJsonObject("album");
		String cover = "";
		if (album.has("images") && album.getAsJsonArray("images").size() > 0) { // the images are listed large to small; the smallest is enough for a thumbnail
			var imgs = album.getAsJsonArray("images");
			cover = imgs.get(imgs.size() > 1 ? imgs.size() - 2 : 0).getAsJsonObject().get("url").getAsString();
		}
		coverUrl = cover;
		setTrack(t);
	}

	private static HttpResponse<String> send(String method, String path) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/" + path))
			.header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8))
			.method(method, HttpRequest.BodyPublishers.noBody()).build();
		return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
	}

	private static void chat(String msg) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
		});
	}

	/** The saved login was made before the player buttons existed and lacks the permission to control playback. */
	private static void needsLogin() {
		MediaPlayer.notice("Spotify: run /slyrics login again");
		if (loginHinted) return;
		loginHinted = true;
		var mc = net.minecraft.client.Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"SLyrics: the player buttons need one more Spotify permission. Run /slyrics login once more and approve it (Spotify Premium is required to control playback)."));
		});
	}

	/** Auto source: a paused Spotify must not push aside another player that is playing. */
	private static boolean yieldToOthers() {
		return "Auto".equals(Config.INSTANCE.musicSource) && othersActive;
	}

	/** The text for the screen when the current song has no lyrics: shown for 10 seconds, then null. */
	public static String noLyricsMessage() {
		return track == null || lyricsLoading || !lyrics.isEmpty() || System.currentTimeMillis() - noLyricsSince > NO_LYRICS_MS ? null : "No lyrics found";
	}

	/** 0..1, fading out at the end of the 10 seconds. */
	public static float noLyricsAlpha() {
		return Math.max(0f, Math.min(1f, (NO_LYRICS_MS - (System.currentTimeMillis() - noLyricsSince)) / 1000f));
	}

	/** Starts the 10 seconds again (called when joining a world). */
	public static void resetNoLyricsTimer() {
		noLyricsSince = System.currentTimeMillis();
	}

	private static final long NO_LYRICS_MS = 10_000L;

	static void seek(long ms) {
		pausedUntil = System.currentTimeMillis() + 2000; // polls must not bring the old position back before Spotify has jumped
		control("seek", ms);
	}

	/** Play / pause / next / previous on the Spotify account (needs Premium and the user-modify-playback-state scope). */
	static void control(String action) {
		control(action, 0);
	}

	static void control(String action, long arg) {
		EXEC.execute(() -> {
			try {
				if (refreshToken == null) {
					MediaPlayer.notice("Not logged in to Spotify");
					return;
				}
				if (accessToken == null || System.currentTimeMillis() > accessExpiresAt) refresh();
				if (!grantedScope.isEmpty() && !grantedScope.contains("user-modify-playback-state")) {
					needsLogin();
					return;
				}
				String path;
				String method = "PUT";
				switch (action) {
					case "play" -> path = "play";
					case "pause" -> path = "pause";
					case "next" -> { path = "next"; method = "POST"; }
					case "previous" -> { path = "previous"; method = "POST"; }
					case "seek" -> path = "seek?position_ms=" + Math.max(0, arg);
					default -> { return; }
				}
				HttpResponse<String> res = send(method, path);
				if (res.statusCode() == 401) { // expired token: get a new one and try once more
					refresh();
					res = send(method, path);
				}
				int code = res.statusCode();
				if (code == 401 || code == 403 && res.body().toLowerCase().contains("scope")) {
					SLyricsClient.LOGGER.warn("Spotify control refused: {} {}", code, res.body());
					needsLogin();
					return;
				}
				if (code == 403) MediaPlayer.notice("Spotify Premium is required");
				else if (code == 404) MediaPlayer.notice("No active Spotify device");
				else if (code == 429) MediaPlayer.notice("Spotify: too many requests");
				else if (code >= 400) MediaPlayer.notice("Spotify error " + code);
				else {
					if (action.equals("play") || action.equals("pause")) setProgress(positionMs(), action.equals("play"));
					else if (action.equals("seek")) setProgress(arg, playing);
					pausedUntil = System.currentTimeMillis() + 900; // let Spotify catch up before the next poll reads the old state
					EXEC.schedule(Spotify::pollNow, 1000, TimeUnit.MILLISECONDS);
				}
			} catch (Exception e) {
				SLyricsClient.LOGGER.debug("Spotify control error", e);
				MediaPlayer.notice("Spotify unreachable");
			}
		});
	}

	/** Makes t the current song (shared by all music sources) and looks up its lyrics when it changed. */
	static void setTrack(Track t) {
		Track old = track;
		if (old == null || !old.id().equals(t.id())) {
			track = t;
			lyrics = List.of();
			lyricsLoading = true;
			status = t.artist() + " - " + t.title() + ": searching lyrics...";
			EXEC.execute(() -> {
				List<LyricLine> l = LrcLib.fetch(t);
				if (track != null && track.id().equals(t.id())) {
					lyrics = l;
					lyricsLoading = false;
					if (l.isEmpty()) noLyricsSince = System.currentTimeMillis();
					status = l.isEmpty() ? t.artist() + " - " + t.title() + ": no lyrics found" : "";
				}
			});
		}
	}

	/** For other music sources: the song is at progressMs right now. */
	static void setProgress(long progressMs, boolean isPlaying) {
		progressAtFetch = progressMs;
		fetchedAtNanos = System.nanoTime();
		playing = isPlaying;
	}

	static ScheduledExecutorService executor() {
		return EXEC;
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
