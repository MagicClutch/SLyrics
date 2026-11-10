package dev.slyrics.profiles;

import com.teamresourceful.resourcefulconfig.client.components.base.CustomButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Adds a "Save profile" button (in the Resourceful Config look) to the bottom of the settings screen. It only appears
 * while a profile is active and you changed something, and it saves into that same profile.
 * The button is put FIRST in the screen's child list: the config lists cover the whole screen and would otherwise
 * swallow every click before it reached the button. It is re-added whenever the screen rebuilt its widgets.
 */
public final class SaveProfileButton {
	private SaveProfileButton() {}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!screen.getClass().getName().equals("com.teamresourceful.resourcefulconfig.client.ConfigScreen")) return;
			if (redirectIfProfilesCategory(client, screen)) return;
			Holder holder = new Holder();
			ScreenEvents.afterTick(screen).register(s -> holder.tick(s));
			ScreenEvents.remove(screen).register(s -> holder.drop(s));
		});
	}

	/** Clicking the "Profiles" category opens the profiles screen right away instead of an almost empty page. */
	private static boolean redirectIfProfilesCategory(net.minecraft.client.Minecraft client, Screen screen) {
		try {
			java.lang.reflect.Field cf = screen.getClass().getDeclaredField("config");
			cf.setAccessible(true);
			var config = (com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig) cf.get(screen);
			if (!"Profiles".equals(config.info().title().toComponent().getString())) return false;
			java.lang.reflect.Field pf = screen.getClass().getDeclaredField("parent");
			pf.setAccessible(true);
			Screen parent = (Screen) pf.get(screen);
			client.execute(() -> client.gui.setScreen(new ProfilesScreen(parent)));
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static final class Holder {
		private CustomButton button;
		private String shownFor = "";

		void tick(Screen screen) {
			boolean show = Profiles.modified();
			String name = Profiles.active();
			if (!show) {
				drop(screen);
				return;
			}
			if (button != null && !name.equals(shownFor)) drop(screen);
			if (button == null) {
				shownFor = name;
				button = new CustomButton(200, 20, Component.literal("Save profile '" + name + "'"), () -> {
					try {
						Profiles.save(Profiles.active());
					} catch (Exception ignored) {
						// the button simply stays visible
					}
				});
				button.setPosition(screen.width / 2 - 100, screen.height - 28);
			}
			button.setPosition(screen.width / 2 - 100, screen.height - 28);
			List<? extends GuiEventListener> kids = screen.children();
			if (kids.isEmpty() || kids.get(0) != button) {
				removeQuietly(screen, button);
				if (call(screen, "addRenderableWidget", button)) toFront(screen, button);
			}
		}

		void drop(Screen screen) {
			if (button != null) removeQuietly(screen, button);
			button = null;
		}
	}

	private static void removeQuietly(Screen screen, AbstractWidget w) {
		call(screen, "removeWidget", w);
	}

	@SuppressWarnings("unchecked")
	private static void toFront(Screen screen, AbstractWidget w) {
		try {
			List<GuiEventListener> kids = (List<GuiEventListener>) screen.children();
			if (kids.remove(w)) kids.add(0, w);
		} catch (RuntimeException ignored) {
			// the button just stays at the end
		}
	}

	/** Screen.addRenderableWidget / removeWidget are protected; call them by name. */
	private static boolean call(Screen screen, String method, AbstractWidget button) {
		for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getName().equals(method) && m.getParameterCount() == 1) {
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
