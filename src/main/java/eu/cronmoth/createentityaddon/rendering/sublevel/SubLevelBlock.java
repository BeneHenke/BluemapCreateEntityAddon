package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import org.jetbrains.annotations.Nullable;

class SubLevelBlock implements BlockAccess {

    private final SubLevelData ship;
    private final int wx, wy, wz;
    private int x, y, z;

    SubLevelBlock(SubLevelData ship, int wx, int wy, int wz) {
        this.ship = ship;
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
    }

    @Override
    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
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
    public BlockAccess copy() {
        SubLevelBlock copy = new SubLevelBlock(ship, wx, wy, wz);
        copy.x = x;
        copy.y = y;
        copy.z = z;
        return copy;
    }

    @Override
    public BlockState getBlockState() {
        int index = ship.stateAt(x - wx, y - wy, z - wz);
        return index < 0 ? BlockState.AIR : ship.palette[index];
    }

    @Override
    public LightData getLightData() {
        return new LightData(15, 0);
    }

    @Override
    public Biome getBiome() {
        return Biome.DEFAULT;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity() {
        return null;
    }

    @Override
    public boolean hasOceanFloorY() {
        return false;
    }

    @Override
    public int getOceanFloorY() {
        return 0;
    }
}
