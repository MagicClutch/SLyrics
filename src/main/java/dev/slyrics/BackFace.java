package dev.slyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text seen from its back, like a real 3D object: Minecraft's text is drawn one-sided (back faces are culled), so the
 * glyphs are drawn again with every quad's corners in reverse order. They stay exactly where they are, so from behind
 * the word reads mirrored, as a sign would.
 */
final class BackFace {
	private BackFace() {}

	static void submitText(SubmitNodeCollector collector, PoseStack ps, Font font, FormattedCharSequence text, float x, float y,
						   int argb, Font.DisplayMode mode, int light) {
		font.prepareText(text, x, y, argb, false, false, 0).visit(new Font.GlyphVisitor() {
			@Override
			public void acceptGlyph(TextRenderable.Styled glyph) {
				submit(glyph);
			}

			@Override
			public void acceptEffect(TextRenderable effect) {
				submit(effect);
			}

			private void submit(TextRenderable r) {
				collector.submitCustomGeometry(ps, r.renderType(mode), (pose, vc) -> {
					Reversed rv = new Reversed(vc);
					r.render(pose.pose(), rv, light, false);
					rv.finish();
				});
			}
		});
	}

	static void submitText(SubmitNodeCollector collector, PoseStack ps, FormattedCharSequence text, float x, float y, int argb,
						   Font.DisplayMode mode, int light) {
		submitText(collector, ps, Minecraft.getInstance().font, text, x, y, argb, mode, light);
	}

	/** Collects each quad's four corners and hands them on last to first, which turns the quad's front to the other side. */
	private static final class Reversed implements VertexConsumer {
		private final VertexConsumer out;
		private final Vertex[] quad = {new Vertex(), new Vertex(), new Vertex(), new Vertex()};
		private int count = -1;

		Reversed(VertexConsumer out) {
			this.out = out;
		}

		private Vertex cur() {
			return quad[count];
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (count == 3) flush();
			count++;
			Vertex v = cur();
			v.reset();
			v.x = x;
			v.y = y;
			v.z = z;
			return this;
		}

		/** Hands on a complete quad: when the next quad starts, and from finish() for the last one. */
		private void flush() {
			for (int i = 3; i >= 0; i--) quad[i].emit(out);
			count = -1;
		}

		void finish() {
			if (count == 3) flush();
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			cur().color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
			cur().hasColor = true;
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			cur().color = argb;
			cur().hasColor = true;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			cur().u = u;
			cur().v = v;
			cur().hasUv = true;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			cur().u1 = u;
			cur().v1 = v;
			cur().hasUv1 = true;
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			cur().u2 = u;
			cur().v2 = v;
			cur().hasUv2 = true;
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			cur().u3 = u;
			cur().v3 = v;
			cur().hasUv3 = true;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			cur().nx = x;
			cur().ny = y;
			cur().nz = z;
			cur().hasNormal = true;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float w) {
			cur().lineWidth = w;
			cur().hasLineWidth = true;
			return this;
		}
	}

	private static final class Vertex {
		float x, y, z, u, v, u3, v3, nx, ny, nz, lineWidth;
		int color, u1, v1, u2, v2;
		boolean hasColor, hasUv, hasUv1, hasUv2, hasUv3, hasNormal, hasLineWidth;

		void reset() {
			hasColor = hasUv = hasUv1 = hasUv2 = hasUv3 = hasNormal = hasLineWidth = false;
		}

		void emit(VertexConsumer out) {
			out.addVertex(x, y, z);
			if (hasColor) out.setColor(color);
			if (hasUv) out.setUv(u, v);
			if (hasUv1) out.setUv1(u1, v1);
			if (hasUv2) out.setUv2(u2, v2);
			if (hasUv3) out.setUv3(u3, v3);
			if (hasNormal) out.setNormal(nx, ny, nz);
			if (hasLineWidth) out.setLineWidth(lineWidth);
		}
	}
}
