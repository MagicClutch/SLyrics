package dev.slyrics;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Appends a symbol after certain words (Unicode, included in the Minecraft font). The word is kept. */
public final class Emojis {
	private static final Pattern WORD = Pattern.compile("[\\p{L}']+");

	private Emojis() {}

	public static Map<String, String> defaults() {
		Map<String, String> m = new java.util.LinkedHashMap<>();
		put(m, "♥", "love", "loving", "loved", "heart", "hearts", "baby", "darling", "kiss", "lover");
		put(m, "⚔", "fight", "fighting", "war", "battle", "kill", "sword", "enemy", "blood");
		put(m, "☠", "die", "dying", "dead", "death", "ghost", "grave", "hell", "devil");
		put(m, "★", "star", "stars", "shine", "shining", "shiny", "gold", "dream", "dreams");
		put(m, "♪", "music", "song", "sing", "singing", "dance", "dancing", "melody", "rhythm");
		put(m, "☀", "sun", "sunshine", "sunny", "summer", "day", "light");
		put(m, "☽", "moon", "night", "midnight", "dark", "tonight");
		put(m, "☂", "rain", "raining", "storm", "cry", "crying", "tears");
		put(m, "☁", "cloud", "clouds", "sky", "heaven", "fly", "flying");
		put(m, "☺", "smile", "smiling", "happy", "laugh", "fun");
		put(m, "☹", "sad", "lonely", "alone", "broken", "pain");
		put(m, "❁", "flower", "flowers", "rose", "roses", "spring");
		put(m, "♛", "king", "queen", "crown", "princess", "prince");
		put(m, "☮", "peace", "free", "freedom");
		put(m, "⚡", "thunder", "lightning", "electric", "power", "energy");
		return m;
	}

	private static void put(Map<String, String> m, String emoji, String... words) {
		for (String w : words) m.put(w, emoji);
	}

	/** All characters counted as emoji (drawn in the default font). */
	public static boolean isEmojiChar(char ch, Config c) {
		for (String v : c.emojiMap.values()) if (v.indexOf(ch) >= 0) return true;
		return false;
	}

	/** "I love you" -> "I love ♥ you" (sep=" "), in sky mode with sep="". */
	public static String decorate(String text, String sep, Config c) {
		if (!c.emojis || c.emojiMap.isEmpty()) return text;
		Matcher m = WORD.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String w = m.group();
			String e = c.emojiMap.get(w.toLowerCase(Locale.ROOT));
			m.appendReplacement(sb, Matcher.quoteReplacement(e == null ? w : w + sep + e));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
