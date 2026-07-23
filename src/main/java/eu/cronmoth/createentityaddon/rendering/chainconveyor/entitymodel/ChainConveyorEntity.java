package eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Connection;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

//@EqualsAndHashCode(callSuper = true)
//@Data
public class ChainConveyorEntity extends MCABlockEntity
{
    private @NBTName("Connections") List<int[]> connections = new ArrayList<>();

    public void setConnections(List<int[]> connections) {
        this.connections = connections;
    }

    public List<int[]> getConnections() {
        return connections;
    }
}
