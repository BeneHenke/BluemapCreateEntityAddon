package eu.cronmoth.createentityaddon.rendering.sublevel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Caches the sub-levels of every dimension's {@code sublevels} folder. */
class SubLevelStore {

    private static final Map<Path, SubLevelDirectory> DIRECTORIES = new ConcurrentHashMap<>();

    private SubLevelStore() {}

    static List<SubLevelData> get(Path sublevelsDir, int minY) {
        return DIRECTORIES.computeIfAbsent(sublevelsDir, d -> new SubLevelDirectory()).get(sublevelsDir, minY);
    }
}
