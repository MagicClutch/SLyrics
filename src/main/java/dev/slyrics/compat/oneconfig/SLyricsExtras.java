package dev.slyrics.compat.oneconfig;

import com.mojang.blaze3d.platform.InputConstants;
import dev.slyrics.Config;
import dev.slyrics.MediaPlayer;
import dev.slyrics.Spotify;
import dev.slyrics.profiles.Profiles;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.config.v1.dsl.ConfigDSL;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The OneConfig options that are not plain fields of {@link dev.slyrics.ConfigLayout}: the profile actions, the keybinds,
 * the Spotify logout, the font list and the emoji switches. Every action calls the same method the matching command uses.
 */
final class SLyricsExtras {
	/** The last name typed into the profile field. */
	private static String profileName = "";

	/** Adds a value that lives in Config.INSTANCE and has to be kept in sync (see SLyricsOneConfig#sync). */
	interface Binder {
		void bind(ConfigDSL.Prop<Object> prop, Field field);
	}

	private SLyricsExtras() {}

	static void add(ConfigDSL dsl, Binder binder) {
		try {
			profiles(dsl);
			spotify(dsl);
			emojis(dsl, binder);
			fonts(dsl);
			keybinds(dsl);
		} catch (ReflectiveOperationException | RuntimeException e) {
			dev.slyrics.SLyricsClient.LOGGER.warn("Could not add the extra OneConfig options", e);
		}
	}

	private static void chat(String msg) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) mc.player.sendSystemMessage(Component.literal("SLyrics: " + msg));
		});
	}

	private static void button(ConfigDSL dsl, String id, String title, String text, String desc, String cat, String sub, Runnable action) {
		ConfigDSL.RunnableProp p = dsl.button(action);
		p.setId(id);
		p.setTitle(title);
		p.setDescription(desc);
		p.setText(text);
		p.setCategory(cat);
		p.setSubcategory(sub);
	}

	private static final String NO_PROFILES = "(no profiles yet)";
	private static ConfigDSL.OptionsProp<Integer> profileList;
	private static java.util.List<String> shownProfiles = java.util.List.of();
	private static int tickCount;

	/** The profile picked in the dropdown, or "" if there is none. */
	private static String picked() {
		var names = Profiles.list();
		Integer i = profileList == null ? null : profileList.getProperty().get();
		return i == null || i < 0 || i >= names.size() ? "" : names.get(i);
	}

	/** Fills the dropdown with the saved profiles and selects the active one. */
	private static void refreshProfiles() {
		if (profileList == null) return;
		var names = Profiles.list();
		shownProfiles = names;
		profileList.setOptionKeys(names.isEmpty() ? new String[]{NO_PROFILES} : names.toArray(new String[0]));
		profileList.getProperty().set(Math.max(0, names.indexOf(Profiles.active())));
	}

	/** Called every client tick: keeps the dropdown in step when profiles are saved, deleted or loaded elsewhere (commands, the profile screen). */
	static void tick() {
		tickFont();
		tickKeys();
		if (profileList == null || ++tickCount % 20 != 0) return;
		if (!Profiles.list().equals(shownProfiles)) refreshProfiles();
	}

	// ------------------------------------------------------------------ font dropdown

	private static ConfigDSL.OptionsProp<Integer> fontPick;
	private static int fontLast = -1;

	/** The font dropdown: Minecraft's fonts and the ones that come with the mod (see {@link dev.slyrics.Fonts}). */
	private static void fonts(ConfigDSL dsl) {
		var all = dev.slyrics.Fonts.ALL;
		String[] labels = new String[all.size()];
		for (int i = 0; i < labels.length; i++) labels[i] = all.get(i).label();
		fontLast = dev.slyrics.Fonts.indexOf(Config.INSTANCE.font);
		fontPick = dsl.dropdown(fontLast, labels);
		fontPick.setId("fontPick");
		fontPick.setTitle("Font");
		fontPick.setDescription("The font of the lyrics. Minecraft's own fonts and a few caption fonts that come with the mod. Characters a font does not have (emojis, other scripts) use the Minecraft font.");
		fontPick.setCategory("Text Settings");
		fontPick.setSubcategory("Text");
	}

	/** Dropdown to config and back: whichever side changed since the last tick wins. */
	private static void tickFont() {
		if (fontPick == null) return;
		Integer idx = fontPick.getProperty().get();
		var all = dev.slyrics.Fonts.ALL;
		if (idx == null || idx < 0 || idx >= all.size()) return;
		if (idx != fontLast) { // picked in OneConfig
			fontLast = idx;
			if (!all.get(idx).id().equals(Config.INSTANCE.font)) {
				Config.INSTANCE.font = all.get(idx).id();
				Config.save();
			}
		} else if (!all.get(idx).id().equals(Config.INSTANCE.font)) { // changed elsewhere (profile)
			if (!dev.slyrics.Fonts.isKnown(Config.INSTANCE.font)) Config.INSTANCE.font = "minecraft:default";
			fontLast = dev.slyrics.Fonts.indexOf(Config.INSTANCE.font);
			fontPick.getProperty().set(fontLast);
		}
	}

	// ------------------------------------------------------------------ key bindings

	private static final String[] KEY_IDS = {"keyPlayPause", "keyNext", "keyPrevious"};
	private static final String[] KEY_TITLES = {"Play / pause", "Next song", "Previous song"};
	private static final java.util.List<ConfigDSL.Prop<OneConfigKeybind>> keyProps = new ArrayList<>();
	private static final int[] keyLast = {-2, -2, -2};

	/** The three media keys, as OneConfig options. The keys themselves stay Minecraft key bindings (Options > Controls), so they are saved with the game's controls and both places always show the same key. */
	private static void keybinds(ConfigDSL dsl) {
		keyProps.clear();
		var maps = MediaPlayer.keyMappings();
		for (int i = 0; i < maps.length; i++) {
			int code = code(maps[i]);
			var p = dsl.keybind(bind(code));
			p.setId(KEY_IDS[i]);
			p.setTitle(KEY_TITLES[i]);
			p.setDescription("Also in Options > Controls > SLyrics. One key only, no Ctrl / Shift / Alt combinations.");
			p.setCategory("Keybinds");
			p.setSubcategory("Media player");
			keyProps.add(p);
			keyLast[i] = code;
		}
	}

	private static OneConfigKeybind bind(int code) {
		var b = KeybindHelper.builder();
		if (code >= 0) b.key(code);
		return b.build();
	}

	private static int code(KeyMapping km) {
		InputConstants.Key k = KeyMappingHelper.getBoundKeyOf(km);
		return k.getType() == InputConstants.Type.KEYBOARD ? k.getValue() : -1;
	}

	/** Key in OneConfig to key binding and back: whichever side changed since the last tick wins. */
	private static void tickKeys() {
		var maps = MediaPlayer.keyMappings();
		for (int i = 0; i < keyProps.size() && i < maps.length; i++) {
			try {
				OneConfigKeybind kb = keyProps.get(i).getProperty().get();
				int widget = kb == null || kb.getKeyCodes().length == 0 ? -1 : kb.getKeyCodes()[0];
				int vanilla = code(maps[i]);
				if (widget != keyLast[i]) { // set in OneConfig
					maps[i].setKey(widget < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYBOARD.getOrCreate(widget));
					KeyMapping.resetMapping();
					Minecraft.getInstance().options.save();
					keyLast[i] = widget;
				} else if (vanilla != keyLast[i]) { // set in Options > Controls
					keyProps.get(i).getProperty().set(bind(vanilla));
					keyLast[i] = vanilla;
				}
			} catch (RuntimeException e) {
				dev.slyrics.SLyricsClient.LOGGER.warn("Key binding sync failed", e);
			}
		}
	}

	private static void profiles(ConfigDSL dsl) {
		String cat = "Profiles";
		String sub = "Your profiles";

		// "New profile" comes first on the page: the options show in the order they are added
		String newSub = "New profile";
		ConfigDSL.TextProp name = dsl.text("");
		name.setId("profileName");
		name.setTitle("New profile name");
		name.setDescription("Type a name, then press Save. Same as /slyrics profile save <name>.");
		name.setCategory(cat);
		name.setSubcategory(newSub);
		name.getProperty().addCallback(v -> {
			profileName = v == null ? "" : v.toString();
			return true;
		});
		button(dsl, "profileSave", "Save as new profile", "Save", "Saves all current settings under the name above. Never replaces an existing profile.", cat, newSub, () -> {
			try {
				if (Profiles.exists(profileName)) { chat("'" + profileName + "' already exists. Use Update to replace the active profile."); return; }
				String n = Profiles.save(profileName);
				chat(n == null ? "invalid profile name" : "saved profile '" + n + "'");
				refreshProfiles();
			} catch (Exception e) {
				chat("could not save: " + e.getMessage());
			}
		});

		var names = Profiles.list();
		shownProfiles = names;
		profileList = dsl.dropdown(Math.max(0, names.indexOf(Profiles.active())), names.isEmpty() ? new String[]{NO_PROFILES} : names.toArray(new String[0]));
		profileList.setId("profilePick");
		profileList.setTitle("Profile");
		profileList.setDescription("All saved profiles. Pick one, then Load or Delete it. The active profile is selected when the list is built.");
		profileList.setCategory(cat);
		profileList.setSubcategory(sub);
		button(dsl, "profileLoad", "Load profile", "Load", "Loads the profile picked above and applies all its settings.", cat, sub, () -> {
			try {
				String n = picked();
				chat(n.isEmpty() ? "no profile picked" : Profiles.load(n) ? "loaded profile '" + n + "'" : "no profile named '" + n + "'");
			} catch (Exception e) {
				chat("could not load: " + e.getMessage());
			}
		});
		button(dsl, "profileUpdate", "Update active profile", "Update", "Replaces the active profile with the current settings.", cat, sub, () -> {
			try {
				String act = Profiles.active();
				if (act.isEmpty() || !Profiles.exists(act)) { chat("no active profile. Load or save one first"); return; }
				Profiles.save(act);
				chat("updated profile '" + act + "'");
			} catch (Exception e) {
				chat("could not save: " + e.getMessage());
			}
		});
		button(dsl, "profileDelete", "Delete profile", "Delete", "Deletes the profile picked above.", cat, sub, () -> {
			String n = picked();
			chat(n.isEmpty() ? "no profile picked" : Profiles.delete(n) ? "deleted profile '" + n + "'" : "no profile named '" + n + "'");
			refreshProfiles();
		});

		String shareSub = "Share";
		button(dsl, "profileExport", "Export to clipboard", "Export", "Copies the profile picked above to the clipboard, so you can send it to someone. Your Spotify client ID is not part of it.", cat, shareSub, () -> {
			try {
				String n = picked();
				if (n.isEmpty() || !Profiles.exists(n)) { chat("pick the profile to export"); return; }
				Minecraft.getInstance().keyboardHandler.setClipboard(Profiles.exportText(n, false));
				chat("copied profile '" + n + "' to the clipboard");
			} catch (Exception e) {
				chat("could not export: " + e.getMessage());
			}
		});
		button(dsl, "profileImport", "Import from clipboard", "Import", "Adds the profile in the clipboard. It is saved under the name typed in New profile name, or under its own name.", cat, shareSub, () -> {
			try {
				String n = Profiles.importText(Minecraft.getInstance().keyboardHandler.getClipboard(), profileName);
				chat("imported profile '" + n + "'");
				refreshProfiles();
			} catch (Exception e) {
				chat("could not import: " + e.getMessage());
			}
		});
	}

	private static void spotify(ConfigDSL dsl) {
		button(dsl, "spotifyLogout", "Log out of Spotify", "Log out", "Same as /slyrics logout.", "Connectivity", "Spotify", () -> {
			Spotify.logout();
			chat("logged out.");
		});
	}

	private static void emojis(ConfigDSL dsl, Binder binder) throws ReflectiveOperationException {
		String cat = "Text Settings";
		String sub = "Emojis";
		for (String[] s : new String[][]{
			{"emojis", "Enable emojis", "Adds an emoji after matching words in the lyrics (love -> love ♥, diamond -> a real diamond icon). The word is kept."}}) {
			ConfigDSL.Prop<Object> p = bool(dsl, Config.class.getField(s[0]).getBoolean(Config.INSTANCE));
			p.setId(s[0]);
			p.setTitle(s[1]);
			p.setDescription(s[2]);
			p.setCategory(cat);
			p.setSubcategory(sub);
			binder.bind(p, Config.class.getField(s[0]));
		}
	}

	/** "switch" is a Java keyword, so the DSL method of that name can only be called by reflection. */
	@SuppressWarnings("unchecked")
	private static ConfigDSL.Prop<Object> bool(ConfigDSL dsl, boolean initial) throws ReflectiveOperationException {
		Method m = ConfigDSL.class.getMethod("switch", boolean.class);
		return (ConfigDSL.Prop<Object>) m.invoke(dsl, initial);
	}


}
