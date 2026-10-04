package eu.cronmoth.createentityaddon.rendering.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.util.Key;
import eu.cronmoth.createentityaddon.AddonLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merges every mounted resource pack's own {@code assets/<namespace>/lang/en_us.json} into one
 * shared lookup - the same {@code assets/*} tree bluemap already scans for block/item models and
 * textures, just for lang files instead. This is what lets {@link ComponentText} resolve a
 * translation key from ANY loaded mod (Minecraft, Create, Steam'n'Rails, or anything else in the
 * modpack) without this addon bundling a static copy of each one's {@code en_us.json} by hand.
 * <p>
 * Registered as a {@link ResourcePackExtension} so {@link #loadResources(Iterable)} runs during
 * bluemap's "Loading resources..." phase, alongside
 * {@link eu.cronmoth.createentityaddon.rendering.tracks.TrackMeshExtension} - well before any
 * block gets rendered, so {@link ComponentText} always sees a fully merged map.
 */
public class LangExtension implements ResourcePackExtension {

    public static final Key KEY = new Key("createentityaddon", "lang");

    public static final ResourcePack.Extension<LangExtension> TYPE =
            new ResourcePack.Extension<>() {
                @Override
                public LangExtension create(ResourcePack pack) {
                    return new LangExtension();
                }

                @Override
                public Key getKey() {
                    return KEY;
                }
            };

    private static volatile LangExtension instance;

    public static LangExtension instance() {
        return instance;
    }

    private final Map<String, String> lang = new ConcurrentHashMap<>();

    public LangExtension() {
        instance = this;
    }

    @Override
    public void loadResources(Iterable<Path> roots) throws IOException {
        // bluemap 5.12+ hands every pack root at once, raw (a mod .jar, a pack dir) rather than
        // already mounted - open .jar/.zip roots ourselves, same as TrackMeshExtension.
        for (Path root : roots) scanRoot(root);
    }

    private void scanRoot(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            try (FileSystem fs = FileSystems.newFileSystem(root, (ClassLoader) null)) {
                for (Path fsRoot : fs.getRootDirectories()) scanRoot(fsRoot);
            } catch (IOException | RuntimeException ex) {
                AddonLog.debug("could not open pack " + root + ": " + ex);
            }
            return;
        }

        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(assets)) {
            for (Path namespace : namespaces) {
                Path enUs = namespace.resolve("lang/en_us.json");
                if (!Files.isRegularFile(enUs)) continue;
                try (InputStream in = Files.newInputStream(enUs)) {
                    mergeLangFile(in);
                } catch (RuntimeException ex) {
                    AddonLog.warn("failed parsing " + enUs + ": " + ex.getMessage());
                }
            }
        }
    }

    private void mergeLangFile(InputStream in) throws IOException {
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (e.getValue().isJsonPrimitive()) lang.put(e.getKey(), e.getValue().getAsString());
        }
    }

    public String get(String key) {
        return lang.get(key);
    }
}
