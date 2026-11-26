package dev.slyrics.pretty;

import dev.slyrics.Config;
import dev.slyrics.LyricLine;

import java.util.ArrayList;
import java.util.List;

/**
 * When every word of the current lyrics is sung (start and end in song ms), and the rows Pretty Lyrics shows: one per line
 * with words, plus a pause row wherever the singing stops for a while. Worked out once per song (and when the settings change).
 */
public final class PrettyTimes {
	/** The words of one lyric line and their times. real = the times come from word marks in the lyrics, not an estimate. */
	public static final class Line {
		public final String[] words;
		public final long[] start, end;
		public final boolean real;

		Line(String[] words, long[] start, long[] end, boolean real) {
			this.words = words;
			this.start = start;
			this.end = end;
			this.real = real;
		}

		/** Index of the word being sung (or last sung) at pos, -1 before the first. */
		public int wordAt(long pos) {
			int k = -1;
			for (int i = 0; i < start.length; i++) if (start[i] <= pos) k = i;
			return k;
		}
	}

	/** A row on screen: a lyric line (line = its index in the lyrics) or a pause (line = -1), shown from start to end. */
	public record Row(boolean pause, int line, long start, long end) {}

	private static List<LyricLine> source;
	private static String settings = "";
	private static Line[] lines = new Line[0];
	private static List<Row> rows = List.of();
	private static int version;

	private PrettyTimes() {}

	/** Brings the times up to date for these lyrics. Call from the render thread before using the other methods. */
	public static void ensure(List<LyricLine> l, Config c) {
		String s = c.prettyWordTimes + "/" + c.prettyDots + "/" + c.prettyPauseMs;
		if (l == source && s.equals(settings)) return;
		source = l;
		settings = s;
		version++;
		lines = new Line[l.size()];
		for (int i = 0; i < l.size(); i++) {
			long ls = l.get(i).timeMs();
			long le = i + 1 < l.size() ? l.get(i + 1).timeMs() : ls + 5000;
			lines[i] = times(l.get(i), ls, Math.max(ls + 400, le), c.prettyWordTimes);
		}
		rows = buildRows(l, c);
	}

	/** Changes whenever the times are worked out again (so cached word animations can be dropped). */
	public static int version() {
		return version;
	}

	public static Line line(int i) {
		return i >= 0 && i < lines.length ? lines[i] : null;
	}

	public static List<Row> rows() {
		return rows;
	}

	/** The row shown at pos: the last one that has started, -1 before the first. */
	public static int rowAt(long pos) {
		int lo = 0, hi = rows.size() - 1, idx = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (rows.get(mid).start() <= pos) {
				idx = mid;
				lo = mid + 1;
			} else hi = mid - 1;
		}
		return idx;
	}

	/** Word times of one line: from the word marks if there are any (and they are wanted), otherwise spread by word length. */
	private static Line times(LyricLine line, long ls, long le, boolean useReal) {
		String t = line.text().trim();
		if (t.isEmpty()) return new Line(new String[0], new long[0], new long[0], false);
		String[] ws = t.split("\\s+");
		int n = ws.length;
		long[] start = new long[n], end = new long[n];
		long[] marks = line.wordMs();
		if (useReal && marks != null && marks.length == n) {
			for (int i = 0; i < n; i++) start[i] = marks[i];
			for (int i = 0; i < n; i++) {
				long e = i + 1 < n ? start[i + 1] : line.endMs() > start[i] ? line.endMs() : Math.min(le, start[i] + 1200);
				end[i] = Math.max(start[i] + 60, Math.min(e, le));
			}
			return new Line(ws, start, end, true);
		}
		// the same estimate the mod always used: weight = letters + 1.5, about 105 ms per weight, at most 85 % of the line
		double[] w = new double[n];
		double sum = 0;
		for (int i = 0; i < n; i++) {
			w[i] = ws[i].length() + 1.5;
			sum += w[i];
		}
		double usable = Math.min((le - ls) * 0.85, 420.0 * sum / 4.0);
		double acc = 0;
		for (int i = 0; i < n; i++) {
			start[i] = ls - 90 + Math.round(acc / sum * usable);
			acc += w[i];
			end[i] = ls - 90 + Math.round(acc / sum * usable);
		}
		// the last word of a line is often held: it may last until shortly before the next line (at most 2.5 s)
		end[n - 1] = Math.max(end[n - 1], Math.min(le - 150, start[n - 1] + 2500));
		return new Line(ws, start, end, false);
	}

	private static List<Row> buildRows(List<LyricLine> l, Config c) {
		List<Row> out = new ArrayList<>();
		List<Integer> sung = new ArrayList<>();
		for (int i = 0; i < l.size(); i++) if (lines[i].words.length > 0) sung.add(i);
		if (sung.isEmpty()) return out;
		long pauseMs = Math.max(1000, c.prettyPauseMs);
		long first = l.get(sung.get(0)).timeMs();
		if (c.prettyDots && first >= pauseMs) out.add(new Row(true, -1, 0, first)); // the intro
		for (int k = 0; k < sung.size(); k++) {
			int i = sung.get(k);
			long ls = l.get(i).timeMs();
			Line ln = lines[i];
			long lastEnd = ln.end[ln.end.length - 1];
			long next = k + 1 < sung.size() ? l.get(sung.get(k + 1)).timeMs() : Long.MAX_VALUE;
			if (c.prettyDots && next != Long.MAX_VALUE && next - lastEnd >= pauseMs) {
				out.add(new Row(false, i, ls, lastEnd));
				out.add(new Row(true, -1, lastEnd, next));
			} else {
				out.add(new Row(false, i, ls, next == Long.MAX_VALUE ? lastEnd + 2000 : next));
			}
		}
		return out;
	}
}
