package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.map.hires.block.ResourceModelRenderer;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Connection;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Normals;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Positions;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.TrackEntity;

import java.util.*;

public class TrackRenderer implements BlockRenderer {

    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("create", "track"),
            TrackRenderer::new
    );

    private final ResourceModelRenderer modelRenderer;
    private final BlockStateModelRenderer blockRenderer;
    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final RenderSettings renderSettings;

    private BlockNeighborhood block;
    private Variant variant;
    private Model modelResource;
    private TileModelView blockModel;

    public TrackRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.renderSettings = renderSettings;
        this.modelRenderer = new ResourceModelRenderer(resourcePack, textureGallery, renderSettings);
        this.blockRenderer = new BlockStateModelRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView tileModel, Color blockColor) {
        this.block = block;
        this.variant = variant;
        this.blockModel = tileModel;

        modelRenderer.render(block, variant, blockModel.initialize(), blockColor);

        if (!(block.getBlockEntity() instanceof TrackEntity entity)) return;
        if (entity.getConnections().isEmpty()) return;
        for (Connection c : entity.getConnections()) {

            List<Positions> pos = c.getPos();
            Vector3d start = new Vector3d(pos.getFirst().getX(), pos.getFirst().getY(), pos.getFirst().getZ());
            Vector3d end = new Vector3d(pos.getLast().getX(), pos.getLast().getY(), pos.getLast().getZ());

            if (!shouldRender(end)) return;

            List<Normals> axis = c.getAxis();
            Vector3d axis0 = new Vector3d(axis.getFirst().v[0], axis.getFirst().v[1], axis.getFirst().v[2]);
            Vector3d axis1 = new Vector3d(axis.getLast().v[0], axis.getLast().v[1], axis.getLast().v[2]);

            List<Normals> normals = c.getNormal();
            Vector3d normal0 = new Vector3d(normals.getFirst().v[0], normals.getFirst().v[1], normals.getFirst().v[2]);
            Vector3d normal1 = new Vector3d(normals.getLast().v[0], normals.getLast().v[1], normals.getLast().v[2]);

            List<SegmentTransform> segments = calculateBezierSegments(start, end, axis0, axis1, normal0, normal1);
            for (int i = 0; i < segments.size(); i++) {
                SegmentTransform segmentT = segments.get(i);
                Vector3d segment = segmentT.position();
                blockModel.initialize();

                ExtendedBlock access = block.copy();
                access.set(
                        block.getX() + segment.getFloorX(),
                        block.getY() + segment.getFloorY(),
                        block.getZ() + segment.getFloorZ()
                );

                ConnectionBlock connectionBlock = new ConnectionBlock(access, block.getBlockState());

                BlockNeighborhood connBlockNeighbour = new BlockNeighborhood(
                        connectionBlock, resourcePack, renderSettings, block.getDimensionType()
                );
                connBlockNeighbour.set(connectionBlock.getX(), connectionBlock.getY(), connectionBlock.getZ());
                if (!(i==0 || i==segments.size()-1)) {
                    modelRenderer.render(connBlockNeighbour, variant, blockModel, new Color());
                }


                MatrixM4f matrix = new MatrixM4f();

                matrix
                        .identity()
                        .translate(-0.5f, -0.5f, -0.5f)
                        .rotateXYZ(segmentT.pitch(), segmentT.yaw(), segmentT.roll())
                        .translate(0.5f, 0.5f, 0.5f)
                        .translate((float) segment.getX(), (float) segment.getY() + ((i%4)/1000f), (float) segment.getZ());
                blockModel.transform(matrix);
            }
        }
    }

    boolean shouldRender(Vector3d v) {
        if (v.getX() != 0) return v.getX() > 0;
        if (v.getZ() != 0) return v.getZ() > 0;
        return v.getY() > 0;
    }

    public static List<SegmentTransform> calculateBezierSegments(
            Vector3d start,
            Vector3d end,
            Vector3d axisStart,
            Vector3d axisEnd,
            Vector3d normalStart,
            Vector3d normalEnd
    ) {
        axisStart = axisStart.normalize();
        axisEnd = axisEnd.normalize();

        List<SegmentTransform> result = new ArrayList<>();

        // Add straight track at the start (one block)
        Vector3d straightStartPos = start;
        Vector3d straightStartTangent = axisStart;
        float straightStartYaw = quantizeYawRadians(straightStartTangent);
        float straightStartPitch = quantizePitchRadians(straightStartTangent);
        result.add(new SegmentTransform(straightStartPos, 0, 0, 0));

        // Move curve start one block forward
        Vector3d curveStart = start.add(axisStart);
        Vector3d curveEnd = end.add(axisEnd);

        double handleLength = curveStart.distance(curveEnd) / 3.0;
        Vector3d handle1 = curveStart.add(axisStart.mul(handleLength));
        Vector3d handle2 = curveEnd.add(axisEnd.mul(handleLength));

        int scanCount = 16;
        double length = 0.0;
        Vector3d prev = curveStart;

        for (int i = 1; i <= scanCount; i++) {
            double t = i / (double) scanCount;
            Vector3d p = bezier(curveStart, handle1, handle2, curveEnd, t);
            length += p.distance(prev);
            prev = p;
        }

        int segments = Math.max(1, (int) (length * 2.0));
        double[] stepLUT = new double[segments + 1];
        stepLUT[0] = 1.0;

        double accumulated = 0.0;
        prev = curveStart;

        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Vector3d p = bezier(curveStart, handle1, handle2, curveEnd, t);
            accumulated += p.distance(prev) / length;
            stepLUT[i] = t / accumulated;
            prev = p;
        }

        Float firstYaw = null;
        Float firstPitch = null;
        for (int i = 0; i <= segments; i++) {
            double t = (i == segments) ? 1.0 : (i * stepLUT[i] / segments);
            Vector3d tangent = bezierDerivative(curveStart, handle1, handle2, curveEnd, t).normalize();
            float yawDiff = 0;
            if (firstYaw==null) {
                firstYaw = quantizeYawRadians(tangent);
                firstPitch = quantizePitchRadians(tangent);
                System.out.println("First yaw: " + Math.toDegrees(firstYaw) + ", pitch: " + Math.toDegrees(firstPitch));
            }
            else {
                float yaw = (float) Math.atan2(tangent.getZ(), tangent.getX());
                yawDiff = (float)Math.toDegrees(firstYaw-yaw);
            }
            Vector3d position = bezier(curveStart, handle1, handle2, curveEnd, t);
            result.add(new SegmentTransform(position, 0, 0, yawDiff));
        }

        // Add straight track at the end (one block)
        Vector3d straightEndPos = end.add(axisEnd.mul(2.0));
        result.add(new SegmentTransform(straightEndPos, 0, 0, 0));

        return result;
    }

    private static Vector3d bezier(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        double u = 1.0 - t;
        double tt = t * t;
        double uu = u * u;

        return p0.mul(uu * u)
                .add(p1.mul(3 * uu * t))
                .add(p2.mul(3 * u * tt))
                .add(p3.mul(tt * t));
    }

    private static Vector3d bezierDerivative(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        double u = 1.0 - t;

        return p1.sub(p0).mul(3 * u * u)
                .add(p2.sub(p1).mul(6 * u * t))
                .add(p3.sub(p2).mul(3 * t * t));
    }

    private static float quantizeYawRadians(Vector3d tangent) {
        double yaw = Math.atan2(tangent.getZ(), tangent.getX()); // radians
        double step = Math.PI / 4.0;
        double best = 0.0;
        double bestDiff = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 8; i++) {
            double candidate = i * step;
            double diff = Math.abs(Math.atan2(Math.sin(yaw - candidate), Math.cos(yaw - candidate)));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = candidate;
            }
        }

        return (float) best;
    }

    private static float quantizePitchRadians(Vector3d tangent) {
        double pitch = Math.atan2(
                tangent.getY(),
                Math.sqrt(tangent.getX() * tangent.getX() + tangent.getZ() * tangent.getZ())
        );
        double step = Math.PI / 4.0;
        double best = 0.0;
        double bestDiff = Double.POSITIVE_INFINITY;

        for (int i = -2; i <= 2; i++) {
            double candidate = i * step;
            double diff = Math.abs(Math.atan2(Math.sin(pitch - candidate), Math.cos(pitch - candidate)));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = candidate;
            }
        }

        return (float) best;
    }
}
