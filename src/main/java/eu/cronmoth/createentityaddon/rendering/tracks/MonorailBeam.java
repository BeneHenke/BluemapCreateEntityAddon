package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector4f;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a bluemap-parsed box model into an {@link ObjMesh} so it can be posed/tiled in code.
 * Used for steam'n'rails' monorail curve pieces ({@code segment_middle/top/bottom}) - the model
 * files come straight from the mod, nothing is authored here. UVs are divided by {@code uvDivisor}:
 * pass {@code 16} to sample the texture exactly as bluemap's {@code ResourceModelRenderer} does for
 * the straight monorail block (which also ignores {@code texture_size}), so the curve matches it.
 */
final class MonorailBeam {

    private MonorailBeam() {}

    /** face direction -&gt; its four corner indices, in bluemap's {@code ResourceModelRenderer} order. */
    private static final Direction[] DIRS = {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int[][] FACE_CORNERS = {
            {0, 2, 3, 1}, // down
            {5, 7, 6, 4}, // up
            {2, 0, 4, 6}, // north
            {1, 3, 7, 5}, // south
            {0, 1, 5, 4}, // west
            {3, 2, 6, 7}, // east
    };

    /** Builds the mesh for {@code modelPath}, or {@code null} if the model / its elements are missing. */
    static ObjMesh fromModel(ResourcePack resourcePack, String modelPath, float uvDivisor) {
        Model model;
        try {
            model = resourcePack.getModels().get(new ResourcePath<>(modelPath));
        } catch (RuntimeException e) {
            return null;
        }
        if (model == null || model.getElements() == null) return null;

        List<Float> pos = new ArrayList<>();
        List<Float> uv = new ArrayList<>();
        int tris = 0;

        for (Element el : model.getElements()) {
            if (el == null) continue;
            Vector3f from = el.getFrom(), to = el.getTo();
            float x0 = Math.min(from.getX(), to.getX()), y0 = Math.min(from.getY(), to.getY()), z0 = Math.min(from.getZ(), to.getZ());
            float x1 = Math.max(from.getX(), to.getX()), y1 = Math.max(from.getY(), to.getY()), z1 = Math.max(from.getZ(), to.getZ());
            float[][] corner = {
                    {x0, y0, z0}, {x0, y0, z1}, {x1, y0, z0}, {x1, y0, z1},
                    {x0, y1, z0}, {x0, y1, z1}, {x1, y1, z0}, {x1, y1, z1},
            };
            MatrixM4f m = el.getRotation().getMatrix();

            for (int d = 0; d < 6; d++) {
                Face face = el.getFaces().get(DIRS[d]);
                if (face == null) continue;

                float[][] p = new float[4][];
                for (int k = 0; k < 4; k++) {
                    float[] c = corner[FACE_CORNERS[d][k]];
                    // pixel-space element rotation, then scale to block units
                    p[k] = new float[]{
                            (m.m00 * c[0] + m.m01 * c[1] + m.m02 * c[2] + m.m03) / 16f,
                            (m.m10 * c[0] + m.m11 * c[1] + m.m12 * c[2] + m.m13) / 16f,
                            (m.m20 * c[0] + m.m21 * c[1] + m.m22 * c[2] + m.m23) / 16f,
                    };
                }

                Vector4f raw = face.getUv();
                float ux = raw.getX() / uvDivisor, uy = raw.getY() / uvDivisor,
                        uz = raw.getZ() / uvDivisor, uw = raw.getW() / uvDivisor;
                float[][] rawUv = {{ux, uw}, {uz, uw}, {uz, uy}, {ux, uy}};
                int steps = Math.floorMod(face.getRotation(), 360) / 90;
                float[][] fUv = new float[4][];
                for (int k = 0; k < 4; k++) fUv[k] = rawUv[(steps + k) % 4];

                addTri(pos, uv, p[0], p[1], p[2], fUv[0], fUv[1], fUv[2]);
                addTri(pos, uv, p[0], p[2], p[3], fUv[0], fUv[2], fUv[3]);
                tris += 2;
            }
        }
        if (tris == 0) return null;

        float[] posArr = new float[pos.size()];
        for (int i = 0; i < posArr.length; i++) posArr[i] = pos.get(i);
        float[] uvArr = new float[uv.size()];
        for (int i = 0; i < uvArr.length; i++) uvArr[i] = uv.get(i);
        return ObjMesh.of(posArr, uvArr, new int[tris]); // one texture slot
    }

    private static void addTri(List<Float> pos, List<Float> uv,
                               float[] a, float[] b, float[] c,
                               float[] ua, float[] ub, float[] uc) {
        pos.add(a[0]); pos.add(a[1]); pos.add(a[2]);
        pos.add(b[0]); pos.add(b[1]); pos.add(b[2]);
        pos.add(c[0]); pos.add(c[1]); pos.add(c[2]);
        uv.add(ua[0]); uv.add(ua[1]);
        uv.add(ub[0]); uv.add(ub[1]);
        uv.add(uc[0]); uv.add(uc[1]);
    }
}
