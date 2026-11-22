package dev.slyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The "Others" music source on Windows. Every player that shows up in the Windows media overlay (volume flyout / lock
 * screen) is reachable through the system media transport controls: Apple Music, YouTube / YouTube Music in a browser,
 * VLC, Cider, Tidal, ... A small PowerShell process runs in the background, prints all players once a second as a JSON
 * line and picks up commands (play / pause, next, previous, seek) from a file, so no native code is needed.
 */
final class WindowsMedia {
	private static final String SCRIPT = String.join("\n",
		"param([string]$CmdFile)",
		"$ErrorActionPreference = 'SilentlyContinue'",
		"[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)",
		"Add-Type -AssemblyName System.Runtime.WindowsRuntime",
		"$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]",
		"$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]",
		"$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
		"function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(5000); $t.Result }",
		"$M = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]",
		"$P = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]",
		"$mgr = Await ($M::RequestAsync()) $M",
		"$last = -100000",
		"while ($true) {",
		"  $force = $false",
		"  if (Test-Path -LiteralPath $CmdFile) {",
		"    $line = Get-Content -LiteralPath $CmdFile -Raw -Encoding UTF8",
		"    Remove-Item -LiteralPath $CmdFile -Force",
		"    $p = $line.Trim() -split \"`t\"",
		"    foreach ($s in $mgr.GetSessions()) {",
		"      if ($s.SourceAppUserModelId -eq $p[1]) {",
		"        if ($p[0] -eq 'PlayPause') { $null = Await ($s.TryTogglePlayPauseAsync()) ([bool]) }",
		"        elseif ($p[0] -eq 'Next') { $null = Await ($s.TrySkipNextAsync()) ([bool]) }",
		"        elseif ($p[0] -eq 'Previous') { $null = Await ($s.TrySkipPreviousAsync()) ([bool]) }",
		"        elseif ($p[0] -eq 'Seek') { $null = Await ($s.TryChangePlaybackPositionAsync([long]$p[2])) ([bool]) }",
		"      }",
		"    }",
		"    Start-Sleep -Milliseconds 300",
		"    $force = $true",
		"  }",
		"  $now = [Environment]::TickCount",
		"  if ($force -or ($now - $last) -ge 1000 -or $now -lt $last) {",
		"    $last = $now",
		"    $list = @()",
		"    foreach ($s in $mgr.GetSessions()) {",
		"      $pi = $s.GetPlaybackInfo()",
		"      $tl = $s.GetTimelineProperties()",
		"      $mp = Await ($s.TryGetMediaPropertiesAsync()) $P",
		"      $st = [int]$pi.PlaybackStatus",
		"      $pos = $tl.Position.TotalMilliseconds",
		"      $len = ($tl.EndTime - $tl.StartTime).TotalMilliseconds",
		"      if ($st -eq 4) {",
		"        $el = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds",
		"        if ($el -gt 0 -and $el -lt 21600000) { $pos += $el }",
		"      }",
		"      if ($len -gt 0 -and $pos -gt $len) { $pos = $len }",
		"      $list += [pscustomobject]@{ id = $s.SourceAppUserModelId; state = $st; title = $mp.Title; artist = $mp.Artist; album = $mp.AlbumTitle; len = [long]$len; pos = [long]$pos }",
		"    }",
		"    [Console]::Out.WriteLine((ConvertTo-Json -InputObject @($list) -Compress))",
		"    [Console]::Out.Flush()",
		"  }",
		"  Start-Sleep -Milliseconds 200",
		"}",
		"");

	private static Process proc;
	private static Path cmdFile;
	private static volatile String line = "[]";
	private static volatile long lineAt;
	/** The app (AppUserModelId) that supplies the song right now, or null; the media buttons control this one. */
	private static volatile String active;

	private WindowsMedia() {}

	static String active() { return active; }

	private static synchronized void ensureRunning() throws IOException {
		if (proc != null && proc.isAlive()) return;
		Path dir = Files.createTempDirectory("slyrics-media");
		Path script = dir.resolve("media.ps1");
		cmdFile = dir.resolve("cmd.txt");
		// Windows PowerShell 5.1 reads a script without BOM as ANSI, so write one with a BOM
		Files.write(script, ("\uFEFF" + SCRIPT).getBytes(StandardCharsets.UTF_8));
		Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
			"-File", script.toString(), cmdFile.toString())
			.redirectError(ProcessBuilder.Redirect.DISCARD).start();
		proc = p;
		Runtime.getRuntime().addShutdownHook(new Thread(p::destroyForcibly));
		Thread reader = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = r.readLine()) != null) {
					if (l.startsWith("[")) {
						line = l;
						lineAt = System.currentTimeMillis();
					}
				}
			} catch (IOException ignored) {
				// the process ended; ensureRunning starts a new one on the next poll
			}
		}, "SLyrics-WindowsMedia");
		reader.setDaemon(true);
		reader.start();
	}

	/** Sends PlayPause / Next / Previous / Seek (arg = position in 100 ns ticks) to the active player. */
	static void command(String method, String arg) throws IOException {
		String id = active;
		if (id == null) throw new IOException("no player");
		ensureRunning();
		Path tmp = cmdFile.resolveSibling("cmd.tmp");
		Files.writeString(tmp, method + "\t" + id + "\t" + (arg == null ? "" : arg));
		Files.move(tmp, cmdFile, StandardCopyOption.REPLACE_EXISTING);
	}

	static void poll(Config c, boolean auto) throws Exception {
		try {
			ensureRunning();
		} catch (IOException e) {
			active = null;
			if (!auto) Spotify.status = "Others: could not start PowerShell (needed on Windows)";
			return;
		}
		String filter = c.otherPlayer == null ? "" : c.otherPlayer.trim().toLowerCase();
		JsonObject best = null;
		int bestScore = -1;
		if (System.currentTimeMillis() - lineAt < 6000) {
			JsonArray all = JsonParser.parseString(line).getAsJsonArray();
			for (JsonElement e : all) {
				JsonObject o = e.getAsJsonObject();
				String id = str(o, "id");
				String lower = id.toLowerCase();
				if (filter.isEmpty() && lower.contains("spotify")) continue; // that one is the Spotify source, unless asked for by name
				if (!filter.isEmpty() && !lower.contains(filter)) continue;
				if (str(o, "title").isBlank()) continue;
				// a playing player beats a paused one; among those, one that knows the artist (clean metadata) wins
				int score = (o.get("state").getAsInt() == 4 ? 2 : 0) + (str(o, "artist").isBlank() ? 0 : 1);
				if (score > bestScore) {
					best = o;
					bestScore = score;
				}
			}
		}
		if (best == null) {
			active = null;
			Spotify.othersActive = false;
			if (auto) return; // nothing to say: Spotify may still play
			Spotify.playing = false;
			Spotify.status = System.currentTimeMillis() - lineAt < 6000
				? "Others: nothing is playing. Play something in a browser, Apple Music, VLC, ..."
				: "Others: waiting for the Windows media controls ...";
			return;
		}
		active = str(best, "id");
		String title = OtherPlayers.clean(str(best, "title"));
		String artist = OtherPlayers.clean(str(best, "artist")).replaceAll("(?i)\\s*-\\s*topic$", "");
		if (artist.isEmpty()) { // browser tabs often put "Artist - Title" into the title
			String[] parts = title.split("\\s+[-\u2013\u2014]\\s+", 2);
			if (parts.length == 2) {
				artist = parts[0].trim();
				title = parts[1].trim();
			}
		}
		boolean playingNow = best.get("state").getAsInt() == 4;
		Spotify.othersActive = playingNow;
		// Auto: Spotify has priority; another player only shows when Spotify is not playing
		if (auto && Spotify.track != null && Spotify.fromSpotify && (Spotify.playing || !playingNow)) return;
		Spotify.fromSpotify = false;
		Spotify.coverUrl = "";
		Spotify.setProgress(best.get("pos").getAsLong(), playingNow);
		Spotify.setTrack(new Track(artist + " - " + title, title, artist, str(best, "album"), best.get("len").getAsLong()));
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
