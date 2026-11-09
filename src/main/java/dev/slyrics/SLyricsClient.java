package dev.slyrics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> ConfigSync.tick());
		Spotify.start();
		AppleMusic.start();
		LyricsWorld.init();
		AfkCamera.init();
		// first start after installing: tell the user where to enter their own Spotify Client ID
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			if (Config.INSTANCE.clientId.isBlank()) {
				if (client.player != null) client.player.sendSystemMessage(Component.literal("SLyrics: enter your Spotify Client ID in /slyrics config (Spotify), then run /slyrics login. Redirect URI: " + Spotify.REDIRECT));
			}
		}));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("slyrics", "lyrics"), new LyricsHud());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("slyrics")
				.executes(ctx -> openConfig())
				.then(ClientCommands.literal("config").executes(ctx -> openConfig()))
				.then(ClientCommands.literal("login").executes(ctx -> login(ctx.getSource())))
				.then(ClientCommands.literal("logout").executes(ctx -> {
					Spotify.logout();
					ctx.getSource().sendFeedback(Component.literal("SLyrics: logged out."));
					return 1;
				}))
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
