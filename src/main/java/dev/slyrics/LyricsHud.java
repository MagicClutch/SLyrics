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
		if (!c.enabled) return;

		int sw = g.guiWidth(), sh = g.guiHeight();
		float cx = sw * c.xPercent / 100f, cy = sh * c.yPercent / 100f;
		List<LyricLine> lines = Spotify.lyrics;

		if (c.hudMode && !lines.isEmpty()) drawPanel(g, mc.font, c, lines);
		if (c.anyWorld() && !lines.isEmpty()) return; // lyrics are shown as holograms in the world
		if (lines.isEmpty() || !Spotify.playing && Spotify.track == null) {
			String msg = c.showStatus ? Spotify.noLyricsMessage() : null; // only "No lyrics found", and only for 10 seconds
			if (msg != null) draw(g, mc.font, msg, cx, cy, Math.min(1f, c.scale) * 0.7f, 0xAAAAAA, 0.8f * Spotify.noLyricsAlpha(), c, 1f);
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
			draw(g, mc.font, text, cx, cy, c.scale, c.rgb(), alpha, c, 1f);
		}

		if (c.showNeighbors) {
			float small = c.scale * 0.6f;
			if (idx > 0 && !lines.get(idx - 1).text().isEmpty())
				draw(g, mc.font, lines.get(idx - 1).text(), cx, cy - 14 * c.scale, small, c.rgb(), 0.45f, c, 1f);
			if (idx + 1 < lines.size() && !lines.get(idx + 1).text().isEmpty())
				draw(g, mc.font, lines.get(idx + 1).text(), cx, cy + 14 * c.scale, small, c.rgb(), 0.45f, c, 1f);
		}
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
		if (prev.isEmpty() && cur.isEmpty() && next.isEmpty()) return;

		// how much of the current line has been sung: spread over the time the line is sung (not the pause after it)
		float progress = 0f;
		if (idx >= 0 && !cur.isEmpty()) {
			long start = lines.get(idx).timeMs();
			long nextStart = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 5000;
			long sing = Math.min(Math.max(1, nextStart - start), Math.max(1200L, cur.length() * 85L));
			progress = Math.max(0f, Math.min(1f, (pos - start) / (float) sing));
		}
		float[] box = panelBox(font, c, g.guiWidth(), g.guiHeight(), prev, cur, next, true);
		drawPanelBox(g, font, c, prev, cur, next, progress, box);
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
