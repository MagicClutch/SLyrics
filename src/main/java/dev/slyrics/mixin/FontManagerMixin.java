package dev.slyrics.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.slyrics.SLyricsClient;
import dev.slyrics.font.UserFont;
import dev.slyrics.font.UserFontHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.AllMissingGlyphProvider;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds one extra font set, slyrics:user, built from a TrueType file chosen on this computer. */
@Mixin(FontManager.class)
public abstract class FontManagerMixin implements UserFontHost {

	@Shadow
	@org.spongepowered.asm.mixin.Final
	private Map<Identifier, FontSet> fontSets;

	@Shadow
	private FontSet createFontSet(Identifier id, List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
		throw new AssertionError();
	}

	@Unique
	private GlyphProvider slyrics$userProvider;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void slyrics$register(CallbackInfo ci) {
		UserFont.host = this;
	}

	/** A resource reload rebuilds all font sets; put ours back. */
	@Inject(method = "apply", at = @At("TAIL"))
	private void slyrics$afterReload(CallbackInfo ci) {
		slyrics$userProvider = null; // the reload already closed it
		Path file = UserFont.currentFile();
		if (file != null) slyrics$setUserFont(file);
	}

	@Override
	public void slyrics$setUserFont(Path file) {
		FontSet old = fontSets.remove(UserFont.ID);
		if (old != null) old.close();
		if (slyrics$userProvider != null) {
			slyrics$userProvider.close();
			slyrics$userProvider = null;
		}
		if (file == null) {
			((FontManager) (Object) this).updateOptions(Minecraft.getInstance().options);
			return;
		}
		ByteBuffer data = null;
		FT_Face face = null;
		try (InputStream in = Files.newInputStream(file)) {
			data = TextureUtil.readResource(in);
			GlyphProvider provider;
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer faceBuffer = stack.mallocPointer(1);
					FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), data, 0L, faceBuffer), "Initializing font face");
					face = FT_Face.create(faceBuffer.get());
				}
				if (!"TrueType".equals(FreeType.FT_Get_Font_Format(face))) throw new IllegalStateException("Not a TrueType font");
				FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
				provider = new TrueTypeGlyphProvider(data, face, 11.0F, 3.0F, 0.0F, 0.0F, "");
			}
			slyrics$userProvider = provider;
			Set<FontOption> options = EnumSet.noneOf(FontOption.class);
			FontSet set = createFontSet(UserFont.ID, List.of(
				new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS),
				new GlyphProvider.Conditional(new AllMissingGlyphProvider(), FontOption.Filter.ALWAYS_PASS)), options);
			fontSets.put(UserFont.ID, set);
		} catch (Exception e) {
			SLyricsClient.LOGGER.warn("Could not load font {}", file, e);
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				if (face != null) FreeType.FT_Done_Face(face);
			}
			if (data != null) MemoryUtil.memFree(data);
		}
		((FontManager) (Object) this).updateOptions(Minecraft.getInstance().options);
	}
}
