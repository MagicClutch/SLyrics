package dev.slyrics;

import java.util.List;

/**
 * The fonts to pick from: Minecraft's own and the free fonts that come with the mod (assets/slyrics/font, licenses in licenses/fonts;
 * OFL / Apache 2.0). Each mod font falls back to the Minecraft font for characters it does not have, such as emojis or other scripts.
 */
public final class Fonts {
	public record Entry(String label, String id) {}

	public static final List<Entry> ALL = List.of(
		new Entry("Minecraft", "minecraft:default"),
		new Entry("Poppins ExtraBold (clean caption)", "slyrics:poppins"),
		new Entry("Bebas Neue (tall caption)", "slyrics:bebas_neue"),
		new Entry("Anton (bold condensed)", "slyrics:anton"),
		new Entry("Luckiest Guy (YouTube caption)", "slyrics:luckiest_guy"),
		new Entry("Titan One (bubbly)", "slyrics:titan_one"),
		new Entry("Bangers (comic)", "slyrics:bangers"),
		new Entry("Permanent Marker (handwritten)", "slyrics:permanent_marker"),
		new Entry("Pacifico (script)", "slyrics:pacifico"),
		new Entry("Minecraft uniform", "minecraft:uniform"),
		new Entry("Minecraft enchanting table", "minecraft:alt"),
		new Entry("Minecraft illager", "minecraft:illageralt"));

	private Fonts() {}

	public static boolean isKnown(String id) {
		for (Entry e : ALL) if (e.id().equals(id)) return true;
		return false;
	}

	/** Position in {@link #ALL}, 0 (Minecraft) if unknown. */
	public static int indexOf(String id) {
		for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return i;
		return 0;
	}
}
