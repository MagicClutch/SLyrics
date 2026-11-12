<p align="center"><img src="docs/logo.png" alt="SLyrics logo" width="128"></p>

# SLyrics

Synced lyrics of the song you are listening to, as part of your Minecraft world: words that pop up around you, huge words in the sky, a sing-along panel, a media player. Client-side Fabric mod for **Minecraft 26.3**.

Needs [Fabric API](https://modrinth.com/mod/fabric-api), [Resourceful Config](https://modrinth.com/mod/resourceful-config) and Java 25. Open the settings with `/slyrics config`.

## Features

**Lyrics in the world** (turn on any combination, every word goes to one of them)
- **Aura:** each word pops up around your crosshair the moment it is sung, glowing and tilted toward you.
- **Sky:** huge words far away that flash up and drift like clouds.
- **Stick on blocks:** words stick flat onto the walls and floors you look at, like stickers.
- **Floating:** words keep drifting through the world instead of standing still.
- Words stay out of blocks (or can be seen through walls, your choice), 3D text, glow, outline, motion blur, bounce, flicker, follow-me.

**Lyrics on the screen**
- **Lyric line:** the current line at the bottom. Limit how many words show at once, or let it **scroll** like a ticker with the sung word always in the middle.
- **Karaoke mode:** a small panel with the previous, the current and the next line; the current line fills in while it is sung.
- **Word sweep:** the sung word is colored from left to right in time with the singing.

**Media player**
- A small window with cover, title, progress and previous / play-pause / next. Click the buttons in chat or the pause menu, or bind keys under *Controls -> SLyrics*. Hold the progress bar to scrub and let go to jump.

**HUD editor** (`/slyrics hud`)
- Drag the media player, the karaoke panel and the lyric line, scroll over one to resize it. Edges and the screen center snap with guide lines, and the modules never overlap.

**AFK camera**
- After you are idle, the camera leaves first person and plays cinematic shots around you (orbit, rise, fly-by, push in, ...) with the lyrics in view. Any input ends it.

**Look**
- Any Minecraft font, a font from a resource pack or a font installed on your computer. Text animations (TikTok pop, Slam, Shake, Wobble, Wave, Spin in, Slide up).
- Emojis: real Minecraft icons or colored symbols after keywords in many languages (love ♥, fight ⚔, ...).

**Profiles**
- Save all settings including the HUD layout under a name, load, import and export them. A *Save profile* button appears whenever the active profile has unsaved changes.

## Music sources
Pick one in *Connectivity*: **Auto** (default: Spotify first, otherwise any other player), **Spotify** or **Others**. Lyrics come from [LRCLIB](https://lrclib.net) (free, no key); not every song has synced lyrics.

### Spotify
1. Create an app at https://developer.spotify.com/dashboard (API: **Web API**) with the redirect URI `http://127.0.0.1:8888/callback`.
2. In game: `/slyrics config` -> enter your Client ID, then run `/slyrics login` and click the link in chat.
3. Play a song. Controlling playback from the media player needs **Spotify Premium**.

### Other players (no account, Linux only)
SLyrics reads the playing song from any player that shows up in your desktop's media controls (MPRIS): Cider, YouTube / YouTube Music / Apple Music / SoundCloud in a browser, VLC, Tidal clients, ... To use one specific player, enter part of its name (e.g. `cider`) in the player field.

**Prism Launcher as Flatpak:** the sandbox hides your players from Minecraft. Allow it once and restart Prism completely:

    flatpak override --user --talk-name='org.mpris.MediaPlayer2.*' org.prismlauncher.PrismLauncher

or add `org.mpris.MediaPlayer2.*` under *Session Bus -> Talk* in **Flatseal**.

## Commands
`/slyrics config | hud | login | logout | toggle | offset <ms>` and `/slyrics profile save|update|load|delete|list <name>`

## Settings at a glance
*Connectivity* (source, Spotify, other players) - *Text Settings* (text, effects, animations, word sweep, size) - *Features* (select features, then one page each for Aura, Sky, Stick on blocks, Lyric line, Media player, Floating & walls, AFK camera) - *Profiles* - *HUD editor*.

## Build
`./gradlew build` (Minecraft 26.3, Java 25). Run the dev client with `./gradlew runClient`.
