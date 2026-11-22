<p align="center"><img src="docs/logo.png" alt="SLyrics logo" width="128"></p>

# SLyrics

**Synced lyrics of the song you are listening to, as part of your Minecraft world.**

Words pop up around your crosshair the moment they are sung, huge words drift through the sky, stick to walls like stickers, or show up in a karaoke panel. Add a built-in media player, cinematic AFK camera shots and a lot of ways to style it all.

> Client-side Fabric mod for **Minecraft 26.3**. Install it only on your own game. It works on any server and needs nothing on the server side.

---

## Quick start

1. Install the mod and its [dependencies](#requirements).
2. Start the game and run `/slyrics config`.
3. Pick your music source under **Connectivity** ([Spotify](#spotify) or [other players](#other-players-no-account-windows-and-linux)).
4. Play a song. The lyrics start by themselves.

Lyrics come from [LRCLIB](https://lrclib.net) (free, no key needed). Not every song has synced lyrics there.

---

## Features

### Lyrics in the world
Turn on any combination. Every word is sent to one of the enabled modes.

| Mode | What it does |
|---|---|
| **Aura** | Each word pops up around your crosshair when it is sung, glowing and tilted toward you, always whole on screen and never on top of another word. |
| **Sky** | Huge words far away that flash up and drift like clouds, always above the horizon and in front of open sky. |
| **Stick on blocks** | Words stick flat onto the walls and floors you look at, like stickers. |
| **Floating** | Words keep drifting through the world instead of standing still. |

Extras: **Spread** sets how far over the screen Aura and Sky words go (from close around the middle to about 3/4 of the screen). Words are placed where you are heading (also when flying with an elytra), are hidden behind blocks and come forward to just in front of the ground when you look down. Plus 3D text, glow, outline, motion blur, bounce, flicker and follow-me.

### Lyrics on the screen
- **Lyric line:** the current line at the bottom. Limit how many words show at once, or let it **scroll** smoothly like a ticker with the sung word always in the middle.
- **Karaoke mode:** a small panel with the previous, current and next line. The current line fills in while it is sung.
- **Word sweep:** the sung word is colored from left to right in time with the singing.

### Media player
A small window with cover, title, progress bar and previous / play-pause / next buttons.
- Click the buttons in chat or the pause menu.
- Or bind keys under **Controls -> SLyrics**.
- Hold the progress bar to scrub, let go to jump.

### HUD editor
Run `/slyrics hud` to open OneConfig's HUD editor, then drag and resize the media player, karaoke panel and lyric line.

### AFK camera
After you are idle, the camera leaves first person and plays cinematic shots around you (orbit, rise, fly-by, push in, ...) with the lyrics in view. Any input ends it.

### Look
- Minecraft's fonts or one of the caption fonts that come with the mod: Poppins, Bebas Neue, Anton, Luckiest Guy, Titan One, Bangers, Permanent Marker, Pacifico.
- Text animations: TikTok pop, Slam, Shake, Wobble, Wave, Spin in, Slide up.
- Emojis: real Minecraft icons or colored symbols after keywords in many languages (love ♥, fight ⚔, ...).

### Profiles
Save all settings, including the HUD layout, under a name. Load, update and delete them, or share them through the clipboard (Profiles tab > Share).

---

## Commands

All commands are client-side and need no permissions.

| Command | What it does |
|---|---|
| `/slyrics config` | Open the settings. |
| `/slyrics hud` | Open the HUD editor. |
| `/slyrics toggle` | Turn the mod on or off. |
| `/slyrics offset <ms>` | Shift the lyrics timing, from -10000 to 10000 milliseconds. Use this if the lyrics are early or late. |
| `/slyrics login` | Log in to Spotify (click the link in chat). |
| `/slyrics logout` | Log out of Spotify. |
| `/slyrics profile list` | List your saved profiles. |
| `/slyrics profile save <name>` | Save the current settings as a new profile. |
| `/slyrics profile update` | Overwrite the active profile with your current settings. |
| `/slyrics profile load <name>` | Load a profile. |
| `/slyrics profile delete <name>` | Delete a profile. |

---

## Music sources

Choose one under **Connectivity**:

- **Auto** (default): Spotify first, otherwise any other player.
- **Spotify**
- **Others**

### Spotify
1. Create an app at the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard). Choose **Web API** as the API.
2. Add this redirect URI to the app: `http://127.0.0.1:8888/callback`
3. In game, run `/slyrics config` and enter your **Client ID**.
4. Run `/slyrics login` and click the link in chat.
5. Play a song.

Reading the current song works with a free account. **Controlling playback** (play, pause, skip, scrub) from the media player needs **Spotify Premium**.

### Other players (no account, Windows and Linux)
SLyrics reads the playing song from any player that shows up in your system's media controls: Apple Music, Cider, YouTube / YouTube Music / SoundCloud in a browser, VLC, Tidal clients and more.

- **Windows:** uses the Windows media controls (the same ones as the volume flyout). Nothing to install, it only needs the PowerShell that comes with Windows.
- **Linux:** uses MPRIS over D-Bus, which most players support.

To use one specific player, enter part of its name (for example `cider` or `chrome`) in the **Player name filter** field under *Connectivity*. Leave it empty to pick automatically: a playing player wins over a paused one.

On Windows the cover art is not shown for these players (title, artist and progress work).

**Linux, Prism Launcher as Flatpak:** the sandbox hides your players from Minecraft. Allow it once and restart Prism completely:

```
flatpak override --user --talk-name='org.mpris.MediaPlayer2.*' org.prismlauncher.PrismLauncher
```

Or add `org.mpris.MediaPlayer2.*` under *Session Bus -> Talk* in **Flatseal**.

---

## Settings

Open them with `/slyrics config`: SLyrics has its own page in OneConfig.

| Category | What is in it |
|---|---|
| **Connectivity** | Music source, Spotify Client ID, other players. |
| **Text Settings** | Font, size, color, effects, animations, word sweep, emojis. |
| **Features** | The lyrics in the world: which modes are on, then Aura, Sky, Stick on blocks, Floating & walls and the AFK camera. |
| **HUD** | Everything on the screen: the HUD editor, the lyric line, the karaoke panel and the media player, each with its own on/off switch. |
| **Profiles** | Save, load, update, delete and share setups. |

Settings are saved automatically to `config/slyrics.json`. With **Developer mode** off (Connectivity > General) only the everyday settings are shown.

---

## Requirements

| | |
|---|---|
| **Minecraft** | 26.3 |
| **Loader** | Fabric Loader 0.20.1 or newer |
| **Java** | 25 or newer |
| **Side** | Client only |
| **OS** | Windows and Linux (macOS: Spotify only) |

### Required mods
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [OneConfig](https://modrinth.com/mod/oneconfig) (1.9.1 or newer): the settings page and the HUD editor.
- [PolyCompose](https://modrinth.com/mod/polycompose) (3.0.0 or newer), needed by OneConfig.
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin), needed by OneConfig.

---

## FAQ

**The lyrics are early or late.**
Use `/slyrics offset <ms>` (-10000 to 10000). Positive and negative values both work.

**Nothing shows up.**
Check that a song is playing, that the source under *Connectivity* matches where you play it, and that the song has synced lyrics on [LRCLIB](https://lrclib.net).

**Spotify login does not work.**
Make sure the redirect URI in your Spotify app is exactly `http://127.0.0.1:8888/callback` and that the Client ID is entered in the config.

**My player is not detected.**
It has to show up in the Windows media flyout (Windows) or your desktop's media controls (Linux). With a Flatpak launcher on Linux, see the Prism fix above.

**Does it work on servers?**
Yes. It is client-side only and works on any server.

---

## Build

`./gradlew build` (Minecraft 26.3, Java 25). Run the dev client with `./gradlew runClient`.

## Links

- Lyrics provided by [LRCLIB](https://lrclib.net)
- Licensed under MIT
