package dev.slyrics;

/**
 * Who draws the HUD elements (lyric line, karaoke panel, media player). OneConfig's HUD system does (see
 * dev.slyrics.compat.oneconfig.SLyricsHuds); only if that could not start does the mod draw them itself.
 */
public final class HudMode {
	/** True once OneConfig's HUD elements are registered; the mod's own drawing is then switched off. */
	public static volatile boolean oneConfig;

	private HudMode() {}
}
