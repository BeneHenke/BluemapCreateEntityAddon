package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;
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
import de.bluecolored.bluemap.core.world.LightData;
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

        String modelPath = variant.getModel().getFormatted();
        int modelStart = blockModel.getStart();
        MatrixM4f modelMatrix = cloneMatrix(variant.getTransformMatrix());
        //reset variant orientation to combine models
        variant.getTransformMatrix().identity();
        if (!(variant.getModel().getFormatted().contains("x_ortho") || variant.getModel().getFormatted().contains("z_ortho"))) {
            variant.getModel().setResource(resourcePack.getModel(new ResourcePath<>("create:block/track/x_ortho")));
            modelRenderer.render(block, variant, blockModel.initialize(), blockColor);
            blockModel.translate(0.5f, 0, 0);
        }

        modelRenderer.render(block, variant, blockModel.initialize(), blockColor);
        blockModel.initialize(modelStart);

        if (modelPath.equals("create:block/track/diag")) {
            MatrixM4f matrix = new MatrixM4f();
            matrix
                    .identity()
                    .translate(-0.25f, 0, 0)
                    .translate(-0.5f, -0.5f, -0.5f)
                    .rotate(0, -45f, 0)
                    .translate(0.5f, 0.5f, 0.5f);
            blockModel.transform(matrix);
        } else if (modelPath.equals("create:block/track/diag_2")) {
            MatrixM4f matrix = new MatrixM4f();
            matrix
                    .identity()
                    .translate(-0.25f, 0, 0)
                    .translate(-0.5f, -0.5f, -0.5f)
                    .rotate(0, 45f, 0)
                    .translate(0.5f, 0.5f, 0.5f);
            blockModel.transform(matrix);
        } else if (modelPath.equals("create:block/track/ascending")) {
            MatrixM4f matrix = new MatrixM4f();
            matrix.identity()
                    .translate(-0.25f, 0, 0)
                    .translate(-0.5f, -0.5f, -0.5f)
                    .rotate(0, 90, -45)
                    .translate(0.5f, 1f, 0.5f);
            blockModel.transform(matrix);
            blockModel.transform(modelMatrix);
        }
        copyMatrix(modelMatrix, variant.getTransformMatrix());

        if (!(block.getBlockEntity() instanceof TrackEntity entity)) return;
        if (entity.getConnections().isEmpty()) return;
        for (Connection c : entity.getConnections()) {

            List<Positions> pos = c.getPos();
            Vector3d start = new Vector3d(pos.getFirst().getX(), pos.getFirst().getY(), pos.getFirst().getZ());
            Vector3d end = new Vector3d(pos.getLast().getX(), pos.getLast().getY(), pos.getLast().getZ());
            if (!shouldRender(end)) continue;

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

                ConnectionBlock connectionBlock = new ConnectionBlock(access, block.getBlockState(), block.getLightData());

                BlockNeighborhood connBlockNeighbour = new BlockNeighborhood(
                        connectionBlock, resourcePack, renderSettings, block.getDimensionType()
                );
                connBlockNeighbour.set(connectionBlock.getX(), connectionBlock.getY(), connectionBlock.getZ());
                variant.getTransformMatrix().identity();
                modelRenderer.render(connBlockNeighbour, variant, blockModel, new Color());
                copyMatrix(modelMatrix, variant.getTransformMatrix());

                if (modelPath.equals("create:block/track/diag")) {
                    MatrixM4f matrix = new MatrixM4f();
                    matrix
                            .identity()
                            .translate(-0.5f, -0.5f, -0.5f)
                            .rotate(0, -45f, 0)
                            .translate(0.5f, 0.5f, 0.5f);
                    blockModel.transform(matrix);
                } else if (modelPath.equals("create:block/track/diag_2")) {
                    MatrixM4f matrix = new MatrixM4f();
                    matrix.identity()
                            .translate(-0.5f, -0.5f, -0.5f)
                            .rotate(0, 45f, 0)
                            .translate(0.5f, 0.5f, 0.5f);
                    blockModel.transform(matrix);
                }
                else if (modelPath.equals("create:block/track/ascending")) {
                    MatrixM4f matrix = new MatrixM4f();
                    matrix.identity()
                            .translate(-0.5f, -0.5f, -0.5f)
                            .rotate(0, 90, -45)
                            .translate(0.5f, 1f, 0.5f);
                    blockModel.transform(matrix);
                    blockModel.transform(modelMatrix);
                }

                MatrixM4f matrix = new MatrixM4f();
                matrix.identity()
                        .translate(-0.5f, -0.5f, -0.5f)
                        .rotate(segmentT.pitch(), segmentT.yaw(), segmentT.roll())
                        .translate(0.5f, 0.5f, 0.5f)
                        .translate((float) segment.getX(), (float) segment.getY() + ((i % 4) / 1000f), (float) segment.getZ());
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
        normalStart = normalStart.normalize();
        normalEnd = normalEnd.normalize();
        start = start.add(axisStart.mul(0.5));
        if (is45DegreeAngle(axisStart)) {
            end = end.add(new Vector3d(0,-1/3.,0));
        }


        if (is45DegreeAngle(axisEnd)) {
            start = start.add(axisStart.mul(0.5));
            end = end.add(axisEnd.mul(0.5));
            end = end.add(new Vector3d(0,0.5,0));
        }
        else {
            start = start.add(axisStart.mul(0.5));
            end = end.add(axisEnd.mul(0.5));

        }

        List<SegmentTransform> result = new ArrayList<>();

        // Determine handles dynamically based on angle between axes
        double handleLength = determineHandleLength(start, end, axisStart, axisEnd);
        Vector3d handle1 = start.add(axisStart.mul(handleLength));
        Vector3d handle2 = end.add(axisEnd.mul(handleLength));

        int scanCount = 16;
        double length = 0.0;
        Vector3d prev = start;

        for (int i = 1; i <= scanCount; i++) {
            double t = i / (double) scanCount;
            Vector3d p = bezier(start, handle1, handle2, end, t);
            length += p.distance(prev);
            prev = p;
        }

        int segments = Math.max(1, (int) (length * 2.0));
        double[] stepLUT = new double[segments + 1];
        stepLUT[0] = 1;

        double combinedDistance = 0.0;
        prev = start;

        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vector3d p = bezier(start, handle1, handle2, end, t);
            if (i > 0) {
                combinedDistance += p.distance(prev) / length;
                stepLUT[i] = t / combinedDistance;
            }
            prev = p;
        }

        Float firstYaw = null;
        Float firstPitch = null;
        Float firstRoll = null;
        boolean isXAxisAligned = false;

        for (int i = 0; i <= segments; i++) {
            double t = (i == segments) ? 1.0 : (i * stepLUT[i] / segments);
            Vector3d tangent = bezierDerivative(start, handle1, handle2, end, t).normalize();

            // Interpolate normal vector along the curve
            Vector3d normal = normalStart.mul(1.0 - t).add(normalEnd.mul(t)).normalize();

            // Calculate binormal (perpendicular to both tangent and normal)
            Vector3d binormal = tangent.cross(normal).normalize();

            // Recalculate normal to ensure orthogonality
            normal = binormal.cross(tangent).normalize();

            float yawDiff = 0;
            float pitchDiff = 0;
            float rollDiff = 0;

            if (firstYaw == null) {
                firstYaw = quantizeYawRadians(tangent);
                firstPitch = quantizePitchRadians(tangent);
                firstRoll = calculateRollRadians(normal, tangent);

                double normalizedYaw = normalizeAngle(firstYaw);
                isXAxisAligned = isCardinalDirection(normalizedYaw, 0) || isCardinalDirection(normalizedYaw, Math.PI);

            } else {
                float yaw = (float) Math.atan2(tangent.getZ(), tangent.getX());
                yawDiff = (float) Math.toDegrees(firstYaw - yaw);

                float pitch = (float) Math.atan2(
                        tangent.getY(),
                        Math.sqrt(tangent.getX() * tangent.getX() + tangent.getZ() * tangent.getZ())
                );
                pitchDiff = (float) Math.toDegrees(firstPitch - pitch);

                float roll = calculateRollRadians(normal, tangent);
                rollDiff = (float) Math.toDegrees(firstRoll - roll);

                if (isXAxisAligned) {
                    // X-axis aligned (East/West) - rotate in opposite direction
                    pitchDiff = -pitchDiff;
                    rollDiff = -rollDiff;
                } else {
                    // Z-axis aligned (North/South) - swap pitch and roll
                    float temp = pitchDiff;
                    pitchDiff = rollDiff;
                    rollDiff = temp;
                }
            }

            Vector3d position = bezier(start, handle1, handle2, end, t);
            result.add(new SegmentTransform(position, pitchDiff, rollDiff, yawDiff));
        }

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

    private static double determineHandleLength(Vector3d end1, Vector3d end2, Vector3d axis1, Vector3d axis2) {
        Vector3d cross1 = axis1.cross(new Vector3d(0, 1, 0));
        Vector3d cross2 = axis2.cross(new Vector3d(0, 1, 0));

        double a1 = Math.atan2(-axis2.getZ(), -axis2.getX());
        double a2 = Math.atan2(axis1.getZ(), axis1.getX());
        double angle = a1 - a2;

        float circle = 2 * (float) Math.PI;
        angle = (angle + circle) % circle;
        if (Math.abs(circle - angle) < Math.abs(angle))
            angle = circle - angle;

        // If parallel (straight track)
        if (Math.abs(angle) < 1e-6) {
            double[] intersect = intersect3d(end1, end2, axis1, cross2);
            if (intersect != null) {
                double t = Math.abs(intersect[0]);
                double u = Math.abs(intersect[1]);
                double min = Math.min(t, u);
                double max = Math.max(t, u);

                if (min > 1.2 && max / min > 1 && max / min < 3) {
                    return max - min;
                }
            }
            return end2.distance(end1) / 3.0;
        }

        // Curved track
        double n = circle / angle;
        double factor = 4.0 / 3.0 * Math.tan(Math.PI / (2 * n));
        double[] intersect = intersect3d(end1, end2, cross1, cross2);

        if (intersect == null) {
            return end2.distance(end1) / 3.0;
        }

        double radius = Math.abs(intersect[1]);
        double handleLength = radius * factor;
        if (Math.abs(handleLength) < 1e-6)
            handleLength = 1;

        return handleLength;
    }

    private static double[] intersect3d(Vector3d p1, Vector3d p2, Vector3d d1, Vector3d d2) {
        // Find intersection of two lines in 3D space (ignoring Y component)
        // Line 1: p1 + t * d1
        // Line 2: p2 + u * d2
        // Returns [t, u] or null if parallel

        double d1x = d1.getX();
        double d1z = d1.getZ();
        double d2x = d2.getX();
        double d2z = d2.getZ();

        double det = d1x * d2z - d1z * d2x;
        if (Math.abs(det) < 1e-6) return null; // parallel

        double dx = p2.getX() - p1.getX();
        double dz = p2.getZ() - p1.getZ();

        double t = (dx * d2z - dz * d2x) / det;
        double u = (dx * d1z - dz * d1x) / det;

        return new double[]{t, u};
    }

    private static float calculateRollRadians(Vector3d normal, Vector3d tangent) {
        // Calculate up vector (perpendicular to tangent, in the direction of normal)
        Vector3d up = new Vector3d(0, 1, 0);

        // Create binormal via cross product
        Vector3d binormal = tangent.cross(normal).normalize();

        // Recalculate normal to ensure orthogonality
        Vector3d correctedNormal = binormal.cross(tangent).normalize();

        // Calculate roll as rotation around tangent axis
        // Project normal onto the plane perpendicular to tangent
        Vector3d upProj = up.sub(tangent.mul(up.dot(tangent))).normalize();
        Vector3d normalProj = correctedNormal.sub(tangent.mul(correctedNormal.dot(tangent))).normalize();

        // Roll is the angle between upProj and normalProj
        double roll = Math.atan2(
                binormal.dot(upProj),
                normalProj.dot(upProj)
        );

        return (float) roll;
    }

    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    private static boolean isCardinalDirection(double angle, double target) {
        double diff = Math.abs(normalizeAngle(angle - target));
        return diff < Math.PI / 8; // Within 22.5 degrees
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

    private MatrixM4f cloneMatrix(MatrixM4f matrix) {
        MatrixM4f result = new MatrixM4f();
        result.set(matrix.m00, matrix.m01, matrix.m02, matrix.m03,
                matrix.m10, matrix.m11, matrix.m12, matrix.m13,
                matrix.m20, matrix.m21, matrix.m22, matrix.m23,
                matrix.m30, matrix.m31, matrix.m32, matrix.m33);
        return result;
    }

    private void copyMatrix(MatrixM4f source, MatrixM4f target) {
        target.set(source.m00, source.m01, source.m02, source.m03,
                source.m10, source.m11, source.m12, source.m13,
                source.m20, source.m21, source.m22, source.m23,
                source.m30, source.m31, source.m32, source.m33);
    }

    private static boolean is45DegreeAngle (Vector3d axis) {
        float x = Math.abs((float) axis.getX());
        float y = Math.abs((float) axis.getY());
        float z = Math.abs((float) axis.getZ());

        return (y!=0) && ((y-x < 0.01) || (y-z < 0.01));
    }
}
