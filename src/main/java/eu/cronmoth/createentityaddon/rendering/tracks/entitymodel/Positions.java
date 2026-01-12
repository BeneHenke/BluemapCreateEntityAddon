package eu.cronmoth.createentityaddon.rendering.tracks.entitymodel;

import de.bluecolored.bluenbt.NBTName;
import java.util.Arrays;


public class Positions {
    @NBTName("Pos") public int[] pos;

    @Override
    public String toString() {
        return "Positions{" +
                "v=" + Arrays.toString(pos) +
                '}';
    }
}
