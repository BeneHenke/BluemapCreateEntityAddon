package eu.cronmoth.createentityaddon.rendering.copycats;

import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;

/**
 * A view of the copycat block that reports the material's blockstate, so that
 * BlueMap's block color calculator resolves tint colors (grass, foliage, ...)
 * for the copied material.
 */
class MaterialStateBlock extends BlockNeighborhood {

    private final BlockState materialState;

    MaterialStateBlock(BlockNeighborhood source, BlockState materialState) {
        super(source, source.getResourcePack(), source.getRenderSettings(), source.getDimensionType());
        this.materialState = materialState;
        copyFrom(source);
    }

    @Override
    public BlockState getBlockState() {
        return materialState;
    }

    @Override
    public void set(int x, int y, int z) {
        throw new UnsupportedOperationException("MaterialStateBlock is pinned to one position");
    }
}
