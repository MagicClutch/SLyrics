package dev.slyrics;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.List;

public class LyricsHud implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Config c = Config.INSTANCE;
		Minecraft mc = Minecraft.getInstance();
		float fadeBlack = AfkCamera.fadeAlpha();
		if (fadeBlack > 0f) g.fill(0, 0, g.guiWidth(), g.guiHeight(), Math.round(fadeBlack * 255) << 24);
		if (!c.enabled || HudMode.oneConfig) return; // with OneConfig its HUD editor draws the line, the panel and the player

		int sw = g.guiWidth(), sh = g.guiHeight();
		float cx = sw * c.xPercent / 100f, cy = sh * c.yPercent / 100f;
		List<LyricLine> lines = Spotify.lyrics;

		if (c.hudMode && !lines.isEmpty()) drawPanel(g, mc.font, c, lines);
		drawLine(g, mc.font, c, cx, cy, c.scale, sw / 2f - 6f);
	}

	/**
	 * The lyric line, centered at (cx, cy). k is the size factor (the line's scale; 1 when a surrounding transform already scales),
	 * half the room to each side of cx in the units drawn in.
	 */
	public static void drawLine(GuiGraphicsExtractor g, Font font, Config c, float cx, float cy, float k, float half) {
		List<LyricLine> lines = Spotify.lyrics;
		if (!c.lineMode && !lines.isEmpty()) return; // the lyric line is its own feature; the other modes show the lyrics elsewhere
		if (lines.isEmpty() || !Spotify.playing && Spotify.track == null) {
			String msg = c.showStatus ? Spotify.noLyricsMessage() : null; // only "No lyrics found", and only for 10 seconds
			if (msg != null) draw(g, font, msg, cx, cy, k * Math.min(1f, c.scale) / c.scale * 0.7f, 0xAAAAAA, 0.8f * Spotify.noLyricsAlpha(), c, 1f);
			return;
		}

		long pos = Spotify.positionMs() - c.offsetMs;
		int idx = -1;
		int lo = 0, hi = lines.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= pos) {
				idx = mid;
				lo = mid + 1;
			} else hi = mid - 1;
		}
		if (idx < 0) return;

		LyricLine cur = lines.get(idx);
		long sinceStart = pos - cur.timeMs();
		long next = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : cur.timeMs() + 5000;
		long len = Math.max(1, next - cur.timeMs());

		float alpha = 1f;
		if (c.fadeMs > 0) {
			alpha = Math.min(alpha, sinceStart / (float) c.fadeMs);
			alpha = Math.min(alpha, (next - pos) / (float) c.fadeMs + 0.0f);
			alpha = Math.max(0f, Math.min(1f, alpha));
		}

		if ((c.lineScroll || c.lineMaxWords > 0) && drawWords(g, font, c, lines, idx, pos, cx, cy, alpha, k, half)) return;

		String text = Emojis.decorate(cur.text(), " ", c);
		if (!text.isEmpty()) {
			if (c.typewriter) {
				// text is typed out over the first ~60% of the line duration (max 2.5s)
				float reveal = Math.min(len * 0.6f, 2500f);
				int shown = Math.min(text.length(), Math.max(1, Math.round(text.length() * Math.min(1f, sinceStart / reveal))));
				text = text.substring(0, shown);
				alpha = Math.max(alpha, Math.min(1f, sinceStart / 120f)); // do not fade while typing
				if (sinceStart < len - c.fadeMs) alpha = 1f;
			}
			draw(g, font, text, cx, cy, k, c.rgb(), alpha, c, 1f);
		}

		if (c.showNeighbors) {
			float small = k * 0.6f;
			if (idx > 0 && !lines.get(idx - 1).text().isEmpty())
				draw(g, font, lines.get(idx - 1).text(), cx, cy - 14 * k, small, c.rgb(), 0.45f, c, 1f);
			if (idx + 1 < lines.size() && !lines.get(idx + 1).text().isEmpty())
				draw(g, font, lines.get(idx + 1).text(), cx, cy + 14 * k, small, c.rgb(), 0.45f, c, 1f);
		}
	}

	private record Word(String text, long start) {}

	/** The same timing as the words in the world: the line's duration spread over its words by length. */
	private static long[] schedule(String[] words, long start, long duration) {
		double[] w = new double[words.length];
		double sum = 0;
		for (int i = 0; i < words.length; i++) {
			w[i] = words[i].length() + 1.5;
			sum += w[i];
		}
		double usable = Math.min(duration * 0.85, 420.0 * sum / 4.0);
		long[] t = new long[words.length];
		double acc = 0;
		for (int i = 0; i < words.length; i++) {
			t[i] = start - 90 + Math.round(acc / sum * usable);
			acc += w[i];
		}
		return t;
	}

	/** The words of lines from..to (inclusive) with the time each one is sung. */
	private static List<Word> words(List<LyricLine> lines, int from, int to, Config c, int[] firstOfCurrent, int current) {
		List<Word> out = new java.util.ArrayList<>();
		for (int li = Math.max(0, from); li <= Math.min(lines.size() - 1, to); li++) {
			String t = lines.get(li).text().trim();
			if (t.isEmpty()) continue;
			if (li == current) firstOfCurrent[0] = out.size();
			String[] ws = t.split("\\s+");
			long start = lines.get(li).timeMs();
			long end = li + 1 < lines.size() ? lines.get(li + 1).timeMs() : start + 5000;
			long[] at = schedule(ws, start, Math.max(400L, end - start));
			for (int i = 0; i < ws.length; i++) out.add(new Word(Emojis.decorate(ws[i], " ", c), at[i]));
		}
		return out;
	}

	/**
	 * The lyric line as words: either scrolling (the word being sung stays in the middle, the rest runs past it, across lines)
	 * or, for a line with more words than the limit, in parts that switch while it is sung. Returns false if the normal line should be drawn.
	 */
	private static boolean drawWords(GuiGraphicsExtractor g, Font font, Config c, List<LyricLine> lines, int idx, long pos, float cx, float cy, float alpha, float sz, float half) {
		int max = c.lineMaxWords;
		if (c.lineScroll) {
			int[] first = {0};
			List<Word> all = words(lines, idx - 3, idx + 4, c, first, idx);
			if (all.isEmpty()) return false;
			int k = first[0];
			for (int i = first[0]; i < all.size() && all.get(i).start() <= pos; i++) k = i;
			boolean started = all.get(k).start() <= pos;
			float f = 0f;
			if (k + 1 < all.size() && started) {
				long gap = Math.max(1, all.get(k + 1).start() - all.get(k).start());
				f = Math.max(0f, Math.min(1f, (pos - all.get(k).start()) / (float) Math.min(gap, 350L))); // glide quickly, also over a long pause
				f = f * f * (3f - 2f * f);
			}
			List<String> texts = new java.util.ArrayList<>();
			for (Word w : all) texts.add(w.text());
			int left = max > 0 ? (max - 1) / 2 : 1000, right = max > 0 ? max / 2 : 1000;
			drawStrip(g, font, c, texts, k, f, left, right, false, started, cx, cy, sz, 1f, half); // no fade between lines: it keeps running
			return true;
		}
		String[] ws = lines.get(idx).text().trim().split("\\s+");
		if (max <= 0 || ws.length <= max) return false;
		long start = lines.get(idx).timeMs();
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 5000;
		long[] at = schedule(ws, start, Math.max(400L, end - start));
		int cw = 0;
		for (int i = 0; i < ws.length; i++) if (at[i] <= pos) cw = i;
		int from = cw / max * max, to = Math.min(ws.length, from + max);
		List<String> part = new java.util.ArrayList<>();
		for (int i = from; i < to; i++) part.add(Emojis.decorate(ws[i], " ", c));
		drawStrip(g, font, c, part, cw - from, 0f, 1000, 1000, true, at[cw] <= pos, cx, cy, sz, alpha, half);
		return true;
	}

	/**
	 * Draws words in a row. rowCentered: the whole row is centered at cx. Otherwise word cur (moved on by f toward the next one)
	 * is centered at cx, and only left/right words around it are shown, fading in and out at the ends.
	 */
	private static void drawStrip(GuiGraphicsExtractor g, Font font, Config c, List<String> words, int cur, float f, int left, int right,
								  boolean rowCentered, boolean highlight, float cx, float cy, float scale, float alpha, float half) {
		int n = words.size();
		if (n == 0) return;
		MutableComponent[] comps = new MutableComponent[n];
		int[] wd = new int[n];
		float[] lx = new float[n];
		int space = font.width(" ");
		float x = 0;
		for (int i = 0; i < n; i++) {
			comps[i] = styled(words.get(i), c, true);
			wd[i] = font.width(comps[i]);
			lx[i] = x;
			x += wd[i] + space;
		}
		float anchor;
		if (rowCentered) anchor = (x - space) / 2f;
		else {
			float c0 = lx[cur] + wd[cur] / 2f, c1 = cur + 1 < n ? lx[cur + 1] + wd[cur + 1] / 2f : c0;
			anchor = c0 + (c1 - c0) * f;
		}
		int base = c.rgb();
		int hi = c.sweepMode ? sweepRgb(c) : base;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		for (int i = 0; i < n; i++) {
			float d = i - (cur + f);
			float vis = rowCentered ? 1f : d < 0 ? Math.max(0f, Math.min(1f, left + 1 + d)) : Math.max(0f, Math.min(1f, right + 1 - d));
			if (vis <= 0f) continue;
			float xs = (lx[i] - anchor) * scale, xe = (lx[i] + wd[i] - anchor) * scale;
			if (xs < -half || xe > half) continue;
			boolean now = i == cur;
			int a = Math.round(255 * alpha * vis * (now ? 1f : Math.max(0.35f, 0.78f - 0.1f * Math.abs(d))));
			if (a < 4) continue;
			int rgb = now && highlight ? hi : base;
			g.text(font, comps[i], Math.round(lx[i] - anchor), -font.lineHeight / 2, (a << 24) | rgb, c.shadow);
		}
		g.pose().popMatrix();
	}

	private static int sweepRgb(Config c) {
		try {
			return Integer.parseInt(c.sweepColor.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return 0xFF4D4D;
		}
	}

	/** Karaoke mode: previous / current / next line, the current line fills in while it is sung. */
	private static void drawPanel(GuiGraphicsExtractor g, Font font, Config c, List<LyricLine> lines) {
		Object[] st = panelState(c, lines);
		if (st == null) return;
		String prev = (String) st[0], cur = (String) st[1], next = (String) st[2];
		float[] box = panelBox(font, c, g.guiWidth(), g.guiHeight(), prev, cur, next, true);
		drawPanelBox(g, font, c, prev, cur, next, (Float) st[3], box);
	}

	/** The three lines and how much of the current one is sung {prev, cur, next, progress}, or null if there is nothing to show. */
	private static Object[] panelState(Config c, List<LyricLine> lines) {
		long pos = Spotify.positionMs() - c.offsetMs;
		int idx = -1;
		int lo = 0, hi = lines.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= pos) {
				idx = mid;
				lo = mid + 1;
			} else hi = mid - 1;
		}
		String prev = idx > 0 ? lines.get(idx - 1).text() : "";
		String cur = idx >= 0 ? lines.get(idx).text() : "";
		String next = idx + 1 < lines.size() ? lines.get(idx + 1).text() : "";
		if (idx < 0 && lines.size() > 0) next = lines.get(0).text(); // before the first line: show what comes first
		prev = Emojis.decorate(prev, " ", c);
		cur = Emojis.decorate(cur, " ", c);
		next = Emojis.decorate(next, " ", c);
		if (prev.isEmpty() && cur.isEmpty() && next.isEmpty()) return null;

		// how much of the current line has been sung: spread over the time the line is sung (not the pause after it)
		float progress = 0f;
		if (idx >= 0 && !cur.isEmpty()) {
			long start = lines.get(idx).timeMs();
			long nextStart = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 5000;
			long sing = Math.min(Math.max(1, nextStart - start), Math.max(1200L, cur.length() * 85L));
			progress = Math.max(0f, Math.min(1f, (pos - start) / (float) sing));
		}
		return new Object[]{prev, cur, next, progress};
	}

	/**
	 * The panel as OneConfig's HUD element draws it: the top-left corner is (0, 0) and nothing is scaled here (the HUD element does
	 * that). Without lyrics (or with sample=true) the sample text is used; with g == null nothing is drawn and only the size is returned. Returns the size {width, height} in those units, or null if there is nothing to draw.
	 */
	public static float[] drawPanelLocal(GuiGraphicsExtractor g, Font font, Config c, boolean sample) {
		String prev, cur, next;
		float progress = 0.5f;
		Object[] st = sample || Spotify.lyrics.isEmpty() ? null : panelState(c, Spotify.lyrics);
		if (st != null) {
			prev = (String) st[0]; cur = (String) st[1]; next = (String) st[2]; progress = (Float) st[3];
		} else {
			if (g != null && !sample && !Spotify.lyrics.isEmpty()) return null; // only empty lines: nothing to draw
			prev = SAMPLE[0]; cur = SAMPLE[1]; next = SAMPLE[2]; // the sample also gives the size when nothing is playing
		}
		float[] box = localBox(font, c, prev, cur, next);
		if (g != null) drawPanelBox(g, font, c, prev, cur, next, progress, box);
		return new float[]{box[3], box[4]};
	}

	private static float[] localBox(Font font, Config c, String prev, String cur, String next) {
		int lh = font.lineHeight;
		float side = 0.72f, pad = 4f;
		int wp = font.width(styled(prev, c, true)), wc = font.width(styled(cur, c, true)), wn = font.width(styled(next, c, true));
		float width = Math.max(Math.max(wp * side, wc), wn * side);
		float height = (prev.isEmpty() ? 0 : lh * side + 2) + lh + 2 + (next.isEmpty() ? 0 : lh * side + 2);
		return new float[]{0, 0, 1f, width + pad * 2, height + pad * 2};
	}

	static final String[] SAMPLE = {"The line before", "The line being sung now", "The line after"};

	/** The lyrics panel with sample text at its configured place (for the HUD editor): {x, y, scale, width, height} in screen pixels. */
	static float[] sampleBox(Font font, Config c, int guiW, int guiH) {
		return panelBox(font, c, guiW, guiH, SAMPLE[0], SAMPLE[1], SAMPLE[2], false);
	}

	static void drawSample(GuiGraphicsExtractor g, Font font, Config c, float[] box) {
		drawPanelBox(g, font, c, SAMPLE[0], SAMPLE[1], SAMPLE[2], 0.5f, box);
	}

	/**
	 * Where the panel goes and how big it is: {x, y, scale, width, height} (width and height in screen pixels, scale included).
	 * With dodge=true the panel moves out of the way of the media player, so the two never overlap.
	 */
	static float[] panelBox(Font font, Config c, int guiW, int guiH, String prev, String cur, String next, boolean dodge) {
		int lh = font.lineHeight;
		float side = 0.72f;
		int wp = font.width(styled(prev, c, true)), wc = font.width(styled(cur, c, true)), wn = font.width(styled(next, c, true));
		float pad = 4f;
		float width = Math.max(Math.max(wp * side, wc), wn * side);
		float height = (prev.isEmpty() ? 0 : lh * side + 2) + lh + 2 + (next.isEmpty() ? 0 : lh * side + 2);

		float scale = c.hudScale / 100f;
		float x0 = guiW * c.hudX / 100f, y0 = guiH * c.hudY / 100f;
		float room = guiW - x0 - 4f;
		float fit = Math.min(1f, room / ((width + pad * 2) * scale));
		scale *= Math.max(0.3f, fit);
		float w = (width + pad * 2) * scale, h = (height + pad * 2) * scale;
		x0 = Math.max(0, Math.min(x0, guiW - w));
		y0 = Math.max(0, Math.min(y0, guiH - h));
		if (dodge) {
			float[] pr = MediaPlayer.rect(guiW, guiH);
			if (pr != null && HudLayout.overlaps(x0, y0, w, h, pr)) {
				float below = pr[1] + pr[3] + 3f;
				if (below + h <= guiH) y0 = below;
				else y0 = Math.max(0, pr[1] - h - 3f);
			}
		}
		return new float[]{x0, y0, scale, w, h};
	}

	private static void drawPanelBox(GuiGraphicsExtractor g, Font font, Config c, String prev, String cur, String next, float progress, float[] box) {
		int cut = Math.round(cur.length() * progress);
		while (cut > 0 && cut < cur.length() && cur.charAt(cut) != ' ' && progress < 1f && cut < cur.length() - 1 && Character.isLetter(cur.charAt(cut))) cut++; // finish the word, no half words
		String sung = cur.substring(0, cut), rest = cur.substring(cut);

		int base = c.rgb();
		int sungRgb = c.sweepMode ? sweepRgb(c) : base;
		int restA = 150, sideA = 105;
		float side = 0.72f;
		int lh = font.lineHeight;
		MutableComponent cp = styled(prev, c, true), cn = styled(next, c, true);
		float pad = 4f;
		float scale = box[2];

		g.pose().pushMatrix();
		g.pose().translate(box[0], box[1]);
		g.pose().scale(scale, scale);
		if (c.background) g.fill(0, 0, Math.round(box[3] / scale), Math.round(box[4] / scale), 140 << 24);
		float y = pad;
		if (!prev.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(pad, y);
			g.pose().scale(side, side);
			g.text(font, cp, 0, 0, (sideA << 24) | base, c.shadow);
			g.pose().popMatrix();
			y += lh * side + 2;
		}
		if (!cur.isEmpty()) {
			MutableComponent cs = styled(sung, c, true);
			int ws = font.width(cs);
			if (!sung.isEmpty()) g.text(font, cs, Math.round(pad), Math.round(y), (255 << 24) | sungRgb, c.shadow);
			if (!rest.isEmpty()) g.text(font, styled(rest, c, true), Math.round(pad) + ws, Math.round(y), (restA << 24) | base, c.shadow);
		}
		y += lh + 2;
		if (!next.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(pad, y);
			g.pose().scale(side, side);
			g.text(font, cn, 0, 0, (sideA << 24) | base, c.shadow);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	public static MutableComponent styled(String s, Config c) {
		return styled(s, c, false);
	}

	/** With colored=true every emoji gets its own color (the rest keeps the color the text is drawn with). */
	public static MutableComponent styled(String s, Config c, boolean colored) {
		Style base = Style.EMPTY.withBold(c.bold);
		Identifier id = dev.slyrics.font.UserFont.isSystem(c.font) ? dev.slyrics.font.UserFont.ID : Identifier.tryParse(c.font);
		Style userStyle = id != null ? base.withFont(new FontDescription.Resource(id)) : base;
		Style emojiStyle = base.withFont(FontDescription.DEFAULT);
		MutableComponent out = Component.empty();
		StringBuilder run = new StringBuilder();
		boolean runEmoji = false;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (c.emojis && Emojis.isSprite(ch)) { // a real Minecraft icon (item, block, heart ...)
				if (run.length() > 0) {
					out.append(Component.literal(run.toString()).withStyle(runEmoji ? emojiStyle : userStyle));
					run.setLength(0);
				}
				out.append(Component.object(Emojis.spriteOf(ch)).withStyle(Style.EMPTY.withColor(0xFFFFFF)));
				runEmoji = false;
				continue;
			}
			boolean em = c.emojis && Emojis.isEmojiChar(ch, c);
			Integer own = colored && c.emojiColors && em ? Emojis.colorOf(ch) : null;
			if (own != null) { // a colored emoji is its own run
				if (run.length() > 0) {
					out.append(Component.literal(run.toString()).withStyle(runEmoji ? emojiStyle : userStyle));
					run.setLength(0);
				}
				out.append(Component.literal(String.valueOf(ch)).withStyle(emojiStyle.withColor(own)));
				runEmoji = false;
				continue;
			}
			if (i > 0 && em != runEmoji && run.length() > 0) {
				out.append(Component.literal(run.toString()).withStyle(runEmoji ? emojiStyle : userStyle));
				run.setLength(0);
			}
			runEmoji = em;
			run.append(ch);
		}
		if (run.length() > 0) out.append(Component.literal(run.toString()).withStyle(runEmoji ? emojiStyle : userStyle));
		return out;
	}

	/** Draws centered text at (cx, cy) with scaling. */
	public static void draw(GuiGraphicsExtractor g, Font font, String s, float cx, float cy, float scale,
							int rgb, float alpha, Config c, float unused) {
		int a = Math.round(alpha * 255);
		if (a < 4) return;
		MutableComponent comp = styled(s, c, true);
		int w = font.width(comp);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		int x = -w / 2, y = -font.lineHeight / 2;
		if (c.background) {
			g.fill(x - 3, y - 2, x + w + 3, y + font.lineHeight + 1, ((a * 140 / 255) << 24));
		}
		g.text(font, comp, x, y, (a << 24) | rgb, c.shadow);
		g.pose().popMatrix();
	}
}
