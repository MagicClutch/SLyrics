package dev.slyrics;

import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
		put(m, "☀", "day", "ete", "gunes", "güneş", "licht", "light", "luce", "lumiere", "lumière", "luz", "sol", "soleil", "sommer", "sonne", "summer", "sun", "sunny", "sunshine", "verano", "verao", "verão", "zomer", "zon", "été", "свет", "солнце");
		put(m, "☽", "dark", "dunkel", "gece", "lua", "luna", "lune", "maan", "midnight", "minuit", "mitternacht", "mond", "moon", "nacht", "night", "noche", "noite", "notte", "nuit", "oscuro", "tonight", "луна", "ночь");
		put(m, "☂", "chorar", "chuva", "cry", "crying", "gözyaşı", "huilen", "lacrime", "lagrimas", "larmes", "llorar", "lluvia", "lágrimas", "piangere", "pioggia", "pleurer", "pluie", "rain", "raining", "regen", "storm", "sturm", "tears", "tempesta", "tempestade", "tempete", "tempête", "tormenta", "tranen", "tränen", "weinen", "yagmur", "yağmur", "дождь", "слезы", "слёзы");
		put(m, "☁", "bulut", "ceu", "ciel", "cielo", "cloud", "clouds", "céu", "fliegen", "fly", "flying", "gokyuzu", "gökyüzü", "heaven", "hemel", "himmel", "lucht", "nuage", "nuages", "nube", "nubes", "nuvem", "nuvens", "nuvola", "nuvole", "sky", "vliegen", "voar", "volar", "voler", "wolk", "wolke", "wolken", "небо", "облако");
		put(m, "☺", "blij", "felice", "feliz", "fun", "glimlach", "glucklich", "glücklich", "gülmek", "happy", "heureux", "lacheln", "lachen", "laugh", "lächeln", "mutlu", "reir", "reír", "ridere", "rir", "rire", "smile", "smiling", "sonrisa", "sorriso", "sourire", "spass", "spaß", "счастье", "улыбка");
		put(m, "☹", "acı", "allein", "alone", "brise", "brisé", "broken", "dolor", "dolore", "dor", "douleur", "eenzaam", "einsam", "lonely", "pain", "pijn", "quebrado", "roto", "sad", "schmerz", "seul", "seule", "solitario", "solitudine", "sozinho", "traurig", "triste", "uzgun", "verdriet", "yalniz", "yalnız", "üzgün", "боль", "грусть");
		put(m, "❁", "bloem", "bloemen", "blume", "blumen", "cicek", "fiore", "fiori", "fleur", "fleurs", "flor", "flores", "flower", "flowers", "fruhling", "frühling", "lente", "primavera", "printemps", "rose", "roses", "spring", "çiçek", "цветок");
		put(m, "♛", "coroa", "corona", "couronne", "crown", "king", "konig", "konigin", "koning", "koningin", "kral", "kraliçe", "krone", "kroon", "könig", "königin", "prince", "princesa", "princess", "principe", "principessa", "prinz", "prinzessin", "príncipe", "queen", "rainha", "regina", "rei", "reina", "reine", "rey", "roi", "королева", "король");
		put(m, "☮", "baris", "barış", "free", "freedom", "freiheit", "frieden", "liberdade", "liberta", "libertad", "liberte", "libertà", "liberté", "paix", "paz", "peace", "vrede", "vrijheid", "özgürlük");
		put(m, "⚡", "bliksem", "blitz", "donder", "donner", "eclair", "electric", "energy", "foudre", "fulmine", "gök", "lightning", "power", "raio", "rayo", "relampago", "relámpago", "simsek", "thunder", "tonnerre", "trovao", "trovão", "trueno", "tuono", "éclair", "şimşek", "гром", "молния");
		put(m, "\u2615", "coffee", "tea", "kaffee", "tee", "café", "cafe", "caf\u00e9", "t\u00e9", "th\u00e9", "caffè", "caffe", "chá", "koffie", "thee");
		put(m, "\u260E", "phone", "call", "calling", "telefon", "anruf", "anrufen", "teléfono", "telefono", "llamada", "llamar", "téléphone", "telephone", "appel", "telefone", "ligar");
		put(m, "\u2744", "cold", "ice", "winter", "frozen", "freeze", "kalt", "eis", "frieren", "frío", "frio", "hielo", "invierno", "froid", "glace", "hiver", "freddo", "ghiaccio", "inverno", "gelo", "koud", "ijs", "soğuk", "buz");
		put(m, "\u2668", "hot", "heat", "steam", "heiß", "heiss", "hitze", "caliente", "calor", "chaud", "chaleur", "caldo", "quente", "heet", "sıcak", "sicak");
		put(m, "\u2604", "comet", "meteor", "komet", "cometa", "comète", "comete");
		put(m, "\u2718", "wrong", "never", "nein", "falsch", "nie", "niemals", "nunca", "jamais", "mai", "nooit");
		put(m, "\u2714", "correct", "richtig", "wahr", "vrai");
		put(m, "\u2622", "danger", "toxic", "poison", "gefahr", "giftig", "gift", "peligro", "veneno", "tóxico", "toxico", "poison", "pericolo", "veleno", "perigo", "veneno", "gevaar", "tehlike", "zehir");
		put(m, "\u2699", "gear", "machine", "robot", "zahnrad", "maschine", "engranaje", "máquina", "maquina", "machine", "ingranaggio", "macchina", "máquina", "robot");
		put(m, "\u2693", "anchor", "harbor", "port", "anker", "hafen", "ancla", "puerto", "ancre", "ancora", "porto", "âncora");
		put(m, "\u2660", "spade", "poker", "gamble", "cards", "karten", "glücksspiel", "cartas", "cartes", "carte", "jogo");
		put(m, "\u2666", "jewel", "gem", "gems", "juwel", "edelstein", "joya", "gema", "joyau", "gemme", "gioiello", "joia");
		put(m, "\u2618", "luck", "lucky", "clover", "glück", "gluck", "kleeblatt", "suerte", "trébol", "trebol", "chance", "fortune", "fortuna", "sorte", "trevo", "geluk", "şans", "sans");
		put(m, "\u2709", "mail", "message", "email", "nachricht", "post", "mensaje", "correo", "courrier", "messaggio", "mensagem", "bericht");
		put(m, "\u2702", "scissors", "schere", "tijeras", "ciseaux", "forbici", "tesoura", "schaar", "makas");
		put(m, "\u270E", "write", "writing", "pen", "schreiben", "stift", "escribir", "lápiz", "lapiz", "écrire", "ecrire", "stylo", "scrivere", "penna", "escrever", "caneta", "schrijven");
		put(m, "\u2696", "justice", "law", "gerechtigkeit", "gesetz", "justicia", "ley", "loi", "giustizia", "legge", "justiça", "lei");
		put(m, "\u26A0", "warning", "careful", "warnung", "vorsicht", "advertencia", "cuidado", "attention", "attenzione", "aviso", "waarschuwing");
		put(m, "\u262F", "balance", "harmony", "gleichgewicht", "harmonie", "equilibrio", "armonía", "armonia", "équilibre", "equilibre", "armonia", "evenwicht");
		put(m, "\u2603", "snowman", "schneemann", "muñeco", "bonhomme", "pupazzo", "boneco");
		return m;
	}

	/** Own color of each symbol, so emojis are colorful no matter what the text color is. */
	private static final Map<Character, Integer> COLORS = new java.util.HashMap<>();

	static {
		COLORS.put('\u2665', 0xFF3B4E); // heart: red
		COLORS.put('\u2694', 0xC8D6E5); // swords: steel
		COLORS.put('\u2620', 0xF2F2EC); // skull: bone white
		COLORS.put('\u2605', 0xFFD23F); // star: gold
		COLORS.put('\u266A', 0x6FE3FF); // note: cyan
		COLORS.put('\u2600', 0xFFC93C); // sun: orange-yellow
		COLORS.put('\u263D', 0xB9C3FF); // moon: pale lavender
		COLORS.put('\u2602', 0x5AB8FF); // umbrella: blue
		COLORS.put('\u2601', 0xDDEBFF); // cloud: light blue-white
		COLORS.put('\u263A', 0xFFD84A); // smile: yellow
		COLORS.put('\u2639', 0x7FA6D9); // sad: dull blue
		COLORS.put('\u2741', 0xFF7AC8); // flower: pink
		COLORS.put('\u265B', 0xFFC21A); // crown: gold
		COLORS.put('\u262E', 0x6BE38A); // peace: green
		COLORS.put('\u26A1', 0xFFE14D); // lightning: yellow
		COLORS.put('\u2615', 0xC98B4E); // coffee
		COLORS.put('\u260E', 0x7CF08C); // phone
		COLORS.put('\u2744', 0xA8E8FF); // snowflake
		COLORS.put('\u2668', 0xFF7A3D); // hot
		COLORS.put('\u2604', 0xFFB86B); // comet
		COLORS.put('\u2718', 0xFF4D4D); // cross
		COLORS.put('\u2714', 0x4DE07A); // check
		COLORS.put('\u2622', 0xB6F23C); // hazard
		COLORS.put('\u2699', 0xB8C2CC); // gear
		COLORS.put('\u2693', 0x6FA8E8); // anchor
		COLORS.put('\u2660', 0xE6E6F0); // spade
		COLORS.put('\u2666', 0xFF5D8F); // diamond suit
		COLORS.put('\u2618', 0x58D96B); // clover
		COLORS.put('\u2709', 0xF2E6C9); // mail
		COLORS.put('\u2702', 0xE0E6EE); // scissors
		COLORS.put('\u270E', 0xFFC857); // pencil
		COLORS.put('\u2696', 0xD9C27A); // scales
		COLORS.put('\u26A0', 0xFFCC33); // warning
		COLORS.put('\u262F', 0xE9ECF2); // balance
		COLORS.put('\u2603', 0xEAF6FF); // snowman
	}

	/** The symbol's own RGB color, or null for characters that are not emojis. */
	public static Integer colorOf(char ch) {
		return COLORS.get(ch);
	}

	// ------------------------------------------------------------------ Minecraft icons (sprites)

	/** Placeholder characters (private use area) standing for sprites inside the lyric text. */
	private static final List<String> SPRITE_SPECS = new ArrayList<>();
	private static final Map<String, Character> SPRITE_CHARS = new java.util.HashMap<>();

	/** Registers a sprite such as "item:diamond", "block:poppy" or "gui:hud/heart/full" and returns its placeholder char. */
	public static synchronized char spriteChar(String spec) {
		Character known = SPRITE_CHARS.get(spec);
		if (known != null) return known;
		char ch = (char) (0xE000 + SPRITE_SPECS.size());
		SPRITE_SPECS.add(spec);
		SPRITE_CHARS.put(spec, ch);
		return ch;
	}

	public static boolean isSprite(char ch) {
		return ch >= 0xE000 && ch - 0xE000 < SPRITE_SPECS.size();
	}

	/** The sprite a placeholder char stands for, or null. */
	public static AtlasSprite spriteOf(char ch) {
		if (!isSprite(ch)) return null;
		String spec = SPRITE_SPECS.get(ch - 0xE000);
		int colon = spec.indexOf(':');
		String kind = colon < 0 ? "item" : spec.substring(0, colon);
		String name = colon < 0 ? spec : spec.substring(colon + 1);
		Identifier atlas, sprite;
		switch (kind) {
			case "block" -> {
				atlas = Identifier.withDefaultNamespace("blocks");
				sprite = Identifier.withDefaultNamespace("block/" + name);
			}
			case "gui" -> {
				atlas = Identifier.withDefaultNamespace("gui");
				sprite = Identifier.withDefaultNamespace(name);
			}
			default -> {
				atlas = Identifier.withDefaultNamespace("items");
				sprite = Identifier.withDefaultNamespace("item/" + name);
			}
		}
		return new AtlasSprite(atlas, sprite);
	}

	/** Words (lowercase) -> sprite spec: real Minecraft item, block and HUD icons. */
	public static Map<String, String> spriteDefaults() {
		Map<String, String> m = new java.util.LinkedHashMap<>();
		sprites(m, "gui:hud/heart/full", "love", "loving", "loved", "lover", "heart", "hearts", "baby", "darling", "kiss", "kisses", "liebe", "lieben", "liebst", "herz", "herzen", "schatz", "kuss", "amor", "corazón", "corazon", "querer", "amour", "coeur", "cœur", "chéri", "cheri", "amore", "cuore", "bacio", "coração", "coracao", "beijo", "liefde", "hart", "aşk", "kalp");
		sprites(m, "gui:hud/food_full", "hungry", "hunger", "eat", "eating", "food", "dinner", "lunch", "essen", "hungrig", "comer", "hambre", "comida", "manger", "faim", "nourriture", "mangiare", "fame", "cibo", "fome", "eten", "honger", "yemek");
		sprites(m, "item:diamond", "diamond", "diamonds", "diamant", "diamanten", "diamante", "diamantes", "elmas");
		sprites(m, "item:emerald", "emerald", "money", "cash", "rich", "dollar", "dollars", "smaragd", "geld", "reich", "dinero", "rico", "argent", "riche", "soldi", "ricco", "dinheiro", "rijk", "para", "zengin");
		sprites(m, "item:gold_ingot", "gold", "golden", "goud", "altın", "altin", "oro", "ouro");
		sprites(m, "item:iron_ingot", "iron", "eisen", "hierro", "fer", "ferro", "ijzer", "demir");
		sprites(m, "item:coal", "coal", "kohle", "carbón", "carbon", "charbon", "carvão", "carvao", "kolen", "kömür");
		sprites(m, "item:copper_ingot", "copper", "kupfer", "cobre", "cuivre", "rame", "koper", "bakır");
		sprites(m, "item:netherite_ingot", "netherite");
		sprites(m, "item:apple", "apple", "apples", "apfel", "äpfel", "manzana", "pomme", "mela", "maçã", "maca", "appel", "elma");
		sprites(m, "item:bread", "bread", "brot", "pan", "pain", "pane", "pão", "brood", "ekmek");
		sprites(m, "item:cookie", "cookie", "cookies", "keks", "kekse", "galleta", "biscuit", "biscotto", "bolacha", "koekje", "kurabiye");
		sprites(m, "item:pumpkin_pie", "pie", "kuchen", "torte", "pastel", "gâteau", "gateau", "torta", "bolo", "taart", "pasta", "cake");
		sprites(m, "item:carrot", "carrot", "carrots", "karotte", "möhre", "mohre", "zanahoria", "carotte", "carota", "cenoura", "wortel", "havuç");
		sprites(m, "item:cod", "fish", "fisch", "pez", "poisson", "pesce", "peixe", "vis", "balık");
		sprites(m, "item:salmon", "salmon", "lachs", "salmón", "saumon", "salmone", "salmão");
		sprites(m, "item:cooked_beef", "steak", "beef", "meat", "fleisch", "carne", "viande", "bife", "vlees");
		sprites(m, "item:cooked_chicken", "chicken", "hähnchen", "huhn", "pollo", "poulet", "frango", "kip", "tavuk");
		sprites(m, "item:honey_bottle", "honey", "honig", "miel", "mel", "honing", "bal");
		sprites(m, "item:milk_bucket", "milk", "milch", "leche", "lait", "latte", "leite", "melk", "süt");
		sprites(m, "item:water_bucket", "water", "wasser", "agua", "eau", "acqua", "água", "suyu", "su");
		sprites(m, "item:lava_bucket", "lava");
		sprites(m, "item:fire_charge", "fire", "flame", "flames", "burn", "burning", "burns", "feuer", "flamme", "flammen", "brennen", "fuego", "llama", "quemar", "feu", "brûler", "bruler", "fuoco", "fiamma", "bruciare", "fogo", "chama", "queimar", "vuur", "vlam", "brandend", "ateş", "ates", "yanmak");
		sprites(m, "block:tnt_side", "bomb", "bombs", "explode", "explosion", "boom", "bang", "dynamite", "bombe", "explodieren", "explosión", "bomba", "esplosione", "explosão");
		sprites(m, "item:gunpowder", "gunpowder", "schießpulver");
		sprites(m, "item:diamond_sword", "sword", "swords", "fight", "fighting", "battle", "kill", "schwert", "kampf", "kämpfen", "kampfen", "espada", "pelea", "épée", "epee", "combat", "spada", "lotta", "luta", "zwaard", "strijd", "kılıç", "kilic", "savaş", "savas");
		sprites(m, "item:bow", "bow", "archer", "bogen", "arco", "arc", "boog", "yay");
		sprites(m, "item:arrow", "arrow", "arrows", "pfeil", "flecha", "flèche", "fleche", "freccia", "pijl");
		sprites(m, "item:diamond_pickaxe", "pickaxe", "mine", "mining", "dig", "digging", "miner", "spitzhacke", "bergbau", "minero", "minar", "mineur", "pioche", "minatore", "mineração");
		sprites(m, "item:iron_axe", "axe", "wood", "lumber", "axt", "holz", "hacha", "madera", "hache", "bois", "ascia", "legno", "machado", "madeira", "bijl");
		sprites(m, "item:mace", "hammer", "mace", "martillo", "marteau", "martello", "martelo", "hamer", "çekiç");
		sprites(m, "item:trident", "trident", "dreizack", "tridente");
		sprites(m, "item:crossbow_standby", "crossbow", "armbrust", "ballesta", "arbalète", "arbalete", "balestra", "besta");
		sprites(m, "item:fishing_rod", "fishing", "angeln", "pesca", "pêche", "peche", "pescaria", "vissen");
		sprites(m, "item:book", "book", "books", "story", "buch", "bücher", "geschichte", "libro", "libros", "historia", "livre", "livres", "histoire", "libri", "storia", "livro", "livros", "boek", "verhaal", "kitap", "hikaye");
		sprites(m, "item:paper", "paper", "letter", "brief", "papier", "carta", "lettre", "lettera", "papel", "kağıt");
		sprites(m, "item:map", "map", "maps", "karte", "landkarte", "mapa", "carte", "mappa", "kaart", "harita");
		sprites(m, "item:compass_16", "compass", "kompass", "brújula", "brujula", "boussole", "bussola", "bússola", "kompas", "pusula");
		sprites(m, "item:clock_00", "clock", "time", "zeit", "uhr", "tiempo", "reloj", "temps", "horloge", "tempo", "orologio", "relógio", "relogio", "tijd", "klok", "zaman", "saat", "hour", "hours", "stunde", "stunden");
		sprites(m, "item:trial_key", "key", "keys", "schlüssel", "schluessel", "llave", "clé", "cle", "chiave", "chave", "sleutel", "anahtar");
		sprites(m, "item:nether_star", "star", "stars", "shine", "shining", "shiny", "stern", "sterne", "glanzen", "estrella", "estrellas", "étoile", "etoile", "étoiles", "etoiles", "stella", "stelle", "estrela", "estrelas", "ster", "sterren", "yıldız", "yildiz");
		sprites(m, "item:ender_pearl", "pearl", "pearls", "perle", "perlen", "perla", "perlas", "pérola", "perola", "parel", "inci");
		sprites(m, "item:ender_eye", "eye", "eyes", "auge", "augen", "ojo", "ojos", "oeil", "yeux", "occhio", "occhi", "olho", "olhos", "oog", "ogen", "göz", "goz");
		sprites(m, "item:enchanted_book", "magic", "magical", "spell", "wizard", "witch", "magie", "zauber", "zauberer", "hexe", "mágica", "magia", "hechizo", "bruja", "sorcier", "sorcière", "sorciere", "incantesimo", "strega", "feitiço", "feitico", "bruxa", "toverij", "büyü", "buyu");
		sprites(m, "item:potion", "potion", "drink", "drinking", "drunk", "trank", "trinken", "betrunken", "poción", "pocion", "beber", "bebida", "boisson", "boire", "pozione", "bere", "drank", "drinken", "içki", "icki");
		sprites(m, "item:experience_bottle", "experience", "xp", "level", "learn", "lernen", "erfahrung", "experiencia", "experiência");
		sprites(m, "item:bone", "bone", "bones", "dog", "dogs", "knochen", "hund", "hunde", "hueso", "perro", "chien", "osso", "cane", "cão", "cao", "bot", "hond", "köpek", "kopek");
		sprites(m, "item:string", "cat", "cats", "katze", "katzen", "gato", "gatos", "chat", "chats", "gatto", "gatti", "kat", "katten", "kedi");
		sprites(m, "item:music_disc_cat", "music", "song", "songs", "sing", "singing", "dance", "dancing", "melody", "rhythm", "record", "musik", "lieder", "singen", "tanzen", "melodie", "música", "canción", "cancion", "canciones", "cantar", "bailar", "musique", "chanson", "chanter", "danser", "musica", "canzone", "cantare", "ballare", "canção", "cancao", "dançar", "muziek", "zingen", "dansen", "müzik", "muzik", "şarkı", "sarki");
		sprites(m, "item:bell", "bell", "bells", "glocke", "glocken", "campana", "cloche", "campanello", "sino", "bel", "zil");
		sprites(m, "item:candle", "candle", "candles", "kerze", "kerzen", "vela", "velas", "bougie", "bougies", "candela", "candele", "kaars", "mum");
		sprites(m, "block:torch", "torch", "torches", "fackel", "fackeln", "antorcha", "antorchas", "torche", "torcia", "torce", "tocha", "fakkel", "meşale");
		sprites(m, "item:lantern", "lantern", "lamp", "laterne", "lampe", "linterna", "lámpara", "lampara", "lanterne", "lampada", "lanterna", "lamba");
		sprites(m, "item:glowstone_dust", "glow", "glowing", "glows", "light", "lights", "bright", "leuchten", "licht", "brillar", "luz", "brilla", "lumière", "lumiere", "briller", "luce", "brillare", "brilho", "glinsteren", "ışık", "isik");
		sprites(m, "item:egg", "egg", "eggs", "ei", "eier", "huevo", "huevos", "oeuf", "oeufs", "uovo", "uova", "ovo", "ovos", "yumurta");
		sprites(m, "item:feather", "feather", "feathers", "angel", "angels", "heaven", "paradise", "feder", "federn", "engel", "himmel", "pluma", "plumas", "ángel", "cielo", "plume", "plumes", "ange", "ciel", "piuma", "piume", "angelo", "pena", "penas", "anjo", "céu", "ceu", "veer", "engelen", "hemel", "tüy", "melek", "cennet");
		sprites(m, "item:wheat", "wheat", "harvest", "weizen", "ernte", "trigo", "cosecha", "blé", "ble", "récolte", "recolte", "grano", "raccolta", "colheita", "tarwe", "oogst", "buğday");
		sprites(m, "item:snowball", "snow", "snowball", "schnee", "schneeball", "nieve", "neige", "neve", "sneeuw", "kar");
		sprites(m, "item:golden_helmet", "king", "queen", "crown", "royal", "prince", "princess", "könig", "konigin", "königin", "krone", "prinz", "prinzessin", "rey", "reina", "corona", "rei", "rainha", "coroa", "roi", "reine", "couronne", "regina", "principe", "principessa", "koning", "koningin", "kroon", "kral", "kraliçe", "kralice");
		sprites(m, "block:poppy", "flower", "flowers", "rose", "roses", "blume", "blumen", "rosen", "flor", "flores", "rosa", "fleur", "fleurs", "rosas", "fiore", "fiori", "bloem", "bloemen", "çiçek", "cicek");
		sprites(m, "block:oak_sapling", "tree", "trees", "baum", "bäume", "baume", "árbol", "arbol", "árboles", "arbre", "arbres", "albero", "alberi", "árvore", "arvore", "bomen", "ağaç", "agac");
		sprites(m, "item:oak_door", "home", "house", "door", "doors", "haus", "tür", "tur", "türen", "casa", "puerta", "hogar", "maison", "porte", "porta", "lar", "huis", "deur", "ev", "kapı", "kapi");
		sprites(m, "item:minecart", "car", "cars", "drive", "driving", "ride", "riding", "road", "auto", "autos", "fahren", "straße", "strasse", "coche", "coches", "conducir", "carretera", "voiture", "voitures", "conduire", "route", "macchina", "guidare", "strada", "carro", "conduzir", "estrada", "wagen", "rijden", "weg", "araba", "yol");
		sprites(m, "item:oak_boat", "boat", "ship", "sea", "ocean", "boot", "schiff", "meer", "ozean", "barco", "océano", "oceano", "bateau", "navire", "mer", "océan", "barca", "nave", "mare", "navio", "schip", "zee", "oceaan", "gemi", "deniz");
		sprites(m, "item:elytra", "fly", "flying", "flies", "wings", "wing", "fliegen", "flügel", "flugel", "flug", "volar", "alas", "ala", "voler", "ailes", "aile", "volare", "ali", "asa", "voar", "asas", "vliegen", "vleugels", "uçmak", "ucmak", "kanat");
		sprites(m, "item:firework_rocket", "rocket", "rockets", "firework", "fireworks", "party", "celebrate", "celebration", "rakete", "feuerwerk", "feiern", "fest", "cohete", "fuegos", "fiesta", "fusée", "fusee", "d'artifice", "fête", "fete", "razzo", "fuochi", "festa", "foguete", "fogos", "raket", "vuurwerk", "feest", "roket", "havai", "fişek");
		sprites(m, "item:phantom_membrane", "ghost", "ghosts", "phantom", "spirit", "geist", "geister", "fantasma", "fantasmas", "espíritu", "fantôme", "fantome", "fantômes", "esprit", "spirito", "espírito", "spook", "geest", "hayalet");
		sprites(m, "item:nether_brick", "inferno", "hölle", "holle", "infierno", "enfer", "hel", "cehennem");
		sprites(m, "item:totem_of_undying", "life", "live", "living", "alive", "survive", "leben", "lebendig", "überleben", "vida", "vivo", "vivir", "vie", "vivant", "vivre", "vita", "vivere", "viver", "leven", "levend", "yaşam", "yasam", "hayat");
		sprites(m, "item:spider_eye", "spider", "spiders", "spinne", "spinnen", "araña", "arana", "arañas", "araignée", "araignee", "ragno", "ragni", "aranha", "spin", "örümcek");
		sprites(m, "item:slime_ball", "slime", "schleim", "limo", "gluant", "melma", "gosma", "slijm");
		sprites(m, "item:rabbit_foot", "rabbit", "rabbits", "bunny", "hase", "kaninchen", "conejo", "lapin", "coniglio", "coelho", "konijn", "tavşan");
		sprites(m, "item:rotten_flesh", "zombie", "zombies", "untot", "zombi", "zombis", "zumbi");
		sprites(m, "block:red_wool", "sleep", "sleeping", "bed", "beds", "rest", "schlafen", "bett", "betten", "müde", "dormir", "cama", "camas", "durmiendo", "lit", "lits", "dormire", "letto", "letti", "slapen", "uyku", "yatak");
		sprites(m, "item:ghast_tear", "cry", "crying", "tear", "tears", "weinen", "träne", "tränen", "traenen", "llorar", "lágrima", "lágrimas", "lagrimas", "pleurer", "larme", "larmes", "piangere", "lacrima", "lacrime", "chorar", "huilen", "traan", "tranen", "ağlamak", "gözyaşı", "gozyasi");
		sprites(m, "item:amethyst_shard", "crystal", "crystals", "kristall", "kristalle", "cristal", "cristales", "cristaux", "cristallo", "cristalli", "kristal");
		sprites(m, "item:blaze_rod", "power", "strong", "strength", "energy", "kraft", "stark", "stärke", "starke", "energie", "poder", "fuerte", "fuerza", "energía", "energia", "pouvoir", "fort", "force", "énergie", "potere", "forte", "forza", "força");
		sprites(m, "item:wind_charge", "thunder", "lightning", "storm", "electric", "blitz", "donner", "gewitter", "sturm", "trueno", "rayo", "relámpago", "relampago", "tormenta", "tonnerre", "éclair", "eclair", "foudre", "orage", "tuono", "fulmine", "temporale", "trovão", "trovao", "raio", "tempestade", "donder", "bliksem", "onweer", "şimşek", "simsek", "gök");
		sprites(m, "item:dragon_breath", "dragon", "dragons", "drache", "drachen", "dragón", "dragones", "drago", "draghi", "dragão", "dragao", "draak", "draken", "ejderha");
		sprites(m, "item:name_tag", "name", "names", "namen", "nombre", "nom", "nome", "naam", "isim");
		sprites(m, "item:saddle", "horse", "horses", "pferd", "pferde", "reiten", "caballo", "caballos", "cheval", "chevaux", "cavallo", "cavalli", "cavalo", "cavalos", "paard", "paarden", "at");
		sprites(m, "item:shears", "cut", "cutting", "schneiden", "cortar", "couper", "tagliare", "knippen", "kesmek");
		sprites(m, "item:spyglass", "see", "seeing", "watch", "look", "looking", "sehen", "schauen", "ver", "mirar", "voir", "regarder", "vedere", "guardare", "olhar", "kijken", "zien", "görmek");
		sprites(m, "item:echo_shard", "echo", "echoes", "silence", "silent", "stille", "eco", "silencio", "écho", "sileno", "silenzio", "silêncio", "stilte", "sessizlik");
		sprites(m, "item:lapis_lazuli", "blue", "blau", "azul", "bleu", "blu", "blauw", "mavi");
		sprites(m, "item:redstone", "red", "rot", "rojo", "rouge", "rosso", "vermelho", "rood", "kırmızı");
		sprites(m, "item:quartz", "white", "weiß", "weiss", "blanco", "blanc", "bianco", "branco", "wit", "beyaz");
		return m;
	}

	private static void sprites(Map<String, String> m, String spec, String... words) {
		for (String w : words) m.put(w, spec);
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
		if (!c.emojis || (c.emojiMap.isEmpty() && c.emojiSpriteMap.isEmpty())) return text;
		Matcher m = WORD.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String w = m.group();
			String key = w.toLowerCase(Locale.ROOT);
			String spec = c.emojiSprites ? c.emojiSpriteMap.get(key) : null;
			String e = spec != null ? String.valueOf(spriteChar(spec)) : c.emojiMap.get(key);
			m.appendReplacement(sb, Matcher.quoteReplacement(e == null ? w : w + sep + e));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
