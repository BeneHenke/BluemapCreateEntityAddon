package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;

public record SegmentTransform(
        Vector3d position,
        float pitch,
        float roll,
        float yaw
) {}