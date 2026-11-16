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
- [ ] **TASK: move the HUD elements in OneConfig's own HUD editor** (not started, no code changed yet).
      Today the "Open HUD editor" button only opens the old editor, because OneConfig's editor only knows elements registered
      with `HudManager.register(...)`, and SLyrics draws its three elements itself through Fabric's `HudElementRegistry`.
      Findings so far (OneConfig 1.4.1, read from the jars):
      - No Kotlin needed: `org.polyfrost.oneconfig.api.hud.v1.LegacyHud` (in the platform jar `26.3-fabric-1.4.1.jar`) can be
        subclassed from Java. Constructor `(String id, String title, Hud.Category)`, implement `getWidth()`, `getHeight()`,
        `render(GuiGraphicsExtractor)`, and also `update()` (abstract in `Hud`). `HudManager.register(hud)` adds it to the editor.
      - The compile classpath will need `androidx.compose.runtime` and Kotlin stdlib (inherited signatures such as
        `Content(Composer, int)`), taken from the compose bundle / Fabric Language Kotlin jars like the other OneConfig jars
        (extend the `unpackOneConfig` task in `build.gradle`).
      Plan:
      1. Three `LegacyHud` classes in `dev.slyrics.compat.oneconfig`: lyric line, karaoke panel, media player. Start with the
         media player (simplest: `MediaPlayer.draw(...)` already draws at a given top-left corner).
      2. Their `render()` calls the existing drawing code, so the look stays the same.
      3. Two-way sync of position and scale with the existing fields (`xPercent/yPercent/scale`, `hudX/hudY/hudScale`,
         `mediaX/mediaY/mediaScale`, all in percent of the screen), so the old editor and OneConfig's editor change the same values.
      4. With OneConfig installed, switch off the mod's own drawing of those elements (otherwise they show twice).
         `LyricsHud` / `MediaPlayer` must behave exactly as before without OneConfig.
      5. Test: drag and scale in OneConfig's HUD editor, then check the old editor and a restart; then without OneConfig.
      Keep the "Open HUD editor" button as an extra.
- [ ] Font picker, profile list screen: still buttons that open the old screens (OneConfig dropdowns cannot be filled dynamically).
      The profile actions work through a name field plus buttons.
- [ ] Keybinds (`key.slyrics.play_pause|next|previous`): still in Options > Controls. OneConfig may list them itself; not checked.
- [ ] Not tested: Config fields without a GUI entry (`flicker`, `depthSize`, `sweepWhole`, `sweepFadeMs`, `sweepGlow`, `beatShake`, AFK shot types). Not added.
- [ ] Final table (feature | native / button fallback | reason) for the changelog / PR.
- [ ] README: short note that OneConfig is optional and supported.

## Notes
- OneConfig 1.4.1 (Modrinth version `OtDWM80a`), Minecraft 26.3, Fabric. Docs at docs.polyfrost.org are the old v0 API: do not use them. The v1 API was read from the jars.
- The mod version was not changed.
