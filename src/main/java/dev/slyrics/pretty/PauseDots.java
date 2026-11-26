package dev.slyrics.pretty;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Three dots that show how long an instrumental pause still lasts: they light up one after another while the whole group
 * breathes slowly, then pop and vanish when the singing starts again. One instance per view.
 */
public final class PauseDots {
	private static final Curve DOT_SCALE = Curve.of(0f, 0.75f, 0.7f, 1.05f, 1f, 1f);
	private static final Curve DOT_LIFT = Curve.of(0f, 0.125f, 0.9f, -0.2f, 1f, 0f);
	private static final Curve DOT_OPACITY = Curve.of(0f, 0.35f, 0.6f, 1f, 1f, 1f);
	private static final Curve DOT_GLOW = Curve.of(0f, 0f, 0.6f, 1f, 1f, 1f);
	/** Each dot gets one third of the first 92.5 % of the pause. */
	private static final float WINDOW = 0.925f / 3f;

	private long key = Long.MIN_VALUE;
	private Curve groupScale, groupOpacity;
	private final Spring gScale = new Spring(0.7, 5, 0), gLift = new Spring(0.4, 1.25, 0), gOpacity = new Spring(0.4, 1.25, 0);
	private final Spring[] dScale = new Spring[3], dLift = new Spring[3], dOpacity = new Spring[3], dGlow = new Spring[3];
	private long last;

	public PauseDots() {
		reset();
	}

	private void reset() {
		gScale.set(0);
		gLift.set(PrettyWord.LIFT.at(0));
		gOpacity.set(0);
		for (int i = 0; i < 3; i++) {
			dScale[i] = new Spring(0.6, 0.7, DOT_SCALE.at(0));
			dLift[i] = new Spring(0.4, 1.25, DOT_LIFT.at(0));
			dOpacity[i] = new Spring(0.5, 1, DOT_OPACITY.at(0));
			dGlow[i] = new Spring(0.5, 1, 0);
		}
	}

	/** The group curves run in seconds: pop in within 0.2 s, a slow pulse every 2.25 s, a last pop just before the end. */
	private void build(float dur) {
		java.util.List<Float> xy = new java.util.ArrayList<>(java.util.List.of(0f, 0f, 0.2f, 1.05f));
		float endPop = Math.max(0.3f, dur - 0.075f);
		boolean low = true;
		for (float t = 2.45f; t < endPop - 0.3f; t += 2.25f, low = !low) {
			xy.add(t);
			xy.add(low ? 0.95f : 1.05f);
		}
		xy.add(endPop);
		xy.add(1.15f);
		xy.add(Math.max(endPop + 0.01f, dur));
		xy.add(0f);
		float[] a = new float[xy.size()];
		for (int i = 0; i < a.length; i++) a[i] = xy.get(i);
		groupScale = Curve.of(a);
		groupOpacity = Curve.of(0f, 0f, Math.min(0.5f, endPop * 0.5f), 1f, endPop, 1f, Math.max(endPop + 0.01f, dur), 0f);
	}

	/** Moves the dots on to pos (song ms) within the pause from start to end. */
	public void update(long start, long end, long pos) {
		float dur = Math.max(0.5f, (end - start) / 1000f);
		if (key != start) {
			key = start;
			build(dur);
			reset();
			last = 0;
		}
		long now = System.nanoTime();
		float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
		if (last != 0 && dt < 0.0005f) return;
		last = now;
		float sec = (pos - start) / 1000f, t = PrettyWord.clamp(sec / dur);
		gScale.to(groupScale.at(sec));
		gOpacity.to(groupOpacity.at(sec));
		gLift.to(PrettyWord.LIFT.at(t));
		gScale.step(dt);
		gOpacity.step(dt);
		gLift.step(dt);
		for (int i = 0; i < 3; i++) {
			float ti = PrettyWord.clamp((t - i * WINDOW) / WINDOW);
			dScale[i].to(DOT_SCALE.at(ti));
			dLift[i].to(DOT_LIFT.at(ti));
			dOpacity[i].to(DOT_OPACITY.at(ti));
			dGlow[i].to(DOT_GLOW.at(ti));
			dScale[i].step(dt);
			dLift[i].step(dt);
			dOpacity[i].step(dt);
			dGlow[i].step(dt);
		}
	}

	/**
	 * Draws the dots with their middle (or, with centered=false, their left edge) at (x, y); d = diameter of one dot in the
	 * current units. alpha fades the whole group.
	 */
	public void draw(GuiGraphicsExtractor g, float x, float y, float d, float alpha, int glowRgb, float glowK, boolean centered) {
		float op = (float) Math.sin(PrettyWord.clamp(gOpacity.pos()) * Math.PI / 2) * alpha;
		float sc = Math.max(0f, gScale.pos());
		if (op < 0.02f || sc < 0.02f) return;
		float step = d * 1.5f, width = step * 2 + d;
		g.pose().pushMatrix();
		g.pose().translate(centered ? x : x + width / 2f * sc, y + gLift.pos() * d);
		g.pose().scale(sc, sc);
		for (int i = 0; i < 3; i++) {
			float s = Math.max(0f, dScale[i].pos());
			float a = op * PrettyWord.clamp(dOpacity[i].pos());
			float gl = PrettyWord.clamp(dGlow[i].pos()) * glowK;
			float cx = (i - 1) * step, cy = dLift[i].pos() * d;
			if (gl > 0.02f) circle(g, cx, cy, d / 2f * s * 1.7f, a * 0.22f * gl, glowRgb);
			circle(g, cx, cy, d / 2f * s, a * 0.85f, 0xFFFFFF);
		}
		g.pose().popMatrix();
	}

	/** Static, dimmed dots for a pause that is not playing right now. */
	public static void drawIdle(GuiGraphicsExtractor g, float x, float y, float d, float alpha) {
		float step = d * 1.5f;
		for (int i = 0; i < 3; i++) circle(g, x + d / 2f * 0.75f + i * step * 0.75f, y, d / 2f * 0.75f, alpha * 0.35f * 0.85f, 0xFFFFFF);
	}

	/** A filled circle, drawn as 16 rows in a local grid scaled to radius r. */
	static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, float alpha, int rgb) {
		int a = Math.round(PrettyWord.clamp(alpha) * 255);
		if (a < 6 || r <= 0.01f) return;
		int col = (a << 24) | (rgb & 0xFFFFFF);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(r / 8f, r / 8f);
		for (int row = -8; row < 8; row++) {
			float yc = row + 0.5f;
			int hw = Math.round((float) Math.sqrt(Math.max(0f, 64f - yc * yc)));
			if (hw > 0) g.fill(-hw, row, hw, row + 1, col);
		}
		g.pose().popMatrix();
	}
}
