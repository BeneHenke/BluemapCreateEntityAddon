package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;
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
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import eu.cronmoth.createentityaddon.AddonLog;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Connection;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Normals;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Positions;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.TrackEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders create's (and steam'n'rails') {@code create:track} blocks. Straight pieces use their
 * block model; diag / diag_2 / ascending are drawn from their {@code .obj} (bluemap can't load
 * {@code .obj} models), and the curved bezier connections between track nodes are tiled out of
 * create's atomic tie / rail-segment {@code .obj}s - a direct port of
 * {@code BezierConnection.SegmentAngles} + {@code TrackRenderer.renderBezierTurn}. Every {@code .obj}
 * is read from bluemap's loaded mod jars via {@link TrackMeshExtension}; nothing is bundled.
 */
public class TrackRenderer implements BlockRenderer {

    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("create", "track"),
            TrackRenderer::new
    );

    /**
     * The atomic tie / rail-segment meshes plus the rail half-gauge for one track gauge. Standard
     * is create's; steam'n'rails narrow/wide ship their own meshes and shift the gauge in
     * {@code MixinSegmentAngles#railways$modifyRailWidth} (narrow {@code -0.4375}, wide {@code +0.5}).
     */
    private record Gauge(ObjMesh tie, ObjMesh railLeft, ObjMesh railRight, double halfGauge) {}

    /** create's per-piece vertical drop: {@code -2/16 - 1/256}. */
    private static final float PIECE_Y = -2f / 16f - 1f / 256f;
    /**
     * Lifts the bezier pieces onto the straight track model. Create applies a {@code (0,-0.25,0)}
     * verticalOffset to the block model at runtime which bluemap does not, so the straight model
     * sits that much higher here than in-game and the curve has to follow it.
     */
    private static final float CURVE_LIFT = 13f / 64f;
    /** monorail beam lift - its own value: {@code renderMonorailConnection} already lines up. */
    private static final float MONO_LIFT = 6f / 32f;

    /**
     * Ambient-occlusion for the tiled bezier pieces' side/bottom faces, matching what bluemap
     * applies to the straight track blocks they join: those models keep the default
     * {@code ambientocclusion:true} and get their non-top faces darkened near the ground.
     * Up-facing triangles keep AO {@code 1}, as a block's top face does - see
     * {@link ObjMeshRenderer#emit}.
     */
    private static final float CURVE_AO = 0.7f;
    /** {@code -Dcea.trackdebug=true} logs every bezier connection and why it was or wasn't drawn. */
    private static final boolean TRACK_DEBUG = Boolean.getBoolean("cea.trackdebug");

    private final ResourceModelRenderer modelRenderer;
    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final RenderSettings renderSettings;

    private BlockNeighborhood block;
    private TileModelView blockModel;

    /** track material id ({@code create:standard}, {@code railways:acacia}, ...) -&gt; [track, mip, crossing] texture ids. */
    private final Map<String, int[]> materialTextures = new HashMap<>();
    /** "standard" / "narrow" / "wide" -&gt; that gauge's meshes; a key maps to {@code null} if unavailable. */
    private final Map<String, Gauge> gauges = new HashMap<>();

    /** steam'n'rails' monorail curve pieces, built from its {@code segment_*} models on first use. */
    private ObjMesh monoMiddle, monoTop, monoBottom;
    private boolean monoBuilt;

    /**
     * Variants this renderer owns, keyed by model-path. BlockRenderers are held in a ThreadLocal by
     * bluemap, so this cache is confined to one render-thread - unlike the Variant handed to
     * {@link #render}, which is parsed once into the resource-pack and shared by every thread.
     */
    private final Map<String, Variant> localVariants = new HashMap<>();

    public TrackRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.renderSettings = renderSettings;
        this.modelRenderer = new ResourceModelRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView tileModel, Color blockColor) {
        this.block = block;
        this.blockModel = tileModel;

        TrackModel model = TrackModel.of(variant.getModel().getFormatted());
        int modelStart = blockModel.getStart();

        if (model.isStraight()) {
            modelRenderer.render(block, variant, blockModel.initialize(), blockColor);
            blockModel.initialize(modelStart);
        } else if (model.hasObjMesh() && renderNativeBlock(model, variant)) {
            blockModel.initialize(modelStart);
        } else if (model.isDiagonal() && model.isNarrowOrWide() && renderDiagonalBlock(model)) {
            blockModel.initialize(modelStart);
        } else if (renderCross(model, variant, blockColor)) {
            // a crossing is the union of two ordinary shapes - each half via its own path
            blockModel.initialize(modelStart);
        } else {
            // unknown shape that still carries the create:track renderer key - at least draw track
            modelRenderer.render(block, localVariant(model.base() + "/x_ortho"), blockModel.initialize(), blockColor);
            blockModel.initialize(modelStart);
        }

        if (!(block.getBlockEntity() instanceof TrackEntity entity)) return;
        if (entity.getConnections() == null) return;

        for (Connection c : entity.getConnections()) {
            List<Positions> pos = c.getPos();
            List<Normals> starts = c.getStarts();
            List<Normals> axes = c.getAxis();
            List<Normals> normals = c.getNormal();
            if (pos == null || pos.size() < 2 || axes == null || axes.size() < 2
                    || normals == null || normals.size() < 2) {
                if (TRACK_DEBUG) AddonLog.info(
                        "track " + block.getX() + "," + block.getY() + "," + block.getZ()
                        + " connection skipped - bad nbt: pos=" + (pos == null ? "null" : pos.size())
                        + " axes=" + (axes == null ? "null" : axes.size())
                        + " normals=" + (normals == null ? "null" : normals.size()));
                continue;
            }

            Vector3d dir = new Vector3d(pos.getLast().getX(), pos.getLast().getY(), pos.getLast().getZ());
            if (!shouldRender(dir)) continue;

            Vector3d end1 = point(starts, 0, pos.getFirst());
            Vector3d end2 = point(starts, 1, pos.getLast());
            Vector3d axis1 = vec(axes.get(0)).normalize();
            Vector3d axis2 = vec(axes.get(1)).normalize();
            Vector3d faceNormal1 = vec(normals.get(0)).normalize();
            Vector3d faceNormal2 = vec(normals.get(1)).normalize();

            BezierCurve.Sample[] samples = BezierCurve.sample(end1, end2, axis1, axis2, faceNormal1, faceNormal2);
            if (TRACK_DEBUG) AddonLog.info(
                    "track " + block.getX() + "," + block.getY() + "," + block.getZ()
                    + " -> " + pos.getLast().getX() + "," + pos.getLast().getY() + "," + pos.getLast().getZ()
                    + " mat=" + c.getMaterial()
                    + " end1=" + end1 + " end2=" + end2 + " axis1=" + axis1 + " axis2=" + axis2
                    + " -> " + (samples == null ? "NULL (curve dropped)" : samples.length + " samples"));
            if (samples == null) continue;

            String material = c.getMaterial();
            if (material != null && material.contains("monorail")) {
                renderMonorailConnection(samples);
            } else {
                Gauge gauge = gauge(model);
                if (gauge != null) renderConnection(samples, materialTextures(material), gauge, CURVE_AO);
            }
        }
    }

    /**
     * Resolves the rail/tie textures for a track material by reading the material's {@code tie}
     * model (steam'n'rails re-textures create's models per wood type this way). Falls back to
     * create's default standard_track. Cached per material.
     */
    private int[] materialTextures(String material) {
        return materialTextures.computeIfAbsent(material == null ? "create:standard" : material, id -> {
            if (id.contains("monorail")) {
                int t = textureGallery.get(new ResourcePath<>("railways:block/monorail/monorail"));
                return new int[]{t, t, t, t};
            }
            int std = textureGallery.get(new ResourcePath<>("create:block/standard_track"));
            int[] fallback = {std, textureGallery.get(new ResourcePath<>("create:block/standard_track_mip")), std};
            String base;
            if (id.equals("create:standard")) {
                base = "create:block/track";
            } else {
                int colon = id.indexOf(':');
                if (colon < 0) return fallback;
                base = id.substring(0, colon) + ":block/track/" + id.substring(colon + 1);
            }
            try {
                // create's x_ortho uses texture slots #1/#2/#3, its obj_track parent uses #0/#1/#2;
                // read whichever the material's x_ortho model defines and map to obj slots 0/1/2.
                var textures = resourcePack.getModels().get(new ResourcePath<>(base + "/x_ortho")).getTextures();
                var t0 = textures.getOrDefault("1", textures.get("0"));
                var t1 = textures.getOrDefault("2", textures.get("1"));
                var t2 = textures.getOrDefault("3", t0);
                if (t0 == null || t1 == null) return fallback;
                return new int[]{
                        textureGallery.get(t0.getTexturePath(textures::get)),
                        textureGallery.get(t1.getTexturePath(textures::get)),
                        t2 == null ? std : textureGallery.get(t2.getTexturePath(textures::get))
                };
            } catch (RuntimeException e) {
                return fallback;
            }
        });
    }

    /** Renders a diag/diag_2/ascending block straight from its {@code .obj}. False if no mesh loaded. */
    private boolean renderNativeBlock(TrackModel model, Variant variant) {
        TrackMeshExtension ext = TrackMeshExtension.instance();
        ObjMesh m = ext == null ? null : ext.mesh(model.gauge() + "/" + model.shape());
        // only reuse the standard mesh for standard gauge - narrow/wide would be the wrong width
        if (m == null && ext != null && "standard".equals(model.gauge())) m = ext.mesh("standard/" + model.shape());
        if (m == null) return false;

        int[] tex = materialTextures(model.material());
        LightData ld = block.getLightData();
        int[] light = {ld.getSkyLight(), ld.getBlockLight()};

        MatrixM4f pose = variant.isTransformed()
                ? cloneMatrix(variant.getTransformMatrix())
                : new MatrixM4f().identity();
        // OBJ pieces, same as the bezier curves - bluemap gives them no AO, so match the side-face
        // darkening it applies to the straight box-model blocks
        emit(m, pose, light, tex, CURVE_AO);
        return true;
    }

    /**
     * The gauge (meshes + rail half-gauge) to tile a curve with, or {@code null} if its meshes are
     * missing. Only the two steam'n'rails rail widths differ from create's; a monorail block that
     * reaches here has no rail meshes of its own and falls back to create's, as it always did.
     */
    private Gauge gauge(TrackModel model) {
        String key = model.isNarrowOrWide() ? model.gauge() : "standard";
        double halfGauge = key.equals("narrow") ? 0.965 - 0.4375 : key.equals("wide") ? 0.965 + 0.5 : 0.965;
        return gauges.computeIfAbsent(key, k -> {
            ObjMesh tie = mesh(k, "tie"), left = mesh(k, "left"), right = mesh(k, "right");
            return tie != null && left != null && right != null ? new Gauge(tie, left, right, halfGauge) : null;
        });
    }

    /** A mesh from {@link TrackMeshExtension}: the requested gauge, else create's standard mesh. */
    private static ObjMesh mesh(String gauge, String part) {
        TrackMeshExtension ext = TrackMeshExtension.instance();
        if (ext == null) return null;
        ObjMesh m = ext.mesh(gauge + "/" + part);
        return m != null ? m : ext.mesh("standard/" + part);
    }

    /**
     * A diagonal crossing block is the union of an ordinary shape and a diagonal one:
     * <pre>
     *   cross_diag   = diag    + diag_2
     *   cross_d1_xo  = x_ortho + diag       cross_d2_xo = x_ortho + diag_2
     *   cross_d1_zo  = z_ortho + diag       cross_d2_zo = z_ortho + diag_2
     * </pre>
     * Bluemap can't load these models (they're {@code .obj} references), so each half is rendered
     * through the path it would take on its own: ortho halves via bluemap's model renderer, diag
     * halves via {@link #renderNativeBlock} (or {@link #renderDiagonalBlock} for narrow/wide, which
     * have no {@code .obj}). {@code cross_ortho} is a real box model and goes through the straight
     * branch instead. Returns {@code false} if {@code modelPath} is not a diagonal crossing.
     */
    private boolean renderCross(TrackModel model, Variant variant, Color blockColor) {
        String[] halves = switch (model.shape()) {
            case "cross_diag"  -> new String[]{"diag", "diag_2"};
            case "cross_d1_xo" -> new String[]{"x_ortho", "diag"};
            case "cross_d1_zo" -> new String[]{"z_ortho", "diag"};
            case "cross_d2_xo" -> new String[]{"x_ortho", "diag_2"};
            case "cross_d2_zo" -> new String[]{"z_ortho", "diag_2"};
            default -> null;
        };
        if (halves == null) return false;

        for (String half : halves) {
            TrackModel halfModel = model.sibling(half);
            if (halfModel.isStraight()) {
                modelRenderer.render(block, localVariant(halfModel.path()), blockModel.initialize(), blockColor);
            } else if (!renderNativeBlock(halfModel, variant) && model.isNarrowOrWide()) {
                renderDiagonalBlock(halfModel);
            }
        }
        return true;
    }

    /**
     * Narrow/wide {@code diag} / {@code diag_2} ship only as gauge-wrong 2-4-block template JSON
     * (steam'n'rails' real gauge comes from a runtime mixin, not those models). Draw the block as a
     * straight 45&deg; segment through {@code renderConnection} - reusing its gauge-aware mesh tiling,
     * poses and textures - so it matches the ortho track and the bezier curves it joins.
     */
    private boolean renderDiagonalBlock(TrackModel model) {
        Gauge gauge = gauge(model);
        if (gauge == null) return false;

        // shape=pd (.../diag) runs (+x,+z); shape=nd (.../diag_2) runs (+x,-z)
        Vector3d dir = (model.shape().equals("diag_2")
                ? new Vector3d(1, 0, -1) : new Vector3d(1, 0, 1)).normalize();
        Vector3d normal = new Vector3d(0, 1, 0).cross(dir).normalize();
        Vector3d center = new Vector3d(0.5, 0, 0.5);

        double half = Math.sqrt(2) / 2; // half the diagonal step - butt-joins the neighbouring diagonal blocks
        int segments = 3;
        BezierCurve.Sample[] samples = new BezierCurve.Sample[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double s = -half + (2 * half) * i / segments;
            samples[i] = new BezierCurve.Sample(center.add(dir.mul(s)), dir, normal);
        }

        // same OBJ-tiled pieces as the bezier curves, so the same side-face AO applies
        renderConnection(samples, materialTextures(model.material()), gauge, CURVE_AO);
        return true;
    }


    /** Port of {@code SegmentAngles} + {@code renderBezierTurn}: tie + left/right rail per segment. */
    private void renderConnection(BezierCurve.Sample[] samples, int[] tex, Gauge gauge, float ao) {
        int segments = samples.length - 1;
        Vector3d[] railL = new Vector3d[segments + 1];
        Vector3d[] railR = new Vector3d[segments + 1];
        Vector3d[] mid = new Vector3d[segments + 1];
        Vector3d[] norm = new Vector3d[segments + 1];
        for (int i = 0; i <= segments; i++) {
            Vector3d normal = samples[i].normal();
            railL[i] = samples[i].position().add(normal.mul(gauge.halfGauge()));
            railR[i] = samples[i].position().sub(normal.mul(gauge.halfGauge()));
            mid[i] = railL[i].add(railR[i]).mul(0.5);
            norm[i] = normal;
        }

        for (int i = 1; i <= segments; i++) {
            boolean end = i == segments;
            int[] light = sampleLight(mid[i]);

            // tie
            double[] tieAngles = BezierCurve.getModelAngles(norm[i], mid[i].sub(mid[i - 1]));
            emit(gauge.tie(), pose(mid[i - 1], tieAngles, -0.5f, 0f, 1f), light, tex, ao);

            // rails (left / right)
            for (int s = 0; s < 2; s++) {
                Vector3d railI = (s == 0) ? railL[i] : railR[i];
                Vector3d prevI = (s == 0) ? railL[i - 1] : railR[i - 1];
                Vector3d diff = railI.sub(prevI);
                double[] a = BezierCurve.getModelAngles(norm[i], diff);
                float zScale = (float) (diff.length() * (end ? 2.2 : 2.1));
                emit(s == 0 ? gauge.railLeft() : gauge.railRight(), pose(prevI, a, 0f, -1f / 32f, zScale), light, tex, ao);
            }
        }
    }

    /**
     * Steam'n'rails monorail curve: port of {@code MixinSegmentAngles.makeMonorailSegments}. A single
     * beam - {@code segment_middle} at the beam centre plus {@code segment_top}/{@code segment_bottom}
     * caps - tiled along the bezier, split along {@code upNormal = derivative x normal} rather than the
     * rail normal. The three pieces are SnR's own {@code segment_*} models read via {@link MonorailBeam}
     * ({@code uv/16}, matching how bluemap samples the straight monorail block).
     */
    private void renderMonorailConnection(BezierCurve.Sample[] samples) {
        int segments = samples.length - 1;
        if (segments < 1) return;
        ensureMonorailMeshes();
        if (monoMiddle == null && monoTop == null && monoBottom == null) return;

        int[] tex = materialTextures("railways:monorail"); // 4-wide, every slot the monorail skin
        int[] light = {block.getLightData().getSkyLight(), block.getLightData().getBlockLight()};

        Vector3d[] top = new Vector3d[segments + 1];
        Vector3d[] bottom = new Vector3d[segments + 1];
        for (int i = 0; i <= segments; i++) {
            Vector3d upNormal = samples[i].derivative().cross(samples[i].normal()).normalize();
            top[i] = samples[i].position().add(upNormal.mul(8.0 / 16));
            bottom[i] = top[i].add(upNormal.mul(-10.0 / 16));
        }

        for (int i = 1; i <= segments; i++) {
            boolean end = i == segments;
            float pieceY = 2f / 16f + (i % 2 == 0 ? 1f : -1f) / 2048f - 1f / 1024f;
            Vector3d normal = samples[i].normal();

            Vector3d beam = top[i].add(bottom[i]).mul(0.5);
            Vector3d prevBeam = top[i - 1].add(bottom[i - 1]).mul(0.5);
            beamPiece(monoMiddle, prevBeam, BezierCurve.getModelAngles(normal, beam.sub(prevBeam)), pieceY, 1f, light, tex);

            for (boolean isTop : new boolean[]{true, false}) {
                Vector3d cur = isTop ? top[i] : bottom[i];
                Vector3d prev = isTop ? top[i - 1] : bottom[i - 1];
                Vector3d diff = cur.sub(prev);
                float zScale = (float) (diff.length() * (end ? 2.3 : 2.2));
                beamPiece(isTop ? monoTop : monoBottom, prev, BezierCurve.getModelAngles(normal, diff), pieceY, zScale, light, tex);
            }
        }
    }

    private void beamPiece(ObjMesh mesh, Vector3d anchor, double[] angles, float pieceY, float zScale, int[] light, int[] tex) {
        if (mesh == null) return;
        emit(mesh, new MatrixM4f().identity()
                .scale(1f, 1f, zScale)
                .translate(0f, pieceY, -1f / 32f)
                .rotate((float) Math.toDegrees(angles[0]),
                        (float) Math.toDegrees(angles[1]),
                        (float) Math.toDegrees(angles[2]))
                .translate((float) anchor.getX(), (float) anchor.getY() + MONO_LIFT, (float) anchor.getZ()),
                light, tex, CURVE_AO);
    }

    private void ensureMonorailMeshes() {
        if (monoBuilt) return;
        monoBuilt = true;
        String base = "railways:block/monorail/monorail/";
        // uv/16 - match how bluemap samples the (texture_size-ignoring) straight monorail block
        monoMiddle = MonorailBeam.fromModel(resourcePack, base + "segment_middle", 16f);
        monoTop = MonorailBeam.fromModel(resourcePack, base + "segment_top", 16f);
        monoBottom = MonorailBeam.fromModel(resourcePack, base + "segment_bottom", 16f);
    }

    /**
     * {@code T(anchor + worldLift) * Ry(yaw) * Rx(pitch) * Rz(roll) * T(offsetX, PIECE_Y, offsetZ) * S(1,1,zScale)}
     * - matches create's {@code TransformStack} chain for ties (offsetX -0.5, offsetZ 0, zScale 1)
     * and rail segments (offsetX 0, offsetZ -1/32, zScale = diff.length * 2.1|2.2). CURVE_LIFT is
     * added in world-Y (on the anchor), not the local frame, so tilted ascending segments lift
     * straight up rather than up-and-back.
     */
    private static MatrixM4f pose(Vector3d anchor, double[] angles, float offsetX, float offsetZ, float zScale) {
        return new MatrixM4f().identity()
                .scale(1f, 1f, zScale)
                .translate(offsetX, PIECE_Y, offsetZ)
                .rotate((float) Math.toDegrees(angles[0]),
                        (float) Math.toDegrees(angles[1]),
                        (float) Math.toDegrees(angles[2]))
                .translate((float) anchor.getX(), (float) anchor.getY() + CURVE_LIFT, (float) anchor.getZ());
    }

    private void emit(ObjMesh mesh, MatrixM4f pose, int[] light, int[] tex, float ao) {
        ObjMeshRenderer.emit(blockModel, mesh, pose, light, tex, ao);
    }

    /**
     * Light at the cell a curve piece at block-relative {@code rel} sits in.
     *
     * <p>A plain {@link ExtendedBlock} on purpose: a {@code BlockNeighborhood} caches by the low 3
     * bits of each coordinate, so cells 8 apart share a slot and {@code set} silently keeps the old
     * one - which a curve, reaching that far along its bezier, would hit.
     */
    private int[] sampleLight(Vector3d rel) {
        // 5.12's ExtendedBlock/BlockNeighborhood.getLightData() caches on the low bits of the last
        // coordinates set and silently no-ops a set() that lands on the same cell modulo that cache
        // - flipping the low bit first busts it before landing on the real target cell. Needed on
        // both the copied block and the BlockNeighborhood wrapped around it (see "Fix 5.12 light
        // data issue").
        int ax = block.getX() + (int) Math.floor(rel.getX());
        int ay = block.getY() + (int) Math.floor(rel.getY() + 0.25);
        int az = block.getZ() + (int) Math.floor(rel.getZ());

        ExtendedBlock access = block.copy();
        access.set(ax | 1, ay | 1, az | 1);
        access.set(ax, ay, az);
        ConnectionBlock cb = new ConnectionBlock(access, block.getBlockState());
        BlockNeighborhood nb = new BlockNeighborhood(cb, resourcePack, renderSettings, block.getDimensionType());
        int cbx = cb.getX(), cby = cb.getY(), cbz = cb.getZ();
        nb.set(cbx | 1, cby | 1, cbz | 1);
        nb.set(cbx, cby, cbz);
        LightData light = nb.getLightData();
        return new int[]{light.getSkyLight(), light.getBlockLight()};
    }

    // --- create's TrackRenderer.getModelAngles -------------------------------------------------





    // --- helpers ------------------------------------------------------------------------------

    private static Vector3d vec(Normals n) {
        return new Vector3d(n.v[0], n.v[1], n.v[2]);
    }

    private static Vector3d point(List<Normals> starts, int i, Positions fallback) {
        if (starts != null && starts.size() > i && starts.get(i) != null
                && starts.get(i).v != null && starts.get(i).v.length >= 3) {
            double[] v = starts.get(i).v;
            return new Vector3d(v[0], v[1], v[2]);
        }
        return new Vector3d(fallback.getX() + 0.5, fallback.getY(), fallback.getZ() + 0.5);
    }

    private Variant localVariant(String modelPath) {
        return localVariants.computeIfAbsent(modelPath, path ->
                ResourcesGson.INSTANCE.fromJson("{\"model\":\"" + path + "\"}", Variant.class));
    }

    boolean shouldRender(Vector3d v) {
        if (v.getX() != 0) return v.getX() > 0;
        if (v.getZ() != 0) return v.getZ() > 0;
        return v.getY() > 0;
    }

    // --- bezier math (create's BezierConnection.Runtime) -------------------------------------





    private MatrixM4f cloneMatrix(MatrixM4f matrix) {
        MatrixM4f result = new MatrixM4f();
        result.set(matrix.m00, matrix.m01, matrix.m02, matrix.m03,
                matrix.m10, matrix.m11, matrix.m12, matrix.m13,
                matrix.m20, matrix.m21, matrix.m22, matrix.m23,
                matrix.m30, matrix.m31, matrix.m32, matrix.m33);
        return result;
    }
}
