package eu.cronmoth.createentityaddon.rendering.copycats;

import com.flowpowered.math.TrigMath;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.color.BlockColorCalculator;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variants;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM2f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;

import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.CopycatBlockEntity;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CopycatRenderer implements BlockRenderer {

    public static final BlockRendererType TYPE = new BlockRendererType.Impl(
            new Key("create", "copycat"), CopycatRenderer::new
    );

    private static final float BLOCK_SCALE = 1f / 16f;

    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final BlockColorCalculator blockColorCalculator;
    private final VectorM3f[] corners = new VectorM3f[8];

    private BlockNeighborhood block;
    private Variant variant;
    private Model modelResource;
    private TileModelView blockModel;
    private float materialYRotation = 0f;
    private BlockState materialBlockState;

    private final Color tintColor = new Color();
    private final VectorM3f rotationRelativeBlockDirection = new VectorM3f(0, 0, 0);

    private final MatrixM4f elementTransform = new MatrixM4f();

    public CopycatRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.blockColorCalculator = resourcePack.createBlockColorCalculator();

        for (int i = 0; i < corners.length; i++) corners[i] = new VectorM3f(0, 0, 0);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView blockModel, Color color) {
        this.block = block;
        this.variant = variant;
        this.blockModel = blockModel;
        float blockColorOpacity = 0f;
        this.modelResource = variant.getModel().getResource(resourcePack.getModels()::get);

        if (!(block.getBlockEntity() instanceof CopycatBlockEntity entity)) return;
        if (modelResource == null) return;

        String half = block.getBlockState().getProperties().get("half"); // bottom or top
        String facingStr = block.getBlockState().getProperties().get("facing");
        Direction facing = Direction.fromString(facingStr);

        String[] name = entity.getMaterial().getName().split(":");
        Map<String,String> materialProperties = entity.getMaterial().getProperties();
        //create BlockState for the copied block
        this.materialBlockState = new BlockState(
                new Key(entity.getMaterial().getName()),
                materialProperties != null ? materialProperties : Map.of());
        this.tintColor.set(0, 0, 0, -1, true);
        this.materialYRotation = resolveMaterialYRotation(materialBlockState);
        Model copiedModel = resourcePack.getModels().get(new ResourcePath<>(name[0] + ":block/" + name[1]));
        if (name[1].equals("copycat_base")) {
            copiedModel = resourcePack.getModels().get(new ResourcePath<>(name[0] + ":block/copycat_base/block"));
        }

        if (copiedModel == null) return;

        int modelStart = blockModel.getStart();
        Element[] elements = modelResource.getElements();

        if (elements != null) {
            for (Element element : elements) {
                buildModelElement(element, blockModel.initialize(), copiedModel, half, facing, materialProperties);
            }
        }

        if (color.a > 0) {
            color.flatten().straight();
            color.a = blockColorOpacity;
        }

        blockModel.initialize(modelStart);
        if (variant.isTransformed()) blockModel.transform(variant.getTransformMatrix());
    }

    private float resolveMaterialYRotation(BlockState materialState) {
        if (materialState.getProperties().isEmpty()) return 0f;

        de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.BlockState resolvedState = resourcePack.getBlockState(materialState);
        if (resolvedState == null) return 0f;

        Variants variants = resolvedState.getVariants();
        if (variants == null) return 0f;

        float[] result = {0f};
        variants.forEach(materialState, 0, 0, 0, v -> result[0] = v.getY());
        return result[0];
    }

    private Color resolveTintColor() {
        if (tintColor.a >= 0) return tintColor;
        if (materialBlockState == null) return tintColor.set(1f, 1f, 1f, 1f, true);
        blockColorCalculator.getBlockColor(block, materialBlockState, tintColor);
        return tintColor;
    }

    private void buildModelElement(Element element, TileModelView model, Model copiedModel, String half, Direction facing, Map<String, String> materialProperties) {
        boolean isStep = half != null;

        Vector3f from = element.getFrom();
        Vector3f to = element.getTo();

        float minX = Math.min(from.getX(), to.getX());
        float minY = Math.min(from.getY(), to.getY());
        float minZ = Math.min(from.getZ(), to.getZ());
        float maxX = Math.max(from.getX(), to.getX());
        float maxY = Math.max(from.getY(), to.getY());
        float maxZ = Math.max(from.getZ(), to.getZ());

        // Compute cube corners (ursprüngliche Position vor Transformation)
        corners[0].set(minX, minY, minZ);
        corners[1].set(minX, minY, maxZ);
        corners[2].set(maxX, minY, minZ);
        corners[3].set(maxX, minY, maxZ);
        corners[4].set(minX, maxY, minZ);
        corners[5].set(minX, maxY, maxZ);
        corners[6].set(maxX, maxY, minZ);
        corners[7].set(maxX, maxY, maxZ);

        // Build transform matrix
        MatrixM4f transform = buildElementTransform(element, isStep, half, facing);

        // Apply rotation/translation to corners
        for (int i = 0; i < 8; i++) transformCorner(corners[i], transform);

        sortCornersByPosition(corners);

        int start = model.getStart();
        for (Direction dir : Direction.values()) {
            VectorM3f[] faceCorners = getFaceCorners(corners, dir);
            face(element, dir, faceCorners[0], faceCorners[1], faceCorners[2], faceCorners[3], copiedModel, materialProperties, facing);
        }

        model.initialize(start);

        // Apply scaling
        MatrixM4f scale = new MatrixM4f();
        scale.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
        model.transform(scale);
    }

    private MatrixM4f buildElementTransform(Element element, boolean isStep, String half, Direction facing) {
        float offsetX = 0f;
        float offsetY = 0f;
        float offsetZ = 0f;

        if ("top".equalsIgnoreCase(half)) offsetY = 8f;

        MatrixM4f transform = elementTransform.copy(element.getRotation().getMatrix());
        transform.translate(offsetX, offsetY, offsetZ);
        transform.translate(-8f, -8f, -8f); // rotate around block center

        if (facing != null) {
            switch (facing) {
                case SOUTH -> transform.rotate(180f, 0f, 1f, 0f);
                case WEST -> transform.rotate(90f, 0f, 1f, 0f);
                case EAST -> transform.rotate(-90f, 0f, 1f, 0f);
                default -> {}
            }

            if (!isStep) {
                switch (facing) {
                    case WEST -> transform.rotate(90f, 0f, 0f, 1f);
                    case EAST -> transform.rotate(-90f, 0f, 0f, 1f);
                    case NORTH -> transform.rotate(-90f, 1f, 0f, 0f);
                    case SOUTH -> transform.rotate(90f, 1f, 0f, 0f);
                    case DOWN -> transform.translate(0f, 13f, 0f);
                    default -> {}
                }
            }
        }

        transform.translate(8f, 8f, 8f);
        return transform;
    }

    private void transformCorner(VectorM3f v, MatrixM4f m) {
        float x = v.x, y = v.y, z = v.z;
        float newX = x * m.m00 + y * m.m01 + z * m.m02 + 1f * m.m03;
        float newY = x * m.m10 + y * m.m11 + z * m.m12 + 1f * m.m13;
        float newZ = x * m.m20 + y * m.m21 + z * m.m22 + 1f * m.m23;
        v.set(newX, newY, newZ);
    }

    private void sortCornersByPosition(VectorM3f[] c) {
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        for (VectorM3f corner : c) {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y);
            maxY = Math.max(maxY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }

        c[0].set(minX, minY, minZ);
        c[1].set(minX, minY, maxZ);
        c[2].set(maxX, minY, maxZ);
        c[3].set(maxX, minY, minZ);
        c[4].set(minX, maxY, minZ);
        c[5].set(minX, maxY, maxZ);
        c[6].set(maxX, maxY, maxZ);
        c[7].set(maxX, maxY, minZ);
    }

    private VectorM3f[] getFaceCorners(VectorM3f[] c, Direction dir) {
        return switch (dir) {
            case DOWN -> new VectorM3f[]{c[2], c[1], c[0], c[3]};
            case UP -> new VectorM3f[]{c[7], c[4], c[5], c[6]};
            case NORTH -> new VectorM3f[]{c[4], c[7], c[3], c[0]};
            case SOUTH -> new VectorM3f[]{c[6], c[5], c[1], c[2]};
            case WEST -> new VectorM3f[]{c[5], c[4], c[0], c[1]};
            case EAST -> new VectorM3f[]{c[7], c[6], c[2], c[3]};
        };
    }

    private void face(
            Element element,
            Direction dir,
            VectorM3f c0,
            VectorM3f c1,
            VectorM3f c2,
            VectorM3f c3,
            Model copiedModel,
            Map<String, String> materialProperties,
            Direction blockFacing) {
        Face face = element.getFaces().get(dir);
        if (face == null) return;

        String axis = materialProperties!=null ? materialProperties.get("axis"):null;
        String facingStr = materialProperties!=null ? materialProperties.get("facing"):null;
        Direction facing = facingStr!=null ? Direction.fromString(facingStr):null;
        Optional<Face> mapped = Arrays.stream(copiedModel.getElements())
                .filter(Objects::nonNull)
                .map(e -> e.getFaces().get(resolveTextureDirection(axis, facing, dir)))
                //.map(e -> e.getFaces().get(dir))
                .filter(Objects::nonNull)
                .findFirst();
        if (mapped.isPresent()) face = mapped.get();

        // Vector from c0 to c1
        VectorM3f vecC0C1 = new VectorM3f(c1.x - c0.x, c1.y - c0.y, c1.z - c0.z);
        float lengthC0C1 = Math.round(vecC0C1.length()*100)/100f;

        // Vector from c0 to c3
        VectorM3f vecC0C3 = new VectorM3f(c3.x - c0.x, c3.y - c0.y, c3.z - c0.z);
        float lengthC0C3 = Math.round(vecC0C3.length()*100)/100f;

        // Each face is split into 4 patches that take their texture from the matching corner of the
        // material's texture. The split does not have to sit in the middle: create builds its 3-thick
        // panel from a 1px slice of the block's back and a 2px slice of its facing side, so an odd
        // thickness is divided into whole pixels instead of 1.5/1.5.
        float lowC0C1 = splitLowLength(lengthC0C1, vecC0C1, blockFacing);
        float lowC0C3 = splitLowLength(lengthC0C3, vecC0C3, blockFacing);
        float highC0C1 = lengthC0C1 - lowC0C1;
        float highC0C3 = lengthC0C3 - lowC0C3;

        // texture-space extent of each half (16 model-units span the whole texture)
        float lowFactorC0C1 = lowC0C1 / 16f, highFactorC0C1 = highC0C1 / 16f;
        float lowFactorC0C3 = lowC0C3 / 16f, highFactorC0C3 = highC0C3 / 16f;

        // where the split sits along each edge, as a fraction of its length
        float splitC0C1 = lengthC0C1 > 0 ? lowC0C1 / lengthC0C1 : 0.5f;
        float splitC0C3 = lengthC0C3 > 0 ? lowC0C3 / lengthC0C3 : 0.5f;

        // ----- AO -----
        float ao0 = 1f, ao1 = 1f, ao2 = 1f, ao3 = 1f;
        if (modelResource.isAmbientocclusion()) {
            ao0 = testAo(c0, dir);
            ao1 = testAo(c1, dir);
            ao2 = testAo(c2, dir);
            ao3 = testAo(c3, dir);
        }

        // ao of the split-points, interpolated at the same fractions the geometry is split at
        float aoC0C1 = lerp(ao0, ao1, splitC0C1);
        float aoC0C3 = lerp(ao0, ao3, splitC0C3);
        float aoC1C2 = lerp(ao1, ao2, splitC0C3);
        float aoC2C3 = lerp(ao3, ao2, splitC0C1);
        float aoCenter = lerp(aoC0C1, aoC2C3, splitC0C3);

        VectorM3f c0c1 = lerpPoint(c0, c1, splitC0C1);
        VectorM3f c0c3 = lerpPoint(c0, c3, splitC0C3);
        VectorM3f c1c2 = lerpPoint(c1, c2, splitC0C3);
        VectorM3f c2c3 = lerpPoint(c3, c2, splitC0C1);
        VectorM3f center = new VectorM3f(
                c0.x + splitC0C1 * vecC0C1.x + splitC0C3 * vecC0C3.x,
                c0.y + splitC0C1 * vecC0C1.y + splitC0C3 * vecC0C3.y,
                c0.z + splitC0C1 * vecC0C1.z + splitC0C3 * vecC0C3.z
        );

        // uvTL/TR/BL/BR belong to the patches at c1 / c0 / c2 / c3 respectively, so each one uses the
        // half-extents of the edges that meet in its own corner.
        VectorM2f[] uvTL = new VectorM2f[]{
                new VectorM2f(0, 0),
                new VectorM2f(highFactorC0C1, 0),
                new VectorM2f(highFactorC0C1, lowFactorC0C3),
                new VectorM2f(0, lowFactorC0C3)};

        VectorM2f[] uvTR = new VectorM2f[]{
                new VectorM2f(1-lowFactorC0C1, 0),
                new VectorM2f(1, 0),
                new VectorM2f(1, lowFactorC0C3),
                new VectorM2f(1-lowFactorC0C1, lowFactorC0C3)};

        VectorM2f[] uvBL = new VectorM2f[]{
                new VectorM2f(0, 1-highFactorC0C3),
                new VectorM2f(highFactorC0C1, 1-highFactorC0C3),
                new VectorM2f(highFactorC0C1, 1),
                new VectorM2f(0, 1)};

        VectorM2f[] uvBR = new VectorM2f[]{
                new VectorM2f(1-lowFactorC0C1, 1-highFactorC0C3),
                new VectorM2f(1, 1-highFactorC0C3),
                new VectorM2f(1, 1),
                new VectorM2f(1-lowFactorC0C1, 1)};


        int rotationSteps = Math.floorMod(
                -(Math.floorDiv(face.getRotation(), 90) + rotationStepsByAxisAndFacing(facing, axis, dir)),
                4);
        rotateUVs(uvTL, rotationSteps);
        rotateUVs(uvTR, rotationSteps);
        rotateUVs(uvBL, rotationSteps);
        rotateUVs(uvBR, rotationSteps);

        int tex = textureGallery.get(face.getTexture().getTexturePath(copiedModel.getTextures()::get));

        float tintR = 1f, tintG = 1f, tintB = 1f;
        if (face.getTintindex() >= 0) {
            Color tint = resolveTintColor();
            tintR = tint.r; tintG = tint.g; tintB = tint.b;
        }

        LightData light = block.getLightData();
        int sunLight = light.getSkyLight();
        int blockLight = light.getBlockLight();

        // Top right
        VectorM3f[] vertexTR = new VectorM3f[]{c0, c0c1, center, c0c3};
        emitQuad(
                new VHelper(vertexTR[0], uvTR[1], ao0),
                new VHelper(vertexTR[1], uvTR[0], aoC0C1),
                new VHelper(vertexTR[2], uvTR[3], aoCenter),
                new VHelper(vertexTR[3], uvTR[2], aoC0C3),
                tex, sunLight, blockLight, tintR, tintG, tintB
        );

        // Top left
        VectorM3f[] vertexTL = new VectorM3f[]{c0c1, c1, c1c2, center};
        emitQuad(
                new VHelper(vertexTL[0], uvTL[1], aoC0C1),
                new VHelper(vertexTL[1], uvTL[0], ao1),
                new VHelper(vertexTL[2], uvTL[3], aoC1C2),
                new VHelper(vertexTL[3], uvTL[2], aoCenter),
                tex, sunLight, blockLight, tintR, tintG, tintB
        );

        // Bottom right
        VectorM3f[] vertexBR = new VectorM3f[]{c0c3, center, c2c3, c3};
        emitQuad(
                new VHelper(vertexBR[0], uvBR[1], aoC0C3),
                new VHelper(vertexBR[1], uvBR[0], aoCenter),
                new VHelper(vertexBR[2], uvBR[3], aoC2C3),
                new VHelper(vertexBR[3], uvBR[2], ao3),
                tex, sunLight, blockLight, tintR, tintG, tintB
        );

        // Bottom left
        VectorM3f[] vertexBL = new VectorM3f[]{center, c1c2, c2, c2c3};
        emitQuad(
                new VHelper(vertexBL[0], uvBL[1], aoCenter),
                new VHelper(vertexBL[1], uvBL[0], aoC1C2),
                new VHelper(vertexBL[2], uvBL[3], ao2),
                new VHelper(vertexBL[3], uvBL[2], aoC2C3),
                tex, sunLight, blockLight, tintR, tintG, tintB
        );
    }

    private float splitLowLength(float length, VectorM3f edge, Direction blockFacing) {
        float half = length / 2f;
        if (blockFacing == null) return half;

        int pixels = Math.round(length);
        if (Math.abs(length - pixels) > 1e-3f || pixels % 2 == 0) return half;

        Vector3i normal = blockFacing.toVector();
        float towardsFacing = edge.x * normal.getX() + edge.y * normal.getY() + edge.z * normal.getZ();
        if (Math.abs(towardsFacing) < 1e-3f) return half; // edge runs across the facing, not along it

        // walking c0 -> c3 towards the facing means the far end is the thick one, and vice versa
        return towardsFacing > 0 ? pixels / 2 : pixels - pixels / 2;
    }

    private VectorM3f lerpPoint(VectorM3f a, VectorM3f b, float t) {
        return new VectorM3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private Direction resolveTextureDirection(String axis, Direction facing, Direction face) {
        if (axis==null && facing != null) {
            if (facing == Direction.UP) {
                return switch (face) {
                    case NORTH -> Direction.DOWN;
                    case EAST -> Direction.EAST;
                    case SOUTH -> Direction.UP;
                    case WEST -> Direction.WEST;
                    case UP -> Direction.NORTH;
                    case DOWN -> Direction.SOUTH;
                };
            }
            if (facing == Direction.DOWN) {
                return switch (face) {
                    case NORTH -> Direction.UP;
                    case EAST -> Direction.EAST;
                    case SOUTH -> Direction.DOWN;
                    case WEST -> Direction.WEST;
                    case UP -> Direction.SOUTH;
                    case DOWN -> Direction.NORTH;
                };
            }

            int steps = Math.floorMod(Math.round(materialYRotation / 90f), 4);
            return rotateHorizontalDirection(face, steps);
        }
        else if (axis != null) {
            switch (axis) {
                case "x" -> {
                    if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                        return Direction.UP;
                    } else if (face.equals(Direction.NORTH) || face.equals(Direction.SOUTH)) {
                        return Direction.NORTH;
                    } else {
                        return Direction.NORTH;
                    }
                }
                case "y" -> {
                    return face;
                }
                case "z" -> {
                    if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                        return Direction.NORTH;
                    } else if (face.equals(Direction.NORTH) || face.equals(Direction.SOUTH)) {
                        return Direction.UP;
                    } else {
                        return Direction.EAST;
                    }
                }
            }
        }
        return face;
    }

    private Direction rotateHorizontalDirection(Direction dir, int steps) {
        if (dir == Direction.UP || dir == Direction.DOWN) return dir;
        Direction result = dir;
        for (int i = 0; i < steps; i++) {
            result = switch (result) {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                default -> result;
            };
        }
        return result;
    }

    private int rotationStepsByAxisAndFacing(Direction facing, String axis, Direction face){

        if (axis==null && facing != null) {
            if (facing == Direction.UP || facing == Direction.DOWN) return 0;
            int steps = Math.floorMod(Math.round(materialYRotation / 90f), 4);
            return (face == Direction.UP || face == Direction.DOWN) ? steps : 0;
        }
        else if (axis != null) {
            switch (axis) {
                case "x" -> {
                    if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                        return 0;
                    } else if (face.equals(Direction.NORTH) || face.equals(Direction.SOUTH)) {
                        return -1;
                    } else {
                        return -1;
                    }
                }
                case "y" -> {
                    return 0;
                }
                case "z" -> {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void rotateUVs (VectorM2f[] uvArray, int rotationSteps) {
        for (VectorM2f uv : uvArray) {
            rotateUV(uv, rotationSteps);
        }
    }

    private void rotateUV (VectorM2f uv, int rotationSteps) {
        for (int i = 0; i < rotationSteps; i++) {
            float cx = TrigMath.cos(Math.PI/2), cy = TrigMath.sin(Math.PI/2);
            uv.translate(-0.5f, -0.5f);
            uv.rotate(cx, cy);
            uv.translate(0.5f, 0.5f);
        }
    }

    private VectorM2f[] swapUV(VectorM2f[] uvArray) {
        VectorM2f[] swapped = new VectorM2f[uvArray.length];
        for (int i = 0; i < uvArray.length; i++) {
            swapped[i] = new VectorM2f(uvArray[i].y, uvArray[i].x);
        }
        return swapped;
    }

    private void emitQuad(
            VHelper a, VHelper b, VHelper c, VHelper d,
            int tex, int sunLight, int blockLight,
            float tintR, float tintG, float tintB
    ) {
        blockModel.initialize();
        blockModel.add(2);
        TileModel tileModel = blockModel.getTileModel();

        int f1 = blockModel.getStart();
        int f2 = f1 + 1;

        tileModel.setPositions(f1, a.p.x, a.p.y, a.p.z, b.p.x, b.p.y, b.p.z, c.p.x, c.p.y, c.p.z);
        tileModel.setPositions(f2, a.p.x, a.p.y, a.p.z, c.p.x, c.p.y, c.p.z, d.p.x, d.p.y, d.p.z);

        tileModel.setUvs(f1, a.uv.x, a.uv.y, b.uv.x, b.uv.y, c.uv.x, c.uv.y);
        tileModel.setUvs(f2, a.uv.x, a.uv.y, c.uv.x, c.uv.y, d.uv.x, d.uv.y);

        tileModel.setMaterialIndex(f1, tex);
        tileModel.setMaterialIndex(f2, tex);

        tileModel.setColor(f1, tintR, tintG, tintB);
        tileModel.setColor(f2, tintR, tintG, tintB);

        tileModel.setBlocklight(f1, blockLight);
        tileModel.setBlocklight(f2, blockLight);

        tileModel.setSunlight(f1, sunLight);
        tileModel.setSunlight(f2, sunLight);

        tileModel.setAOs(f1, a.ao, b.ao, c.ao);
        tileModel.setAOs(f2, a.ao, c.ao, d.ao);
    }


    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private ExtendedBlock getRotationRelativeBlock(int dx, int dy, int dz) {
        rotationRelativeBlockDirection.set(dx, dy, dz);
        makeRotationRelative(rotationRelativeBlockDirection);
        return block.getNeighborBlock(
                Math.round(rotationRelativeBlockDirection.x),
                Math.round(rotationRelativeBlockDirection.y),
                Math.round(rotationRelativeBlockDirection.z)
        );
    }

    private void makeRotationRelative(VectorM3f direction) {
        if (variant.isTransformed()) direction.rotateAndScale(variant.getTransformMatrix());
    }

    /**
     * Whether a coordinate sits on the block's lower (-1) or upper (1) edge, or inside it (0).
     * Compared with a tolerance: the corners of a wall-mounted panel come out of a quaternion rotation
     * as 15.999999 / 0.0000009, so an exact ==16 test would miss them and silently drop their occlusion.
     */
    private static int blockEdge(float coordinate) {
        if (Math.abs(coordinate - 16f) < 1e-3f) return 1;
        if (Math.abs(coordinate) < 1e-3f) return -1;
        return 0;
    }

    private float testAo(VectorM3f vertex, Direction dir) {
        Vector3i dirVec = dir.toVector();
        int occluding = 0;

        int x = blockEdge(vertex.x);
        int y = blockEdge(vertex.y);
        int z = blockEdge(vertex.z);

        if (x * dirVec.getX() + y * dirVec.getY() > 0) {
            if (getRotationRelativeBlock(x, y, 0).getProperties().isOccluding()) occluding++;
        }
        if (x * dirVec.getX() + z * dirVec.getZ() > 0) {
            if (getRotationRelativeBlock(x, 0, z).getProperties().isOccluding()) occluding++;
        }
        if (y * dirVec.getY() + z * dirVec.getZ() > 0) {
            if (getRotationRelativeBlock(0, y, z).getProperties().isOccluding()) occluding++;
        }
        if (x * dirVec.getX() + y * dirVec.getY() + z * dirVec.getZ() > 0) {
            if (getRotationRelativeBlock(x, y, z).getProperties().isOccluding()) occluding++;
        }

        if (occluding > 3) occluding = 3;
        return Math.max(0f, Math.min(1f - occluding * 0.25f, 1f));
    }

}
