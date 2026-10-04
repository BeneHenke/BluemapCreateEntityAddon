package eu.cronmoth.createentityaddon.rendering.tracks;

import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtension;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePackExtensionType;
import de.bluecolored.bluemap.core.util.Key;
import eu.cronmoth.createentityaddon.AddonLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** mesh key -&gt; asset path to look for in each pack root. */
    private static final Map<String, String> WANTED = new LinkedHashMap<>() {{
        put("standard/tie", "assets/create/models/block/track/tie.obj");
        put("standard/left", "assets/create/models/block/track/segment_left.obj");
        put("standard/right", "assets/create/models/block/track/segment_right.obj");
        put("standard/diag", "assets/create/models/block/track/diag.obj");
        put("standard/diag_2", "assets/create/models/block/track/diag_2.obj");
        put("standard/ascending", "assets/create/models/block/track/ascending.obj");
        // steam'n'rails ships narrow/wide diag + diag_2 as vanilla json, not .obj - only these exist
        put("narrow/tie", "assets/railways/models/block/narrow_gauge_base/tie.obj");
        put("narrow/left", "assets/railways/models/block/narrow_gauge_base/segment_left.obj");
        put("narrow/right", "assets/railways/models/block/narrow_gauge_base/segment_right.obj");
        put("narrow/ascending", "assets/railways/models/block/narrow_gauge_base/ascending.obj");
        put("wide/tie", "assets/railways/models/block/wide_gauge_base/tie.obj");
        put("wide/left", "assets/railways/models/block/wide_gauge_base/segment_left.obj");
        put("wide/right", "assets/railways/models/block/wide_gauge_base/segment_right.obj");
        put("wide/ascending", "assets/railways/models/block/wide_gauge_base/ascending.obj");
        // steam'n'rails monorail: only the diagonal static blocks (straight + curve stay elsewhere)
        put("monorail/diag", "assets/railways/models/block/monorail/monorail/static_blocks/diag.obj");
        put("monorail/diag_2", "assets/railways/models/block/monorail/monorail/static_blocks/diag_2.obj");
        // create's chain conveyor: wheel + shaft + connection port, all porting_lib:obj models
        put("chain_conveyor/wheel", "assets/create/models/block/chain_conveyor/conveyor_wheel.obj");
        put("chain_conveyor/shaft", "assets/create/models/block/chain_conveyor/conveyor_shaft.obj");
        put("chain_conveyor/ports", "assets/create/models/block/chain_conveyor/conveyor_ports.obj");
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

    @Override
    public void loadResources(Path root) throws IOException {
        for (Map.Entry<String, String> e : WANTED.entrySet()) {
            if (meshes.containsKey(e.getKey())) continue;
            Path file = root.resolve(e.getValue());
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
                    keys.forEach((mat, key) ->
                            nameToSlot.put(mat, keySlot.computeIfAbsent(key, k -> keySlot.size())));
                    slots = keySlot.keySet().toArray(new String[0]);
                }

                try (InputStream in = Files.newInputStream(file)) {
                    meshes.put(e.getKey(), ObjMesh.parse(in, nameToSlot));
                }
                if (slots != null) slotKeys.put(e.getKey(), slots);
                AddonLog.info("mesh '" + e.getKey() + "' from " + e.getValue());
            } catch (RuntimeException ex) {
                AddonLog.warn("failed parsing " + e.getValue() + ": " + ex.getMessage());
            }
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

    /** Parsed mesh for the key, or {@code null} if no loaded pack had it. */
    public ObjMesh mesh(String key) {
        return meshes.get(key);
    }

    /** Texture key per material slot for a named-texture mesh (chain conveyor), or {@code null}. */
    public String[] slotKeys(String key) {
        return slotKeys.get(key);
    }
}
