package dev.slyrics.compat.oneconfig;

import dev.slyrics.Config;
import dev.slyrics.MediaPlayer;
import dev.slyrics.Spotify;
import dev.slyrics.profiles.Profiles;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.config.v1.dsl.ConfigDSL;

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
		if (profileList == null || ++tickCount % 20 != 0) return;
		if (!Profiles.list().equals(shownProfiles)) refreshProfiles();
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
