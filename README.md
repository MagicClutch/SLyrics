# SLyrics (Fabric, Minecraft 26.2 / 26.3)

Shows the synced lyrics of the song currently playing on Spotify as floating text in your Minecraft world:
holograms in front of you, words scattered in the sky, or a plain HUD line.

## Setup
1. Create an app at https://developer.spotify.com/dashboard (API: **Web API**) with the redirect URI `http://127.0.0.1:8888/callback`.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) and [YACL](https://modrinth.com/mod/yacl) (YetAnotherConfigLib).
3. In game: `/slyrics config` -> enter your Client ID, then run `/slyrics login` and click the link in chat.
4. Play a song in Spotify. Lyrics come from [LRCLIB](https://lrclib.net) (free, no key); not every song has synced lyrics.

## Commands
`/slyrics config | login | logout | toggle | offset <ms>`

## Features
- **Words mode:** each word pops up around your crosshair the moment it is sung, glowing, tilted a little toward you, and fades shortly after. Words avoid overlapping each other.
- **Lines mode:** the whole line appears and the word being sung lights up. Optional **Center layout** puts the words in a row straight ahead.
- Distance, size, opacity, glow, outline, motion blur, bounce, flicker, follow-me, walls protection, uppercase.
- Custom font: `minecraft:default`, `minecraft:uniform`, `minecraft:alt`, `minecraft:illageralt`, or any font from a resource pack (`assets/<namespace>/font/<name>.json` -> `<namespace>:<name>`).
- Emojis: adds a symbol after keywords in many languages (love ♥, fight ⚔, ...) without replacing the word. Toggle in the config; edit the word list via `emojiMap` in `config/slyrics.json`.
- Words fade out when Spotify is paused.

## Build
`./gradlew build` (26.3). For 26.2:
`./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2 -Pyacl_version=3.9.7+26.2-fabric -Pmod_version=1.0.0+26.2`

Run the dev client with `./gradlew runClient`.
