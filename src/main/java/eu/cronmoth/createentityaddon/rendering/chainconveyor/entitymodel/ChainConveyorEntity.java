package eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTDeserializer;
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
     * Create's {@code chain_conveyor} block-entity connections, normalized to {@code [x, y, z]}
     * per entry regardless of NBT shape - see {@link ConnectionsDeserializer}: MC 1.21 changed
     * {@code NbtUtils.writeBlockPos} to emit a bare {@code [I; x, y, z]} where MC <=1.20.1
     * (Create 6.0.8 there) writes a {@code {X,Y,Z}} compound. Without the custom deserializer,
     * whichever shape doesn't match a plain field type makes bluenbt throw, and the whole
     * block-entity silently falls back to {@link MCABlockEntity} (no ports, no chains).
     */
    private @NBTName("Connections") @NBTDeserializer(ConnectionsDeserializer.class)
    List<int[]> connections = new ArrayList<>();

}
