package dev.slyrics.compat.polyplus;

import dev.slyrics.SLyricsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Talks to the OneClient lobby (PolyPlus' {@code LobbyApi}) by reflection, so SLyrics runs the same with or without PolyPlus.
 * The lobby is a real client level that PolyPlus swaps in while it draws the main menu background.
 */
public final class PolyPlusLobby {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("polyplus");
	private static Method isLobbyLevel, isRendering, phase;
	private static boolean initialized, broken;

	private PolyPlusLobby() {}

	private static boolean ready() {
		if (!LOADED || broken) return false;
		if (!initialized) {
			initialized = true;
			try {
				Class<?> api = Class.forName("org.polyfrost.polyplus.api.lobby.LobbyApi");
				isLobbyLevel = api.getMethod("isLobbyLevel", Level.class);
				isRendering = api.getMethod("isRendering");
				phase = api.getMethod("phase");
			} catch (Throwable t) {
				broken = true;
				SLyricsClient.LOGGER.warn("SLyrics could not access PolyPlus' LobbyApi; lobby lyrics are off", t);
				return false;
			}
		}
		return true;
	}

	/** True while the lobby scene is being drawn (its level and camera are the ones in use right now). */
	public static boolean isRendering() {
		if (!ready()) return false;
		try {
			return (Boolean) isRendering.invoke(null);
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	/** True while the OneClient lobby is showing: on its menu (no world joined) or while its level is the current one. */
	public static boolean inLobby() {
		if (!ready()) return false;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null && (Boolean) isLobbyLevel.invoke(null, mc.level)) return true;
			if ((Boolean) isRendering.invoke(null)) return true;
			return mc.level == null && "READY".equals(String.valueOf(phase.invoke(null)));
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	private static void fail(Throwable t) {
		broken = true;
		SLyricsClient.LOGGER.warn("PolyPlus' LobbyApi failed; lobby lyrics are off", t);
	}
}
