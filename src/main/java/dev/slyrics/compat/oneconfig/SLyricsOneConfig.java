package dev.slyrics.compat.oneconfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigButton;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import dev.slyrics.Config;
import dev.slyrics.ConfigSync;
import dev.slyrics.RcConfig;
import dev.slyrics.SLyricsClient;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.dsl.ConfigDSL;
import org.polyfrost.oneconfig.api.config.v1.dsl.Tree_extensionsKt;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The SLyrics page in OneConfig. It is built from the annotations of {@link RcConfig}, so every option has the same name,
 * category, order, range and comment as in the Resourceful Config screen. The values are not stored here: they are read from and
 * written to {@link Config#INSTANCE} (which Resourceful Config and the rest of the mod use), and the page is marked UI-only so
 * OneConfig never writes a file of its own for it.
 */
public final class SLyricsOneConfig extends org.polyfrost.oneconfig.api.config.v1.Config {
	/** One option on the page and the {@link Config} field behind it. */
	private static final class Binding {
		ConfigDSL.Prop<Object> prop;
		Field field;
		Object[] enumValues;
		boolean color;
		boolean map;
		Object last;

		/** What the Config field holds, as the OneConfig widget wants it. */
		Object toWidget(Object v) {
			if (map) return SLyricsExtras.mapToLines(v);
			if (color) return new PolyColor(0xFF000000 | parseHex((String) v), false, 1f);
			if (enumValues != null) {
				for (int i = 0; i < enumValues.length; i++) if (enumValues[i].toString().equals(v)) return i;
				return 0;
			}
			return v;
		}

		/** What the widget holds, as the Config field wants it. */
		Object toConfig(Object w) {
			if (map) return SLyricsExtras.linesToMap(w);
			if (color) return String.format("%06X", ((PolyColor) w).getArgb() & 0xFFFFFF);
			if (enumValues != null) return enumValues[Math.max(0, Math.min(enumValues.length - 1, (Integer) w))].toString();
			return w;
		}
	}

	private static final java.lang.reflect.Method SWITCH;
	static {
		try {
			SWITCH = ConfigDSL.class.getMethod("switch", boolean.class);
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private final List<Binding> bindings = new ArrayList<>();

	private SLyricsOneConfig() {
		super("slyrics", "SLyrics", Category.UTILITY);
	}

	static void register() {
		SLyricsOneConfig page = new SLyricsOneConfig();
		SLyricsHuds.register();
		OneConfigBridge.syncer = () -> {
			page.sync();
			SLyricsHuds.tick();
		};
	}

	/** Shows the SLyrics page in OneConfig's own screen. */
	static boolean open() {
		org.polyfrost.oneconfig.api.ui.v1.OneConfigUI.open(new org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute("slyrics", null));
		return true;
	}

	/** Used by SLyricsExtras for options that have no Resourceful Config entry. */
	private void addBinding(ConfigDSL.Prop<Object> prop, Field field, boolean map) {
		Binding b = new Binding();
		b.prop = prop;
		b.field = field;
		b.map = map;
		try {
			b.last = b.toWidget(field.get(Config.INSTANCE));
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		bindings.add(b);
	}

	private static int parseHex(String s) {
		try { return Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF; } catch (NumberFormatException e) { return 0xFFFFFF; }
	}

	/** Builds the page from RcConfig. The page is UI-only: the values stay in Config.INSTANCE / Resourceful Config. */
	@Override
	protected Tree makeTree() {
		ConfigDSL dsl = new ConfigDSL("slyrics", "SLyrics", "Spotify lyrics as floating words in the world");
		JsonObject lang = loadLang();
		try {
			var top = RcConfig.class.getAnnotation(com.teamresourceful.resourcefulconfig.api.annotations.Config.class);
			for (Class<?> cat : top.categories()) {
				com.teamresourceful.resourcefulconfig.api.annotations.Category c = cat.getAnnotation(com.teamresourceful.resourcefulconfig.api.annotations.Category.class);
				String catName = c.value();
				for (Class<?> sub : c.categories()) addCategory(dsl, lang, catName, sub);
				if (c.categories().length == 0) addCategory(dsl, lang, catName, cat);
			}
		} catch (Throwable t) {
			SLyricsClient.LOGGER.warn("Could not build the OneConfig page", t);
		}
		SLyricsExtras.add(dsl, this::addBinding);
		Tree tree = dsl.getTree();
		applyDevMode(tree);
		Tree_extensionsKt.setUiOnly(tree, true);
		return tree;
	}

	/** Options that stay visible with Developer mode off: everything else is hidden until it is switched on. */
	private static final java.util.Set<String> EASY = java.util.Set.of(
		"enabled", "musicSource", "offsetMs", "devMode", "clientId", "connect", "spotifyLogout",
		"font", "fontPick", "pickFont", "color", "textAnim", "sweepMode",
		"hologram", "skyMode", "lineMode", "hudMode", "mediaPlayer", "afkEnabled",
		"keyPlayPause", "keyNext", "keyPrevious",
		"profilePick", "profileLoad", "profileUpdate", "profileDelete", "profileName", "profileSave");

	/** Hides every option that is not in {@link #EASY} while the Developer mode switch is off. */
	private static void applyDevMode(Tree tree) {
		List<org.polyfrost.oneconfig.api.config.v1.Property<?>> all = new ArrayList<>();
		tree.onAllProps((path, p) -> all.add(p));
		org.polyfrost.oneconfig.api.config.v1.Property<?> dev = null;
		for (var p : all) if ("devMode".equals(p.getID())) dev = p;
		if (dev == null) return;
		@SuppressWarnings("unchecked") org.polyfrost.oneconfig.api.config.v1.Property<Boolean> devProp = (org.polyfrost.oneconfig.api.config.v1.Property<Boolean>) dev;
		for (var p : all) {
			if (EASY.contains(p.getID())) continue;
			p.addDisplayCondition(devProp, true);
		}
	}

	private void addCategory(ConfigDSL dsl, JsonObject lang, String catName, Class<?> cls) {
		com.teamresourceful.resourcefulconfig.api.annotations.Category sc = cls.getAnnotation(com.teamresourceful.resourcefulconfig.api.annotations.Category.class);
		String subName = sc == null ? catName : sc.value();
		for (Field f : cls.getDeclaredFields()) {
			try {
				if (!Modifier.isStatic(f.getModifiers())) continue;
				ConfigButton button = f.getAnnotation(ConfigButton.class);
				ConfigEntry entry = f.getAnnotation(ConfigEntry.class);
				Comment comment = f.getAnnotation(Comment.class);
				String desc = comment == null ? "" : comment.value();
				if (button != null && Runnable.class.isAssignableFrom(f.getType())) {
					Runnable action = (Runnable) f.get(null);
					if (f.getName().equals("editHud")) { // OneConfig's own HUD editor is the main one; the mod's editor stays as an extra
						ConfigDSL.RunnableProp main = dsl.button(SLyricsHuds::openEditor);
						main.setId("editHudOneConfig");
						main.setTitle("Open HUD editor");
						main.setDescription("Drag the lyric line, the karaoke panel and the media player in OneConfig's HUD editor. Also: /slyrics hud");
						main.setText("Open");
						main.setCategory("Features"); // not its own tab: that tab would stay empty with Developer mode off
						main.setSubcategory("HUD editor");
						ConfigDSL.RunnableProp classic = dsl.button(action);
						classic.setId(f.getName());
						classic.setTitle("Open classic HUD editor");
						classic.setDescription("The mod's own editor (the one used without OneConfig, also in Resourceful Config). " + desc);
						classic.setText("Open");
						classic.setCategory("Features");
						classic.setSubcategory("HUD editor");
						continue;
					}
					ConfigDSL.RunnableProp p = dsl.button(action);
					p.setId(f.getName());
					p.setTitle(button.title());
					p.setDescription(desc);
					p.setText(button.text());
					p.setCategory(catName);
					p.setSubcategory(subName);
					continue;
				}
				if (entry == null) continue;
				String id = entry.id();
				if (id.equals("clientId")) { // never shown: the field stays empty and only takes a new ID
					clientIdField(dsl, lang, catName, subName, desc);
					continue;
				}
				Field cf = Config.class.getField(id);
				Class<?> t = f.getType();
				Binding b = new Binding();
				b.field = cf;
				ConfigDSL.Prop<?> p;
				Object initial = cf.get(Config.INSTANCE);
				if (t == boolean.class) {
					p = (ConfigDSL.Prop<?>) SWITCH.invoke(dsl, (Boolean) initial); // "switch" is a Java keyword, so it can only be called by reflection
				} else if (t.isEnum()) {
					b.enumValues = t.getEnumConstants();
					String[] labels = new String[b.enumValues.length];
					for (int i = 0; i < labels.length; i++) labels[i] = b.enumValues[i].toString();
					p = dsl.dropdown((Integer) b.toWidget(initial), labels);
				} else if (f.isAnnotationPresent(ConfigOption.Color.class)) {
					b.color = true;
					ConfigDSL.ColorProp cp = dsl.color((PolyColor) b.toWidget(initial));
					cp.setAlpha(false);
					p = cp;
				} else if (f.isAnnotationPresent(ConfigOption.Slider.class) && f.isAnnotationPresent(ConfigOption.Range.class)) {
					ConfigOption.Range r = f.getAnnotation(ConfigOption.Range.class);
					if (t == int.class) {
						ConfigDSL.IntProp ip = dsl.slider((Integer) initial);
						ip.setMin((int) r.min());
						ip.setMax((int) r.max());
						ip.setStep(1f);
						p = ip;
					} else {
						ConfigDSL.FloatProp fp = dsl.slider((Float) initial);
						fp.setMin((float) r.min());
						fp.setMax((float) r.max());
						fp.setStep(0.1f);
						p = fp;
					}
				} else if (t == String.class) {
					p = dsl.text((String) initial);
				} else if (t == int.class) {
					p = dsl.number((Integer) initial);
				} else {
					p = dsl.number((Float) initial);
				}
				@SuppressWarnings("unchecked") ConfigDSL.Prop<Object> prop = (ConfigDSL.Prop<Object>) p;
				prop.setId(id);
				String key = "slyrics.config." + id;
				prop.setTitle(lang.has(key) ? lang.get(key).getAsString() : id);
				prop.setTitleKey(key);
				prop.setDescription(desc);
				boolean sweep = id.equals("sweepMode") || id.equals("sweepColor"); // word sweep lives under Features
				prop.setCategory(sweep ? "Features" : catName);
				prop.setSubcategory(sweep ? (id.equals("sweepMode") ? "Select features" : "Word sweep") : subName);
				b.prop = prop;
				b.last = b.toWidget(initial);
				bindings.add(b);
			} catch (ReflectiveOperationException | RuntimeException e) {
				SLyricsClient.LOGGER.warn("Skipped OneConfig option {}", f.getName(), e);
			}
		}
	}

	/** One star per character of the saved Client ID (nothing if none is saved). */
	private static String clientIdStars() {
		return "*".repeat(Config.INSTANCE.clientId.length());
	}

	/**
	 * The Spotify Client ID is never shown: the field holds one * per character of the saved ID. Pasting a new ID (32 hex
	 * characters) replaces it and the field turns back into stars; emptying the field removes the saved ID.
	 */
	private static void clientIdField(ConfigDSL dsl, JsonObject lang, String cat, String sub, String desc) {
		ConfigDSL.TextProp p = dsl.text(clientIdStars());
		p.setId("clientId");
		String key = "slyrics.config.clientId";
		p.setTitle(lang.has(key) ? lang.get(key).getAsString() : "Client ID");
		p.setTitleKey(key);
		p.setDescription(desc);
		p.setCategory(cat);
		p.setSubcategory(sub);
		p.getProperty().addCallback(v -> {
			String s = v == null ? "" : v.toString().trim();
			if (s.matches("[*]+")) return true; // the stars themselves
			if (s.isEmpty()) {
				if (!Config.INSTANCE.clientId.isEmpty()) {
					Config.INSTANCE.clientId = "";
					ConfigSync.saveAll();
				}
			} else if (s.matches("[0-9a-fA-F]{32}")) {
				Config.INSTANCE.clientId = s;
				ConfigSync.saveAll();
				net.minecraft.client.Minecraft.getInstance().execute(() -> p.getProperty().set(clientIdStars()));
			}
			return true;
		});
	}

	private static JsonObject loadLang() {
		try (var in = SLyricsOneConfig.class.getResourceAsStream("/assets/slyrics/lang/en_us.json")) {
			if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not read the language file", e);
		}
		return new JsonObject();
	}

	/** Called every client tick. Whichever side changed since the last pass wins: OneConfig edits go to the config, outside edits are shown. */
	private boolean logged;

	void sync() {
		SLyricsExtras.tick();
		if (!logged && !bindings.isEmpty()) {
			logged = true;
			SLyricsClient.LOGGER.info("OneConfig page ready: {} synced options, tree uiOnly={}", bindings.size(), Tree_extensionsKt.getUiOnly(getTree()));
		}
		boolean write = false;
		for (Binding b : bindings) {
			try {
				Object widget = b.prop.getProperty().get();
				Object cfgAsWidget = b.toWidget(b.field.get(Config.INSTANCE));
				if (!same(widget, b.last)) { // changed in OneConfig
					b.field.set(Config.INSTANCE, b.toConfig(widget));
					b.last = widget;
					write = true;
				} else if (!same(cfgAsWidget, b.last)) { // changed elsewhere (settings screen, command, profile)
					b.prop.getProperty().set(cfgAsWidget);
					b.last = cfgAsWidget;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				SLyricsClient.LOGGER.warn("OneConfig sync failed for {}", b.field.getName(), e);
			}
		}
		if (write) ConfigSync.saveAll();
	}

	private static boolean same(Object a, Object b) {
		if (a instanceof PolyColor x && b instanceof PolyColor y) return (x.getArgb() & 0xFFFFFF) == (y.getArgb() & 0xFFFFFF);
		if (a instanceof String[] x && b instanceof String[] y) return java.util.Arrays.equals(x, y);
		return a == null ? b == null : a.equals(b);
	}
}
