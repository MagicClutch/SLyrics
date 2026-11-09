package dev.slyrics.font;

import dev.slyrics.Config;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/**
 * Lets the font setting point at a font file on this computer ("system:/path/to/font.ttf"). The file is loaded into
 * Minecraft's font system under the id slyrics:user; everything else about the setting stays a normal font id.
 */
public final class UserFont {
	public static final String PREFIX = "system:";
	public static final Identifier ID = Identifier.fromNamespaceAndPath("slyrics", "user");

	/** Set by the font manager mixin. */
	public static UserFontHost host;
	private static String loaded = "";

	private UserFont() {}

	public static boolean isSystem(String font) {
		return font != null && font.startsWith(PREFIX);
	}

	/** Called every client tick: loads the chosen font file when the setting changed. */
	public static void tick() {
		String want = isSystem(Config.INSTANCE.font) ? Config.INSTANCE.font : "";
		if (want.equals(loaded)) return;
		if (host == null) return;
		loaded = want;
		host.slyrics$setUserFont(want.isEmpty() ? null : Path.of(want.substring(PREFIX.length())));
	}

	/** After a resource reload the font set is gone; the mixin asks for the current file again. */
	public static Path currentFile() {
		String f = Config.INSTANCE.font;
		return isSystem(f) ? Path.of(f.substring(PREFIX.length())) : null;
	}
}
