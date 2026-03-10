package eu.cronmoth.createentityaddon.rendering.tracks.entitymodel;

import de.bluecolored.bluenbt.NBTName;
import java.util.Arrays;


public class Positions {
    @NBTName("Pos") public int[] pos;
    @NBTName("X") public int x;
    @NBTName("Y") public int y;
    @NBTName("Z") public int z;

    public int getX() {
        return pos!=null ? pos[0] :  x;
    }

    public int getY() {
        return pos!=null ? pos[1] :  y;
    }

    public int getZ() {
        return pos!=null ? pos[2] :  z;
    }

    @Override
    public String toString() {
        return "Positions{" +
                "v=" + Arrays.toString(pos) +
                " || x= " + x +
                ", y= " + y +
                ", z= " + z +
                '}';
    }
}
