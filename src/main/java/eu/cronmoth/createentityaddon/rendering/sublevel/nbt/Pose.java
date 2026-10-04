package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

import de.bluecolored.bluenbt.NBTName;

/** World position of the pivot, orientation, and the pivot itself in plot space. */
public class Pose {

    public Vec3 position;
    public Quat orientation;
    @NBTName("rotation_point") public Vec3 rotationPoint;
}
