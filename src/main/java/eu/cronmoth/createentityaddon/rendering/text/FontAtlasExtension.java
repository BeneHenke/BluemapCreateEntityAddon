package eu.cronmoth.createentityaddon.rendering.text;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.util.Key;
import eu.cronmoth.createentityaddon.AddonLog;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Registers {@link FontAtlas}'s generated texture into the resource pack during bluemap's
 * "Loading resources..." phase - the only correct hook for a texture that isn't a file in a pack
 * (see {@link FontAtlas}'s class doc for why a lazy {@code TextureGallery.put} from inside a
 * renderer is too late and silently produces invisible text). Unlike bluemap 5.7 (a dedicated
 * {@code loadTextures(Path root)} hook per mounted root), 5.12's {@link ResourcePackExtension} only
 * has {@code loadResources(Iterable<Path> roots)} - so the texture is put directly into the
 * {@link ResourcePack}'s own texture pool (handed to this extension at construction time), which
 * runs before the pack's real texture-loading/baking steps and before {@code textures.json} is
 * written.
 */
public class FontAtlasExtension implements ResourcePackExtension {

    public static final Key KEY = new Key("createentityaddon", "font_atlas_extension");

    public static final ResourcePack.Extension<FontAtlasExtension> TYPE =
            new ResourcePack.Extension<>() {
                @Override
                public FontAtlasExtension create(ResourcePack pack) {
                    return new FontAtlasExtension(pack);
                }

                @Override
                public Key getKey() {
                    return KEY;
                }
            };

    private final ResourcePack pack;

    public FontAtlasExtension(ResourcePack pack) {
        this.pack = pack;
    }

    @Override
    public void loadResources(Iterable<Path> roots) throws IOException {
        try {
            pack.getTextures().put(FontAtlas.PATH, FontAtlas.buildTexture());
        } catch (IOException e) {
            AddonLog.warn("failed building font atlas: " + e.getMessage());
        }
    }
}
