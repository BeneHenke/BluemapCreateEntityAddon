package eu.cronmoth.createentityaddon.rendering.text;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import eu.cronmoth.createentityaddon.AddonLog;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class FontAtlasExtension implements ResourcePackExtension {

    public static final Key KEY = new Key("createentityaddon", "font_atlas_extension");

    public static final ResourcePackExtensionType<FontAtlasExtension> TYPE =
            new ResourcePackExtensionType<>() {
                @Override
                public FontAtlasExtension create() {
                    return new FontAtlasExtension();
                }

                @Override
                public Key getKey() {
                    return KEY;
                }
            };

    private volatile boolean built = false;

    @Override
    public Iterable<Texture> loadTextures(Path root) throws IOException {
        if (built) return List.of();
        try {
            BufferedImage image = FontAtlas.readBitmap(root);
            if (image == null) return List.of();
            built = true;
            return List.of(FontAtlas.buildTexture(image));
        } catch (IOException e) {
            AddonLog.warn("failed building font atlas: " + e.getMessage());
            return List.of();
        }
    }
}
