package eu.cronmoth.createentityaddon.rendering.sublevel;

import eu.cronmoth.createentityaddon.AddonLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class SubLevelDirectory {

    private static final long CHECK_INTERVAL_MS = 10_000;

    private volatile List<SubLevelData> ships = List.of();
    private volatile boolean loaded;
    private volatile long lastCheck;
    private long signature;

    List<SubLevelData> get(Path dir, int minY) {
        if (loaded && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return ships;
        synchronized (this) {
            if (loaded && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return ships;
            List<Path> files = listFiles(dir);
            long newSignature = signature(files);
            if (!loaded || newSignature != signature) {
                ships = load(files, minY);
                signature = newSignature;
                loaded = true;
            }
            lastCheck = System.currentTimeMillis();
            return ships;
        }
    }

    private static List<Path> listFiles(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".slvls")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long signature(List<Path> files) {
        long sig = 1;
        for (Path file : files) {
            try {
                sig = sig * 31 + file.getFileName().hashCode();
                sig = sig * 31 + Files.getLastModifiedTime(file).toMillis();
                sig = sig * 31 + Files.size(file);
            } catch (IOException ignored) {
                // file vanished between listing and stat; the next check picks up the change
            }
        }
        return sig;
    }

    private static List<SubLevelData> load(List<Path> files, int minY) {
        SubLevelReader reader = new SubLevelReader(minY);
        List<SubLevelData> result = new ArrayList<>();
        for (Path file : files) {
            try {
                result.addAll(reader.readFile(file));
            } catch (Exception e) {
                AddonLog.warn("failed to read sub-level file " + file + ": " + e);
            }
        }
        if (!files.isEmpty()) AddonLog.info("loaded " + result.size() + " sub-levels from " + files.get(0).getParent());
        return List.copyOf(result);
    }
}
