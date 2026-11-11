package dev.slyrics;

import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.base.CustomButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the media player with the mouse, scroll to resize it. Esc or Done saves. */
public final class MediaPlayerEditor extends Screen {
	private static final Track SAMPLE = new Track("sample", "Song title", "Artist", "", 215_000L);
	private boolean dragging;
	private double offX, offY;

	private MediaPlayerEditor() {
		super(Component.literal("Edit media player"));
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new MediaPlayerEditor()));
	}

	@Override
	protected void init() {
		MediaPlayer.editing = true;
		int bw = 70, gap = 6, x = (this.width - 2 * bw - gap) / 2, y = this.height - 30;
		CustomButton reset = addRenderableWidget(new CustomButton(bw, 18, Component.literal("Reset"), () -> {
			Config d = new Config();
			apply(d.mediaX, d.mediaY, d.mediaScale);
		}));
		reset.setPosition(x, y);
		CustomButton done = addRenderableWidget(new CustomButton(bw, 18, Component.literal("Done"), this::onClose));
		done.setPosition(x + bw + gap, y);
	}

	@Override
	public void removed() {
		MediaPlayer.editing = false;
	}

	@Override
	public void onClose() {
		ConfigSync.saveAll();
		this.minecraft.gui.setScreen(null);
	}

	/** Sets position (percent of the screen) and size on both the runtime config and the Resourceful Config fields, so they are not reverted. */
	private void apply(float xPercent, float yPercent, int scale) {
		scale = Math.max(50, Math.min(300, scale));
		float w = W() * scale / 100f, h = MediaPlayer.H * scale / 100f;
		xPercent = Math.max(0, Math.min(xPercent, Math.max(0, (this.width - w) / this.width * 100f)));
		yPercent = Math.max(0, Math.min(yPercent, Math.max(0, (this.height - h) / this.height * 100f)));
		Config c = Config.INSTANCE;
		c.mediaX = RcConfig.MediaCat.mediaX = xPercent;
		c.mediaY = RcConfig.MediaCat.mediaY = yPercent;
		c.mediaScale = RcConfig.MediaCat.mediaScale = scale;
	}

	private static int W() {
		return MediaPlayer.W;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, Component.literal("Edit media player").withColor(UIConstants.TEXT_TITLE), this.width / 2, 10, -1);
		g.centeredText(this.font, Component.literal("Drag to move, scroll to resize (" + Config.INSTANCE.mediaScale + "%)"), this.width / 2, 24, 0xFFAAAAAA);
		float[] p = MediaPlayer.place(this.width, this.height);
		Track t = Spotify.track != null ? Spotify.track : SAMPLE;
		long pos = Spotify.track != null ? Spotify.positionMs() : 83_000L;
		MediaPlayer.draw(g, t, pos, p[0], p[1], p[2], false);
		int x = Math.round(p[0]), y = Math.round(p[1]), w = Math.round(W() * p[2]), h = Math.round(MediaPlayer.H * p[2]);
		g.outline(x - 2, y - 2, w + 4, h + 4, dragging ? 0xFFFFFFFF : 0x88FFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		float[] p = MediaPlayer.place(this.width, this.height);
		if (e.button() == 0 && e.x() >= p[0] && e.x() < p[0] + W() * p[2] && e.y() >= p[1] && e.y() < p[1] + MediaPlayer.H * p[2]) {
			dragging = true;
			offX = e.x() - p[0];
			offY = e.y() - p[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (!dragging) return super.mouseDragged(e, dx, dy);
		apply((float) ((e.x() - offX) / this.width * 100.0), (float) ((e.y() - offY) / this.height * 100.0), Config.INSTANCE.mediaScale);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = false;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		Config c = Config.INSTANCE;
		apply(c.mediaX, c.mediaY, c.mediaScale + (sy > 0 ? 10 : sy < 0 ? -10 : 0));
		return true;
	}
}
