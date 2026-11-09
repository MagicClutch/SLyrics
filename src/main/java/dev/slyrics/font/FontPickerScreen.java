package dev.slyrics.font;

import dev.slyrics.Config;
import dev.slyrics.ConfigSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A searchable list of the fonts built into Minecraft and every TrueType font installed on this computer. */
public final class FontPickerScreen extends Screen {
	private record Choice(String label, String value) {}

	private static final List<Choice> BUILT_IN = List.of(
		new Choice("Minecraft (default)", "minecraft:default"),
		new Choice("Minecraft uniform", "minecraft:uniform"),
		new Choice("Minecraft alternate (enchanting table)", "minecraft:alt"),
		new Choice("Minecraft illager", "minecraft:illageralt"));

	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final List<Choice> all = new ArrayList<>(BUILT_IN);
	private volatile boolean loading = true;
	private FontList list;
	private EditBox search;

	public FontPickerScreen(Screen parent) {
		super(Component.literal("Choose a font"));
		this.parent = parent;
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new FontPickerScreen(mc.gui.screen())));
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.search = header.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.empty()));
		this.search.setHint(Component.literal("Search fonts...").withStyle(EditBox.SEARCH_HINT_STYLE));
		this.search.setResponder(s -> refill());
		this.layout.setHeaderHeight(12 + 9 + 15);

		this.list = this.layout.addToContents(new FontList(this.minecraft));
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(200).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		refill();
		this.setInitialFocus(this.search);

		if (loading && !scanning) {
			scanning = true;
			Thread t = new Thread(() -> {
				List<SystemFonts.Entry> found = SystemFonts.list();
				List<Choice> add = new ArrayList<>();
				for (SystemFonts.Entry e : found) add.add(new Choice(e.name(), UserFont.PREFIX + e.file().toAbsolutePath()));
				Minecraft.getInstance().execute(() -> {
					all.addAll(add);
					loading = false;
					scanning = false;
					refill();
				});
			}, "SLyrics-Fonts");
			t.setDaemon(true);
			t.start();
		}
	}

	private boolean scanning;

	private void refill() {
		if (list == null) return;
		String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
		List<FontList.Row> rows = new ArrayList<>();
		for (Choice c : all) if (q.isEmpty() || c.label().toLowerCase(Locale.ROOT).contains(q)) rows.add(list.new Row(c));
		list.replaceEntries(rows);
		list.refreshScrollAmount();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) this.list.updateSize(this.width, this.layout);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (loading) g.centeredText(this.font, Component.literal("Loading installed fonts..."), this.width / 2, this.height / 2, 0xFFAAAAAA);
	}

	private void pick(Choice c) {
		Config.INSTANCE.font = c.value();
		ConfigSync.saveAll();
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private final class FontList extends ObjectSelectionList<FontList.Row> {
		FontList(Minecraft mc) {
			super(mc, FontPickerScreen.this.width, FontPickerScreen.this.height - 33 - 33, 33, 18);
		}

		@Override
		public int getRowWidth() {
			return super.getRowWidth() + 100;
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			private final Choice choice;

			Row(Choice choice) {
				this.choice = choice;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
				boolean current = choice.value().equals(Config.INSTANCE.font);
				g.centeredText(FontPickerScreen.this.font, Component.literal(choice.label()), FontList.this.width / 2, this.getContentYMiddle() - 9 / 2, current ? 0xFFFFE14D : -1);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				FontList.this.setSelected(this);
				if (doubleClick) pick(choice);
				return super.mouseClicked(event, doubleClick);
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isSelection()) {
					pick(choice);
					return true;
				}
				return super.keyPressed(event);
			}

			@Override
			public Component getNarration() {
				return Component.literal(choice.label());
			}
		}
	}
}
