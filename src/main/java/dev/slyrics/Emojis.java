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
		put(m, "♥", "amor", "amore", "amour", "aşk", "baby", "bacio", "beijo", "cheri", "chéri", "coeur", "coracao", "corazon", "corazón", "coração", "cuore", "cœur", "darling", "hart", "heart", "hearts", "herz", "herzen", "kalp", "kiss", "kuss", "liebe", "lieben", "liebst", "liefde", "love", "loved", "lover", "loving", "querer", "schatz", "любовь", "сердце");
		put(m, "⚔", "bataille", "batalha", "batalla", "battaglia", "battle", "bloed", "blood", "blut", "combat", "enemy", "epee", "espada", "feind", "fight", "fighting", "guerra", "guerre", "kampf", "kampfen", "kan", "kill", "krieg", "kämpfen", "lotta", "luta", "oorlog", "pelea", "sang", "sangre", "sangue", "savas", "savaş", "schwert", "spada", "strijd", "sword", "épée", "бой", "война", "кровь");
		put(m, "☠", "dead", "death", "devil", "diable", "diablo", "diavolo", "dood", "dying", "enfer", "fantasma", "fantome", "fantôme", "geist", "ghost", "grab", "graf", "grave", "inferno", "infierno", "mort", "morte", "morto", "muerta", "muerte", "muerto", "olum", "spook", "sterben", "teufel", "tod", "tomba", "tombe", "tot", "tumba", "ölüm", "призрак", "смерть");
		put(m, "★", "dream", "dreams", "dromen", "droom", "estrela", "estrelas", "estrella", "estrellas", "etoile", "etoiles", "glanzen", "gold", "reve", "ruya", "rêve", "rüya", "shine", "shining", "shiny", "sogni", "sogno", "sonho", "sonhos", "star", "stars", "stella", "stelle", "ster", "stern", "sterne", "sterren", "sueno", "sueño", "sueños", "traum", "traume", "träume", "yildiz", "yıldız", "étoile", "étoiles", "звезда", "мечта");
		put(m, "♪", "bailar", "ballare", "cancao", "cancion", "canción", "cantar", "cantare", "canzone", "canção", "chanson", "chanter", "dance", "dancing", "dansen", "danser", "dançar", "melodie", "melody", "music", "musica", "musik", "musique", "muziek", "muzik", "música", "müzik", "rhythm", "sarki", "sing", "singen", "singing", "song", "tanzen", "zingen", "şarkı", "музыка", "песня", "танец");
		put(m, "☀", "day", "ete", "gunes", "güneş", "licht", "light", "luce", "lumiere", "lumière", "luz", "sol", "sole", "soleil", "sommer", "sonne", "summer", "sun", "sunny", "sunshine", "verano", "verao", "verão", "zomer", "zon", "été", "свет", "солнце");
		put(m, "☽", "dark", "dunkel", "gece", "lua", "luna", "lune", "maan", "midnight", "minuit", "mitternacht", "mond", "moon", "nacht", "night", "noche", "noite", "notte", "nuit", "oscuro", "tonight", "луна", "ночь");
		put(m, "☂", "chorar", "chuva", "cry", "crying", "gözyaşı", "huilen", "lacrime", "lagrimas", "larmes", "llorar", "lluvia", "lágrimas", "piangere", "pioggia", "pleurer", "pluie", "rain", "raining", "regen", "storm", "sturm", "tears", "tempesta", "tempestade", "tempete", "tempête", "tormenta", "tranen", "tränen", "weinen", "yagmur", "yağmur", "дождь", "слезы", "слёзы");
		put(m, "☁", "bulut", "ceu", "ciel", "cielo", "cloud", "clouds", "céu", "fliegen", "fly", "flying", "gokyuzu", "gökyüzü", "heaven", "hemel", "himmel", "lucht", "nuage", "nuages", "nube", "nubes", "nuvem", "nuvens", "nuvola", "nuvole", "sky", "vliegen", "voar", "volar", "voler", "wolk", "wolke", "wolken", "небо", "облако");
		put(m, "☺", "blij", "felice", "feliz", "fun", "glimlach", "glucklich", "glücklich", "gülmek", "happy", "heureux", "lacheln", "lachen", "laugh", "lächeln", "mutlu", "reir", "reír", "ridere", "rir", "rire", "smile", "smiling", "sonrisa", "sorriso", "sourire", "spass", "spaß", "счастье", "улыбка");
		put(m, "☹", "acı", "allein", "alone", "brise", "brisé", "broken", "dolor", "dolore", "dor", "douleur", "eenzaam", "einsam", "lonely", "pain", "pijn", "quebrado", "roto", "sad", "schmerz", "seul", "seule", "solitario", "solitudine", "sozinho", "traurig", "triste", "uzgun", "verdriet", "yalniz", "yalnız", "üzgün", "боль", "грусть");
		put(m, "❁", "bloem", "bloemen", "blume", "blumen", "cicek", "fiore", "fiori", "fleur", "fleurs", "flor", "flores", "flower", "flowers", "fruhling", "frühling", "lente", "primavera", "printemps", "rose", "roses", "spring", "çiçek", "цветок");
		put(m, "♛", "coroa", "corona", "couronne", "crown", "king", "konig", "konigin", "koning", "koningin", "kral", "kraliçe", "krone", "kroon", "könig", "königin", "prince", "princesa", "princess", "principe", "principessa", "prinz", "prinzessin", "príncipe", "queen", "rainha", "regina", "rei", "reina", "reine", "rey", "roi", "королева", "король");
		put(m, "☮", "baris", "barış", "free", "freedom", "freiheit", "frieden", "liberdade", "liberta", "libertad", "liberte", "libertà", "liberté", "paix", "paz", "peace", "vrede", "vrijheid", "özgürlük");
		put(m, "⚡", "bliksem", "blitz", "donder", "donner", "eclair", "electric", "energy", "foudre", "fulmine", "gök", "lightning", "power", "raio", "rayo", "relampago", "relámpago", "simsek", "thunder", "tonnerre", "trovao", "trovão", "trueno", "tuono", "éclair", "şimşek", "гром", "молния");
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
