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
        System.out.println("track renderer");
        this.block = block;
        this.variant = variant;
        this.blockModel = tileModel;

        modelRenderer.render(block, variant, blockModel.initialize(), blockColor);

        if (!(block.getBlockEntity() instanceof TrackEntity entity)) return;
        if (entity.getConnections().isEmpty()) return;

        Connection c = entity.getConnections().get(0);
        List<Positions> pos = c.getPos();
        Vector3d start = new Vector3d(pos.getFirst().pos[0], pos.getFirst().pos[1], pos.getFirst().pos[2]);
        Vector3d end = new Vector3d(pos.getLast().pos[0], pos.getLast().pos[1], pos.getLast().pos[2]);

        System.out.print(start + " End: ");
        System.out.println(end);

        if (shouldRender(end)) return;

        List<Normals> axis = c.getAxis();
        Vector3d axis0 = new Vector3d(axis.getFirst().v[0], axis.getFirst().v[1], axis.getFirst().v[2]);
        Vector3d axis1 = new Vector3d(axis.getLast().v[0], axis.getLast().v[1], axis.getLast().v[2]);

        List<Normals> normals = c.getNormal();
        Vector3d normal0 = new Vector3d(normals.getFirst().v[0], normals.getFirst().v[1], normals.getFirst().v[2]);
        Vector3d normal1 = new Vector3d(normals.getLast().v[0], normals.getLast().v[1], normals.getLast().v[2]);

        List<SegmentTransform> segments = calculateBezierSegments(start, end, axis0, axis1, normal0, normal1);

        for (SegmentTransform segmentT : segments) {
            Vector3d segment = segmentT.position();
            System.out.println(segment);

            blockModel.initialize();

            ExtendedBlock access = block.copy();
            access.set(
                    block.getX() + segment.getFloorX(),
                    block.getY() + segment.getFloorY(),
                    block.getZ() + segment.getFloorZ()
            );

            ConnectionBlock connectionBlock = new ConnectionBlock(access, block.getBlockState());
            connectionBlock.set(0, 0, 0);

            BlockNeighborhood connBlockNeighbour = new BlockNeighborhood(
                    connectionBlock, resourcePack, renderSettings, block.getDimensionType()
            );

            blockRenderer.render(connBlockNeighbour, blockModel, new Color());
            blockModel.translate((float) segment.getX(), (float) segment.getY(), (float) segment.getZ());
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

        double handleLength = start.distance(end) / 3.0;
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
        stepLUT[0] = 1.0;

        double accumulated = 0.0;
        prev = start;

        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Vector3d p = bezier(start, handle1, handle2, end, t);
            accumulated += p.distance(prev) / length;
            stepLUT[i] = t / accumulated;
            prev = p;
        }

        List<SegmentTransform> result = new ArrayList<>(segments + 1);

        for (int i = 0; i <= segments; i++) {
            double t = (i == segments) ? 1.0 : (i * stepLUT[i] / segments);
            Vector3d position = bezier(start, handle1, handle2, end, t);
            result.add(new SegmentTransform(position, 0, 0, 0));
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

    private static Vector3d slerp(Vector3d a, Vector3d b, double t) {
        double dot = a.dot(b);
        dot = Math.max(-1.0, Math.min(1.0, dot));

        double theta = Math.acos(dot) * t;
        Vector3d relative = b.sub(a.mul(dot)).normalize();

        return a.mul(Math.cos(theta)).add(relative.mul(Math.sin(theta)));
    }
}
