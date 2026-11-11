package dev.slyrics;

/** Keeps HUD modules (media player, lyrics panel) from overlapping. Rectangles are {x, y, width, height} in screen pixels. */
final class HudLayout {
	private HudLayout() {}

	static boolean overlaps(float x, float y, float w, float h, float[] r) {
		return x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1];
	}

	/** Moves the rectangle (x, y, w, h) out of r along the shortest way, then back onto the screen. Returns {x, y}. */
	static float[] separate(float x, float y, float w, float h, float[] r, int guiW, int guiH) {
		x = Math.max(0, Math.min(x, guiW - w));
		y = Math.max(0, Math.min(y, guiH - h));
		if (r == null || !overlaps(x, y, w, h, r)) return new float[]{x, y};
		float left = r[0] - w, right = r[0] + r[2], up = r[1] - h, down = r[1] + r[3];
		float[][] options = {{left, y}, {right, y}, {x, up}, {x, down}};
		float[] best = null;
		float bestDist = Float.MAX_VALUE;
		for (float[] o : options) {
			float ox = Math.max(0, Math.min(o[0], guiW - w)), oy = Math.max(0, Math.min(o[1], guiH - h));
			if (overlaps(ox, oy, w, h, r)) continue; // pushed back into the other module by the screen edge
			float d = Math.abs(ox - x) + Math.abs(oy - y);
			if (d < bestDist) {
				bestDist = d;
				best = new float[]{ox, oy};
			}
		}
		return best != null ? best : new float[]{x, y};
	}
}
