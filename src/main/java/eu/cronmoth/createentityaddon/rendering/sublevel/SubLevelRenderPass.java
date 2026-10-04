package eu.cronmoth.createentityaddon.rendering.sublevel;

import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.TileMetaConsumer;
import de.bluecolored.bluemap.core.map.hires.RenderPass;
import de.bluecolored.bluemap.core.map.hires.RenderPassType;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.world.World;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.mca.MCAWorld;

import java.util.List;

/**
 * Render pass that adds every Sable sub-level whose world position lies inside the tile.
 * Registered in {@link RenderPassType#REGISTRY}, which BlueMap runs for every tile after the block and entity passes.
 */
public class SubLevelRenderPass implements RenderPass {

    public static final RenderPassType TYPE = new RenderPassType.Impl(
            new Key("aeronautics", "sub_levels"),
            SubLevelRenderPass::new
    );

    private final ResourcePack resourcePack;
    private final RenderSettings renderSettings;
    private final BlockStateModelRenderer blockRenderer;

    public SubLevelRenderPass(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.renderSettings = renderSettings;
        this.blockRenderer = new BlockStateModelRenderer(resourcePack, textureGallery, renderSettings);
    }

    @Override
    public void render(World world, Vector3i modelMin, Vector3i modelMax, Vector3i modelAnchor, TileModelView tileModel, TileMetaConsumer tileMetaConsumer) {
        if (!(world instanceof MCAWorld mcaWorld)) return;

        List<SubLevelData> ships = SubLevelStore.get(
                mcaWorld.getDimensionFolder().resolve("sublevels"), world.getDimensionType().getMinY());

        for (SubLevelData ship : ships) {
            int x = (int) Math.floor(ship.position[0]);
            int z = (int) Math.floor(ship.position[2]);
            if (x < modelMin.getX() || x > modelMax.getX() || z < modelMin.getZ() || z > modelMax.getZ()) continue;
            renderShip(world, ship, modelAnchor, tileModel);
        }
    }

    private void renderShip(World world, SubLevelData ship, Vector3i modelAnchor, TileModelView tileModel) {
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
            blockRenderer.render(block, tileModel, color);
            tileModel.translate(ship.xs[i], ship.ys[i], ship.zs[i]);
        }
        tileModel.initialize(modelStart);

        MatrixM4f pose = new MatrixM4f().identity()
                .translate((float) (ship.baseX - ship.pivot[0]), (float) (ship.baseY - ship.pivot[1]), (float) (ship.baseZ - ship.pivot[2]))
                .rotateByQuaternion((float) ship.orientation[0], (float) ship.orientation[1], (float) ship.orientation[2], (float) ship.orientation[3])
                .translate((float) (ship.position[0] - modelAnchor.getX()), (float) (ship.position[1] - modelAnchor.getY()), (float) (ship.position[2] - modelAnchor.getZ()));
        tileModel.transform(pose);
    }
}
