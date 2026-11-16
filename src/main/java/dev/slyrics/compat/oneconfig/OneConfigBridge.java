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

	/** Called every client tick; does nothing without OneConfig. */
	public static void tick() {
		Runnable s = syncer;
		if (s != null) s.run();
	}
}
