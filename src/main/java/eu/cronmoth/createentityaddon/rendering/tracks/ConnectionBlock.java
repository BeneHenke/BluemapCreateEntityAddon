package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.DimensionType;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import de.bluecolored.bluemap.core.world.block.ExtendedBlock;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class ConnectionBlock implements BlockAccess {
    private int x, xOrigin;
    private int y, yOrigin;
    private int z, zOrigin;
    private ExtendedBlock block;
    private BlockState state;
    public ConnectionBlock(ExtendedBlock block, BlockState state) {
        this.block=block;
        this.state=state;
        xOrigin = block.getX();
        yOrigin = block.getY();
        zOrigin = block.getZ();
        x=block.getX();
        y=block.getY();
        z=block.getZ();
    }

    @Override
    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public BlockAccess copy() {
        ConnectionBlock connectionBlock = new ConnectionBlock(block.copy(), state);
        connectionBlock.x = x;
        connectionBlock.y = y;
        connectionBlock.z = z;
        return connectionBlock;
    }

    @Override
    public BlockState getBlockState() {
        if (x == xOrigin && y == yOrigin && z == zOrigin) {
            return state;
        }
        return BlockState.AIR;
    }

    @Override
    public LightData getLightData() {
        //return new LightData(15, 15);
        return block.getLightData();
    }

    @Override
    public Biome getBiome() {
        return block.getBiome();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity() {
        return null;
    }

    @Override
    public boolean hasOceanFloorY() {
        return block.hasOceanFloorY();
    }

    @Override
    public int getOceanFloorY() {
        return block.getOceanFloorY();
    }
}
