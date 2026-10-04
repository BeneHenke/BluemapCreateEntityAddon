package eu.cronmoth.createentityaddon.rendering.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.util.Key;
import eu.cronmoth.createentityaddon.AddonLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LangExtension implements ResourcePackExtension {

    public static final Key KEY = new Key("createentityaddon", "lang");

    public static final ResourcePackExtensionType<LangExtension> TYPE =
            new ResourcePackExtensionType<>() {
                @Override
                public LangExtension create() {
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
    public void loadResources(Path root) throws IOException {
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
