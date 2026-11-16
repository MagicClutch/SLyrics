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
- [ ] **Run the client without OneConfig** (`./gradlew runClient -PnoOneConfig`). Both attempts failed before the game started
      (DevAuth login: SSL handshake error), so "mod starts fine without OneConfig" is NOT verified yet. Retry, or run with
      `-Ddevauth.enabled=false`.
- [ ] Look at the SLyrics page in the OneConfig GUI (categories, titles, sliders, color pickers, dropdowns, buttons).
      Only checked through logs so far, not visually. Check that titles/descriptions show correctly (`title` is set to the English text).
- [ ] Verify a restart keeps OneConfig edits (the file write was confirmed; the page after restart was not looked at).
- [ ] HUD elements: not native. OneConfig HUDs are Compose-based (`Hud.Content(Composer, int)`), this mod is Java only.
      Currently the "Open HUD editor" button opens the existing editor. Decide: add Kotlin + Compose compiler, or keep the fallback.
- [ ] Font picker, profile list screen: still buttons that open the old screens (OneConfig dropdowns cannot be filled dynamically).
      The profile actions work through a name field plus buttons.
- [ ] Keybinds (`key.slyrics.play_pause|next|previous`): still in Options > Controls. OneConfig may list them itself; not checked.
- [ ] Not tested: Config fields without a GUI entry (`flicker`, `depthSize`, `sweepWhole`, `sweepFadeMs`, `sweepGlow`, `beatShake`, AFK shot types). Not added.
- [ ] Final table (feature | native / button fallback | reason) for the changelog / PR.
- [ ] README: short note that OneConfig is optional and supported.

## Notes
- OneConfig 1.4.1 (Modrinth version `OtDWM80a`), Minecraft 26.3, Fabric. Docs at docs.polyfrost.org are the old v0 API: do not use them. The v1 API was read from the jars.
- The mod version was not changed.
