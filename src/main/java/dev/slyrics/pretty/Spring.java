package dev.slyrics.pretty;

/**
 * A damped spring that pulls its position toward a target. Each step is solved exactly (closed form), so it looks the same at any
 * frame rate and can never blow up. zeta below 1 overshoots a little and swings back; freq is how fast it swings, in Hz.
 */
public final class Spring {
	private final double zeta, omega;
	private double pos, vel, target;

	public Spring(double zeta, double freq, double initial) {
		this.zeta = zeta;
		this.omega = 2 * Math.PI * freq;
		this.pos = initial;
		this.target = initial;
	}

	public void to(double target) {
		this.target = target;
	}

	/** Jumps straight to v and stops moving. */
	public void set(double v) {
		pos = v;
		target = v;
		vel = 0;
	}

	public void step(double dt) {
		if (dt <= 0) return;
		double x = pos - target, v = vel, w = omega;
		if (Math.abs(x) < 1e-5 && Math.abs(v) < 1e-4) { // at rest: snap exactly onto the target
			pos = target;
			vel = 0;
			return;
		}
		if (zeta < 1) {
			double wd = w * Math.sqrt(1 - zeta * zeta);
			double e = Math.exp(-zeta * w * dt), c = Math.cos(wd * dt), s = Math.sin(wd * dt);
			double nx = e * (x * c + (v + zeta * w * x) / wd * s);
			double nv = e * (v * c - (w * w * x + zeta * w * v) / wd * s);
			x = nx;
			v = nv;
		} else { // critically damped (used for zeta >= 1)
			double e = Math.exp(-w * dt), k = v + w * x;
			double nx = (x + k * dt) * e;
			double nv = (v - w * k * dt) * e;
			x = nx;
			v = nv;
		}
		pos = target + x;
		vel = v;
	}

	public float pos() {
		return (float) pos;
	}

	public double target() {
		return target;
	}
}
