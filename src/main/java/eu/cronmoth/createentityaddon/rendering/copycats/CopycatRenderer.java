
package eu.cronmoth.createentityaddon.rendering.copycats;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Element;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Face;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM2f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
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
    private final VectorM3f[] corners = new VectorM3f[8];

    private BlockNeighborhood block;
    private Variant variant;
    private Model modelResource;
    private TileModelView blockModel;
    private final VectorM3f rotationRelativeBlockDirection = new VectorM3f(0, 0, 0);

    private final MatrixM4f elementTransform = new MatrixM4f();

    public CopycatRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;

        for (int i = 0; i < corners.length; i++) corners[i] = new VectorM3f(0, 0, 0);
    }

    @Override
    public void render(BlockNeighborhood block, Variant variant, TileModelView blockModel, Color color) {
        this.block = block;
        this.variant = variant;
        this.blockModel = blockModel;
        float blockColorOpacity = 0f;
        this.modelResource = variant.getModel().getResource(resourcePack::getModel);

        if (!(block.getBlockEntity() instanceof CopycatBlockEntity entity)) return;
        if (modelResource == null) return;

        String half = block.getBlockState().getProperties().get("half"); // bottom or top
        String facingStr = block.getBlockState().getProperties().get("facing");
        Direction facing = Direction.fromString(facingStr);

        String[] name = entity.getMaterial().getName().split(":");
        Map<String,String> materialProperties = entity.getMaterial().getProperties();
        Model copiedModel = resourcePack.getModel(new ResourcePath<>(name[0] + ":block/" + name[1]));
        if (name[1].equals("copycat_base")) {
            copiedModel = resourcePack.getModel(new ResourcePath<>(name[0] + ":block/copycat_base/block"));
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
            face(element, dir, faceCorners[0], faceCorners[1], faceCorners[2], faceCorners[3], copiedModel, materialProperties);
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
            case DOWN -> new VectorM3f[]{c[0], c[3], c[2], c[1]};     // Boden: minY (von außen unten)
            case UP -> new VectorM3f[]{c[5], c[6], c[7], c[4]};       // Decke: maxY (von außen oben)
            case NORTH -> new VectorM3f[]{c[4], c[7], c[3], c[0]};    // Vorne: minZ (von außen, CW)
            case SOUTH -> new VectorM3f[]{c[6], c[5], c[1], c[2]};    // Hinten: maxZ (von außen, CW)
            case WEST -> new VectorM3f[]{c[5], c[4], c[0], c[1]};     // Links: minX (von außen, CW)
            case EAST -> new VectorM3f[]{c[7], c[6], c[2], c[3]};     // Rechts: maxX (von außen, CW)
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
            Map<String, String> materialProperties) {
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

        float factorC0C3 = lengthC0C3 / 32;
        float factorC0C1 = lengthC0C1 / 32;

        // ----- AO -----
        float ao0 = 1f, ao1 = 1f, ao2 = 1f, ao3 = 1f;
        if (modelResource.isAmbientocclusion()) {
            ao0 = testAo(c0, dir);
            ao1 = testAo(c1, dir);
            ao2 = testAo(c2, dir);
            ao3 = testAo(c3, dir);
        }

        VectorM3f c0c1 = new VectorM3f((c0.x + c1.x)/2f, (c0.y + c1.y)/2f, (c0.z + c1.z)/2f);
        VectorM3f c0c3 = new VectorM3f((c0.x + c3.x)/2f, (c0.y + c3.y)/2f, (c0.z + c3.z)/2f);
        VectorM3f c1c2 = new VectorM3f((c1.x + c2.x)/2f, (c1.y + c2.y)/2f, (c1.z + c2.z)/2f);
        VectorM3f c2c3 = new VectorM3f((c2.x + c3.x)/2f, (c2.y + c3.y)/2f, (c2.z + c3.z)/2f);
        VectorM3f center = new VectorM3f(
                (c0.x + c1.x + c2.x + c3.x)/4f,
                (c0.y + c1.y + c2.y + c3.y)/4f,
                (c0.z + c1.z + c2.z + c3.z)/4f
        );

        VectorM2f[] uvTL = new VectorM2f[]{
                new VectorM2f(0, 0),
                new VectorM2f(factorC0C1, 0),
                new VectorM2f(factorC0C1, factorC0C3),
                new VectorM2f(0, factorC0C3)};

        VectorM2f[] uvTR = new VectorM2f[]{
                new VectorM2f(1-factorC0C1, 0),
                new VectorM2f(1, 0),
                new VectorM2f(1, factorC0C3),
                new VectorM2f(1-factorC0C1, factorC0C3)};

        VectorM2f[] uvBL = new VectorM2f[]{
                new VectorM2f(0, 1-factorC0C3),
                new VectorM2f(factorC0C1, 1-factorC0C3),
                new VectorM2f(factorC0C1, 1),
                new VectorM2f(0, 1)};

        VectorM2f[] uvBR = new VectorM2f[]{
                new VectorM2f(1-factorC0C1, 1-factorC0C3),
                new VectorM2f(1, 1-factorC0C3),
                new VectorM2f(1, 1),
                new VectorM2f(1-factorC0C1, 1)};

        boolean invertUV = invertUV(facing, axis, dir);

        if (invertUV) {
            uvTL = swapUV(uvTL);
            uvTR = swapUV(uvTR);
            uvBL = swapUV(uvBL);
            uvBR = swapUV(uvBR);
        }
        int tex = textureGallery.get(face.getTexture().getTexturePath(copiedModel.getTextures()::get));
        // ----- lighting -----
        LightData light = block.getLightData();
        int sunLight = light.getSkyLight();
        int blockLight = light.getBlockLight();

        // Top right
        VectorM3f[] vertexTR = new VectorM3f[]{c0, c0c1, center, c0c3};
        emitQuad(
                new VHelper(vertexTR[0], uvTR[1], ao0),
                new VHelper(vertexTR[1], uvTR[0], lerp(ao0, ao1, 0.5f)),
                new VHelper(vertexTR[2], uvTR[3], (ao0 + ao1 + ao2 + ao3) * 0.25f),
                new VHelper(vertexTR[3], uvTR[2], lerp(ao0, ao3, 0.5f)),
                tex, sunLight, blockLight
        );

        // Top left
        VectorM3f[] vertexTL = new VectorM3f[]{c0c1, c1, c1c2, center};
        emitQuad(
                new VHelper(vertexTL[0], uvTL[1], lerp(ao0, ao1, 0.5f)),
                new VHelper(vertexTL[1], uvTL[0], ao1),
                new VHelper(vertexTL[2], uvTL[3], lerp(ao1, ao2, 0.5f)),
                new VHelper(vertexTL[3], uvTL[2], (ao0 + ao1 + ao2 + ao3) * 0.25f),
                tex, sunLight, blockLight
        );

        // Bottom right
        VectorM3f[] vertexBR = new VectorM3f[]{c0c3, center, c2c3, c3};
        emitQuad(
                new VHelper(vertexBR[0], uvBR[1], (ao0 + ao1 + ao2 + ao3) * 0.25f),
                new VHelper(vertexBR[1], uvBR[0], lerp(ao1, ao2, 0.5f)),
                new VHelper(vertexBR[2], uvBR[3], ao2),
                new VHelper(vertexBR[3], uvBR[2], lerp(ao2, ao3, 0.5f)),
                tex, sunLight, blockLight
        );

        // Bottom left
        VectorM3f[] vertexBL = new VectorM3f[]{center, c1c2, c2, c2c3};
        emitQuad(
                new VHelper(vertexBL[0], uvBL[1], lerp(ao0, ao3, 0.5f)),
                new VHelper(vertexBL[1], uvBL[0], (ao0 + ao1 + ao2 + ao3) * 0.25f),
                new VHelper(vertexBL[2], uvBL[3], lerp(ao2, ao3, 0.5f)),
                new VHelper(vertexBL[3], uvBL[2], ao3),
                tex, sunLight, blockLight
        );
    }

    private Direction resolveTextureDirection(String axis, Direction facing, Direction face) {
        if (axis==null && facing != null) {
            return face;
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

    private boolean invertUV (Direction facing, String axis, Direction face){

        if (axis==null && facing != null) {
            return false;
        }
        else if (axis != null) {
            switch (axis) {
                case "x" -> {
                    if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                        return false;
                    } else if (face.equals(Direction.NORTH) || face.equals(Direction.SOUTH)) {
                        return true;
                    } else {
                        return true;
                    }
                }
                case "y" -> {
                    return false;
                }
                case "z" -> {
                    return false;
                }
            }
        }
        return false;
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
            int tex, int sunLight, int blockLight
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

        tileModel.setColor(f1, 1, 1, 1);
        tileModel.setColor(f2, 1, 1, 1);

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

    private float testAo(VectorM3f vertex, Direction dir) {
        Vector3i dirVec = dir.toVector();
        int occluding = 0;

        int x = vertex.x == 16 ? 1 : vertex.x == 0 ? -1 : 0;
        int y = vertex.y == 16 ? 1 : vertex.y == 0 ? -1 : 0;
        int z = vertex.z == 16 ? 1 : vertex.z == 0 ? -1 : 0;

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
