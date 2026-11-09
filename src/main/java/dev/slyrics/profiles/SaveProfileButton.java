package dev.slyrics.profiles;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A "Save profile" button at the bottom of the settings screen. It only shows while a profile is active and you
 * changed something, and it saves into that same profile; nothing is ever overwritten by accident.
 * It is drawn and clicked through screen events instead of being added as a child widget, because the settings
 * screen rebuilds its widgets (category switch, search, resize) and would throw a child button away.
 */
public final class SaveProfileButton {
	private static final Map<Screen, Button> BUTTONS = new WeakHashMap<>();

	private SaveProfileButton() {}

	private static boolean isSettingsScreen(Screen screen) {
		String n = screen.getClass().getName();
		return n.startsWith("com.teamresourceful.resourcefulconfig.") && n.endsWith("ConfigScreen");
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!isSettingsScreen(screen) || BUTTONS.containsKey(screen)) return;
			Button button = Button.builder(Component.empty(), b -> {
				try {
					Profiles.save(Profiles.active());
				} catch (Exception ignored) {
					// the button simply stays visible
				}
			}).bounds(0, 0, 180, 20).build();
			BUTTONS.put(screen, button);

			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
				boolean show = Profiles.modified();
				button.visible = show;
				button.active = show;
				if (!show) return;
				button.setMessage(Component.literal("Save profile '" + Profiles.active() + "'"));
				button.setX(s.width / 2 - 90);
				button.setY(s.height - 26);
				button.extractRenderState(g, mouseX, mouseY, delta);
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (button.visible && button.active && button.isMouseOver(event.x(), event.y())) {
					button.mouseClicked(event, false);
					return false; // handled here, the settings screen must not also react
				}
				return true;
			});
		});
	}
}
