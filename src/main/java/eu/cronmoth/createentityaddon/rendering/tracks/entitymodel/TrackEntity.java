package eu.cronmoth.createentityaddon.rendering.tracks.entitymodel;

import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TrackEntity extends MCABlockEntity  {
    private @NBTName("Connections") List<Connection> connections = new ArrayList<>();
    private @NBTName("Starts") List<Double[]> starts = new ArrayList<>();
    private @NBTName("keepPacked") boolean keepPacked;
}
