package dev.slyrics.profiles;

import com.teamresourceful.resourcefulconfig.client.components.base.CustomButton;
import dev.slyrics.ConfigSync;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A "Save profile" button at the bottom of the settings screen, in Resourceful Config's own button style. It only
 * shows while a profile is active and you changed something, and it saves into that same profile.
 * It is drawn and clicked through screen events instead of being added as a child widget, because the settings
 * screen rebuilds its widgets (category switch, search, resize) and would throw a child button away.
 */
public final class SaveProfileButton {
	private static final int WIDTH = 120;
	private static final int HEIGHT = 16;

	private static final class State {
		CustomButton button;
		String label = "";
	}

	private static final Map<Screen, State> STATES = new WeakHashMap<>();

	private SaveProfileButton() {}

	private static boolean isSettingsScreen(Screen screen) {
		String n = screen.getClass().getName();
		return n.startsWith("com.teamresourceful.resourcefulconfig.") && n.endsWith("ConfigScreen");
	}

	private static void save() {
		try {
			ConfigSync.fromRc(); // take over what was just changed in the screen
			Profiles.save(Profiles.active());
		} catch (Exception ignored) {
			// the button simply stays visible
		}
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!isSettingsScreen(screen) || STATES.containsKey(screen)) return;
			State st = new State();
			STATES.put(screen, st);

			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
				ConfigSync.fromRc();
				if (!Profiles.modified()) {
					st.button = null;
					return;
				}
				String label = "Save profile";
				if (st.button == null || !label.equals(st.label)) {
					st.label = label;
					st.button = new CustomButton(WIDTH, HEIGHT, Component.literal(label), SaveProfileButton::save);
				}
				st.button.setX(s.width / 2 - WIDTH / 2);
				st.button.setY(s.height - HEIGHT - 6);
				st.button.extractRenderState(g, mouseX, mouseY, delta);
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				CustomButton b = st.button;
				if (b != null && b.isMouseOver(event.x(), event.y())) {
					b.mouseClicked(event, false);
					return false; // handled here, the settings screen must not also react
				}
				return true;
			});
		});
	}
}
