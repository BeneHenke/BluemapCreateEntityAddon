package eu.cronmoth.createentityaddon.rendering.tracks.entitymodel;

import de.bluecolored.bluenbt.NBTName;
import lombok.Data;
import java.util.Arrays;

@Data
public class Normals {
    @NBTName("V") public double[] v;

    @Override
    public String toString() {
        return "Normals{" +
                "v=" + Arrays.toString(v) +
                '}';
    }
}
