package dev.slyrics.pretty;

import dev.slyrics.Config;
import dev.slyrics.LyricLine;
import dev.slyrics.LyricsHud;
import dev.slyrics.Spotify;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Matrix3x2f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pretty Lyrics on the screen: the lyric line and the karaoke panel, and the word drawing both share. */
public final class PrettyHud {
	/**
	 * Minecraft text is small and pixel-sharp: at the base lift (1/60 of the font size) a word would move far less than
	 * one pixel. So the lift is shown this many times stronger; the Motion slider scales it from there.
	 */
	public static final float LIFT_GAIN = 4f;

	private static final Map<Long, PrettyWord> WORDS = new HashMap<>();
	private static int wordsVersion = -1;
	private static final PauseDots LINE_DOTS = new PauseDots(), PANEL_DOTS = new PauseDots();
	/** Which row is in the middle of the panel; follows the current row with a spring (barely overshoots, there in ~0.5 s). */
	private static final Spring FOCUS = new Spring(0.85, 1.4, -1);
	private static long focusLast;

	private PrettyHud() {}

	public static boolean on(Config c) {
		return c.prettyLyrics;
	}

	private static double clockMs;
	private static long clockNanos;

	/**
	 * Song position the lyrics are drawn at (with the offset). Spotify's position is corrected about once a second, which
	 * would make the sweep twitch; this clock runs on evenly and glides such small corrections out over about 0.4 s.
	 * Real jumps (seek, new song, pause) are taken over at once.
	 */
	public static long pos(Config c) {
		long raw = Spotify.positionMs() - c.offsetMs;
		long now = System.nanoTime();
		if (clockNanos == 0) clockMs = raw;
		else {
			double dt = (now - clockNanos) / 1e6;
			if (Spotify.playing) clockMs += dt;
			double err = raw - clockMs;
			if (Math.abs(err) > 350 || dt > 500) clockMs = raw;
			else clockMs += err * Math.min(1.0, dt / 400.0);
		}
		clockNanos = now;
		return Math.round(clockMs);
	}

	/** The animation state of word w of lyric line li (kept until the lyrics or their times change). */
	public static PrettyWord word(int li, int w, String text, long start, long end, Config c) {
		if (wordsVersion != PrettyTimes.version()) {
			WORDS.clear();
			wordsVersion = PrettyTimes.version();
		}
		long key = ((long) li << 20) | (w & 0xFFFFF);
		PrettyWord pw = WORDS.get(key);
		if (pw == null || pw.n != Math.max(1, text.length())) {
			pw = new PrettyWord(text.length(), emphasized(text, end - start, c));
			WORDS.put(key, pw);
		}
		return pw;
	}

	/** A held note: long enough, and short enough to animate letter by letter. */
	public static boolean emphasized(String text, long durMs, Config c) {
		int len = text.codePointCount(0, text.length());
		return c.prettyLetters && durMs >= c.prettyEmphasisMs && len >= 2 && len <= 12;
	}

	public static float progress(long pos, long start, long end) {
		return PrettyWord.clamp((pos - start) / (float) Math.max(1, end - start));
	}

	static int hex(String s, int def) {
		try {
			return Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static int argb(float a, int rgb) {
		int v = Math.round(PrettyWord.clamp(a) * 255);
		return v < 6 ? 0 : (v << 24) | (rgb & 0xFFFFFF); // tiny alpha would be drawn opaque by the font renderer
	}

	// ------------------------------------------------------------------ one word

	/**
	 * Draws one word with its top-left corner at (x, y): it grows and lifts around the middle of its baseline, glows, and the
	 * sung color sweeps over it from left to right with a soft edge. With letter ripple on, every letter also hops and grows
	 * while the sweep passes it (held notes more, with their own glow). t = progress of the word (0..1), alpha = row opacity.
	 */
	public static void drawWord(GuiGraphicsExtractor g, Font font, Config c, String s, PrettyWord pw, float t, float x, float y, float alpha) {
		if (s.isEmpty() || alpha < 0.02f) return;
		int lh = font.lineHeight;
		float motion = c.prettyMotion / 100f, glowK = c.prettyGlow / 100f;
		float unsung = c.prettyUnsung / 100f, sung = c.prettySung / 100f;
		int base = c.rgb(), hi = hex(c.sweepColor, 0xFFFFFF), glowRgb = hex(c.glowColor, 0xFFFFFF);
		MutableComponent comp = LyricsHud.styled(s, c, true);
		int w = font.width(comp);
		float edge = edgeWidth(c, lh);
		float litTo = -edge + (w + edge) * PrettyWord.clamp(t); // where the fully lit part ends, in the word's units

		boolean letters = c.prettyLetters && pw.n == s.length() && s.length() > 1;
		// the whole word: held notes leave the movement to their letters
		float sc = pw.emph && letters ? 1f : 1f + (pw.scale() - 1f) * motion;
		float lift = pw.emph && letters ? 0f : pw.lift() * lh * LIFT_GAIN * motion;
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + lh);
		g.pose().scale(sc, sc);
		g.pose().translate(-w / 2f, -lh + lift);
		float[] ws = charWidths(font, c, s);
		if (!pw.emph || !letters) PrettyGlow.behind(g, ws, lh, pw.glow() * glowK, false, alpha, glowRgb);
		if (!letters) {
			text(g, font, comp, 0, 0, argb(alpha * unsung, base), c.shadow);
			sweep(g, font, comp, w, litTo, edge, alpha * sung, hi);
			g.pose().popMatrix();
			return;
		}
		int n = s.length();
		float[] xs = new float[n], lt = new float[n], gt = new float[n];
		float ax = 0;
		for (int i = 0; i < n; i++) {
			xs[i] = ax;
			ax += ws[i];
		}
		PrettyWord.letterProgress(litTo, edge, xs, ws, w, t, lt, gt);
		pw.letters(lt, gt);
		for (int i = 0; i < n; i++) {
			MutableComponent ch = LyricsHud.styled(s.substring(i, i + 1), c, true);
			float cw = ws[i], ls = 1f + (pw.letterScale(i) - 1f) * motion;
			g.pose().pushMatrix();
			g.pose().translate(xs[i] + cw / 2f, lh);
			g.pose().scale(ls, ls);
			g.pose().translate(-cw / 2f, -lh + pw.letterLift(i) * lh * LIFT_GAIN * motion);
			if (pw.emph) PrettyGlow.behind(g, new float[]{cw}, lh, pw.letterGlow(i) * glowK, true, alpha, glowRgb);
			text(g, font, ch, 0, 0, argb(alpha * unsung, base), c.shadow);
			sweep(g, font, ch, cw, litTo - xs[i], edge, alpha * sung, hi);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	private static void text(GuiGraphicsExtractor g, Font font, MutableComponent comp, float x, float y, int col, boolean shadow) {
		if (col == 0) return;
		if (x == 0 && y == 0) {
			g.text(font, comp, 0, 0, col, shadow);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.text(font, comp, 0, 0, col, shadow);
		g.pose().popMatrix();
	}

	/**
	 * Width of the soft sweep edge in font units: the same for short and long words, so every word sweeps equally softly.
	 * The default (20) is about 0.9 of the letter height.
	 */
	public static float edgeWidth(Config c, float em) {
		return c.prettyEdge / 20f * 0.882f * em;
	}

	private static float[] charWidths(Font font, Config c, String s) {
		float[] w = new float[s.length()];
		for (int i = 0; i < w.length; i++) w[i] = font.width(LyricsHud.styled(s.substring(i, i + 1), c, true));
		return w;
	}

	/**
	 * The sung color over the text (top-left at 0, 0, width w): fully on up to litTo, then fading out smoothly over the soft
	 * edge (both in the text's units). The light is cut with the scissor in screen pixels: one cut for the lit part and 1-pixel
	 * strips for the edge, so it moves on smoothly instead of letter by letter.
	 */
	private static void sweep(GuiGraphicsExtractor g, Font font, MutableComponent comp, float w, float litTo, float edge, float alpha, int rgb) {
		if (alpha < 0.02f || w <= 0 || litTo + edge <= 0) return;
		int lh = font.lineHeight;
		if (litTo >= w) {
			text(g, font, comp, 0, 0, argb(alpha, rgb), false);
			return;
		}
		Matrix3x2f m = new Matrix3x2f(g.pose());
		float k = m.m00(); // no rotation on the screen: x and y map separately
		float sx0 = m.m20(), sx1 = k * w + m.m20();
		int y0 = (int) Math.floor(m.m11() * -2f + m.m21()), y1 = (int) Math.ceil(m.m11() * (lh + 2f) + m.m21());
		float lit = sx0 + k * litTo, fade = sx0 + k * (litTo + edge);
		int full = (int) Math.floor(lit);
		if (full > sx0 - 2) clipped(g, font, comp, (int) Math.floor(sx0) - 3, y0, full, y1, argb(alpha, rgb));
		float span = Math.max(0.001f, fade - lit);
		int step = Math.max(1, (int) Math.ceil(span / 48f)); // at most ~48 strips
		for (int x = Math.max(full, (int) Math.floor(sx0) - 3); x < Math.ceil(fade) && x < sx1 + 3; x += step) {
			float b = PrettyWord.clamp((fade - (x + step / 2f)) / span);
			if (b > 0.02f) clipped(g, font, comp, x, y0, x + step, y1, argb(alpha * b, rgb));
		}
	}

	/** Draws the text only inside the screen rectangle (x0, y0)-(x1, y1) (screen units, not the current transform). */
	private static void clipped(GuiGraphicsExtractor g, Font font, MutableComponent comp, int x0, int y0, int x1, int y1, int col) {
		if (col == 0 || x1 <= x0) return;
		g.pose().pushMatrix();
		g.pose().identity();
		g.enableScissor(x0, y0, x1, y1);
		g.pose().popMatrix();
		g.text(font, comp, 0, 0, col, false);
		g.disableScissor();
	}

	// ------------------------------------------------------------------ the lyric line

	/**
	 * The lyric line in Pretty Lyrics style, centered at (cx, cy), k = size factor, half = room to each side. Returns false if
	 * the line should be drawn the normal way (scroll or part mode, which draw their words through {@link #drawWord} themselves).
	 */
	public static boolean drawLine(GuiGraphicsExtractor g, Font font, Config c, List<LyricLine> lines, float cx, float cy, float k, float half) {
		PrettyTimes.ensure(lines, c);
		long pos = pos(c);
		int r = PrettyTimes.rowAt(pos);
		if (r < 0) return true;
		PrettyTimes.Row row = PrettyTimes.rows().get(r);
		if (row.pause()) {
			LINE_DOTS.update(row.start(), row.end(), pos);
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().scale(k, k);
			LINE_DOTS.draw(g, 0, 0, font.lineHeight * 0.7f, 1f, hex(c.glowColor, 0xFFFFFF), c.prettyGlow / 100f, true);
			g.pose().popMatrix();
			return true;
		}
		if (c.lineScroll || c.lineMaxWords > 0) return false;
		PrettyTimes.Line ln = PrettyTimes.line(row.line());
		if (ln == null || ln.words.length == 0) return true;
		float alpha = PrettyWord.clamp((pos - row.start() + 90) / 150f) * PrettyWord.clamp((row.end() + 250 - pos) / 250f);
		int n = ln.words.length;
		String[] ws = new String[n];
		int[] wd = new int[n];
		int space = font.width(" ");
		float total = space * (n - 1);
		for (int i = 0; i < n; i++) {
			ws[i] = dev.slyrics.Emojis.decorate(ln.words[i], " ", c);
			wd[i] = font.width(LyricsHud.styled(ws[i], c, true));
			total += wd[i];
		}
		float kk = total * k > 2 * half && total > 0 ? 2 * half / total : k; // a long line shrinks to fit
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(kk, kk);
		float x = -total / 2f, y = -font.lineHeight / 2f;
		for (int i = 0; i < n; i++) {
			PrettyWord pw = word(row.line(), i, ws[i], ln.start[i], ln.end[i], c);
			float t = progress(pos, ln.start[i], ln.end[i]);
			pw.update(t);
			drawWord(g, font, c, ws[i], pw, t, x, y, alpha);
			x += wd[i] + space;
		}
		g.pose().popMatrix();
		return true;
	}

	// ------------------------------------------------------------------ the karaoke panel

	/** Height of the panel's content (three rows) in font units. */
	public static float panelContentHeight(Font font) {
		return font.lineHeight * (1f + 2f * 0.72f) + 6f;
	}

	/**
	 * The karaoke panel in Pretty Lyrics style: the rows scroll smoothly, the one being sung sits in the middle at full size and
	 * brightness, the others are smaller and dimmer. box = {x, y, scale, width, height} as LyricsHud works it out.
	 */
	public static void drawPanel(GuiGraphicsExtractor g, Font font, Config c, List<LyricLine> lines, float[] box) {
		PrettyTimes.ensure(lines, c);
		List<PrettyTimes.Row> rows = PrettyTimes.rows();
		if (rows.isEmpty()) return;
		long pos = pos(c);
		int r = PrettyTimes.rowAt(pos);
		float target = r;
		long now = System.nanoTime();
		float dt = focusLast == 0 ? 0f : Math.min(0.1f, (now - focusLast) / 1e9f);
		focusLast = now;
		if (dt == 0f || Math.abs(FOCUS.pos() - target) > 2.5f) FOCUS.set(target); // a jump (seek, new song): no long scroll
		FOCUS.to(target);
		FOCUS.step(dt);
		float f = FOCUS.pos();

		int lh = font.lineHeight;
		float side = 0.72f, pad = 4f, scale = box[2];
		float step = lh * 0.86f + 2f;
		float centerY = pad + lh * side + 2f + lh / 2f;
		int glowRgb = hex(c.glowColor, 0xFFFFFF);

		g.pose().pushMatrix();
		g.pose().translate(box[0], box[1]);
		g.pose().scale(scale, scale);
		if (c.background) g.fill(0, 0, Math.round(box[3] / scale), Math.round(box[4] / scale), 140 << 24);
		int from = (int) Math.floor(f) - 2, to = (int) Math.ceil(f) + 2;
		for (int ri = Math.max(0, from); ri <= Math.min(rows.size() - 1, to); ri++) {
			float d = ri - f, ad = Math.abs(d);
			if (ad > 1.6f) continue;
			float vis = ad <= 1f ? 1f : 1f - (ad - 1f) / 0.6f;
			float near = 1f - Math.min(1f, ad);
			float sc = 1f - (1f - side) * Math.min(1f, ad);
			float alpha = (0.5f + 0.5f * near) * vis;
			if (c.prettySoftNeighbors) alpha *= 1f - Math.min(0.24f, 0.06f * ad);
			PrettyTimes.Row row = rows.get(ri);
			g.pose().pushMatrix();
			g.pose().translate(pad, centerY + d * step);
			g.pose().scale(sc, sc);
			if (row.pause()) {
				if (ri == r) {
					PANEL_DOTS.update(row.start(), row.end(), pos);
					PANEL_DOTS.draw(g, 0, 0, lh * 0.6f, vis, glowRgb, c.prettyGlow / 100f, false);
				} else PauseDots.drawIdle(g, 0, 0, lh * 0.6f, alpha);
			} else {
				drawRow(g, font, c, row, pos, alpha);
			}
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	/** A lyric row: every word follows its own times, so a row that is not sung shows its words at rest (dim, or lit when past). */
	private static void drawRow(GuiGraphicsExtractor g, Font font, Config c, PrettyTimes.Row row, long pos, float alpha) {
		PrettyTimes.Line ln = PrettyTimes.line(row.line());
		if (ln == null) return;
		int space = font.width(" ");
		float x = 0, y = -font.lineHeight / 2f;
		for (int i = 0; i < ln.words.length; i++) {
			String s = dev.slyrics.Emojis.decorate(ln.words[i], " ", c);
			int w = font.width(LyricsHud.styled(s, c, true));
			float t = progress(pos, ln.start[i], ln.end[i]);
			PrettyWord pw = word(row.line(), i, s, ln.start[i], ln.end[i], c);
			pw.update(t);
			drawWord(g, font, c, s, pw, t, x, y, alpha);
			x += w + space;
		}
	}
}
