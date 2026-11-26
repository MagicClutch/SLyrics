package dev.slyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One synced lyric line. wordMs: when each word of the text (split at spaces) starts, in song ms, if the lyrics come with word
 * marks (Enhanced LRC: {@code [00:12.34] <00:12.34> Hold <00:12.90> me}); null otherwise. endMs: the mark after the last word
 * (when it is sung to the end), or -1.
 */
public record LyricLine(long timeMs, String text, long[] wordMs, long endMs) {
	private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");
	private static final Pattern WORD_TS = Pattern.compile("<(\\d+):(\\d+)(?:[.:](\\d+))?>");

	public LyricLine(long timeMs, String text) {
		this(timeMs, text, null, -1);
	}

	private static long ms(Matcher m) {
		long ms = Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
		if (m.group(3) != null) ms += Long.parseLong((m.group(3) + "00").substring(0, 3));
		return ms;
	}

	/** Brackets like (Ooh) or [Chorus] are dropped, the words inside stay. */
	private static String clean(String s) {
		return s.replaceAll("[()\\[\\]{}]", "").replaceAll("\\s{2,}", " ").trim();
	}

	/** Parses synced LRC lyrics. Empty lines are kept (pauses). */
	public static List<LyricLine> parseLrc(String lrc) {
		List<LyricLine> out = new ArrayList<>();
		for (String raw : lrc.split("\\R")) {
			Matcher m = TS.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				times.add(ms(m));
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String rest = raw.substring(end);
			if (WORD_TS.matcher(rest).find()) {
				LyricLine l = enhanced(times.get(0), rest);
				if (times.size() == 1) out.add(l);
				else for (long t : times) out.add(new LyricLine(t, l.text())); // one text at several times: the word marks only fit one
				continue;
			}
			String text = clean(rest);
			for (long t : times) out.add(new LyricLine(t, text));
		}
		out.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
		return out;
	}

	/** A line with word marks: the text without them, and when each word starts. */
	private static LyricLine enhanced(long lineMs, String rest) {
		List<String> words = new ArrayList<>();
		List<Long> starts = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		Long pending = null, curStart = null;
		long lastTag = -1;
		boolean tagAfterText = false;
		Matcher wm = WORD_TS.matcher(rest);
		int i = 0;
		while (i < rest.length()) {
			if (wm.find(i) && wm.start() == i) {
				long t = ms(wm);
				if (cur.length() == 0) pending = t; // a mark inside a word (syllables) does not start a new word
				lastTag = t;
				tagAfterText = true;
				i = wm.end();
				continue;
			}
			char ch = rest.charAt(i++);
			if (Character.isWhitespace(ch)) {
				if (cur.length() > 0) {
					words.add(cur.toString());
					starts.add(curStart);
					cur.setLength(0);
					curStart = null;
				}
				continue;
			}
			if (cur.length() == 0) {
				curStart = pending;
				pending = null;
			}
			cur.append(ch);
			tagAfterText = false;
		}
		if (cur.length() > 0) {
			words.add(cur.toString());
			starts.add(curStart);
		}
		List<String> kept = new ArrayList<>();
		List<Long> keptStarts = new ArrayList<>();
		int known = 0;
		for (int k = 0; k < words.size(); k++) {
			String w = clean(words.get(k));
			if (w.isEmpty()) continue;
			kept.add(w);
			keptStarts.add(starts.get(k));
			if (starts.get(k) != null) known++;
		}
		String text = String.join(" ", kept);
		if (known == 0) return new LyricLine(lineMs, text);
		long endMs = tagAfterText ? lastTag : -1;
		long[] at = new long[kept.size()];
		// a word without its own mark (e.g. after a mark inside the word before) goes evenly between its neighbours' times
		for (int k = 0; k < at.length; k++) {
			if (keptStarts.get(k) != null) {
				at[k] = keptStarts.get(k);
				continue;
			}
			int p = k - 1, q = k + 1;
			while (q < at.length && keptStarts.get(q) == null) q++;
			long from = p >= 0 ? at[p] : lineMs;
			long to = q < at.length ? keptStarts.get(q) : endMs > from ? endMs : from + 400L * (q - p);
			at[k] = from + (to - from) * (k - p) / (q - p);
		}
		for (int k = 1; k < at.length; k++) at[k] = Math.max(at[k], at[k - 1]); // never backwards
		return new LyricLine(lineMs, text, at, endMs);
	}
}
