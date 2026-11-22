# Changelog

## 1.2.0

- One **text size** for the words in the world (Text Settings > Text): Aura, Stick on blocks and the AFK camera use it, Sky keeps its own size and the lyric line is sized in the HUD editor. Words that the movement prediction places farther ahead (sprinting, flying with an elytra) grow with the distance, so they look just as big.
- Aura words can be placed much farther away (closest up to 40, farthest up to 48 blocks). The size ranges are wider too.
- New **predict movement**, always on: the next word goes where you are heading (walking direction and turning) instead of only where you look. "Prediction (seconds ahead)" in Aura sets how far along your path. While flying (elytra, creative) it follows the whole flight path, also when diving or climbing, and words placed far ahead at high speed no longer disappear right away. Works with Aura and Stick on blocks, not in the AFK camera.
- **OneConfig is now required** (1.9.1 or newer, with PolyCompose and Fabric Language Kotlin); Resourceful Config is no longer used. All settings are on the OneConfig page (/slyrics config) and are stored in config/slyrics.json. Your settings are taken over from slyrics.jsonc once; the old files are kept as .old.
- All of SLyrics' own screens are gone: the classic HUD editor, the profile manager and the font picker. Use OneConfig's HUD editor (/slyrics hud), the Profiles tab (now with Export to / Import from clipboard) and the Choose font dropdown. `/slyrics resourceful` and `/slyrics classichud` were removed.
- The scrolling lyric line now runs smoothly: the word being sung stays in the middle and the line glides on continuously instead of jumping from word to word.
- The word sweep options (on/off and color) moved back to Text Settings > Word sweep.
- Aura words go back in the order they are sung: each new word stands a little behind the ones still showing, so a word to come is never in front of one already sung (when the row has gone far back it starts in front again).
- New **HUD** tab: the lyric line, the karaoke panel and the media player (each with its on/off switch) and the HUD editor moved out of Features into their own tab.
- New **Spread** setting for Aura and for Sky: low keeps the words close around the middle, high lets them use up to about 3/4 of the screen (still centred).
- Sky words always stay above the horizon and whole on screen, and where both allow it in the same middle band as Aura words; when you look down they move up to the top edge of the screen. Sky words are only placed in front of open sky: if a mountain, forest or building is in the way they move up until the sky is clear. "Sky elevation" is gone since the height now follows your view.
- Fix: when the middle is full, Aura words now move a little further out instead of landing on top of each other, and Sky words no longer pile up at the top of the screen when you look down.
- Aura words appear as close to the middle of the view as there is room for, in a band around the crosshair (nothing far up or far down), never on top of a word you can see, and always whole: a word that would stick out over the edge of the screen is not placed there.
- Words no longer pop up several at once when a line starts midway, the song is skipped ahead or the position jumps: words that were missed are left out and only the word being sung appears.
- Every word now stays the same time after it has been sung (2 seconds, or what Word hold is set to); a word that is sung longer stays that much longer. Before, the time depended on the song speed, and some words vanished right away.
- Fix: world words (most of all sky words) no longer flicker; their glow, outline and effect layers now sit slightly behind the text. **Glow** (Text Settings > Effects, 0 = off) is now shown without Developer mode.
- Blocks always hide the words behind them. A word that would land in the ground or behind a wall comes forward to just in front of it and shrinks by as much, so it looks just as big where you look (small on the ground when you look down). The "Prevent block clipping" and "Lyrics clipping (see through blocks)" options are gone.
- The AFK camera has fewer settings: close-up chance, camera distance and height, text distances and the like now use fixed defaults, and its words use the general text size.
- Spotify is asked less often when nothing plays (every 5 s instead of every second, and not at all outside a world); while a song plays it is still every second. When Spotify does limit the app, the chat says so and how long it lasts, and Spotify errors are now written to the log.
- Others: typing `spotify` as the player now reads the Spotify app on this computer directly, without the Spotify login or its request limits (before, Others always skipped Spotify).
- **Fonts**: eight caption fonts now come with the mod (Poppins ExtraBold, Bebas Neue, Anton, Luckiest Guy, Titan One, Bangers, Permanent Marker, Pacifico), picked from the Font dropdown. Fonts installed on your computer and the free-text font field are gone; a saved computer font goes back to Minecraft's font.
- Fix: profiles only hold the look. Loading or importing one no longer changes your music source, player, lyrics offset, on/off switch or Developer mode.
- Fix: Aura words now also keep clear of words stuck on blocks (their size was compared in the wrong unit).
- Removed the previous / play-pause / next buttons from Features > Media player; the player window and the keybinds still have them.
- Emojis: one switch, **Enable emojis** (on by default). Emoji colors and Minecraft icons are always used, and the Emoji words / Icon words lists are no longer shown (the built-in word lists stay).

## 1.1.4

- OneConfig: new **Developer mode** switch (Connectivity > General). With it off, only the easy everyday settings are shown (music source, offset, Spotify login, font and color, text animation, word sweep, the feature toggles, keybinds and profiles). Turn it on to get every detailed setting back. Stick on blocks, Floating, the media buttons and the HUD editor buttons are part of Developer mode. Resourceful Config cannot hide options, so it still shows everything.
- OneConfig: the HUD editor buttons and the word sweep options moved into Features, and "New profile" is now at the top of the Profiles tab.
- OneConfig: the Spotify Client ID is hidden. The field shows one * per character; paste a new ID to replace it, or empty the field to remove it.
- New installs start lighter: sky mode only, no glow, outline, blur, bounce or text animation, block clipping off and the AFK camera off. Existing settings are not changed, and the built-in profiles still turn the effects on.
- Fix: removed a debug log line that wrote an entry to the game log for every mouse click.

## 1.1.3

- OneConfig: the font can now be picked from a dropdown (Text Settings > Text): Minecraft's fonts first, then every installed font. It stays in step with the font field and the Choose button.
- OneConfig: new "Keybinds" category for play / pause, next and previous song. The keys are the same Minecraft key bindings as in Options > Controls, so both places always show the same key (one key only, no Ctrl / Shift / Alt).

## 1.1.2

- Fix: the "Others" music source did nothing on Windows. It now works there too (Windows media controls via PowerShell: Apple Music, browsers, VLC, ...), including play / pause, next, previous and seeking. The cover is not shown on Windows.
- The config entry for the player name filter was renamed (`applePlayer` -> `otherPlayer`), so a filter you entered before has to be entered again.
- Internal: the Linux code moved into `LinuxMedia`, next to the new `WindowsMedia`; `AppleMusic` is now `OtherPlayers`.

## 1.1.1

- Fix: with OneConfig, the media player buttons and the progress bar now click where the player is drawn, also after it was
  moved or resized in OneConfig's HUD editor.

## 1.1.0

- Complete optional OneConfig support. With OneConfig installed, SLyrics gets its own page there with every setting of the
  Resourceful Config screen (same names, categories, order, defaults and ranges), plus the profile actions, the media player
  buttons, the Spotify logout and the emoji word lists. Both screens change the same values; OneConfig does not write a file
  of its own. Without OneConfig nothing changes.
- With OneConfig, OneConfig is the main config: `/slyrics config` opens its page and `/slyrics hud` its HUD editor. The lyric
  line, karaoke panel and media player are OneConfig HUD elements (move and scale them there); position and size stay in
  step with the mod's own settings. Resourceful Config stays the fallback and can do everything: `/slyrics resourceful`
  opens it, `/slyrics classichud` the mod's own HUD editor.
- Profiles in OneConfig: a dropdown with all saved profiles plus Load, Update, Delete and Save as new.
- Dev: `-PnoDevAuth` skips the DevAuth login; the Skia library for the dev client now matches the OS.
