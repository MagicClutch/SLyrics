package dev.slyrics.pretty;

/**
 * The animation state of one word: size, lift and glow of the whole word, each a curve over the word's progress t (0..1) that a
 * spring follows, and the same for every letter: a letter hops and grows while the sweep passes over it, so a wave runs through
 * the word. A held note (emphasized) moves its letters more and lets each of them glow on its own.
 */
public final class PrettyWord {
	static final Curve SCALE = Curve.of(0f, 0.95f, 0.7f, 1.0505f, 1f, 1f);
	static final Curve LIFT = Curve.of(0f, 0.01f, 0.9f, -1f / 60f, 1f, 0f);
	static final Curve GLOW = Curve.of(0f, 0f, 0.15f, 1f, 0.6f, 1f, 1f, 0f);
	/** Letters of a normal word: a short hop and a little growth while the sweep passes. */
	static final Curve HOP_SCALE = Curve.of(0f, 1f, 0.55f, 1.1f, 1f, 1f);
	static final Curve HOP_LIFT = Curve.of(0f, 0f, 0.55f, -1f / 36f, 1f, 0f);
	/** Letters of a held note: they grow more and lift higher. */
	static final Curve LETTER_SCALE = Curve.of(0f, 0.95f, 0.7f, 1.175f, 1f, 1f);
	static final Curve LETTER_LIFT = Curve.of(0f, 0.02f, 0.9f, -1f / 28f, 1f, 0f);
	/** Springs (damping, Hz): scale, lift, glow. */
	private static final double SZ = 0.64, SF = 0.88, LZ = 0.4, LF = 1.45, GZ = 0.56, GF = 1.18;

	public final boolean emph;
	public final int n;
	private final Spring scale = new Spring(SZ, SF, SCALE.at(0)), lift = new Spring(LZ, LF, LIFT.at(0)), glow = new Spring(GZ, GF, 0);
	private final Spring[] ls, ll, lg;
	private long last, lettersLast;

	public PrettyWord(int n, boolean emph) {
		this.n = Math.max(1, n);
		this.emph = emph && n > 1;
		ls = new Spring[this.n];
		ll = new Spring[this.n];
		lg = new Spring[this.n];
		for (int i = 0; i < this.n; i++) {
			ls[i] = new Spring(SZ, SF, this.emph ? LETTER_SCALE.at(0) : HOP_SCALE.at(0));
			ll[i] = new Spring(LZ, LF, this.emph ? LETTER_LIFT.at(0) : HOP_LIFT.at(0));
			lg[i] = new Spring(GZ, GF, 0);
		}
	}

	/** Time since the last call (s), 0 on the first, -1 if this frame was already handled. */
	private static float tick(long[] lastRef) {
		long now = System.nanoTime();
		if (lastRef[0] == 0) {
			lastRef[0] = now;
			return 0f;
		}
		float dt = (now - lastRef[0]) / 1e9f;
		if (dt < 0.0005f) return -1f;
		lastRef[0] = now;
		return Math.min(dt, 0.1f); // after a stutter: no jump
	}

	private final long[] wordClock = new long[1], letterClock = new long[1];

	/** Moves the word's springs on to progress t. Called by every view each frame; a second call in the same frame changes nothing. */
	public void update(float t) {
		float dt = tick(wordClock);
		if (dt < 0) return;
		t = clamp(t);
		scale.to(SCALE.at(t));
		lift.to(LIFT.at(t));
		glow.to(GLOW.at(t));
		if (dt == 0f) { // a word that shows up mid-song starts where it already should be
			scale.set(scale.target());
			lift.set(lift.target());
			glow.set(glow.target());
		}
		scale.step(dt);
		lift.step(dt);
		glow.step(dt);
	}

	/**
	 * Moves the letters on. lt[i] = how far the sweep has passed letter i (0..1), gt[i] = its glow progress (held notes:
	 * from when the sweep reaches the letter to the end of the word, so the whole word keeps glowing).
	 */
	public void letters(float[] lt, float[] gt) {
		float dt = tick(letterClock);
		if (dt < 0) return;
		for (int i = 0; i < n && i < lt.length; i++) {
			float p = clamp(lt[i]);
			ls[i].to(emph ? LETTER_SCALE.at(p) : HOP_SCALE.at(p));
			ll[i].to(emph ? LETTER_LIFT.at(p) : HOP_LIFT.at(p));
			lg[i].to(emph ? GLOW.at(clamp(gt[i])) : 0);
			if (dt == 0f) {
				ls[i].set(ls[i].target());
				ll[i].set(ll[i].target());
				lg[i].set(lg[i].target());
			}
			ls[i].step(dt);
			ll[i].step(dt);
			lg[i].step(dt);
		}
	}

	public float scale() { return scale.pos(); }
	public float lift() { return lift.pos(); }
	public float glow() { return Math.max(0f, glow.pos()); }

	public float letterScale(int i) { return ls[Math.min(i, n - 1)].pos(); }
	public float letterLift(int i) { return ll[Math.min(i, n - 1)].pos(); }
	public float letterGlow(int i) { return Math.max(0f, lg[Math.min(i, n - 1)].pos()); }

	/**
	 * Letter progress from the sweep: the edge's lit end is at litTo (in the word's units, may be negative), edge = width of the
	 * soft edge, xs/ws = left edge and width of every letter, t = word progress. Fills lt and gt.
	 */
	public static void letterProgress(float litTo, float edge, float[] xs, float[] ws, float total, float t, float[] lt, float[] gt) {
		for (int i = 0; i < xs.length; i++) {
			lt[i] = clamp((litTo + edge - xs[i]) / Math.max(0.001f, ws[i] + edge));
			float from = total > 0 ? xs[i] / total : 0f;
			gt[i] = clamp((t - from) / Math.max(1e-4f, 1f - from));
		}
	}

	static float clamp(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}
}
