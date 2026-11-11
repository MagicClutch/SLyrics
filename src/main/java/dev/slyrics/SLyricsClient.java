package dev.slyrics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class SLyricsClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("SLyrics");

	@Override
	public void onInitializeClient() {
		Config.load();
		ConfigSync.init();
		dev.slyrics.profiles.Profiles.ensureDefaults();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
			ConfigSync.tick();
			dev.slyrics.font.UserFont.tick();
		});
		dev.slyrics.profiles.SaveProfileButton.init();
		Spotify.start();
		AppleMusic.start();
		LyricsWorld.init();
		AfkCamera.init();
		MediaPlayer.init();
		// first start after installing: tell the user where to enter their own Spotify Client ID
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			Spotify.resetNoLyricsTimer();
			if ("Spotify".equals(Config.INSTANCE.musicSource) && Config.INSTANCE.clientId.isBlank()) {
				if (client.player != null) client.player.sendSystemMessage(Component.literal("SLyrics: enter your Spotify Client ID in /slyrics config (Spotify), then run /slyrics login. Redirect URI: " + Spotify.REDIRECT));
			}
		}));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("slyrics", "lyrics"), new LyricsHud());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("slyrics")
				.executes(ctx -> openConfig())
				.then(ClientCommands.literal("config").executes(ctx -> openConfig()))
				.then(ClientCommands.literal("hud").executes(ctx -> {
					HudEditor.open();
					return 1;
				}))
				.then(ClientCommands.literal("login").executes(ctx -> login(ctx.getSource())))
				.then(ClientCommands.literal("logout").executes(ctx -> {
					Spotify.logout();
					ctx.getSource().sendFeedback(Component.literal("SLyrics: logged out."));
					return 1;
				}))
				.then(ClientCommands.literal("profile")
					.then(ClientCommands.literal("list").executes(ctx -> {
						var names = dev.slyrics.profiles.Profiles.list();
						ctx.getSource().sendFeedback(Component.literal("SLyrics profiles: " + (names.isEmpty() ? "none yet" : String.join(", ", names))));
						return 1;
					}))
					.then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
						try {
							String raw = StringArgumentType.getString(ctx, "name");
							if (dev.slyrics.profiles.Profiles.exists(raw)) {
								ctx.getSource().sendError(Component.literal("SLyrics: '" + raw + "' already exists. Use /slyrics profile update to replace the active profile"));
								return 0;
							}
							String n = dev.slyrics.profiles.Profiles.save(raw);
							ctx.getSource().sendFeedback(Component.literal(n == null ? "SLyrics: invalid profile name" : "SLyrics: saved profile '" + n + "'"));
						} catch (Exception e) {
							ctx.getSource().sendError(Component.literal("SLyrics: could not save: " + e.getMessage()));
						}
						return 1;
					})))
					.then(ClientCommands.literal("update").executes(ctx -> {
						try {
							String act = dev.slyrics.profiles.Profiles.active();
							if (act.isEmpty() || !dev.slyrics.profiles.Profiles.exists(act)) {
								ctx.getSource().sendError(Component.literal("SLyrics: no active profile. Load or save one first"));
								return 0;
							}
							dev.slyrics.profiles.Profiles.save(act);
							ctx.getSource().sendFeedback(Component.literal("SLyrics: updated profile '" + act + "'"));
						} catch (Exception e) {
							ctx.getSource().sendError(Component.literal("SLyrics: could not save: " + e.getMessage()));
						}
						return 1;
					}))
					.then(ClientCommands.literal("load").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
						try {
							String n = StringArgumentType.getString(ctx, "name");
							if (dev.slyrics.profiles.Profiles.load(n)) ctx.getSource().sendFeedback(Component.literal("SLyrics: loaded profile '" + n + "'"));
							else ctx.getSource().sendError(Component.literal("SLyrics: no profile named '" + n + "'"));
						} catch (Exception e) {
							ctx.getSource().sendError(Component.literal("SLyrics: could not load: " + e.getMessage()));
						}
						return 1;
					})))
					.then(ClientCommands.literal("delete").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
						String n = StringArgumentType.getString(ctx, "name");
						if (dev.slyrics.profiles.Profiles.delete(n)) ctx.getSource().sendFeedback(Component.literal("SLyrics: deleted profile '" + n + "'"));
						else ctx.getSource().sendError(Component.literal("SLyrics: no profile named '" + n + "'"));
						return 1;
					}))))
				.then(ClientCommands.literal("toggle").executes(ctx -> {
					Config.INSTANCE.enabled = !Config.INSTANCE.enabled;
					ConfigSync.saveAll();
					ctx.getSource().sendFeedback(Component.literal("SLyrics: " + (Config.INSTANCE.enabled ? "on" : "off")));
					return 1;
				}))
				.then(ClientCommands.literal("offset")
					.then(ClientCommands.argument("ms", IntegerArgumentType.integer(-10000, 10000)).executes(ctx -> {
						Config.INSTANCE.offsetMs = IntegerArgumentType.getInteger(ctx, "ms");
						ConfigSync.saveAll();
						ctx.getSource().sendFeedback(Component.literal("SLyrics: Offset " + Config.INSTANCE.offsetMs + " ms"));
						return 1;
					})))));
	}

	private static int openConfig() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(ConfigSync::openScreen);
		return 1;
	}

	private static int login(FabricClientCommandSource src) {
		if (Config.INSTANCE.clientId.isBlank()) {
			src.sendError(Component.literal("Set your Spotify Client ID in /slyrics config first (Redirect URI: " + Spotify.REDIRECT + ")"));
			return 0;
		}
		try {
			String url = Spotify.beginLogin(msg -> Minecraft.getInstance().execute(() ->
				src.sendFeedback(Component.literal("SLyrics: " + msg))));
			src.sendFeedback(Component.literal("SLyrics: Click here to log in to Spotify")
				.withStyle(Style.EMPTY.withUnderlined(true).withColor(0x1DB954).withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
		} catch (Exception e) {
			src.sendError(Component.literal("Failed to start login: " + e.getMessage()));
		}
		return 1;
	}
}
