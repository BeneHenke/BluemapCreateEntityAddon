package eu.cronmoth.createentityaddon;

import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    private final Path configPath;
    private final Yaml yaml = new Yaml();
    @Getter
    private Config config;

    public ConfigLoader(Path folder) throws IOException {
        Files.createDirectories(folder);
        configPath = folder.resolve("createentityaddon.yml");

        if (Files.notExists(configPath)) {
            config = new Config();
            save();
        } else {
            load();
        }
    }

    public void load() throws IOException {
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Config loaded = yaml.loadAs(reader, Config.class);

            if (loaded != null) {
                config = loaded;
            } else {
                config = new Config();
            }
        }
    }

    public void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            writer.write(config.toString());

        }
    }
}
