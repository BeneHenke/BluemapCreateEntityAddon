package eu.cronmoth.createentityaddon.rendering.chainconveyor;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.map.hires.block.ResourceModelRenderer;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.block.Block;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
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
    private final BlockStateModelRenderer blockRenderer;

    private TileModelView blockModel;

    public ChainConveyorRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.modelRenderer = new ResourceModelRenderer(resourcePack, textureGallery, renderSettings);
        this.renderSettings = renderSettings;
        this.blockRenderer = new BlockStateModelRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView tileModel, Color blockColor) {
        this.blockModel = tileModel;
        blockModel.initialize();

        if (!(block.getBlockEntity() instanceof ChainConveyorEntity entity)) return;
        if (entity.getConnections().isEmpty()) return;
        Model chainModel = resourcePack.getModels().get(new ResourcePath<>( "minecraft", "block/chain"));
        variant.getModel().setResource(chainModel);
        for (int[] c : entity.getConnections()) {

            VectorM3f originalDirection = new VectorM3f(c[0], c[1], c[2]);

            VectorM3f horizontal = horizontalDirection(originalDirection);

            // Move endpoints one block outward
            VectorM3f start = new VectorM3f(
                    horizontal.x,
                    0,
                    horizontal.z
            );

            VectorM3f end = new VectorM3f(
                    originalDirection.x - horizontal.x,
                    originalDirection.y,
                    originalDirection.z - horizontal.z
            );

            float[] rotation = computeRotation(start, end);

            BlockAccess blockAccess = new ChainConveyorPortsBlock(block, new VectorM3f(block.getX(), block.getY(), block.getZ()));
            BlockNeighborhood neighborhood = new BlockNeighborhood(blockAccess, resourcePack, renderSettings,block.getDimensionType());
            neighborhood.set(block.getX(), block.getY(), block.getZ());
            blockRenderer.render(neighborhood, tileModel, new Color());
            MatrixM4f portMatrix = new MatrixM4f();
            portMatrix.identity()
                    .translate(-0.5f, -0.5f, -0.5f)
                    .rotateXYZ(0, rotation[1], 0)
                    .translate(0.5f, 0.5f, 0.5f);
            blockModel.transform(portMatrix);
            blockModel.initialize();

            VectorM3f leftOffset = computeLeftOffset(originalDirection);

            for (VectorM3f linePoint : lineSegments(start, end)) {
                modelRenderer.render(block, variant, blockModel, blockColor);

                MatrixM4f matrix = new MatrixM4f();
                matrix.identity()
                        .translate(-0.5f, -0.5f, -0.5f)
                        .rotateXYZ(rotation[0], rotation[1], rotation[2])
                        .translate(0.5f, 0.5f, 0.5f)
                        .translate(
                                linePoint.x + leftOffset.x,
                                linePoint.y + leftOffset.y,
                                linePoint.z + leftOffset.z
                        );

                blockModel.transform(matrix);
                blockModel.initialize();
            }
        }
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

        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.atan2(dx, dz);

        float pitch = (float) Math.atan2(horizontal, dy);

        System.out.printf(
                "Dir=(%.1f, %.1f, %.1f) Pitch=%.1f Yaw=%.1f%n",
                dx, dy, dz,
                Math.toDegrees(pitch),
                Math.toDegrees(yaw)
        );

        return new float[]{
                (float) Math.toDegrees(pitch),
                (float) Math.toDegrees(yaw),
                0f
        };
    }

    public static VectorM3f computeLeftOffset(VectorM3f direction) {
        // Left vector in the horizontal XZ plane
        VectorM3f left = new VectorM3f(
                -direction.z,
                -0.125f,
                direction.x
        );

        float length = (float) Math.sqrt(
                left.x * left.x +
                        left.z * left.z
        );

        if (length > 0) {
            left.x /= length;
            left.z /= length;
        }

        left.x *= 0.7f;
        left.z *= 0.7f;

        return left;
    }

    public static VectorM3f horizontalDirection(VectorM3f direction) {
        float length = (float) Math.sqrt(
                direction.x * direction.x +
                        direction.z * direction.z
        );

        if (length == 0) {
            return new VectorM3f(0, 0, 0);
        }

        return new VectorM3f(
                direction.x / length,
                0,
                direction.z / length
        );
    }
}
