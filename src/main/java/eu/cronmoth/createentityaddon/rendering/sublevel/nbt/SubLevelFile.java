package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

/**
 * Typed view of the NBT stored for one Sable sub-level in a {@code .slvls} file, read with BlueNBT.
 * Tags that are not modelled (light, heightmaps, block entities, ...) are ignored.
 */
public class SubLevelFile {

    public Plot plot;
    public Pose pose;
    public int[] uuid;
}
