package dev.slyrics.profiles;

import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.ModSprites;
import com.teamresourceful.resourcefulconfig.client.components.base.BaseWidget;
import com.teamresourceful.resourcefulconfig.client.components.base.CustomButton;
import com.teamresourceful.resourcefulconfig.client.components.options.text.TextBox;
import com.teamresourceful.resourcefulconfig.client.utils.ListenableState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Save the current settings as a named profile, load one, or delete one. Drawn in the Resourceful Config look. */
public final class ProfilesScreen extends Screen {
	private static final int COLUMN = 320;
	private static final int ROW = 20;
	private final Screen parent;
	private ProfileList list;
	private TextBox name;
	private String message = "";
	private List<String> profiles = new ArrayList<>();
	private String nameText = "";

	public ProfilesScreen(Screen parent) {
		super(Component.literal("Profiles"));
		this.parent = parent;
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ProfilesScreen(mc.gui.screen())));
	}

	private static TextBox box(int w, String hint, int max, String initial) {
		TextBox t = new TextBox(w, 20, ListenableState.of(initial));
		t.setPlaceholder(hint, 0xFF8B8B8B);
		t.setMaxLength(max);
		t.setValue(initial);
		return t;
	}

	private CustomButton button(int x, int y, int w, String text, Runnable action) {
		CustomButton b = addRenderableWidget(new CustomButton(w, 18,Component.literal(text), action));
		b.setPosition(x, y);
		return b;
	}

	@Override
	protected void init() {
		int w = Math.min(COLUMN, this.width - 20);
		int x = (this.width - w) / 2;
		int y = 38;
		this.name = addRenderableWidget(box(w, "Profile name", 40, nameText));
		this.name.setPosition(x, y);

		int gap = 6;
		int bw = Math.min(70, (w - 3 * gap) / 4);
		int total = 4 * bw + 3 * gap;
		int bx = (this.width - total) / 2;
		int rowTwo = this.height - 12 - 18;
		int rowOne = rowTwo - 18 - gap;
		button(bx, rowOne, bw, "Save new", this::save);
		button(bx + (bw + gap), rowOne, bw, "Update", this::update);
		button(bx + 2 * (bw + gap), rowOne, bw, "Load", this::load);
		button(bx + 3 * (bw + gap), rowOne, bw, "Delete", this::delete);
		button(bx, rowTwo, bw, "Import", () -> { nameText = name.getValue(); this.minecraft.gui.setScreen(new SharePopup(this, true)); });
		button(bx + (bw + gap), rowTwo, bw, "Export", () -> { nameText = name.getValue(); this.minecraft.gui.setScreen(new SharePopup(this, false)); });
		button(bx + 2 * (bw + gap), rowTwo, 2 * bw + gap, "Done", this::onClose);

		int listTop = y + 24 + 4;
		int listBottom = rowOne - 22;
		this.list = addRenderableWidget(new ProfileList(w, Math.max(ROW, listBottom - listTop)));
		this.list.setPosition(x, listTop);
		refill();
	}

	@Override
	public void resize(int w, int h) {
		if (name != null) nameText = name.getValue();
		super.resize(w, h);
	}

	private void refill() {
		profiles = Profiles.list();
		if (list != null) list.clampScroll();
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

	void exportFile(String pathText) {
		try {
			if (!Profiles.exists(chosen())) {
				message = "Pick the profile to export";
				return;
			}
			java.nio.file.Path target = pathText.isBlank() ? Profiles.exportsFolder().resolve(chosen() + ".json") : java.nio.file.Path.of(pathText.trim());
			if (java.nio.file.Files.isDirectory(target)) target = target.resolve(chosen() + ".json");
			java.nio.file.Files.writeString(target, Profiles.exportText(chosen(), true));
			message = "Exported to " + target;
		} catch (Exception e) {
			message = "Could not export: " + e.getMessage();
		}
	}

	void exportClipboard() {
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

	void importFile(String pathText) {
		if (pathText.isBlank()) {
			message = "Type the file path, or drop a .json file on this window";
			return;
		}
		importFrom(java.nio.file.Path.of(pathText.trim()));
	}

	/** Small window for Import / Export: choose file or clipboard. */
	private static final class SharePopup extends Screen {
		private final ProfilesScreen owner;
		private final boolean importing;
		private TextBox file;

		SharePopup(ProfilesScreen owner, boolean importing) {
			super(Component.literal(importing ? "Import profile" : "Export profile"));
			this.owner = owner;
			this.importing = importing;
		}

		@Override
		protected void init() {
			int w = Math.min(260, this.width - 20);
			int x = (this.width - w) / 2;
			int y = this.height / 2 - 40;
			file = addRenderableWidget(box(w, importing ? "Path to a .json file (or drop it here)" : "Target file or folder (empty = exports folder)", 400, ""));
			file.setPosition(x, y + 20);
			int gap = 6;
			int bw = (w - gap) / 2;
			CustomButton a = addRenderableWidget(new CustomButton(bw, 18, Component.literal(importing ? "From file" : "To file"), () -> {
				if (importing) owner.importFile(file.getValue()); else owner.exportFile(file.getValue());
				onClose();
			}));
			a.setPosition(x, y + 48);
			CustomButton b = addRenderableWidget(new CustomButton(bw, 18, Component.literal(importing ? "From clipboard" : "To clipboard"), () -> {
				if (importing) owner.importClipboard(); else owner.exportClipboard();
				onClose();
			}));
			b.setPosition(x + bw + gap, y + 48);
			CustomButton c = addRenderableWidget(new CustomButton(w, 18, Component.literal("Cancel"), this::onClose));
			c.setPosition(x, y + 48 + 18 + gap);
		}

		@Override
		public void onFilesDrop(java.util.List<java.nio.file.Path> files) {
			owner.onFilesDrop(files);
			onClose();
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			g.fill(0, 0, this.width, this.height, UIConstants.BACKGROUND);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			super.extractRenderState(g, mouseX, mouseY, a);
			g.centeredText(this.font, Component.literal(importing ? "Import profile" : "Export '" + owner.chosen() + "'").withColor(UIConstants.TEXT_TITLE), this.width / 2, this.height / 2 - 40, -1);
		}

		@Override
		public void onClose() {
			this.minecraft.gui.setScreen(owner);
		}
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

	void importClipboard() {
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
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, UIConstants.BACKGROUND);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, Component.literal("Profiles").withColor(UIConstants.TEXT_TITLE), this.width / 2, 10, -1);
		String act = Profiles.active();
		String sub = act.isEmpty() ? "No active profile" : "Active profile: " + act + (Profiles.modified() ? " (changed, not saved)" : "");
		g.centeredText(this.font, Component.literal(sub).withColor(act.isEmpty() ? UIConstants.TEXT_PARAGRAPH : 0xFF9FE08A), this.width / 2, 22, -1);
		if (!message.isEmpty()) g.centeredText(this.font, Component.literal(message).withColor(0xFFFFE14D), this.width / 2, this.height - 10 - 20 - 4 - 20 - 4 - 20 - 4 - 10, -1);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private final class ProfileList extends BaseWidget {
		private double scroll;

		ProfileList(int w, int h) {
			super(w, h);
		}

		void clampScroll() {
			scroll = Math.max(0, Math.min(scroll, Math.max(0, profiles.size() * ROW - this.height)));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			g.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x40000000);
			g.enableScissor(getX(), getY(), getX() + this.width, getY() + this.height);
			String sel = Profiles.clean(name.getValue());
			for (int i = 0; i < profiles.size(); i++) {
				int ry = getY() + i * ROW - (int) scroll;
				if (ry + ROW < getY() || ry > getY() + this.height) continue;
				String p = profiles.get(i);
				boolean hover = mouseX >= getX() && mouseX < getX() + this.width && mouseY >= Math.max(ry, getY()) && mouseY < Math.min(ry + ROW, getY() + this.height);
				boolean picked = p.equals(sel);
				if (hover || picked) g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? ModSprites.BUTTON_HOVER : ModSprites.BUTTON, getX(), ry, this.width, ROW);
				boolean active = p.equals(Profiles.active());
				g.centeredText(font, Component.literal(p + (active ? "  (active)" : "")).withColor(picked || hover ? UIConstants.TEXT_TITLE : UIConstants.TEXT_PARAGRAPH), getX() + this.width / 2, ry + (ROW - 8) / 2, -1);
			}
			g.disableScissor();
			if (profiles.isEmpty()) g.centeredText(font, Component.literal("No profiles yet. Type a name and press Save new.").withColor(UIConstants.TEXT_PARAGRAPH), getX() + this.width / 2, getY() + this.height / 2 - 4, -1);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			int i = (int) ((event.y() - getY() + scroll) / ROW);
			if (i < 0 || i >= profiles.size()) return;
			name.setValue(profiles.get(i));
			if (doubleClick) load();
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double sx, double sy) {
			if (!isMouseOver(mx, my)) return false;
			scroll -= sy * ROW;
			clampScroll();
			return true;
		}
	}
}
