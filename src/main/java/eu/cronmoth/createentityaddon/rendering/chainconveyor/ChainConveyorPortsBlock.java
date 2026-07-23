package eu.cronmoth.createentityaddon.rendering.chainconveyor;

import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.DimensionType;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import org.jetbrains.annotations.Nullable;

public class ChainConveyorPortsBlock implements BlockAccess {
    private final VectorM3f pos;
    private final BlockNeighborhood blockNeighborhood;

    private int x;
    private int y;
    private int z;

    public ChainConveyorPortsBlock(BlockNeighborhood block, VectorM3f vectorM3f) {
        this.pos = vectorM3f;
        this.blockNeighborhood = block;
    }

    @Override
    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public BlockAccess copy() {
        ChainConveyorPortsBlock copy = new ChainConveyorPortsBlock(blockNeighborhood, pos);
        copy.x = this.x;
        copy.y = this.y;
        copy.z = this.z;
        return copy;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public BlockState getBlockState() {
        if (getX() == pos.x || getY() == pos.y|| getZ() == pos.z) {
            return new BlockState(new Key("create:chain_conveyor_ports"));
        }
        return  BlockState.AIR;
    }

    @Override
    public LightData getLightData() {
        //return blockNeighborhood.getLightData();
        return new LightData(15, 0);
    }

    @Override
    public Biome getBiome() {
        return blockNeighborhood.getBiome();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity() {
        return blockNeighborhood.getBlockEntity();
    }

    @Override
    public boolean hasOceanFloorY() {
        return blockNeighborhood.hasOceanFloorY();
    }

    @Override
    public int getOceanFloorY() {
        return blockNeighborhood.getOceanFloorY();
    }
}
