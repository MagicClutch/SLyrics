package dev.slyrics;

import java.util.concurrent.TimeUnit;

/**
 * The "Others" music source: the song is read from the media player that plays it on this computer (the Apple Music web
 * player in a browser, YouTube Music, Cider, VLC, ...), since services like Apple Music have no public "what is playing
 * right now" API. This class only picks the right implementation for the operating system: {@link LinuxMedia} (MPRIS
 * over D-Bus) or {@link WindowsMedia} (Windows media controls).
 */
public final class OtherPlayers {

	/** After a seek the position is not read for a moment, so the bar does not jump back to the old place. */
	private static volatile long holdUntil;

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

	private OtherPlayers() {}

	/** Sends PlayPause / Next / Previous to the active player. */
	static void control(String method) {
		control(method, null, null);
	}

	/** Jumps to a position: MPRIS Seek takes an offset in microseconds from the current position. */
	static void seek(long ms) {
		if (WINDOWS) { // the Windows media controls take an absolute position in 100 ns ticks
			holdUntil = System.currentTimeMillis() + 1500;
			control("Seek", null, Long.toString(Math.max(0, ms) * 10_000L));
			return;
		}
		long offsetUs = (ms - Spotify.positionMs()) * 1000L;
		holdUntil = System.currentTimeMillis() + 1500;
		control("Seek", "x", Long.toString(offsetUs));
	}

	private static void control(String method, String sig, String arg) {
		if (WINDOWS) {
			Spotify.executor().execute(() -> {
				try {
					WindowsMedia.command(method, arg);
					if (method.equals("PlayPause")) Spotify.setProgress(Spotify.positionMs(), !Spotify.playing);
				} catch (Exception e) {
					SLyricsClient.LOGGER.debug("Player control error", e);
					MediaPlayer.notice(WindowsMedia.active() == null ? "No media player found" : "Could not control the player");
				}
			});
			return;
		}
		LinuxMedia.control(method, sig, arg);
	}

	public static void start() {
		Spotify.executor().scheduleWithFixedDelay(OtherPlayers::pollSafe, 1, 1, TimeUnit.SECONDS);
	}

	static void pollSafe() {
		try {
			poll();
		} catch (Exception e) {
			SLyricsClient.LOGGER.debug("Other-player poll error", e);
		}
	}

	private static void poll() throws Exception {
		if (System.currentTimeMillis() < holdUntil) return;
		Config c = Config.INSTANCE;
		boolean auto = "Auto".equals(c.musicSource);
		if (!c.enabled || !(auto || "Others".equals(c.musicSource))) return;
		if (WINDOWS) {
			WindowsMedia.poll(c, auto);
			return;
		}
		if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) {
			if (auto) return;
			Spotify.status = "Others: only supported on Linux and Windows for now";
			return;
		}
		LinuxMedia.poll(c, auto);
	}

	/** Removes what web players add to titles: invisible marks, " - YouTube", " - Apple Music", "(Official Video)", "[Lyrics]". */
	static String clean(String s) {
		String t = s.replaceAll("[\\u200E\\u200F\\u202A-\\u202E]", "").trim();
		t = t.replaceAll("(?i)\\s*[-\u2013\u2014|]\\s*(youtube music|youtube|apple music|soundcloud|tidal|deezer)\\s*$", "");
		t = t.replaceAll("(?i)\\s*[\\(\\[](official[^\\)\\]]*|lyrics?[^\\)\\]]*|audio[^\\)\\]]*|video[^\\)\\]]*|hd|hq|4k)[\\)\\]]", "");
		return t.trim();
	}
}
