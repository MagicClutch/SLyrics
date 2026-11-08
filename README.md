# SLyrics (Fabric, Minecraft 26.2 / 26.3)

Zeigt die synchronisierten Lyrics des aktuell auf Spotify laufenden Songs Zeile für Zeile im Spiel an.

## Einrichtung
1. Auf https://developer.spotify.com/dashboard eine App erstellen, Redirect URI: `http://127.0.0.1:8888/callback`
2. `/slyrics config` -> Client ID eintragen, dann `/slyrics login` und den Link im Chat anklicken.
3. Song in Spotify abspielen. Lyrics kommen von https://lrclib.net.

## Befehle
`/slyrics config | login | logout | toggle | offset <ms>`

## Schriftart
Feld "Schrift" im Menü: `minecraft:default`, `minecraft:uniform`, `minecraft:alt`, `minecraft:illageralt`
oder eine eigene Schrift aus einem Resourcepack (`assets/<namespace>/font/<name>.json` -> `<namespace>:<name>`).

## Build
`./gradlew build` (26.3). Für 26.2:
`./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2 -Pmod_version=1.0.0+26.2`
