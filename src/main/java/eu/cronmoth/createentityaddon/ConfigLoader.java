package eu.cronmoth.createentityaddon;

import lombok.Getter;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigLoader {

    private static final String CONFIG_NAME = "createentityaddon";
    private static final String CONFIG_TEMPLATE_RESOURCE = "/eu/cronmoth/createentityaddon/config/createentityaddon.conf";

    private final Path configPath;
    @Getter
    private Config config;

    public ConfigLoader(Path configRoot) throws IOException {
        Files.createDirectories(configRoot);
        configPath = configRoot.resolve(CONFIG_NAME + ".conf");

        if (Files.notExists(configPath)) {
            writeDefaultConfig();
        }

        load();
    }

    private void writeDefaultConfig() throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(CONFIG_TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IOException("Config template not found: " + CONFIG_TEMPLATE_RESOURCE);
            }
            Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
        AddonLog.info("created default config at " + configPath);
    }

    public void load() {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .path(configPath)
                .build();

        try {
            ConfigurationNode node = loader.load();
            Config loaded = node.get(Config.class);
            config = loaded != null ? loaded : new Config();
        } catch (ConfigurateException ex) {
            AddonLog.error("failed to parse config file " + configPath + ", using defaults", ex);
            config = new Config();
        }
    }
}
