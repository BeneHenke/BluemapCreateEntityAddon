package eu.cronmoth.createentityaddon.rendering.sublevel;

import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.TileMetaConsumer;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.map.hires.HiresModelRenderer;
import de.bluecolored.bluemap.core.map.hires.MaxCapacityReachedException;
import de.bluecolored.bluemap.core.map.hires.PRBMWriter;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.world.World;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;
import eu.cronmoth.createentityaddon.AddonLog;
import eu.cronmoth.createentityaddon.CreateEntityAddon;
import eu.cronmoth.createentityaddon.rendering.contraptions.ContraptionEntityRenderer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Renders the vanilla tile and then adds every Sable sub-level whose world position lies
 * inside the tile. Installed over the map's own renderer by {@link SubLevelInstaller}.
 */
public class SubLevelHiresModelRenderer extends HiresModelRenderer {

    private final ResourcePack resourcePack;
    private final RenderSettings renderSettings;
    private final ThreadLocal<BlockStateModelRenderer> blockRenderer;
    private final ContraptionEntityRenderer contraptionRenderer;

    public SubLevelHiresModelRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        super(resourcePack, textureGallery, renderSettings);
        this.resourcePack = resourcePack;
        this.renderSettings = renderSettings;
        this.blockRenderer = ThreadLocal.withInitial(() -> new BlockStateModelRenderer(resourcePack, textureGallery, renderSettings));
        this.contraptionRenderer = new ContraptionEntityRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(World world, Vector3i modelMin, Vector3i modelMax, TileModel tileModel, TileMetaConsumer tileMetaConsumer) {
        super.render(world, modelMin, modelMax, tileModel, tileMetaConsumer);
        if (!(world instanceof MCAWorld mcaWorld)) return;

        List<SubLevelData> ships = SubLevelStore.get(
                mcaWorld.getDimensionFolder().resolve("sublevels"), world.getDimensionType().getMinY());
        if (ships.isEmpty()) return;

        try {
            boolean bakeIntoMap = CreateEntityAddon.config == null
                    || CreateEntityAddon.config.isBakeSableSublevelsIntoMap();

            for (SubLevelData ship : ships) {

                if (!bakeIntoMap) {
                    ArrayTileModel arrayTileModel = new ArrayTileModel(100);
                    TileModelView tileModelView = new TileModelView(arrayTileModel);
                    tileModel = tileModelView.getTileModel();
                }

                int x = (int) Math.floor(ship.position[0]);
                int z = (int) Math.floor(ship.position[2]);
                if (x < modelMin.getX() || x > modelMax.getX() || z < modelMin.getZ() || z > modelMax.getZ()) continue;
                renderShip(world, ship, new Vector3i(modelMin.getX(), 0, modelMin.getZ()), new TileModelView(tileModel), bakeIntoMap);

                if (!bakeIntoMap) {
                    String workingDir = System.getProperty("user.dir");
                    Path dir = Path.of(workingDir, CreateEntityAddon.config.getSublevelsDirectory());
                    Path filePath = dir.resolve("sublevel_" + ship.uuid + ".prbm");
                    try {
                        Files.createDirectories(filePath.getParent());
                        try (OutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                            new PRBMWriter(outputStream).write((ArrayTileModel) tileModel);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (!bakeIntoMap) {
                cleanupStaleSublevelFiles(ships);
            }
        } catch (MaxCapacityReachedException ex) {
            AddonLog.warnOnce("max-capacity-reached",
                    "one or more map-tiles are too complex to be completed (@~ %s to %s): %s".formatted(modelMin, modelMax, ex));
        }
    }

    private void cleanupStaleSublevelFiles(List<SubLevelData> ships) {
        String workingDir = System.getProperty("user.dir");
        Path dir = Path.of(workingDir, CreateEntityAddon.config.getSublevelsDirectory());
        if (!Files.isDirectory(dir)) return;

        Set<String> expected = new HashSet<>();
        for (SubLevelData ship : ships) {
            expected.add("sublevel_" + ship.uuid + ".prbm");
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".prbm"))
                    .filter(p -> !expected.contains(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            AddonLog.warn("failed to delete stale sublevel file " + p);
                        }
                    });
        } catch (IOException e) {
            AddonLog.warn("failed to list sublevel directory " + dir);
        }
    }

    private void renderShip(World world, SubLevelData ship, Vector3i modelAnchor, TileModelView tileModel, boolean bakeTransform) {
        BlockStateModelRenderer renderer = blockRenderer.get();
        Color color = new Color();

        int wx = (int) Math.floor(ship.position[0]);
        int wy = (int) Math.floor(ship.position[1]);
        int wz = (int) Math.floor(ship.position[2]);

        int modelStart = tileModel.initialize().getStart();
        for (int i = 0; i < ship.blockCount(); i++) {
            int nx = wx + ship.xs[i], ny = wy + ship.ys[i], nz = wz + ship.zs[i];

            BlockNeighborhood block = new BlockNeighborhood(
                    new SubLevelBlock(ship, wx, wy, wz), resourcePack, renderSettings, world.getDimensionType());
            block.set(nx | 1, ny | 1, nz | 1);
            block.set(nx, ny, nz);

            tileModel.initialize();
            renderer.render(block, tileModel, color);
            tileModel.translate(ship.xs[i], ship.ys[i], ship.zs[i]);
        }

        // Render contraption entities (static Create contraptions inside the sub-level)
        for (SublevelContraption sc : ship.contraptions) {
            tileModel.initialize();
            BlockNeighborhood neighborhood = new BlockNeighborhood(
                    new SubLevelBlock(ship, wx, wy, wz), resourcePack, renderSettings, world.getDimensionType());
            neighborhood.set(wx + sc.localX, wy + sc.localY, wz + sc.localZ);
            contraptionRenderer.render(sc.entity, neighborhood, null, tileModel);
            tileModel.translate(sc.localX, sc.localY, sc.localZ);
        }

        tileModel.initialize(modelStart);

        MatrixM4f pose = new MatrixM4f().identity()
                .translate((float) (ship.baseX - ship.pivot[0]), (float) (ship.baseY - ship.pivot[1]), (float) (ship.baseZ - ship.pivot[2]));
        if (bakeTransform) {
            pose.rotateByQuaternion((float) ship.orientation[0], (float) ship.orientation[1], (float) ship.orientation[2], (float) ship.orientation[3])
                .translate((float) (ship.position[0] - modelAnchor.getX()), (float) (ship.position[1] - modelAnchor.getY()), (float) (ship.position[2] - modelAnchor.getZ()));
        }
        tileModel.transform(pose);
    }
}
