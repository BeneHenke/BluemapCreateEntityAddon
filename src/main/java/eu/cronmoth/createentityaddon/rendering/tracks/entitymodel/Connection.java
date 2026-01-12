package eu.cronmoth.createentityaddon.rendering.tracks.entitymodel;

import de.bluecolored.bluenbt.NBTName;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Connection {
    private @NBTName("Girder") int girder;
    private @NBTName("Normals") List<Normals> normal;
    private @NBTName("Primary") boolean primary;
    private @NBTName("Positions") List<Positions> pos;
    private @NBTName("Axes") List<Normals> axis;
    private @NBTName("Material") String material;

    @Override
    public String toString() {
        return "Connection{" +
                "girder=" + girder +
                ", normal=" + normal +
                ", primary=" + primary +
                ", pos=" + pos +
                ", axis=" + axis +
                ", material='" + material + '\'' +
                '}';
    }
}


