package dev.slyrics.pretty;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Soft light for Pretty Lyrics: a round blob that fades out smoothly (gaussian), drawn tinted behind the letters. Many of them
 * along a word read as natural bloom, not as copies of the pixel font.
 */
public final class PrettyGlow {
	public static final int SIZE = 64;
	private static Identifier id;

	private PrettyGlow() {}

	/** The blob texture (made on first use; call from the render thread). */
	public static Identifier texture() {
		if (id != null) return id;
		NativeImage ni = new NativeImage(SIZE, SIZE, false);
		float half = SIZE / 2f;
		for (int y = 0; y < SIZE; y++)
			for (int x = 0; x < SIZE; x++) {
				float dx = (x + 0.5f - half) / half, dy = (y + 0.5f - half) / half;
				float d2 = dx * dx + dy * dy, d = (float) Math.sqrt(d2);
				float a = (float) Math.exp(-3.2f * d2) * PrettyWord.clamp((1f - d) / 0.3f); // gaussian, reaching 0 at the rim
				ni.setPixel(x, y, (Math.round(a * 255) << 24) | 0xFFFFFF);
			}
		id = Identifier.fromNamespaceAndPath("slyrics", "pretty_glow");
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "slyrics pretty glow", ni));
		return id;
	}

	/** One blob filling the box (x, y, w, h) in the current units. */
	public static void blob(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, int rgb) {
		int a = Math.round(PrettyWord.clamp(alpha) * 255);
		if (a < 2 || w <= 0 || h <= 0) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w / SIZE, h / SIZE);
		g.blit(RenderPipelines.GUI_TEXTURED, texture(), 0, 0, 0f, 0f, SIZE, SIZE, SIZE, SIZE, SIZE, SIZE, (a << 24) | (rgb & 0xFFFFFF));
		g.pose().popMatrix();
	}

	/**
	 * Glow behind a run of text whose top-left is (0, 0): a soft blob behind every character (so the light follows the letters)
	 * and a wide, faint halo around the whole run. widths = width of each character, lh = line height, gv = glow 0..1.
	 */
	public static void behind(GuiGraphicsExtractor g, float[] widths, float lh, float gv, boolean emph, float alpha, int rgb) {
		if (gv < 0.02f || alpha < 0.02f) return;
		float r = (emph ? 4f + 6f * gv : 4f + 2f * gv) / 32f * lh * 2.2f; // how far the light reaches past the letters
		float op = Math.min(1f, emph ? gv : 0.45f * gv) * alpha;
		float total = 0;
		for (float w : widths) total += w;
		blob(g, -2f * r, lh * 0.45f - (lh + 3f * r) / 2f, total + 4f * r, lh + 3f * r, op * 0.35f, rgb);
		float x = 0;
		for (float w : widths) {
			if (w > 0) blob(g, x - r, lh * 0.45f - (lh * 0.9f + 2f * r) / 2f, w + 2f * r, lh * 0.9f + 2f * r, op * 0.5f, rgb);
			x += w;
		}
	}
}
