package eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ChainConveyorEntity extends MCABlockEntity
{
    /**
     * Create's {@code chain_conveyor} block-entity connections. MC 1.21 changed
     * {@code NbtUtils.writeBlockPos} to emit {@code [I; x, y, z]} instead of a {@code {X,Y,Z}}
     * compound, so on 1.21.1 each entry is a raw int array (block-relative offset to the partner).
     * A {@code List<Positions>} here makes bluenbt throw on the array elements, and the whole
     * block-entity silently falls back to {@link MCABlockEntity} (no ports, no chains).
     */
    private @NBTName("Connections") List<int[]> connections = new ArrayList<>();

}
