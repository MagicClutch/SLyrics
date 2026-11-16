package dev.slyrics;

/**
 * Who draws the HUD elements (lyric line, karaoke panel, media player). Normally the mod does, with its own editor. When OneConfig
 * is installed its HUD editor owns them (see dev.slyrics.compat.oneconfig.SLyricsHuds) and the mod's own drawing stays off.
 */
public final class HudMode {
	/** True once OneConfig's HUD elements are registered; the mod's own drawing is then switched off. */
	public static volatile boolean oneConfig;

	private HudMode() {}

	/** True while the mod's own HUD editor is open (it draws the elements itself, so OneConfig's copies stay hidden). */
	public static boolean classicEditor() {
		return MediaPlayer.editing;
	}
}
