package eu.cronmoth.createentityaddon.rendering.contraptions;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.BlockAttribute;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.PaletteAttribute;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.CopycatBlockEntity;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class ContraptionBlock implements BlockAccess {
    private ContraptionEntity contraption;
    private BlockAttribute block;

    private int x;
    private int y;
    private int z;

    public ContraptionBlock(ContraptionEntity contraption) {
        this.contraption = contraption;
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
        if (block != null && block.getData() != null && block.getData().getId().contains("copycat")) {
            CopycatBlockEntity entity = new CopycatBlockEntity();
            entity.setMaterial(block.getData().getMaterial());
            return entity;
        }

        return null;
    }

    @Override
    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public BlockAccess copy() {
        ContraptionBlock copy = new ContraptionBlock(contraption);
        copy.x = this.x;
        copy.y = this.y;
        copy.z = this.z;
        copy.block = this.block;
        return copy;
    }

    @Override
    public BlockState getBlockState() {
        double x = this.block.getCoords().getX()+this.x;
        double y = this.block.getCoords().getY()+this.y;
        double z = this.block.getCoords().getZ()+this.z;
        BlockAttribute block = contraption.getBlocks().get(new Vector3d(x,y,z));
        if (block == null) {
            return BlockState.AIR;
        }
        PaletteAttribute palette = contraption.getContraption().getBlocks().getPalette().get(block.getState());
        if (palette.getProperties()==null) {
            return new BlockState(new Key(palette.getName()));
        }
        if (palette.getName().contains("copycat")) {
            palette.getProperties().put("copycat", "true");
        }
        return new BlockState(new Key(palette.getName()), palette.getProperties());
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
