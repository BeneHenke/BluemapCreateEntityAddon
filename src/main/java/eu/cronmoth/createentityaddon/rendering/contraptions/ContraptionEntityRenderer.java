package eu.cronmoth.createentityaddon.rendering.contraptions;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.map.hires.entity.EntityRenderer;
import de.bluecolored.bluemap.core.map.hires.entity.EntityRendererType;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.entitystate.Part;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.Entity;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import eu.cronmoth.createentityaddon.rendering.ContraptionBlockNeighborhood;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.BlockAttribute;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;

import java.util.HashMap;
import java.util.Map;

public class ContraptionEntityRenderer implements EntityRenderer {
    public static final EntityRendererType TYPE = new EntityRendererType.Impl(new Key("create","contraption"), ContraptionEntityRenderer::new);
    private final ResourcePack resourcePack;
    private final BlockStateModelRenderer blockRenderer;
    private final RenderSettings renderSettings;


    public ContraptionEntityRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.blockRenderer = new BlockStateModelRenderer(resourcePack, textureGallery, renderSettings);
        this.renderSettings = renderSettings;
    }

    @Override
    public void render(Entity entity, BlockNeighborhood block, Part part, TileModelView tileModel) {
        if (!(entity instanceof ContraptionEntity contraption)) {
            return;
        }
        if (contraption.isTrain()) {
            contraption.setPos(new Vector3d(0, 0, 0));
        }
        Map<Vector3d, BlockAttribute> blocks = new HashMap<>();
        for (BlockAttribute nbtBlock : contraption.getContraption().getBlocks().getBlockList()) {
            long[] coords = unpackCoordinates(nbtBlock.getPosition());

            double x = coords[0] + contraption.getPos().getX();
            double y = coords[1] + contraption.getPos().getY();
            double z = coords[2] + contraption.getPos().getZ();

            nbtBlock.setRelativePosition(new Vector3d(coords[0], coords[1], coords[2]));
            Vector3d key = new Vector3d(x,y,z);
            blocks.put(key, nbtBlock);
            nbtBlock.setCoords(key);
        }
        contraption.setBlocks(blocks);
        for (BlockAttribute nbtBlock : contraption.getContraption().getBlocks().getBlockList()) {
            ContraptionBlock blockAccess = new ContraptionBlock(contraption);
            blockAccess.setBlock(nbtBlock);
            tileModel.initialize();

            BlockNeighborhood neighborhood = new ContraptionBlockNeighborhood(blockAccess, resourcePack, renderSettings,block.getDimensionType());
            neighborhood.set(0,0,0);
            blockRenderer.render(neighborhood, tileModel, new Color());
            Vector3d relativePos = nbtBlock.getRelativePosition();
            tileModel.translate((int)relativePos.getX(), (int)relativePos.getY(), (int)relativePos.getZ());
        }
    }

    private long[] unpackCoordinates(long pos)
    {
        long ym = 1<<12;
        long xm = 1<<26;
        long zm = 1<<26;

        long ys = (pos>>00)&(ym-1);
        long zs = (pos>>12)&(zm-1);
        long xs = (pos>>38)&(xm-1);

        long y = (ys>=ym/2)?(ys-ym):ys;
        long z = (zs>=zm/2)?(zs-zm):zs;
        long x = (xs>=xm/2)?(xs-xm):xs;

        return new long[]  {x, y, z};
    }
}
