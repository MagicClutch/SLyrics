package dev.slyrics.font;

import java.nio.file.Path;

/** Implemented by the font manager through a mixin; null file removes the user font. */
public interface UserFontHost {
	void slyrics$setUserFont(Path file);
}
