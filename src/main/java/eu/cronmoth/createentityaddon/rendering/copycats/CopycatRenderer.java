
package eu.cronmoth.createentityaddon.rendering.copycats;

import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRenderer;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.resources.BlockColorCalculatorFactory;
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

        // Compute cube corners
        VectorM3f[] c = corners;
        c[0].set(minX, minY, minZ);
        c[1].set(minX, minY, maxZ);
        c[2].set(maxX, minY, minZ);
        c[3].set(maxX, minY, maxZ);
        c[4].set(minX, maxY, minZ);
        c[5].set(minX, maxY, maxZ);
        c[6].set(maxX, maxY, minZ);
        c[7].set(maxX, maxY, maxZ);

        // Build transform matrix before faces
        MatrixM4f transform = buildElementTransform(element, isStep, half, facing);

        // Apply rotation/translation to corners
        for (int i = 0; i < 8; i++) transformCorner(c[i], transform);

        int start = model.getStart();
        for (Direction dir : Direction.values()) {
            VectorM3f[] faceCorners = getFaceCorners(c, dir);
            face(element, dir, faceCorners[0], faceCorners[1], faceCorners[2], faceCorners[3], copiedModel, materialProperties, facing, isStep);
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

    private VectorM3f[] getFaceCorners(VectorM3f[] c, Direction dir) {
        return switch (dir) {
            case DOWN -> new VectorM3f[]{c[0], c[2], c[3], c[1]};
            case UP -> new VectorM3f[]{c[5], c[7], c[6], c[4]};
            case NORTH -> new VectorM3f[]{c[2], c[0], c[4], c[6]};
            case SOUTH -> new VectorM3f[]{c[1], c[3], c[7], c[5]};
            case WEST -> new VectorM3f[]{c[0], c[1], c[5], c[4]};
            case EAST -> new VectorM3f[]{c[3], c[2], c[6], c[7]};
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
            Direction blockFacing, boolean isStep) {
        Face face = element.getFaces().get(dir);
        if (face == null) return;

        String axis = materialProperties!=null ? materialProperties.get("axis"):null;
        Optional<Face> mapped = Arrays.stream(copiedModel.getElements())
                .filter(Objects::nonNull)
                .map(e -> e.getFaces().get((isStep)?orientStep(blockFacing,axis, dir):orientPanel(blockFacing, axis, dir)))
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
        VectorM2f uvBL0 = new VectorM2f(0, 0);
        VectorM2f uvBL1 = new VectorM2f(factorC0C1, 0);
        VectorM2f uvBL2 = new VectorM2f(factorC0C1,factorC0C3);
        VectorM2f uvBL3 = new VectorM2f(0, factorC0C3);

        VectorM2f uvBR0 = new VectorM2f(0, 1-factorC0C3);
        VectorM2f uvBR1 = new VectorM2f(factorC0C1, 1-factorC0C3);
        VectorM2f uvBR2 = new VectorM2f(factorC0C1, 1);
        VectorM2f uvBR3 = new VectorM2f(0, 1);

        VectorM2f uvTL0 = new VectorM2f(1-factorC0C1, 0);
        VectorM2f uvTL1 = new VectorM2f(1, 0);
        VectorM2f uvTL2 = new VectorM2f(1, factorC0C3);
        VectorM2f uvTL3 = new VectorM2f(1-factorC0C1, factorC0C3);

        VectorM2f uvTR0 = new VectorM2f(1-factorC0C1, 1-factorC0C3);
        VectorM2f uvTR1 = new VectorM2f(1, 1-factorC0C3);
        VectorM2f uvTR2 = new VectorM2f(1, 1);
        VectorM2f uvTR3 = new VectorM2f(1-factorC0C1, 1);
        int tex = textureGallery.get(face.getTexture().getTexturePath(copiedModel.getTextures()::get));
        // ----- lighting -----
        LightData light = block.getLightData();
        int sunLight = light.getSkyLight();
        int blockLight = light.getBlockLight();


        // Bottom-left quad
        emitQuad(
                new VHelper(c0,    uvBL0, ao0),
                new VHelper(c0c1,  uvBL1, lerp(ao0, ao1, 0.5f)),
                new VHelper(center,uvBL2, (ao0 + ao1 + ao2 + ao3) * 0.25f),
                new VHelper(c0c3,  uvBL3, lerp(ao0, ao3, 0.5f)),
                tex, sunLight, blockLight
        );

        // Bottom-right quad (now uses TOP-LEFT UVs)
        emitQuad(
                new VHelper(c0c1,  uvTL0, lerp(ao0, ao1, 0.5f)),
                new VHelper(c1,    uvTL1, ao1),
                new VHelper(c1c2,  uvTL2, lerp(ao1, ao2, 0.5f)),
                new VHelper(center,uvTL3, (ao0 + ao1 + ao2 + ao3) * 0.25f),
                tex, sunLight, blockLight
        );

        // Top-right quad
        emitQuad(
                new VHelper(center,uvTR0, (ao0 + ao1 + ao2 + ao3) * 0.25f),
                new VHelper(c1c2,  uvTR1, lerp(ao1, ao2, 0.5f)),
                new VHelper(c2,    uvTR2, ao2),
                new VHelper(c2c3,  uvTR3, lerp(ao2, ao3, 0.5f)),
                tex, sunLight, blockLight
        );

        // Top-left quad
        emitQuad(
                new VHelper(c0c3,  uvBR0, lerp(ao0, ao3, 0.5f)),
                new VHelper(center,uvBR1, (ao0 + ao1 + ao2 + ao3) * 0.25f),
                new VHelper(c2c3,  uvBR2, lerp(ao2, ao3, 0.5f)),
                new VHelper(c3,    uvBR3, ao3),
                tex, sunLight, blockLight
        );
    }

    private Direction orientStep(Direction facing, String axis, Direction face) {
        if (axis==null) {
            return face;
        }
        switch (axis) {
            case "x" -> {
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    if (face == Direction.EAST || face == Direction.WEST) {
                        return Direction.UP;
                    } else {
                        return Direction.WEST;
                    }
                } else {
                    if (face == Direction.NORTH || face == Direction.SOUTH) {
                        return Direction.UP;
                    } else {
                        return Direction.WEST;
                    }
                }
            }
            case "y" -> {
                return face;
            }
            case "z" -> {
                if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                    if (face == Direction.NORTH || face == Direction.SOUTH) {
                        return Direction.UP;
                    } else {
                        return Direction.WEST;
                    }
                } else {
                    if (face == Direction.EAST || face == Direction.WEST) {
                        return Direction.UP;
                    } else {
                        return Direction.WEST;
                    }
                }
            }
        }
        return facing;
    }

    private Direction orientPanel(Direction facing, String axis, Direction face) {
        if (axis==null) {
            return face;
        }
        if (axis.equals("y")) {
            if (facing.equals(Direction.UP) || facing.equals(Direction.DOWN)) {
                return face;
            }
            else if (face.equals(Direction.NORTH) || face.equals(Direction.SOUTH)) {
                return Direction.UP;
            }
            else {
                return Direction.EAST;
            }
        } else if (axis.equals("x")) {
            if (facing.equals(Direction.UP) || facing.equals(Direction.DOWN)) {
                if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                    return Direction.UP;
                }
                else {
                    return Direction.EAST;
                }
            } else if (facing.equals(Direction.WEST) || facing.equals(Direction.EAST)) {
                return face;
            }
            else {
                if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                    return Direction.UP;
                }
                else {
                    return Direction.EAST;
                }
            }
        }
        //Z/North
        else {
            if (facing.equals(Direction.UP) || facing.equals(Direction.DOWN)) {
                if (face.equals(Direction.NORTH) || face.equals(Direction.SOUTH)) {
                    return Direction.UP;
                }
                else {
                    return Direction.EAST;
                }
            } else if (facing.equals(Direction.NORTH) || facing.equals(Direction.SOUTH)) {
                return face;
            }
            else {
                if (face.equals(Direction.EAST) || face.equals(Direction.WEST)) {
                    return Direction.UP;
                }
                else {
                    return Direction.EAST;
                }
            }
        }
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

    private ExtendedBlock getRotationRelativeBlock(Vector3i direction) {
        return getRotationRelativeBlock(direction.getX(), direction.getY(), direction.getZ());
    }

    private final VectorM3f rotationRelativeBlockDirection = new VectorM3f(0, 0, 0);

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
