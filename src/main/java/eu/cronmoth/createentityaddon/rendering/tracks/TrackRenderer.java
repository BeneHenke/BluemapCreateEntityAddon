package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
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
     * Empirical lift for the bezier pieces so they line up with the straight track model. Create
     * applies a {@code (0,-0.25,0)} verticalOffset to the track block model at runtime that bluemap
     * does not, so the straight model sits higher here than in-game - this matches the curve to it.
     */
    private static final float CURVE_LIFT = 13f / 64f;
    /** monorail beam lift - its own value: {@code renderMonorailConnection} already lines up. */
    private static final float MONO_LIFT = 6f / 32f;

    private static final java.util.Set<String> ASCENDING_SHAPES = java.util.Set.of("ae", "an", "as", "aw");
    /**
     * Ambient-occlusion for the tiled bezier pieces' side/bottom faces. Bluemap darkens the straight
     * track blocks' non-top faces near the ground (their models keep the default
     * {@code ambientocclusion:true}); the OBJ pieces get no AO, so without this their sides read
     * brighter than the blocks they join. Up-facing triangles keep AO {@code 1} - so does a block's
     * top face - handled in {@link ObjMeshRenderer#emit}.
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

        String modelPath = variant.getModel().getFormatted();
        int modelStart = blockModel.getStart();

        // x_ortho / z_ortho and cross_ortho are real box models bluemap renders fine on its own
        boolean straight = modelPath.contains("x_ortho") || modelPath.contains("z_ortho")
                || modelPath.endsWith("/cross_ortho");
        boolean nativeModel = modelPath.endsWith("/diag")
                || modelPath.endsWith("/diag_2")
                || modelPath.endsWith("/ascending");

        if (straight) {
            modelRenderer.render(block, variant, blockModel.initialize(), blockColor);
            blockModel.initialize(modelStart);
        } else if (nativeModel && renderNativeBlock(modelPath, variant)) {
            blockModel.initialize(modelStart);
        } else if ((modelPath.endsWith("/diag") || modelPath.endsWith("/diag_2"))
                && (modelPath.contains("_narrow") || modelPath.contains("_wide"))
                && renderDiagonalBlock(modelPath)) {
            blockModel.initialize(modelStart);
        } else if (renderCross(modelPath, variant, blockColor)) {
            // a crossing is the union of two ordinary shapes - each half via its own path
            blockModel.initialize(modelStart);
        } else {
            // unknown shape that still carries the create:track renderer key - at least draw track
            int lastSlash = modelPath.lastIndexOf('/');
            String base = (lastSlash != -1) ? modelPath.substring(0, lastSlash) : modelPath;
            modelRenderer.render(block, localVariant(base + "/x_ortho"), blockModel.initialize(), blockColor);
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
                if (TRACK_DEBUG) de.bluecolored.bluemap.core.logger.Logger.global.logInfo(
                        "[cea] track " + block.getX() + "," + block.getY() + "," + block.getZ()
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

            Sample[] samples = sampleCurve(end1, end2, axis1, axis2, faceNormal1, faceNormal2);
            if (TRACK_DEBUG) de.bluecolored.bluemap.core.logger.Logger.global.logInfo(
                    "[cea] track " + block.getX() + "," + block.getY() + "," + block.getZ()
                    + " -> " + pos.getLast().getX() + "," + pos.getLast().getY() + "," + pos.getLast().getZ()
                    + " mat=" + c.getMaterial()
                    + " end1=" + end1 + " end2=" + end2 + " axis1=" + axis1 + " axis2=" + axis2
                    + " -> " + (samples == null ? "NULL (curve dropped)" : samples.length + " samples"));
            if (samples == null) continue;

            String material = c.getMaterial();
            if (material != null && material.contains("monorail")) {
                renderMonorailConnection(samples);
            } else {
                Gauge gauge = gauge(modelPath);
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
    private boolean renderNativeBlock(String modelPath, Variant variant) {
        String which = modelPath.endsWith("/diag_2") ? "diag_2"
                : modelPath.endsWith("/ascending") ? "ascending" : "diag";
        String gaugeKey = modelPath.contains("/monorail/") ? "monorail"
                : modelPath.contains("_narrow") ? "narrow"
                : modelPath.contains("_wide") ? "wide" : "standard";

        TrackMeshExtension ext = TrackMeshExtension.instance();
        ObjMesh m = ext == null ? null : ext.mesh(gaugeKey + "/" + which);
        // only reuse the standard mesh for standard gauge - narrow/wide would be the wrong width
        if (m == null && ext != null && "standard".equals(gaugeKey)) m = ext.mesh("standard/" + which);
        if (m == null) return false;

        int[] tex = materialTextures(materialFromModelPath(modelPath));
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

    /** {@code create:block/track/diag} -&gt; {@code create:standard}; {@code railways:block/track/acacia/diag} -&gt; {@code railways:acacia}. */
    private static String materialFromModelPath(String modelPath) {
        if (modelPath.startsWith("create:")) return "create:standard";
        if (modelPath.contains("/monorail/")) return "railways:monorail";
        int colon = modelPath.indexOf(':');
        int lastSlash = modelPath.lastIndexOf('/');
        int prevSlash = lastSlash > 0 ? modelPath.lastIndexOf('/', lastSlash - 1) : -1;
        if (colon < 0 || prevSlash <= colon) return "create:standard";
        return modelPath.substring(0, colon) + ":" + modelPath.substring(prevSlash + 1, lastSlash);
    }

    /** The gauge (meshes + rail half-gauge) for the model path, or {@code null} if its meshes are missing. */
    private Gauge gauge(String modelPath) {
        String key = modelPath.contains("_narrow") ? "narrow" : modelPath.contains("_wide") ? "wide" : "standard";
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
    private boolean renderCross(String modelPath, Variant variant, Color blockColor) {
        int lastSlash = modelPath.lastIndexOf('/');
        if (lastSlash < 0) return false;
        String base = modelPath.substring(0, lastSlash);
        String[] halves = switch (modelPath.substring(lastSlash + 1)) {
            case "cross_diag"  -> new String[]{"diag", "diag_2"};
            case "cross_d1_xo" -> new String[]{"x_ortho", "diag"};
            case "cross_d1_zo" -> new String[]{"z_ortho", "diag"};
            case "cross_d2_xo" -> new String[]{"x_ortho", "diag_2"};
            case "cross_d2_zo" -> new String[]{"z_ortho", "diag_2"};
            default -> null;
        };
        if (halves == null) return false;

        boolean narrowWide = base.contains("_narrow") || base.contains("_wide");
        for (String half : halves) {
            String halfPath = base + "/" + half;
            if (half.endsWith("ortho")) {
                modelRenderer.render(block, localVariant(halfPath), blockModel.initialize(), blockColor);
            } else if (!renderNativeBlock(halfPath, variant) && narrowWide) {
                renderDiagonalBlock(halfPath);
            }
        }
        return true;
    }

    private record Sample(Vector3d position, Vector3d derivative, Vector3d normal) {}

    /**
     * Narrow/wide {@code diag} / {@code diag_2} ship only as gauge-wrong 2-4-block template JSON
     * (steam'n'rails' real gauge comes from a runtime mixin, not those models). Draw the block as a
     * straight 45&deg; segment through {@code renderConnection} - reusing its gauge-aware mesh tiling,
     * poses and textures - so it matches the ortho track and the bezier curves it joins.
     */
    private boolean renderDiagonalBlock(String modelPath) {
        Gauge gauge = gauge(modelPath);
        if (gauge == null) return false;

        // shape=pd (.../diag) runs (+x,+z); shape=nd (.../diag_2) runs (+x,-z)
        Vector3d dir = (modelPath.endsWith("/diag_2")
                ? new Vector3d(1, 0, -1) : new Vector3d(1, 0, 1)).normalize();
        Vector3d normal = new Vector3d(0, 1, 0).cross(dir).normalize();
        Vector3d center = new Vector3d(0.5, 0, 0.5);

        double half = Math.sqrt(2) / 2; // half the diagonal step - butt-joins the neighbouring diagonal blocks
        int segments = 3;
        Sample[] samples = new Sample[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double s = -half + (2 * half) * i / segments;
            samples[i] = new Sample(center.add(dir.mul(s)), dir, normal);
        }

        // same OBJ-tiled pieces as the bezier curves, so the same side-face AO applies
        renderConnection(samples, materialTextures(materialFromModelPath(modelPath)), gauge, CURVE_AO);
        return true;
    }

    /** Port of {@code BezierConnection.Runtime} + {@code Bezierator}: arc-length-even samples of the curve. */
    private static Sample[] sampleCurve(Vector3d end1, Vector3d end2, Vector3d axis1, Vector3d axis2,
                                        Vector3d faceNormal1, Vector3d faceNormal2) {
        double handleLength = determineHandleLength(end1, end2, axis1, axis2);
        Vector3d finish1 = axis1.mul(handleLength).add(end1);
        Vector3d finish2 = axis2.mul(handleLength).add(end2);

        double length = 0;
        Vector3d prev = end1;
        for (int i = 1; i <= 16; i++) {
            Vector3d p = bezier(end1, finish1, finish2, end2, i / 16.0);
            length += p.distance(prev);
            prev = p;
        }
        int segments = (int) (length * 2);
        if (segments < 1) return null;

        double[] lut = new double[segments + 1];
        lut[0] = 1;
        double combined = 0;
        prev = end1;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vector3d p = bezier(end1, finish1, finish2, end2, t);
            if (i > 0) {
                combined += p.distance(prev) / length;
                lut[i] = t / combined;
            }
            prev = p;
        }

        boolean sameFace = faceNormal1.distance(faceNormal2) < 1e-6;
        Sample[] out = new Sample[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = (i == segments) ? 1.0 : (i * lut[i] / segments);
            Vector3d position = bezier(end1, finish1, finish2, end2, t);
            Vector3d derivative = bezierDerivative(end1, finish1, finish2, end2, t).normalize();
            Vector3d faceNormal = sameFace ? faceNormal1 : slerp(t, faceNormal1, faceNormal2);
            Vector3d normal = faceNormal.cross(derivative).normalize();
            out[i] = new Sample(position, derivative, normal);
        }
        return out;
    }

    /** Port of {@code SegmentAngles} + {@code renderBezierTurn}: tie + left/right rail per segment. */
    private void renderConnection(Sample[] samples, int[] tex, Gauge gauge, float ao) {
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
            double[] tieAngles = getModelAngles(norm[i], mid[i].sub(mid[i - 1]));
            emit(gauge.tie(), pose(mid[i - 1], tieAngles, -0.5f, 0f, 1f), light, tex, ao);

            // rails (left / right)
            for (int s = 0; s < 2; s++) {
                Vector3d railI = (s == 0) ? railL[i] : railR[i];
                Vector3d prevI = (s == 0) ? railL[i - 1] : railR[i - 1];
                Vector3d diff = railI.sub(prevI);
                double[] a = getModelAngles(norm[i], diff);
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
    private void renderMonorailConnection(Sample[] samples) {
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
            beamPiece(monoMiddle, prevBeam, getModelAngles(normal, beam.sub(prevBeam)), pieceY, 1f, light, tex);

            for (boolean isTop : new boolean[]{true, false}) {
                Vector3d cur = isTop ? top[i] : bottom[i];
                Vector3d prev = isTop ? top[i - 1] : bottom[i - 1];
                Vector3d diff = cur.sub(prev);
                float zScale = (float) (diff.length() * (end ? 2.3 : 2.2));
                beamPiece(isTop ? monoTop : monoBottom, prev, getModelAngles(normal, diff), pieceY, zScale, light, tex);
            }
        }
    }

    private void beamPiece(ObjMesh mesh, Vector3d anchor, double[] angles, float pieceY, float zScale, int[] light, int[] tex) {
        if (mesh == null) return;
        emit(mesh, new MatrixM4f().identity()
                .scale(1f, 1f, zScale)
                .translate(0f, pieceY, -1f / 32f)
                .rotateYXZ((float) Math.toDegrees(angles[0]),
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
                .rotateYXZ((float) Math.toDegrees(angles[0]),
                        (float) Math.toDegrees(angles[1]),
                        (float) Math.toDegrees(angles[2]))
                .translate((float) anchor.getX(), (float) anchor.getY() + CURVE_LIFT, (float) anchor.getZ());
    }

    private void emit(ObjMesh mesh, MatrixM4f pose, int[] light, int[] tex) {
        ObjMeshRenderer.emit(blockModel, mesh, pose, light, tex, 1f);
    }

    private void emit(ObjMesh mesh, MatrixM4f pose, int[] light, int[] tex, float ao) {
        ObjMeshRenderer.emit(blockModel, mesh, pose, light, tex, ao);
    }

    private int[] sampleLight(Vector3d rel) {
        ExtendedBlock access = block.copy();
        access.set(
                block.getX() + (int) Math.floor(rel.getX()),
                block.getY() + (int) Math.floor(rel.getY() + 0.25),
                block.getZ() + (int) Math.floor(rel.getZ()));
        ConnectionBlock cb = new ConnectionBlock(access, block.getBlockState());
        BlockNeighborhood nb = new BlockNeighborhood(cb, resourcePack, renderSettings, block.getDimensionType());
        nb.set(cb.getX(), cb.getY(), cb.getZ());
        LightData light = nb.getLightData();
        return new int[]{light.getSkyLight(), light.getBlockLight()};
    }

    // --- create's TrackRenderer.getModelAngles -------------------------------------------------

    private static double[] getModelAngles(Vector3d normal, Vector3d diff) {
        double dx = diff.getX();
        double dy = diff.getY();
        double dz = diff.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.atan2(dx, dz);
        double pitch = Math.atan2(len, dy) - Math.PI * 0.5;

        Vector3d ref = rotate(rotate(new Vector3d(0, 1, 0), Math.toDegrees(pitch), 0), Math.toDegrees(yaw), 1);

        double signum = Math.signum(ref.dot(normal));
        if (Math.abs(signum) < 0.5f)
            signum = ref.sub(normal).lengthSquared() < 0.5f ? -1 : 1;
        double dot = diff.cross(normal).normalize().dot(ref);
        double roll = Math.acos(clamp(dot, -1, 1)) * signum;
        return new double[]{pitch, yaw, roll};
    }

    /** rotate a vector by {@code deg} degrees about axis 0=x / 1=y / 2=z (minecraft sense). */
    private static Vector3d rotate(Vector3d v, double deg, int axis) {
        double r = Math.toRadians(deg);
        double s = Math.sin(r);
        double c = Math.cos(r);
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        return switch (axis) {
            case 0 -> new Vector3d(x, y * c - z * s, y * s + z * c);
            case 1 -> new Vector3d(x * c + z * s, y, -x * s + z * c);
            default -> new Vector3d(x * c - y * s, x * s + y * c, z);
        };
    }

    private static Vector3d slerp(double t, Vector3d a, Vector3d b) {
        double dot = clamp(a.dot(b), -1, 1);
        double theta = Math.acos(dot) * t;
        Vector3d rel = b.sub(a.mul(dot));
        if (rel.lengthSquared() < 1e-12) return a;
        rel = rel.normalize();
        return a.mul(Math.cos(theta)).add(rel.mul(Math.sin(theta)));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

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

    private String neighborShape(Vector3d rel) {
        ExtendedBlock nb = block.getNeighborBlock(
                (int) Math.round(rel.getX()), (int) Math.round(rel.getY()), (int) Math.round(rel.getZ()));
        return nb == null ? null : nb.getBlockState().getProperties().get("shape");
    }

    private static boolean isAscending(String shape) {
        return shape != null && ASCENDING_SHAPES.contains(shape);
    }

    // --- bezier math (create's BezierConnection.Runtime) -------------------------------------

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

        if (Math.abs(angle) < 1e-6) {
            double[] intersect = intersect3d(end1, end2, axis1, cross2);
            if (intersect != null) {
                double t = Math.abs(intersect[0]);
                double u = Math.abs(intersect[1]);
                double min = Math.min(t, u);
                double max = Math.max(t, u);
                if (min > 1.2 && max / min > 1 && max / min < 3)
                    return max - min;
            }
            return end2.distance(end1) / 3.0;
        }

        double n = circle / angle;
        double factor = 4.0 / 3.0 * Math.tan(Math.PI / (2 * n));
        double[] intersect = intersect3d(end1, end2, cross1, cross2);
        if (intersect == null)
            return end2.distance(end1) / 3.0;

        double radius = Math.abs(intersect[1]);
        double handleLength = radius * factor;
        if (Math.abs(handleLength) < 1e-6)
            handleLength = 1;
        return handleLength;
    }

    private static double[] intersect3d(Vector3d p1, Vector3d p2, Vector3d d1, Vector3d d2) {
        double d1x = d1.getX();
        double d1z = d1.getZ();
        double d2x = d2.getX();
        double d2z = d2.getZ();

        double det = d1x * d2z - d1z * d2x;
        if (Math.abs(det) < 1e-6) return null;

        double dx = p2.getX() - p1.getX();
        double dz = p2.getZ() - p1.getZ();

        double t = (dx * d2z - dz * d2x) / det;
        double u = (dx * d1z - dz * d1x) / det;
        return new double[]{t, u};
    }

    private MatrixM4f cloneMatrix(MatrixM4f matrix) {
        MatrixM4f result = new MatrixM4f();
        result.set(matrix.m00, matrix.m01, matrix.m02, matrix.m03,
                matrix.m10, matrix.m11, matrix.m12, matrix.m13,
                matrix.m20, matrix.m21, matrix.m22, matrix.m23,
                matrix.m30, matrix.m31, matrix.m32, matrix.m33);
        return result;
    }
}
