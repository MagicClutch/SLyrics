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
 * What SLyrics offers outside the Resourceful Config screen, rebuilt as OneConfig options: the profile actions, the media
 * player buttons, the Spotify logout and the emoji word lists. Every action calls the same method the old screen or command uses.
 */
final class SLyricsExtras {
	/** The last name typed into the profile field. */
	private static String profileName = "";

	/** Adds a value that lives in Config.INSTANCE and has to be kept in sync (see SLyricsOneConfig#sync). */
	interface Binder {
		void bind(ConfigDSL.Prop<Object> prop, Field field, boolean map);
	}

	private SLyricsExtras() {}

	static void add(ConfigDSL dsl, Binder binder) {
		try {
			profiles(dsl);
			media(dsl);
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
	private static final java.util.List<String> fontLabels = new ArrayList<>();
	private static final java.util.List<String> fontValues = new ArrayList<>();
	private static int fontLast = -1;

	private static void fontAdd(String label, String value) {
		fontLabels.add(label);
		fontValues.add(value);
	}

	private static void fonts(ConfigDSL dsl) {
		fontLabels.clear();
		fontValues.clear();
		fontAdd("Minecraft (default)", "minecraft:default");
		fontAdd("Minecraft uniform", "minecraft:uniform");
		fontAdd("Minecraft alternate (enchanting table)", "minecraft:alt");
		fontAdd("Minecraft illager", "minecraft:illageralt");
		String cur = Config.INSTANCE.font;
		if (!fontValues.contains(cur)) fontAdd(fontName(cur), cur);
		fontPick = dsl.dropdown(fontValues.indexOf(cur), fontLabels.toArray(new String[0]));
		fontLast = fontValues.indexOf(cur);
		fontPick.setId("fontPick");
		fontPick.setTitle("Choose font");
		fontPick.setDescription("Minecraft's fonts and every TrueType font installed on this computer (the installed ones appear a moment after the game started). Same as the Choose button and the font field.");
		fontPick.setCategory("Text Settings");
		fontPick.setSubcategory("Text");
		Thread t = new Thread(() -> {
			var found = dev.slyrics.font.SystemFonts.list();
			Minecraft.getInstance().execute(() -> {
				for (var e : found) {
					String v = dev.slyrics.font.UserFont.PREFIX + e.file().toAbsolutePath();
					if (!fontValues.contains(v)) fontAdd(e.name(), v);
				}
				fontRefill();
			});
		}, "SLyrics-Fonts-OneConfig");
		t.setDaemon(true);
		t.start();
	}

	private static String fontName(String value) {
		return value.startsWith(dev.slyrics.font.UserFont.PREFIX) ? java.nio.file.Path.of(value.substring(dev.slyrics.font.UserFont.PREFIX.length())).getFileName().toString() : value;
	}

	private static void fontRefill() {
		if (fontPick == null) return;
		fontPick.setOptionKeys(fontLabels.toArray(new String[0]));
		fontLast = Math.max(0, fontValues.indexOf(Config.INSTANCE.font));
		fontPick.getProperty().set(fontLast);
	}

	/** Dropdown to config and back: whichever side changed since the last tick wins. */
	private static void tickFont() {
		if (fontPick == null) return;
		Integer idx = fontPick.getProperty().get();
		if (idx == null || idx < 0 || idx >= fontValues.size()) return;
		if (idx != fontLast) { // picked in OneConfig
			fontLast = idx;
			if (!fontValues.get(idx).equals(Config.INSTANCE.font)) {
				Config.INSTANCE.font = fontValues.get(idx);
				dev.slyrics.ConfigSync.saveAll();
			}
		} else if (!fontValues.get(idx).equals(Config.INSTANCE.font)) { // changed elsewhere (font field, picker screen, profile)
			if (!fontValues.contains(Config.INSTANCE.font)) fontAdd(fontName(Config.INSTANCE.font), Config.INSTANCE.font);
			fontRefill();
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
	}

	private static void media(ConfigDSL dsl) {
		String cat = "Features";
		String sub = "Media player";
		button(dsl, "mediaPrevious", "Previous song", "Previous", "Same as the player's previous button and its key binding.", cat, sub, MediaPlayer::previous);
		button(dsl, "mediaPlayPause", "Play / pause", "Play / pause", "Same as the player's play/pause button and its key binding.", cat, sub, MediaPlayer::togglePlay);
		button(dsl, "mediaNext", "Next song", "Next", "Same as the player's next button and its key binding.", cat, sub, MediaPlayer::next);
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
			{"emojis", "Emojis", "Symbols after keywords (love -> love ♥). The word is kept."},
			{"emojiColors", "Emoji colors", "Give every emoji its own color instead of the text color."},
			{"emojiSprites", "Minecraft icons", "Use real Minecraft icons (items, blocks, hearts, ...) for words that have one; other words still use the symbols."}}) {
			ConfigDSL.Prop<Object> p = bool(dsl, Config.class.getField(s[0]).getBoolean(Config.INSTANCE));
			p.setId(s[0]);
			p.setTitle(s[1]);
			p.setDescription(s[2]);
			p.setCategory(cat);
			p.setSubcategory(sub);
			binder.bind(p, Config.class.getField(s[0]), false);
		}
		for (String[] s : new String[][]{
			{"emojiMap", "Emoji words", "One entry per word: word=symbol, e.g. love=♥. Stored in config/slyrics-extra.json."},
			{"emojiSpriteMap", "Icon words", "One entry per word: word=sprite, e.g. diamond=item:diamond, flower=block:poppy or heart=gui:hud/heart/full."}}) {
			Field f = Config.class.getField(s[0]);
			ConfigDSL.TextListProp p = dsl.textList(mapToLines(f.get(Config.INSTANCE)));
			p.setId(s[0]);
			p.setTitle(s[1]);
			p.setDescription(s[2]);
			p.setCategory(cat);
			p.setSubcategory(sub);
			p.setReorderable(false);
			@SuppressWarnings("unchecked") ConfigDSL.Prop<Object> prop = (ConfigDSL.Prop<Object>) (ConfigDSL.Prop<?>) p;
			binder.bind(prop, f, true);
		}
	}

	/** "switch" is a Java keyword, so the DSL method of that name can only be called by reflection. */
	@SuppressWarnings("unchecked")
	private static ConfigDSL.Prop<Object> bool(ConfigDSL dsl, boolean initial) throws ReflectiveOperationException {
		Method m = ConfigDSL.class.getMethod("switch", boolean.class);
		return (ConfigDSL.Prop<Object>) m.invoke(dsl, initial);
	}

	static String[] mapToLines(Object map) {
		var lines = new ArrayList<String>();
		for (var e : ((Map<?, ?>) map).entrySet()) lines.add(e.getKey() + "=" + e.getValue());
		return lines.toArray(new String[0]);
	}

	static Map<String, String> linesToMap(Object lines) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String line : (String[]) lines) {
			int i = line == null ? -1 : line.indexOf('=');
			if (i <= 0) continue;
			String word = line.substring(0, i).trim().toLowerCase(java.util.Locale.ROOT);
			String value = line.substring(i + 1).trim();
			if (!word.isEmpty() && !value.isEmpty()) out.put(word, value);
		}
		return out;
	}

}
