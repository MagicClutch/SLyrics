package dev.slyrics.compat.oneconfig;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Entry point for the optional OneConfig support. Nothing here may touch a OneConfig class unless OneConfig is installed:
 * the real work sits in {@link SLyricsOneConfig}, which is only loaded after the mod check.
 */
public final class OneConfigBridge {
	/** Set once the OneConfig page exists; a plain Runnable so this class never names a OneConfig type. */
	static Runnable syncer;

	private OneConfigBridge() {}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("oneconfig")) return;
		try {
			SLyricsOneConfig.register();
		} catch (Throwable t) { // OneConfig is optional: a version mismatch must never break the mod
			dev.slyrics.SLyricsClient.LOGGER.warn("OneConfig support could not be started", t);
		}
	}

	/** Opens the SLyrics page in OneConfig. False if OneConfig is not there (or could not do it): the caller then uses Resourceful Config. */
	public static boolean openConfig() {
		if (syncer == null) return false;
		try {
			return SLyricsOneConfig.open();
		} catch (Throwable t) {
			dev.slyrics.SLyricsClient.LOGGER.warn("Could not open the OneConfig page", t);
			return false;
		}
	}

	/** Opens OneConfig's HUD editor. False if OneConfig is not there: the caller then uses the mod's own editor. */
	public static boolean openHudEditor() {
		if (syncer == null) return false;
		try {
			SLyricsHuds.openEditor();
			return true;
		} catch (Throwable t) {
			dev.slyrics.SLyricsClient.LOGGER.warn("Could not open OneConfig's HUD editor", t);
			return false;
		}
	}

	/** Called every client tick; does nothing without OneConfig. */
	public static void tick() {
		Runnable s = syncer;
		if (s != null) s.run();
	}
}
