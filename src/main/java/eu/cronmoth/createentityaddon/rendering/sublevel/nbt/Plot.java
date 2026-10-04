package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

import de.bluecolored.bluenbt.NBTName;

import java.util.Map;

public class Plot {

    @NBTName("plot_x") public int plotX;
    @NBTName("plot_z") public int plotZ;
    @NBTName("log_size") public int logSize;

    /** Chunks keyed by the packed chunk position ({@code x} in the low, {@code z} in the high 32 bits). */
    public Map<String, Chunk> chunks;
}
