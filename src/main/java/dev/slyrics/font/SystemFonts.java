package dev.slyrics.font;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Finds the TrueType fonts installed on this computer (Minecraft can only draw TrueType, not OpenType/CFF). */
public final class SystemFonts {
	public record Entry(String name, Path file) {}

	private static volatile List<Entry> cache;

	private SystemFonts() {}

	/** Blocking: scans the font folders on first call, then returns the same list. Call from a background thread. */
	public static List<Entry> list() {
		List<Entry> c = cache;
		if (c != null) return c;
		List<Entry> out = new ArrayList<>();
		for (Path dir : folders()) {
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> walk = Files.walk(dir, 6)) {
				walk.filter(p -> {
					String n = p.getFileName().toString().toLowerCase();
					return n.endsWith(".ttf") || n.endsWith(".otf");
				}).forEach(p -> {
					String name = readName(p);
					if (name != null) out.add(new Entry(name, p));
				});
			} catch (IOException ignored) {
				// unreadable folder
			}
		}
		out.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase()));
		cache = out;
		return out;
	}

	private static List<Path> folders() {
		List<Path> f = new ArrayList<>();
		String os = System.getProperty("os.name", "").toLowerCase();
		String home = System.getProperty("user.home", "");
		if (os.contains("win")) {
			String win = System.getenv("WINDIR"), local = System.getenv("LOCALAPPDATA");
			if (win != null) f.add(Path.of(win, "Fonts"));
			if (local != null) f.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
		} else if (os.contains("mac")) {
			f.add(Path.of("/Library/Fonts"));
			f.add(Path.of("/System/Library/Fonts"));
			f.add(Path.of(home, "Library", "Fonts"));
		} else {
			f.add(Path.of("/usr/share/fonts"));
			f.add(Path.of("/usr/local/share/fonts"));
			f.add(Path.of(home, ".fonts"));
			f.add(Path.of(home, ".local", "share", "fonts"));
		}
		return f;
	}

	/** Reads the full font name from the file's 'name' table. Null if it is not a plain TrueType font or unreadable. */
	static String readName(Path file) {
		try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
			DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(java.nio.channels.Channels.newInputStream(raf.getChannel())));
			int version = in.readInt();
			if (version != 0x00010000 && version != 0x74727565) return null; // 'true'; skips CFF ('OTTO') and collections
			int tables = in.readUnsignedShort();
			in.skipBytes(6);
			long nameOffset = -1;
			for (int i = 0; i < tables; i++) {
				byte[] tag = new byte[4];
				in.readFully(tag);
				in.readInt();
				long offset = in.readInt() & 0xFFFFFFFFL;
				in.readInt();
				if (new String(tag, StandardCharsets.US_ASCII).equals("name")) {
					nameOffset = offset;
					break;
				}
			}
			if (nameOffset < 0) return null;
			raf.seek(nameOffset);
			in = new DataInputStream(new java.io.BufferedInputStream(java.nio.channels.Channels.newInputStream(raf.getChannel())));
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			int stringOffset = in.readUnsignedShort();
			record Rec(int platform, int id, int length, int offset) {}
			List<Rec> recs = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				int platform = in.readUnsignedShort();
				in.readUnsignedShort();
				in.readUnsignedShort();
				int id = in.readUnsignedShort();
				int length = in.readUnsignedShort();
				int offset = in.readUnsignedShort();
				recs.add(new Rec(platform, id, length, offset));
			}
			String best = null;
			for (int want : new int[]{4, 1}) { // 4 = full name, 1 = family
				for (Rec r : recs) {
					if (r.id() != want || (r.platform() != 3 && r.platform() != 1 && r.platform() != 0)) continue;
					byte[] b = new byte[r.length()];
					raf.seek(nameOffset + stringOffset + r.offset());
					raf.readFully(b);
					String s = new String(b, r.platform() == 1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_16BE).trim();
					if (!s.isEmpty()) {
						best = s;
						if (r.platform() == 3) break;
					}
				}
				if (best != null) break;
			}
			return best;
		} catch (Exception e) {
			return null;
		}
	}
}
