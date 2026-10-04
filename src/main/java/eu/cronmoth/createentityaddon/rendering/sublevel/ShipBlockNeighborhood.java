package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.world.DimensionType;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;

/**
 * BlueMap 5.12's ExtendedBlock only computes its render mask inside set(), which can be skipped for a freshly
 * created neighborhood, and then throws a NullPointerException in isInsideRenderBounds(). Ship blocks are always
 * rendered, so the bounds check is bypassed.
 */
class ShipBlockNeighborhood extends BlockNeighborhood {

    ShipBlockNeighborhood(BlockAccess blockAccess, ResourcePack resourcePack, RenderSettings renderSettings, DimensionType dimensionType) {
        super(blockAccess, resourcePack, renderSettings, dimensionType);
    }

    @Override
    public boolean isInsideRenderBounds() {
        return true;
    }
}
