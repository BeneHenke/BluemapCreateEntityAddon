package eu.cronmoth.createentityaddon.rendering.copycats.entitymodel;

import de.bluecolored.bluenbt.NBTName;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.MaterialData;
import lombok.Data;

@Data
public class MaterialEntry {
    @NBTName("material")
    private MaterialData material;
}
