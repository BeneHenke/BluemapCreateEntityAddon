package eu.cronmoth.createentityaddon.rendering.chainconveyor;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.ResourceModelRenderer;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.adapter.ResourcesGson;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.TextureVariable;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel.ChainConveyorEntity;
import eu.cronmoth.createentityaddon.rendering.tracks.ObjMesh;
import eu.cronmoth.createentityaddon.rendering.tracks.ObjMeshRenderer;
import eu.cronmoth.createentityaddon.rendering.tracks.TrackMeshExtension;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Positions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChainConveyorRenderer implements BlockRenderer {
    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("create", "chain_conveyor"),
            ChainConveyorRenderer::new
    );

    private static final String WHEEL = "chain_conveyor/wheel";
    private static final String SHAFT = "chain_conveyor/shaft";
    private static final String PORTS = "chain_conveyor/ports";

    private final ResourceModelRenderer modelRenderer;
    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;

    private final Map<String, int[]> texCache = new HashMap<>();
    private Variant chainVariant;

    public ChainConveyorRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.modelRenderer = new ResourceModelRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView tileModel, Color blockColor) {
        TrackMeshExtension ext = TrackMeshExtension.instance();
        if (ext == null) return;

        LightData ld = block.getLightData();
        int[] light = {ld.getSkyLight(), ld.getBlockLight()};

        emit(ext.mesh(WHEEL), new MatrixM4f().identity(), tileModel, light, tex(WHEEL));
        emit(ext.mesh(SHAFT), new MatrixM4f().identity(), tileModel, light, tex(SHAFT));

        if (!(block.getBlockEntity() instanceof ChainConveyorEntity entity)) return;
        if (entity.getConnections().isEmpty()) return;

        ObjMesh ports = ext.mesh(PORTS);
        int[] portsTex = tex(PORTS);

        for (Positions c : entity.getConnections()) {
            VectorM3f direction = new VectorM3f(c.getX(), c.getY(), c.getZ());
            VectorM3f horizontal = horizontalDirection(direction);

            VectorM3f start = new VectorM3f(horizontal.x, 0, horizontal.z);
            VectorM3f end = new VectorM3f(
                    direction.x - horizontal.x,
                    direction.y,
                    direction.z - horizontal.z);

            float[] rotation = computeRotation(start, end);

            // connection port on this block, turned to face the connection
            if (ports != null) {
                MatrixM4f portPose = new MatrixM4f().identity()
                        .translate(-0.5f, -0.5f, -0.5f)
                        .rotateYXZ(0, rotation[1], 0)
                        .translate(0.5f, 0.5f, 0.5f);
                emit(ports, portPose, tileModel, light, portsTex);
            }

            VectorM3f leftOffset = computeLeftOffset(direction);
            for (VectorM3f linePoint : lineSegments(start, end)) {
                modelRenderer.render(block, chainVariant(), tileModel.initialize(), blockColor);
                tileModel.transform(new MatrixM4f().identity()
                        .translate(-0.5f, -0.5f, -0.5f)
                        .rotateYXZ(rotation[0], rotation[1], rotation[2])
                        .translate(0.5f, 0.5f, 0.5f)
                        .translate(
                                linePoint.x + leftOffset.x,
                                linePoint.y + leftOffset.y,
                                linePoint.z + leftOffset.z));
            }
        }
    }

    /**
     * AO for the OBJ pieces' side/bottom faces - bluemap darkens box-model block faces near the
     * ground, the raw meshes get nothing, so they read brighter without this. Up-facing triangles
     * keep full AO (handled in {@link ObjMeshRenderer#emit}).
     */
    private static final float SIDE_AO = 0.7f;

    private static void emit(ObjMesh mesh, MatrixM4f pose, TileModelView view, int[] light, int[] tex) {
        if (mesh == null || tex.length == 0) return;
        ObjMeshRenderer.emit(view, mesh, pose, light, tex, SIDE_AO);
    }

    private int[] tex(String meshKey) {
        return texCache.computeIfAbsent(meshKey, k -> {
            TrackMeshExtension ext = TrackMeshExtension.instance();
            String[] keys = ext == null ? null : ext.slotKeys(k);
            if (keys == null || keys.length == 0) return new int[0];

            Map<String, TextureVariable> textures = null;
            try {
                Model m = resourcePack.getModels().get(new ResourcePath<>("create", "block/chain_conveyor/textures"));
                if (m != null) textures = m.getTextures();
            } catch (RuntimeException ignored) {}

            int fallback = textureGallery.get(new ResourcePath<>("create", "block/andesite_casing"));
            int[] out = new int[keys.length];
            for (int i = 0; i < keys.length; i++) {
                TextureVariable v = textures == null ? null : textures.get(keys[i]);
                out[i] = v == null ? fallback
                        : textureGallery.get(v.getTexturePath(textures::get));
            }
            return out;
        });
    }

    private Variant chainVariant() {
        if (chainVariant == null)
            chainVariant = ResourcesGson.INSTANCE.fromJson("{\"model\":\"minecraft:block/chain\"}", Variant.class);
        return chainVariant;
    }

    public static List<VectorM3f> lineSegments(VectorM3f start, VectorM3f end) {
        if (start == null || end == null) return Collections.emptyList();

        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float dz = end.z - start.z;

        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        final float EPS = 1e-6f;

        float ux = dx / dist;
        float uy = dy / dist;
        float uz = dz / dist;

        int wholeSteps = (int) Math.floor(dist);

        List<VectorM3f> points = new ArrayList<>(wholeSteps + 2);
        points.add(new VectorM3f(start.x, start.y, start.z));

        for (int i = 1; i <= wholeSteps; i++) {
            points.add(new VectorM3f(start.x + ux * i, start.y + uy * i, start.z + uz * i));
        }

        if (Math.abs(dist - wholeSteps) > EPS) {
            points.add(new VectorM3f(end.x, end.y, end.z));
        }

        return Collections.unmodifiableList(points);
    }

    public static float[] computeRotation(VectorM3f start, VectorM3f end) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float dz = end.z - start.z;

        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.atan2(dx, dz);
        float pitch = (float) Math.atan2(horizontal, dy);

        return new float[]{
                (float) Math.toDegrees(pitch),
                (float) Math.toDegrees(yaw),
                0f
        };
    }

    public static VectorM3f computeLeftOffset(VectorM3f direction) {
        VectorM3f left = new VectorM3f(-direction.z, -0.125f, direction.x);

        float length = (float) Math.sqrt(left.x * left.x + left.z * left.z);
        if (length > 0) {
            left.x /= length;
            left.z /= length;
        }
        left.x *= 0.7f;
        left.z *= 0.7f;
        return left;
    }

    public static VectorM3f horizontalDirection(VectorM3f direction) {
        float length = (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (length == 0) return new VectorM3f(0, 0, 0);
        return new VectorM3f(direction.x / length, 0, direction.z / length);
    }
}
