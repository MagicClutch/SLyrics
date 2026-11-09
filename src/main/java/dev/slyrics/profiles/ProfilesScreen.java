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
		this.layout.setHeaderHeight(12 + 9 + 15);

		this.list = this.layout.addToContents(new ProfileList(this.minecraft));
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(6));
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

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.list != null) this.list.updateSize(this.width, this.layout);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		String act = Profiles.active();
		if (!act.isEmpty()) g.centeredText(this.font, Component.literal("Active profile: " + act + (Profiles.modified() ? " (changed, not saved)" : "")), this.width / 2, 22, 0xFF9FE08A);
		if (!message.isEmpty()) g.centeredText(this.font, Component.literal(message), this.width / 2, this.height - 46, 0xFFFFE14D);
		if (list != null && Profiles.list().isEmpty()) g.centeredText(this.font, Component.literal("No profiles yet. Type a name and press Save."), this.width / 2, this.height / 2, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private final class ProfileList extends ObjectSelectionList<ProfileList.Row> {
		ProfileList(Minecraft mc) {
			super(mc, ProfilesScreen.this.width, ProfilesScreen.this.height - 33 - 33, 33, 18);
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
