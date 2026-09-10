package eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import eu.cronmoth.createentityaddon.rendering.tracks.entitymodel.Positions;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ChainConveyorEntity extends MCABlockEntity
{
    private @NBTName("Connections") List<Positions> connections = new ArrayList<>();

}
