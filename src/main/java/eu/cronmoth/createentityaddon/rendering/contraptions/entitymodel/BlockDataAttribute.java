package eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel;


import de.bluecolored.bluenbt.NBTName;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.MaterialEntry;
import lombok.Data;

import java.util.Map;

@Data
public class BlockDataAttribute {
        @NBTName("Item")
        private ItemData item;

        @NBTName("Material")
        private MaterialData material;

        @NBTName("material_data")
        private Map<String, MaterialEntry> materialData;

        @NBTName("id")
        private String id; // e.g. "create:copycat"

}
