package dev.slyrics.pretty;

/** A smooth curve through points (x, y): monotone cubic, so it never overshoots between two points. Outside the points it stays flat. */
public final class Curve {
	private final float[] xs, ys, m;

	public Curve(float[] xs, float[] ys) {
		int n = xs.length;
		this.xs = xs;
		this.ys = ys;
		this.m = new float[n];
		if (n < 2) return;
		float[] d = new float[n - 1];
		for (int i = 0; i < n - 1; i++) d[i] = (ys[i + 1] - ys[i]) / Math.max(1e-6f, xs[i + 1] - xs[i]);
		m[0] = d[0];
		m[n - 1] = d[n - 2];
		for (int i = 1; i < n - 1; i++) m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2f;
		for (int i = 0; i < n - 1; i++) { // Fritsch-Carlson: limit the slopes so each piece stays between its two points
			if (d[i] == 0) {
				m[i] = 0;
				m[i + 1] = 0;
				continue;
			}
			float a = m[i] / d[i], b = m[i + 1] / d[i], s = a * a + b * b;
			if (s > 9) {
				float tau = 3f / (float) Math.sqrt(s);
				m[i] = tau * a * d[i];
				m[i + 1] = tau * b * d[i];
			}
		}
	}

	public static Curve of(float... xy) {
		float[] xs = new float[xy.length / 2], ys = new float[xy.length / 2];
		for (int i = 0; i < xs.length; i++) {
			xs[i] = xy[2 * i];
			ys[i] = xy[2 * i + 1];
		}
		return new Curve(xs, ys);
	}

	public float at(float x) {
		int n = xs.length;
		if (n == 0) return 0f;
		if (x <= xs[0]) return ys[0];
		if (x >= xs[n - 1]) return ys[n - 1];
		int i = 0;
		while (i < n - 2 && x > xs[i + 1]) i++;
		float h = xs[i + 1] - xs[i], t = (x - xs[i]) / h, t2 = t * t, t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * m[i + 1];
	}
}
