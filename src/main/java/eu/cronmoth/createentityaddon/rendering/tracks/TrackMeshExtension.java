package eu.cronmoth.createentityaddon.rendering.tracks;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.util.Key;
import eu.cronmoth.createentityaddon.AddonLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pulls the {@code .obj} meshes bluemap can't load itself (create's track tie / rail segments /
 * diag / ascending per gauge, and create's chain-conveyor wheel / shaft / ports) straight out of
 * whatever resource packs and mod jars bluemap loads - works for the in-server plugin and the
 * standalone CLI ({@code -n <mods>}) alike, so those {@code .obj} files never need to be bundled.
 */
public class TrackMeshExtension implements ResourcePackExtension {

    public static final Key KEY = new Key("createentityaddon", "track_meshes");

    public static final ResourcePackExtensionType<TrackMeshExtension> TYPE =
            new ResourcePackExtensionType<>() {
                @Override
                public TrackMeshExtension create() {
                    return new TrackMeshExtension();
                }

                @Override
                public Key getKey() {
                    return KEY;
                }
            };

    /** The instance bluemap created for this run, so renderers can reach it without the registry. */
    private static volatile TrackMeshExtension instance;

    public static TrackMeshExtension instance() {
        return instance;
    }

    private static final Map<String, List<String>> WANTED = new ConcurrentHashMap<>() {{
        put("standard/tie", List.of("assets/create/models/block/track/tie.obj"));
        put("standard/left", List.of("assets/create/models/block/track/segment_left.obj"));
        put("standard/right", List.of("assets/create/models/block/track/segment_right.obj"));
        put("standard/diag", List.of("assets/create/models/block/track/diag.obj"));
        put("standard/diag_2", List.of("assets/create/models/block/track/diag_2.obj"));
        put("standard/ascending", List.of("assets/create/models/block/track/ascending.obj"));
        // steam'n'rails ships narrow/wide diag + diag_2 as vanilla json, not .obj - only these exist
        put("narrow/tie", List.of("assets/railways/models/block/narrow_gauge_base/tie.obj"));
        put("narrow/left", List.of("assets/railways/models/block/narrow_gauge_base/segment_left.obj"));
        put("narrow/right", List.of("assets/railways/models/block/narrow_gauge_base/segment_right.obj"));
        put("narrow/ascending", List.of("assets/railways/models/block/narrow_gauge_base/ascending.obj"));
        put("wide/tie", List.of("assets/railways/models/block/wide_gauge_base/tie.obj"));
        put("wide/left", List.of("assets/railways/models/block/wide_gauge_base/segment_left.obj"));
        put("wide/right", List.of("assets/railways/models/block/wide_gauge_base/segment_right.obj"));
        put("wide/ascending", List.of("assets/railways/models/block/wide_gauge_base/ascending.obj"));
        // steam'n'rails monorail: only the diagonal static blocks (straight + curve stay elsewhere)
        put("monorail/diag", List.of("assets/railways/models/block/monorail/monorail/static_blocks/diag.obj"));
        put("monorail/diag_2", List.of("assets/railways/models/block/monorail/monorail/static_blocks/diag_2.obj"));
    }};

    private final Map<String, ObjMesh> meshes = new ConcurrentHashMap<>();
    /**
     * For meshes whose {@code .mtl} names textures instead of numbering slots (chain conveyor): the
     * texture key per material slot ({@code slot i} = {@code slotKeys[i]}), for the renderer to
     * resolve against the model's texture map. Absent for create's numeric-slot track meshes.
     */
    private final Map<String, String[]> slotKeys = new ConcurrentHashMap<>();

    public TrackMeshExtension() {
        instance = this;
    }

    /** Every pack root seen so far, so meshes can be loaded lazily regardless of pack load order. */
    private final List<Path> roots = new CopyOnWriteArrayList<>();

    @Override
    public void loadResources(Path root) throws IOException {
        roots.add(root);
        scanConveyorBlockstates(root);
        for (String key : new ArrayList<>(WANTED.keySet())) loadMesh(root, key);
    }

    /** Loads {@code key} from {@code root} if any candidate exists there. True if the mesh is present afterwards. */
    private boolean loadMesh(Path root, String key) throws IOException {
        if (meshes.containsKey(key)) return true;
        List<String> candidates = WANTED.get(key);
        if (candidates == null) return false;
        for (String assetPath : candidates) {
            Path file = root.resolve(assetPath);
            if (!Files.isRegularFile(file)) continue;
            try {
                Map<String, Integer> numericMtl = readNumericMtl(file);
                Map<String, Integer> nameToSlot;
                String[] slots = null;

                if (!numericMtl.isEmpty()) {
                    nameToSlot = numericMtl;                       // create track meshes: #0/#1/#2
                } else {
                    // named-texture mtl (chain conveyor): assign a slot per distinct texture key
                    Map<String, String> keys = readTextureKeyMtl(file);
                    LinkedHashMap<String, Integer> keySlot = new LinkedHashMap<>();
                    nameToSlot = new HashMap<>();
                    keys.forEach((mat, texKey) ->
                            nameToSlot.put(mat, keySlot.computeIfAbsent(texKey, k -> keySlot.size())));
                    slots = keySlot.keySet().toArray(new String[0]);
                }

                ObjMesh parsed;
                try (InputStream in = Files.newInputStream(file)) {
                    parsed = ObjMesh.parse(in, nameToSlot);
                }
                meshes.putIfAbsent(key, parsed);
                if (slots != null) slotKeys.putIfAbsent(key, slots);
                AddonLog.info("mesh '" + key + "' from " + assetPath);
                return true;
            } catch (RuntimeException ex) {
                AddonLog.warn("failed parsing " + assetPath + ": " + ex.getMessage());
            }
        }
        return false;
    }

    /** The chain-conveyor mesh parts every conveyor blockstate gets registered for. */
    private static final String[] CONVEYOR_PARTS = {"wheel", "shaft", "ports"};

    private static void scanConveyorBlockstates(Path root) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (var namespaces = Files.list(assets)) {
            for (Path nsDir : (Iterable<Path>) namespaces::iterator) {
                if (!Files.isDirectory(nsDir)) continue;
                String ns = nsDir.getFileName().toString();
                Path blockstates = nsDir.resolve("blockstates");
                if (!Files.isDirectory(blockstates)) continue;
                List<String> blocks = new ArrayList<>();
                try (var files = Files.list(blockstates)) {
                    files.map(f -> f.getFileName().toString())
                            .filter(n -> n.equals("chain_conveyor.json") || n.endsWith("_chain_conveyor.json"))
                            .map(n -> n.substring(0, n.length() - ".json".length()))
                            .forEach(blocks::add);
                } catch (IOException ignored) { /* next namespace */ }
                for (String block : blocks) registerConveyorMeshes(ns, block);
            }
        } catch (IOException ignored) { /* no assets dir */ }
    }

    private static void registerConveyorMeshes(String ns, String block) {
        String material = block.equals("chain_conveyor") ? null
                : block.substring(0, block.length() - "_chain_conveyor".length());
        for (String part : CONVEYOR_PARTS) {
            List<String> candidates = new ArrayList<>();
            if (material != null) {
                candidates.add("assets/" + ns + "/models/block/chain_conveyor/" + material + "/conveyor_" + part + ".obj");
                candidates.add("assets/" + ns + "/models/block/chain_conveyor/" + material + "/" + part + ".obj");
            }
            candidates.add("assets/" + ns + "/models/block/" + block + "/conveyor_" + part + ".obj");
            candidates.add("assets/create/models/block/chain_conveyor/conveyor_" + part + ".obj");
            WANTED.putIfAbsent(ns + ":" + block + "/" + part, candidates);
        }
    }

    /** {@code map_Kd #N} slots from the .obj's sibling .mtl (per-model name, then {@code track.mtl}). Empty if named. */
    private static Map<String, Integer> readNumericMtl(Path obj) {
        for (Path mtl : siblingMtls(obj)) {
            try (InputStream in = Files.newInputStream(mtl)) {
                Map<String, Integer> map = ObjMesh.parseMtl(in);
                if (!map.isEmpty()) return map;
            } catch (IOException ignored) { /* next candidate */ }
        }
        return Map.of();
    }

    /** {@code map_Kd #<key>} texture keys from the .obj's sibling .mtl. */
    private static Map<String, String> readTextureKeyMtl(Path obj) {
        for (Path mtl : siblingMtls(obj)) {
            try (InputStream in = Files.newInputStream(mtl)) {
                Map<String, String> map = ObjMesh.parseMtlTextureKeys(in);
                if (!map.isEmpty()) return map;
            } catch (IOException ignored) { /* next candidate */ }
        }
        return Map.of();
    }

    private static Path[] siblingMtls(Path obj) {
        Path dir = obj.getParent();
        String name = obj.getFileName().toString().replaceFirst("\\.obj$", "");
        return new Path[]{dir.resolve(name + ".mtl"), dir.resolve("track.mtl")};
    }

    public ObjMesh mesh(String key) {
        ensureLoaded(key);
        return meshes.get(key);
    }

    /** Texture key per material slot for a named-texture mesh (chain conveyor), or {@code null}. */
    public String[] slotKeys(String key) {
        ensureLoaded(key);
        return slotKeys.get(key);
    }

    private void ensureLoaded(String key) {
        if (meshes.containsKey(key) || !WANTED.containsKey(key)) return;
        for (Path root : roots) {
            try {
                if (loadMesh(root, key)) return;
            } catch (IOException ex) {
                AddonLog.warn("failed reading mesh '" + key + "' from " + root + ": " + ex.getMessage());
            }
        }
    }
}
