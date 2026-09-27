package eu.cronmoth.createentityaddon.rendering.tracks;

import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;

/**
 * Writes an {@link ObjMesh}'s triangles straight into a bluemap {@link TileModelView} - the meshes
 * bluemap can't load as models ({@code .obj} track and chain-conveyor pieces) - and poses them.
 */
public final class ObjMeshRenderer {

    private ObjMeshRenderer() {}

    /**
     * Appends {@code mesh} to {@code view} and applies {@code pose} to just the appended triangles.
     *
     * @param light  {@code [skyLight, blockLight]}
     * @param tex    texture-gallery ids indexed by the mesh's per-triangle material slot
     *               ({@code mesh.mat}); clamped to the array's last entry
     * @param sideAo AO for non-up-facing triangles ({@code 1} = none), to match the darkening
     *               bluemap applies to the box-model blocks beside them. Note that a model's
     *               {@code ambientocclusion} is not inherited from its parent, so SnR's per-wood
     *               track models keep the default {@code true} where create's own set
     *               {@code false}. Up-facing triangles keep AO {@code 1} - a block's top face
     *               isn't occluded either.
     */
    public static void emit(TileModelView view, ObjMesh mesh, MatrixM4f pose, int[] light, int[] tex, float sideAo) {
        emit(view, mesh, pose, light, tex, sideAo, 1f, 1f, 1f);
    }

    /** Same as {@link #emit(TileModelView, ObjMesh, MatrixM4f, int[], int[], float)}, tinted {@code (r,g,b)} - e.g. a sign's dye-colored text drawn from one white glyph atlas. */
    public static void emit(TileModelView view, ObjMesh mesh, MatrixM4f pose, int[] light, int[] tex, float sideAo, float r, float g, float b) {
        int first = view.initialize().add(mesh.triangles);
        TileModel tm = view.getTileModel();
        for (int t = 0; t < mesh.triangles; t++) {
            int f = first + t;
            int p = t * 9;
            int u = t * 6;
            tm.setPositions(f,
                    mesh.pos[p], mesh.pos[p + 1], mesh.pos[p + 2],
                    mesh.pos[p + 3], mesh.pos[p + 4], mesh.pos[p + 5],
                    mesh.pos[p + 6], mesh.pos[p + 7], mesh.pos[p + 8]);
            tm.setUvs(f,
                    mesh.uv[u], mesh.uv[u + 1],
                    mesh.uv[u + 2], mesh.uv[u + 3],
                    mesh.uv[u + 4], mesh.uv[u + 5]);
            tm.setMaterialIndex(f, tex[Math.min(mesh.mat[t], tex.length - 1)]);
            tm.setColor(f, r, g, b);

            // (v1->v2) x (v1->v3), y component vs length - up-facing tris keep full AO
            float ax = mesh.pos[p + 3] - mesh.pos[p], ay = mesh.pos[p + 4] - mesh.pos[p + 1], az = mesh.pos[p + 5] - mesh.pos[p + 2];
            float bx = mesh.pos[p + 6] - mesh.pos[p], by = mesh.pos[p + 7] - mesh.pos[p + 1], bz = mesh.pos[p + 8] - mesh.pos[p + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float ao = (nLen > 1e-6f && ny / nLen > 0.5f) ? 1f : sideAo;
            tm.setAOs(f, ao, ao, ao);

            tm.setSunlight(f, light[0]);
            tm.setBlocklight(f, light[1]);
        }
        view.transform(pose);
    }
}
