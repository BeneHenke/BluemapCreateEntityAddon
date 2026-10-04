package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

import java.util.List;

public class BlockStates {

    public List<PaletteEntry> palette;
    /** Palette indices packed into longs; absent when the palette has a single entry. */
    public long[] data;
}
