package eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel;

import com.flowpowered.math.vector.Vector2f;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.Entity;
import de.bluecolored.bluenbt.NBTName;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class ContraptionEntity implements Entity {
    Key id;
    private @NBTName("UUID") UUID uuid;
    private @NBTName("CustomName") String customName;
    private @NBTName("CustomNameVisible") boolean customNameVisible;
    private @NBTName("Pos") Vector3d pos;
    private @NBTName("Motion") Vector3d motion;
    private @NBTName("Rotation") Vector2f rotation;
    private @NBTName("Contraption") ContraptionAttribute contraption;
    private @NBTName("AssemblyDirection") String assemblyDirection;
    private @NBTName("Axis") String axis;
    private @NBTName("Angle") float angle;
    private Map<Vector3d, BlockAttribute> blocks;
    private boolean isTrain;
}
