package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.CopycatBlockEntity;
import eu.cronmoth.createentityaddon.rendering.sublevel.nbt.SubLevelBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class SubLevelBlock implements BlockAccess {

    private final SubLevelData ship;
    private final int wx, wy, wz;
    private int x, y, z;

    public SubLevelBlock(SubLevelData ship, int wx, int wy, int wz) {
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
        if (index < 0) return BlockState.AIR;
        BlockState state = ship.palette[index];
        if (state.getFormatted().contains("copycat")) {
            Map<String, String> props = new HashMap<>(state.getProperties());
            props.put("copycat", "true");
            return new BlockState(state.getFormatted(), props);
        }
        return state;
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
        int lx = x - wx, ly = y - wy, lz = z - wz;
        int stateIndex = ship.stateAt(lx, ly, lz);
        if (stateIndex < 0) return null;
        BlockState state = ship.palette[stateIndex];
        if (!state.getFormatted().contains("copycat")) return null;

        SubLevelBlockEntity be = ship.blockEntityAt(lx, ly, lz);
        if (be == null) return null;

        CopycatBlockEntity entity = new CopycatBlockEntity();
        entity.setMaterial(be.material);
        entity.setMaterialData(be.materialData);
        return entity;
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
