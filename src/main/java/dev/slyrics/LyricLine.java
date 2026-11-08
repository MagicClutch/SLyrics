package dev.slyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LyricLine(long timeMs, String text) {
	private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

	/** Parst synchronisierte LRC-Lyrics. Leere Zeilen bleiben erhalten (Pausen). */
	public static List<LyricLine> parseLrc(String lrc) {
		List<LyricLine> out = new ArrayList<>();
		for (String raw : lrc.split("\\R")) {
			Matcher m = TS.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long ms = Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
				if (m.group(3) != null) {
					String frac = (m.group(3) + "00").substring(0, 3);
					ms += Long.parseLong(frac);
				}
				times.add(ms);
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = raw.substring(end).trim();
			for (long t : times) out.add(new LyricLine(t, text));
		}
		out.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
		return out;
	}
}
