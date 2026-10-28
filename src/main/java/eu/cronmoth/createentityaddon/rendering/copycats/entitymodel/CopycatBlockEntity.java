package eu.cronmoth.createentityaddon.rendering.copycats.entitymodel;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.MaterialData;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class CopycatBlockEntity extends MCABlockEntity {

    @NBTName("Material")
    private MaterialData material;

    @NBTName("material_data")
    private Map<String, MaterialEntry> materialData;

    public CopycatBlockEntity() {
        super();
    }

    public MaterialData getMaterial(String partKey) {
        if (material != null) return material;
        if (materialData == null || partKey == null) return null;
        MaterialEntry entry = materialData.get(partKey);
        return entry != null ? entry.getMaterial() : null;
    }
}
