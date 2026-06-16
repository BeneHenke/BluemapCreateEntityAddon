package eu.cronmoth.createentityaddon.rendering.chainconveyor;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.ResourceModelRenderer;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel.ChainConveyorEntity;
import eu.cronmoth.createentityaddon.rendering.tracks.ConnectionBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChainConveyorRenderer implements BlockRenderer {
    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("create", "chain_conveyor"),
            ChainConveyorRenderer::new
    );

    private final ResourceModelRenderer modelRenderer;
    private final ResourcePack resourcePack;
    private final RenderSettings renderSettings;

    private TileModelView blockModel;

    public ChainConveyorRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.modelRenderer = new ResourceModelRenderer(resourcePack, textureGallery, renderSettings);
        this.renderSettings = renderSettings;

    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView tileModel, Color blockColor) {
        this.blockModel = tileModel;
        blockModel.initialize();
        System.out.println("Rendering chain conveyor at ");

        if (!(block.getBlockEntity() instanceof ChainConveyorEntity entity)) return;
        if (entity.getConnections().isEmpty()) return;
        Model chainModel = resourcePack.getModel(new ResourcePath<>( "minecraft", "block/chain"));
        variant.getModel().setResource(chainModel);
        for (int[] c : entity.getConnections()) {
            int start = blockModel.getStart();
            float[] rotation = computeRotation(new VectorM3f(0,0,0), new VectorM3f(c[0], c[1], c[2]));
            for (VectorM3f linePoint : lineSegments(new VectorM3f(0,0,0), new VectorM3f(c[0], c[1], c[2]))) {
                blockModel.initialize();
                modelRenderer.render(block, variant, blockModel, blockColor);
                MatrixM4f matrix = new MatrixM4f();
                matrix.identity()
                        .translate(linePoint.x,linePoint.y,linePoint.z);
                blockModel.transform(matrix);
            }
            blockModel.initialize();
        }
    }


    /**
     * Erzeugt eine Liste von Punkten entlang der geraden Strecke von {@code start} nach {@code end}.
     * Zwischen aufeinanderfolgenden Punkten ist der Abstand 1 (Segmentlänge = 1). Das letzte
     * Segment kann kürzer sein, wenn die Gesamtlänge kein Vielfaches von 1 ist. Start wird immer
     * als erstes Element zurückgegeben, End wird nur angehängt, falls es nicht bereits durch einen
     * ganzen Schritt erreicht wurde.
     *
     * @param start Startpunkt (inklusive)
     * @param end Endpunkt (inklusive falls Restlänge &gt; 0)
     * @return unveränderliche Liste von VectorM3f-Punkten entlang der Strecke
     */
    public static List<VectorM3f> lineSegments(VectorM3f start, VectorM3f end) {
        if (start == null || end == null) return Collections.emptyList();

        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float dz = end.z - start.z;

        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        final float EPS = 1e-6f;

        if (dist < EPS) {
            // Start und End sind gleich
            return Collections.singletonList(new VectorM3f(start.x, start.y, start.z));
        }

        float ux = dx / dist;
        float uy = dy / dist;
        float uz = dz / dist;

        int wholeSteps = (int) Math.floor(dist);

        List<VectorM3f> points = new ArrayList<>(wholeSteps + 2);
        // always include the start
        points.add(new VectorM3f(start.x, start.y, start.z));

        // Add intermediate whole-step points at distance 1,2,...,wholeSteps
        for (int i = 1; i <= wholeSteps; i++) {
            points.add(new VectorM3f(start.x + ux * i, start.y + uy * i, start.z + uz * i));
        }

        // If there's a remainder (final partial segment), add the exact end point
        if (Math.abs(dist - wholeSteps) > EPS) {
            points.add(new VectorM3f(end.x, end.y, end.z));
        }

        return Collections.unmodifiableList(points);
    }

    public static float[] computeRotation(VectorM3f start, VectorM3f end) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float dz = end.z - start.z;

        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(
                Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))
        );

        return new float[]{pitch, yaw, 0f};
    }
}
