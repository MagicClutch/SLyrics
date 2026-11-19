# Changelog

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
