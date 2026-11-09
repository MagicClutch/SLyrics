package dev.slyrics.profiles;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/**
 * Adds a "Save profile" button to the bottom of the settings screen. It only appears while a profile is active and
 * you changed something, and it saves into that same profile; nothing is ever overwritten by accident.
 */
public final class SaveProfileButton {
	private SaveProfileButton() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!screen.getClass().getName().equals("com.teamresourceful.resourcefulconfig.client.ConfigScreen")) return;
			Button button = Button.builder(Component.empty(), b -> {
				try {
					Profiles.save(Profiles.active());
				} catch (Exception ignored) {
					// the button simply stays visible
				}
			}).bounds(w / 2 - 90, h - 26, 180, 20).build();
			button.visible = false;
			if (!add(screen, button)) return;
			ScreenEvents.afterTick(screen).register(s -> {
				boolean show = Profiles.modified();
				button.visible = show;
				button.active = show;
				if (show) button.setMessage(Component.literal("Save profile '" + Profiles.active() + "'"));
			});
		});
	}

	/** Screen.addRenderableWidget is protected; call it by name. */
	private static boolean add(Screen screen, Button button) {
		for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
					try {
						m.setAccessible(true);
						m.invoke(screen, button);
						return true;
					} catch (ReflectiveOperationException | RuntimeException e) {
						return false;
					}
				}
			}
		}
		return false;
	}
}
