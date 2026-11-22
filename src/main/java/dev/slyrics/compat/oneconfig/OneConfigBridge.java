package dev.slyrics.compat.oneconfig;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Entry point for the OneConfig page and HUD elements; the real work sits in {@link SLyricsOneConfig}. OneConfig is a
 * required dependency, but a failure in it is caught here so it never takes the rest of the mod down.
 */
public final class OneConfigBridge {
	/** Set once the OneConfig page exists; a plain Runnable so this class never names a OneConfig type. */
	static Runnable syncer;

	private OneConfigBridge() {}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("oneconfig")) return;
		try {
			SLyricsOneConfig.register();
		} catch (Throwable t) { // a OneConfig problem must never break the rest of the mod
			dev.slyrics.SLyricsClient.LOGGER.warn("OneConfig support could not be started", t);
		}
	}

	/** Opens the SLyrics page in OneConfig. False if it could not be opened. */
	public static boolean openConfig() {
		if (syncer == null) return false;
		try {
			return SLyricsOneConfig.open();
		} catch (Throwable t) {
			dev.slyrics.SLyricsClient.LOGGER.warn("Could not open the OneConfig page", t);
			return false;
		}
	}

	/** Opens OneConfig's HUD editor. False if it could not be opened. */
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
