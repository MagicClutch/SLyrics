# TODO: OneConfig support (work in progress)

## Done
- Optional OneConfig page `dev.slyrics.compat.oneconfig` (`OneConfigBridge`, `SLyricsOneConfig`, `SLyricsExtras`).
- All Resourceful Config options mirrored (67 synced options), built from the `RcConfig` annotations: same names, categories, order, ranges, comments.
- The page is UI-only: no second config file. Values live in `Config.INSTANCE` / Resourceful Config.
- Extras as native OneConfig options: profile actions, media player buttons, Spotify logout, emoji lists and switches.
- `fabric.mod.json`: `suggests` oneconfig. Gradle: OneConfig is compile-only, never bundled; the built jar has no OneConfig classes.
- Dev client loads OneConfig (+ Fabric Language Kotlin, Compose Multiplatform, Skiko). `-PnoOneConfig` turns that off.
- Tested with OneConfig: sync in both directions (OneConfig <-> Resourceful Config), value saved to `slyrics.jsonc`.

## Still open
- [x] Client starts without OneConfig (`./gradlew runClient -PnoDevAuth -PnoOneConfig`): no crash, no OneConfig classes loaded.
- [x] HUD elements in OneConfig's HUD editor (`SLyricsHuds`: line, panel, media player as `LegacyHud`), synced with the config,
      own drawing off while they exist (`HudMode.oneConfig`). OneConfig is now the main config (`/slyrics config`, `/slyrics hud`),
      Resourceful Config the fallback (`/slyrics resourceful`, `/slyrics classichud`).
- [x] **Not looked at visually** (no way to drive the game window from here): the SLyrics page in the OneConfig GUI, dragging and
      scaling the three elements in OneConfig's HUD editor (check that the lyric line box, the panel and the player land where the
      old editor had them, and that a restart keeps them), the "Open HUD editor" / "Open classic HUD editor" buttons, `/slyrics config`
      opening the page (uses OneConfig's internal `ModConfigRoute("slyrics")`; falls back to Resourceful Config if that fails).
- [x] Font picker as a OneConfig dropdown (`fontPick`, Text Settings > Text): Minecraft fonts first, installed fonts added in the background;
      synced with `Config.font`. **Not tested visually** (check a long list with hundreds of fonts: scrolling, speed, selection).
- [x] Keybinds: new OneConfig category "Keybinds" (`keyPlayPause|keyNext|keyPrevious`), synced both ways with the Minecraft key bindings
      (Options > Controls, saved in options.txt). One key only, no modifiers. **Not tested in the game** (check: key set in OneConfig shows in Controls and works, and the other way round).
- [ ] Not tested: Config fields without a GUI entry (`flicker`, `depthSize`, `sweepWhole`, `sweepFadeMs`, `sweepGlow`, `beatShake`, AFK shot types). Not added.
- [ ] Final table (feature | native / button fallback | reason) for the changelog / PR.
- [ ] README: short note that OneConfig is optional and supported.

## Notes
- OneConfig 1.4.1 (Modrinth version `OtDWM80a`), Minecraft 26.3, Fabric. Docs at docs.polyfrost.org are the old v0 API: do not use them. The v1 API was read from the jars.
- The mod version was not changed.
