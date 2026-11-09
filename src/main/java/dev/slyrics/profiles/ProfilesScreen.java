package dev.slyrics.profiles;

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

/** Save the current settings as a named profile, load one, or delete one. */
public final class ProfilesScreen extends Screen {
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private ProfileList list;
	private EditBox name;
	private EditBox path;
	private String message = "";

	public ProfilesScreen(Screen parent) {
		super(Component.literal("Profiles"));
		this.parent = parent;
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ProfilesScreen(mc.gui.screen())));
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.name = header.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.empty()));
		this.name.setHint(Component.literal("Profile name").withStyle(EditBox.SEARCH_HINT_STYLE));
		this.name.setMaxLength(40);
		this.path = header.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.empty()));
		this.path.setHint(Component.literal("File path (optional, or drop a .json here)").withStyle(EditBox.SEARCH_HINT_STYLE));
		this.path.setMaxLength(400);
		this.layout.setHeaderHeight(12 + 9 + 15 + 19);
		this.layout.setFooterHeight(54);

		this.list = this.layout.addToContents(new ProfileList(this.minecraft));
		LinearLayout footerBox = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
		footerBox.defaultCellSetting().alignHorizontallyCenter();
		LinearLayout share = footerBox.addChild(LinearLayout.horizontal().spacing(4));
		share.addChild(Button.builder(Component.literal("Export > file"), b -> exportFile()).width(76).build());
		share.addChild(Button.builder(Component.literal("Export > clipboard"), b -> exportClipboard()).width(96).build());
		share.addChild(Button.builder(Component.literal("Import < file"), b -> importFile()).width(76).build());
		share.addChild(Button.builder(Component.literal("Import < clipboard"), b -> importClipboard()).width(96).build());
		LinearLayout footer = footerBox.addChild(LinearLayout.horizontal().spacing(6));
		footer.addChild(Button.builder(Component.literal("Save new"), b -> save()).width(62).build());
		footer.addChild(Button.builder(Component.literal("Update"), b -> update()).width(62).build());
		footer.addChild(Button.builder(Component.literal("Load"), b -> load()).width(62).build());
		footer.addChild(Button.builder(Component.literal("Delete"), b -> delete()).width(62).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(62).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		refill();
		this.setInitialFocus(this.name);
	}

	private void refill() {
		List<ProfileList.Row> rows = new ArrayList<>();
		for (String n : Profiles.list()) rows.add(list.new Row(n));
		list.replaceEntries(rows);
		list.refreshScrollAmount();
	}

	private String chosen() {
		return Profiles.clean(name.getValue());
	}

	/** Saves under a new name; never replaces an existing profile (that is what Update is for). */
	private void save() {
		try {
			if (chosen().isEmpty()) {
				message = "Type a name first";
			} else if (Profiles.exists(chosen())) {
				message = "'" + chosen() + "' already exists. Use Update to replace it, or pick another name";
			} else {
				message = "Saved '" + Profiles.save(chosen()) + "'";
				refill();
			}
		} catch (Exception e) {
			message = "Could not save: " + e.getMessage();
		}
	}

	/** Replaces the selected profile with the current settings. */
	private void update() {
		try {
			if (!Profiles.exists(chosen())) {
				message = "Pick an existing profile to update";
			} else {
				message = "Updated '" + Profiles.save(chosen()) + "'";
			}
		} catch (Exception e) {
			message = "Could not save: " + e.getMessage();
		}
	}

	private void load() {
		try {
			message = chosen().isEmpty() ? "Pick or type a profile" : Profiles.load(chosen()) ? "Loaded '" + chosen() + "'" : "No profile named '" + chosen() + "'";
		} catch (Exception e) {
			message = "Could not load: " + e.getMessage();
		}
	}

	private void delete() {
		message = !chosen().isEmpty() && Profiles.delete(chosen()) ? "Deleted '" + chosen() + "'" : "No profile named '" + chosen() + "'";
		refill();
	}

	private void exportFile() {
		try {
			if (!Profiles.exists(chosen())) {
				message = "Pick the profile to export";
				return;
			}
			java.nio.file.Path target = path.getValue().isBlank() ? Profiles.exportsFolder().resolve(chosen() + ".json") : java.nio.file.Path.of(path.getValue().trim());
			if (java.nio.file.Files.isDirectory(target)) target = target.resolve(chosen() + ".json");
			java.nio.file.Files.writeString(target, Profiles.exportText(chosen(), true));
			message = "Exported to " + target;
		} catch (Exception e) {
			message = "Could not export: " + e.getMessage();
		}
	}

	private void exportClipboard() {
		try {
			if (!Profiles.exists(chosen())) {
				message = "Pick the profile to export";
				return;
			}
			this.minecraft.keyboardHandler.setClipboard(Profiles.exportText(chosen(), false));
			message = "Copied '" + chosen() + "' to the clipboard";
		} catch (Exception e) {
			message = "Could not export: " + e.getMessage();
		}
	}

	private void importFile() {
		if (path.getValue().isBlank()) {
			message = "Type the file path above, or drop a .json file on this window";
			return;
		}
		importFrom(java.nio.file.Path.of(path.getValue().trim()));
	}

	private void importFrom(java.nio.file.Path file) {
		try {
			String text = java.nio.file.Files.readString(file);
			String fallback = file.getFileName().toString().replaceAll("(?i)\\.json$", "");
			String got = Profiles.importText(text, name.getValue(), fallback);
			message = "Imported as '" + got + "'";
			refill();
		} catch (Exception e) {
			message = "Could not import: " + e.getMessage();
		}
	}

	private void importClipboard() {
		try {
			String got = Profiles.importText(this.minecraft.keyboardHandler.getClipboard(), name.getValue());
			message = "Imported as '" + got + "'";
			refill();
		} catch (Exception e) {
			message = "Could not import: " + e.getMessage();
		}
	}

	@Override
	public void onFilesDrop(java.util.List<java.nio.file.Path> files) {
		for (java.nio.file.Path f : files) {
			if (f.getFileName().toString().toLowerCase().endsWith(".json")) importFrom(f);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) this.list.updateSize(this.width, this.layout);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		String act = Profiles.active();
		if (!act.isEmpty()) g.centeredText(this.font, Component.literal("Active profile: " + act + (Profiles.modified() ? " (changed, not saved)" : "")), this.width / 2, this.height - 70, 0xFF9FE08A);
		if (!message.isEmpty()) g.centeredText(this.font, Component.literal(message), this.width / 2, this.height - 58, 0xFFFFE14D);
		if (list != null && Profiles.list().isEmpty()) g.centeredText(this.font, Component.literal("No profiles yet. Type a name and press Save."), this.width / 2, this.height / 2, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private final class ProfileList extends ObjectSelectionList<ProfileList.Row> {
		ProfileList(Minecraft mc) {
			super(mc, ProfilesScreen.this.width, ProfilesScreen.this.height - 52 - 54, 52, 18);
		}

		@Override
		public int getRowWidth() {
			return super.getRowWidth() + 60;
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			private final String profile;

			Row(String profile) {
				this.profile = profile;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
				g.centeredText(ProfilesScreen.this.font, Component.literal(profile), ProfileList.this.width / 2, this.getContentYMiddle() - 9 / 2, -1);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				ProfileList.this.setSelected(this);
				name.setValue(profile);
				if (doubleClick) load();
				return super.mouseClicked(event, doubleClick);
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isSelection()) {
					name.setValue(profile);
					load();
					return true;
				}
				return super.keyPressed(event);
			}

			@Override
			public Component getNarration() {
				return Component.literal(profile);
			}
		}
	}
}
