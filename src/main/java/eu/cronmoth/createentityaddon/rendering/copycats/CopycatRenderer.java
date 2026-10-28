package eu.cronmoth.createentityaddon.rendering.copycats;

import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector4f;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.resources.BlockColorCalculatorFactory;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.BlockState;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.world.BlockProperties;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;

import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.MaterialData;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.CopycatBlockEntity;
import eu.cronmoth.createentityaddon.rendering.copycats.shape.CopycatShapes;
import eu.cronmoth.createentityaddon.rendering.copycats.shape.Piece;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CopycatRenderer implements BlockRenderer {

    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("create", "copycat"), CopycatRenderer::new
    );

    private static final float EPS = 1e-4f;

    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final RenderSettings renderSettings;
    private final BlockColorCalculatorFactory.BlockColorCalculator blockColorCalculator;
    private final Function<ResourcePath<Model>, Model> modelProvider;

    private final Color tintColor = new Color();
    private final Color mapColor = new Color();
    private final Color tintResult = new Color();

    private BlockNeighborhood block;
    private TileModelView blockModel;
    private Color blockColor;
    private float blockColorOpacity;

    private de.bluecolored.bluemap.core.world.BlockState materialState;

    public CopycatRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.renderSettings = renderSettings;
        this.blockColorCalculator = resourcePack.getColorCalculatorFactory().createCalculator();
        this.modelProvider = resourcePack.getModels()::get;
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView blockModel, Color color) {
        this.block = block;
        this.blockModel = blockModel;
        this.blockColor = color;
        this.blockColorOpacity = 0f;
        this.tintColor.set(0, 0, 0, -1, true);

        List<Piece> pieces = CopycatShapes.pieces(block.getBlockState());
        if (pieces == null) return;

        Object blockEntity = block.getBlockEntity();
        if (!(blockEntity instanceof CopycatBlockEntity copycatsEntity)) return;

        int modelStart = blockModel.getStart();
        for (Piece piece : pieces) {
            MaterialData material = copycatsEntity.getMaterial(piece.partKey());
            if (material == null || material.getName() == null) continue;

            this.materialState = new de.bluecolored.bluemap.core.world.BlockState(
                    new Key(material.getName()),
                    material.getProperties() == null ? Map.of() : material.getProperties());
            this.tintColor.set(0, 0, 0, -1, true); // tint resolves per material (per piece)
            renderPiece(piece, materialState);
        }

        if (blockColor.a > 0) {
            blockColor.flatten().straight();
            blockColor.a = blockColorOpacity;
        }
        blockModel.initialize(modelStart);
        if (variant.isTransformed()) blockModel.transform(variant.getTransformMatrix());
    }

    private void renderPiece(Piece piece, de.bluecolored.bluemap.core.world.BlockState materialState) {
        BlockState stateResource = resourcePack.getBlockStates().get(materialState.getId());
        if (stateResource == null) return;

        final Variant[] selected = new Variant[1];
        stateResource.forEach(materialState, block.getX(), block.getY(), block.getZ(), v -> selected[0] = v);
        Variant materialVariant = selected[0];
        if (materialVariant == null) return;

        Model model = materialVariant.getModel().getResource(modelProvider);
        if (model == null || model.getElements() == null) return;

        // the material variant transform (0..1 block space) has to be applied to the
        // material geometry before cropping, so the crop happens in render space
        MatrixM4f variantMatrix = materialVariant.isTransformed() ? materialVariant.getTransformMatrix() : null;

        for (Element element : model.getElements()) {
            if (element == null) continue;
            renderElement(piece, element, model, variantMatrix);
        }
    }

    private void renderElement(Piece piece, Element element, Model model, MatrixM4f variantMatrix) {
        Vector3f from = element.getFrom();
        Vector3f to = element.getTo();
        double[] min = {
                Math.min(from.getX(), to.getX()),
                Math.min(from.getY(), to.getY()),
                Math.min(from.getZ(), to.getZ())};
        double[] max = {
                Math.max(from.getX(), to.getX()),
                Math.max(from.getY(), to.getY()),
                Math.max(from.getZ(), to.getZ())};

        MatrixM4f elementMatrix =
                element.getRotation() != null && element.getRotation().getAngle() != 0f
                        ? element.getRotation().getMatrix()
                        : null;
        if (elementMatrix != null && element.getRotation().getAngle() % 90f != 0f) {
            return;
        }

        for (Direction d : Direction.values()) {
            Face face = element.getFaces().get(d);
            if (face == null) continue;

            int axis = axisOf(d);
            int uAxis, vAxis;
            boolean uFlip, vFlip;
            switch (d) {
                case DOWN -> { uAxis = 0; vAxis = 2; uFlip = false; vFlip = false; }
                case UP -> { uAxis = 0; vAxis = 2; uFlip = false; vFlip = true; }
                case NORTH -> { uAxis = 0; vAxis = 1; uFlip = true; vFlip = false; }
                case SOUTH -> { uAxis = 0; vAxis = 1; uFlip = false; vFlip = false; }
                case WEST -> { uAxis = 2; vAxis = 1; uFlip = false; vFlip = false; }
                default -> { uAxis = 2; vAxis = 1; uFlip = true; vFlip = false; } // EAST
            }
            double plane = (d == Direction.UP || d == Direction.EAST || d == Direction.SOUTH)
                    ? max[axis] : min[axis];
            double sizeU = Math.max(max[uAxis] - min[uAxis], EPS);
            double sizeV = Math.max(max[vAxis] - min[vAxis], EPS);
            if (sizeU < EPS || sizeV < EPS) continue;

            double[][] ring = cornerUVs(d, min[uAxis], max[uAxis], min[vAxis], max[vAxis]);
            double[][] pos = new double[4][3];
            float[][] uvs = new float[4][2];
            Vector4f uvRaw = face.getUv();
            float u1 = uvRaw.getX();
            float u2 = uvRaw.getZ();
            float vLow = uvRaw.getW();
            float vHigh = uvRaw.getY();
            for (int i = 0; i < 4; i++) {
                double u = ring[i][0];
                double v = ring[i][1];
                pos[i][axis] = plane;
                pos[i][uAxis] = u;
                pos[i][vAxis] = v;
                double fu = uFlip ? (max[uAxis] - u) / sizeU : (u - min[uAxis]) / sizeU;
                double fv = vFlip ? (max[vAxis] - v) / sizeV : (v - min[vAxis]) / sizeV;
                uvs[i][0] = (u1 + (float) fu * (u2 - u1)) / 16f;
                uvs[i][1] = (vLow + (float) fv * (vHigh - vLow)) / 16f;
            }

            int steps = Math.floorMod(Math.floorDiv(face.getRotation(), 90), 4);
            float[][] quadUvs = new float[4][];
            for (int i = 0; i < 4; i++) quadUvs[i] = uvs[(steps + i) % 4];

            for (int i = 0; i < 4; i++) {
                if (elementMatrix != null) {
                    pos[i] = applyMatrix(elementMatrix, pos[i][0], pos[i][1], pos[i][2]);
                }
                if (variantMatrix != null) {
                    // variant transform matrices operate on 0..1 block space
                    double[] t = applyMatrix(variantMatrix, pos[i][0] / 16, pos[i][1] / 16, pos[i][2] / 16);
                    pos[i] = new double[]{t[0] * 16, t[1] * 16, t[2] * 16};
                }
            }

            Direction materialDir = mapDirection(d, elementMatrix, variantMatrix);
            if (materialDir == null) continue;
            int planeAxis = axisOf(materialDir);
            boolean aligned = true;
            for (int a = 0; a < 3; a++) {
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (double[] p : pos) { lo = Math.min(lo, p[a]); hi = Math.max(hi, p[a]); }
                boolean isPlane = a == planeAxis;
                if (isPlane ? hi - lo > EPS : hi - lo > EPS && countDistinct(pos, a) > 2) aligned = false;
            }
            if (!aligned) continue;

            if (piece.culledMaterialFaces().contains(materialDir)) continue;

            double[] cropMin = piece.cropMin();
            double[] cropMax = piece.cropMax();
            double[] move = piece.move();
            double[][] grad = new double[3][2];
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
            for (int[] e : edges) {
                double[] p0 = pos[e[0]];
                double[] p1 = pos[e[1]];
                int diffAxis = -1;
                for (int a = 0; a < 3; a++) {
                    if (Math.abs(p1[a] - p0[a]) > EPS) {
                        if (diffAxis >= 0) { diffAxis = -1; break; }
                        diffAxis = a;
                    }
                }
                if (diffAxis < 0) continue;
                double len = p1[diffAxis] - p0[diffAxis];
                grad[diffAxis][0] = (quadUvs[e[1]][0] - quadUvs[e[0]][0]) / len;
                grad[diffAxis][1] = (quadUvs[e[1]][1] - quadUvs[e[0]][1]) / len;
            }
            double[][] finalPos = new double[4][3];
            for (int i = 0; i < 4; i++) {
                for (int a = 0; a < 3; a++) {
                    double clamped = clamp(pos[i][a], cropMin[a] * 16, cropMax[a] * 16);
                    double delta = clamped - pos[i][a];
                    quadUvs[i][0] += (float) (grad[a][0] * delta);
                    quadUvs[i][1] += (float) (grad[a][1] * delta);
                    finalPos[i][a] = clamped + move[a] * 16;
                }
            }

            boolean collapsed = false;
            for (int a = 0; a < 3; a++) {
                if (a == planeAxis) continue;
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (double[] p : finalPos) { lo = Math.min(lo, p[a]); hi = Math.max(hi, p[a]); }
                if (hi - lo < EPS) collapsed = true;
            }
            if (collapsed) continue;

            emitQuad(face, model, materialDir, finalPos, quadUvs, planeAxis);
        }
    }

    private Direction mapDirection(Direction dir, MatrixM4f elementMatrix, MatrixM4f variantMatrix) {
        double dx = dir.toVector().getX();
        double dy = dir.toVector().getY();
        double dz = dir.toVector().getZ();
        if (elementMatrix != null) {
            double[] t = applyMatrixLinear(elementMatrix, dx, dy, dz);
            dx = t[0]; dy = t[1]; dz = t[2];
        }
        if (variantMatrix != null) {
            double[] t = applyMatrixLinear(variantMatrix, dx, dy, dz);
            dx = t[0]; dy = t[1]; dz = t[2];
        }
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax < EPS && ay < EPS && az < EPS) return null;
        if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static double[] applyMatrix(MatrixM4f m, double x, double y, double z) {
        return new double[]{
                x * m.m00 + y * m.m01 + z * m.m02 + m.m03,
                x * m.m10 + y * m.m11 + z * m.m12 + m.m13,
                x * m.m20 + y * m.m21 + z * m.m22 + m.m23
        };
    }

    private static double[] applyMatrixLinear(MatrixM4f m, double x, double y, double z) {
        return new double[]{
                x * m.m00 + y * m.m01 + z * m.m02,
                x * m.m10 + y * m.m11 + z * m.m12,
                x * m.m20 + y * m.m21 + z * m.m22
        };
    }

    private void emitQuad(Face face, Model model, Direction materialDir,
                          double[][] pos, float[][] quadUvs, int planeAxis) {

        if (renderSettings.isRenderTopOnly() && materialDir != Direction.UP) return;

        // light of the block itself and of the faced neighbor
        LightData ownLight = block.getLightData();
        ExtendedBlock facedNeighbor = block.getNeighborBlock(
                materialDir.toVector().getX(), materialDir.toVector().getY(), materialDir.toVector().getZ());
        LightData facedLight = facedNeighbor.getLightData();
        int sunLight = Math.max(ownLight.getSkyLight(), facedLight.getSkyLight());
        int blockLight = Math.max(ownLight.getBlockLight(), facedLight.getBlockLight());

        if (block.isRemoveIfCave() &&
                (renderSettings.isCaveDetectionUsesBlockLight() ? Math.max(blockLight, sunLight) : sunLight) == 0) {
            return;
        }

        double finalPlane = pos[0][planeAxis] / 16;
        boolean positiveFinal = materialDir == Direction.UP || materialDir == Direction.EAST || materialDir == Direction.SOUTH;
        boolean atBoundary = positiveFinal ? finalPlane > 1 - EPS : finalPlane < EPS;
        if (atBoundary) {
            BlockProperties p = facedNeighbor.getProperties();
            if (p.isCulling()) return;
            if (p.getCullingIdentical() && facedNeighbor.getBlockState().equals(block.getBlockState())) return;
        }

        // texture
        ResourcePath<Texture> texturePath = face.getTexture().getTexturePath(model.getTextures()::get);
        if (texturePath == null) return;
        int textureId = textureGallery.get(texturePath);

        // tint (resolved for the material state)
        float tR = 1f, tG = 1f, tB = 1f;
        if (face.getTintindex() >= 0) {
            if (tintColor.a < 0) {
                blockColorCalculator.getBlockColor(block, materialState, tintColor);
            }
            tR = tintColor.r; tG = tintColor.g; tB = tintColor.b;
        }

        blockModel.initialize();
        blockModel.add(2);
        TileModel tileModel = blockModel.getTileModel();
        int face1 = blockModel.getStart();
        int face2 = face1 + 1;

        tileModel.setPositions(face1,
                (float) pos[0][0] / 16f, (float) pos[0][1] / 16f, (float) pos[0][2] / 16f,
                (float) pos[1][0] / 16f, (float) pos[1][1] / 16f, (float) pos[1][2] / 16f,
                (float) pos[2][0] / 16f, (float) pos[2][1] / 16f, (float) pos[2][2] / 16f);
        tileModel.setPositions(face2,
                (float) pos[0][0] / 16f, (float) pos[0][1] / 16f, (float) pos[0][2] / 16f,
                (float) pos[2][0] / 16f, (float) pos[2][1] / 16f, (float) pos[2][2] / 16f,
                (float) pos[3][0] / 16f, (float) pos[3][1] / 16f, (float) pos[3][2] / 16f);

        tileModel.setUvs(face1, quadUvs[0][0], quadUvs[0][1], quadUvs[1][0], quadUvs[1][1],
                quadUvs[2][0], quadUvs[2][1]);
        tileModel.setUvs(face2, quadUvs[0][0], quadUvs[0][1], quadUvs[2][0], quadUvs[2][1],
                quadUvs[3][0], quadUvs[3][1]);

        tileModel.setMaterialIndex(face1, textureId);
        tileModel.setMaterialIndex(face2, textureId);

        tileModel.setColor(face1, tR, tG, tB);
        tileModel.setColor(face2, tR, tG, tB);

        tileModel.setBlocklight(face1, blockLight);
        tileModel.setBlocklight(face2, blockLight);

        tileModel.setSunlight(face1, sunLight);
        tileModel.setSunlight(face2, sunLight);

        tileModel.setAOs(face1, 1f, 1f, 1f);
        tileModel.setAOs(face2, 1f, 1f, 1f);

        // accumulate map color (used for the flat map tiles) from up-facing faces
        if (materialDir == Direction.UP) {
            Texture texture = resourcePack.getTextures().get(texturePath);
            if (texture != null) {
                mapColor.set(texture.getColorPremultiplied());
                if (tintColor.a >= 0) mapColor.multiply(tintColor);
                float combinedLight = Math.max(sunLight / 15f, blockLight / 15f);
                combinedLight = (1f - renderSettings.getAmbientLight()) * combinedLight + renderSettings.getAmbientLight();
                mapColor.r *= combinedLight;
                mapColor.g *= combinedLight;
                mapColor.b *= combinedLight;
                if (mapColor.a > blockColorOpacity) blockColorOpacity = mapColor.a;
                blockColor.add(mapColor);
            }
        }
    }

    private static double[][] cornerUVs(Direction dir, double uLo, double uHi, double vLo, double vHi) {
        return switch (dir) {
            case DOWN -> new double[][]{{uLo, vLo}, {uHi, vLo}, {uHi, vHi}, {uLo, vHi}};
            case UP -> new double[][]{{uLo, vHi}, {uHi, vHi}, {uHi, vLo}, {uLo, vLo}};
            case NORTH -> new double[][]{{uHi, vLo}, {uLo, vLo}, {uLo, vHi}, {uHi, vHi}};
            case SOUTH -> new double[][]{{uLo, vLo}, {uHi, vLo}, {uHi, vHi}, {uLo, vHi}};
            case WEST -> new double[][]{{uLo, vLo}, {uHi, vLo}, {uHi, vHi}, {uLo, vHi}};
            default -> new double[][]{{uHi, vLo}, {uLo, vLo}, {uLo, vHi}, {uHi, vHi}}; // EAST
        };
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(Math.max(v, min), max);
    }

    private static int countDistinct(double[][] pos, int axis) {
        List<Double> values = new ArrayList<>();
        for (double[] p : pos) {
            double v = p[axis];
            boolean found = false;
            for (double e : values) {
                if (Math.abs(e - v) <= EPS) { found = true; break; }
            }
            if (!found) values.add(v);
        }
        return values.size();
    }

    private static int axisOf(Direction dir) {
        return switch (dir) {
            case UP, DOWN -> 1;
            case NORTH, SOUTH -> 2;
            default -> 0;
        };
    }
}
