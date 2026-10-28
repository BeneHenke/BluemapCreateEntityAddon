package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

import de.bluecolored.bluenbt.NBTName;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.MaterialData;
import eu.cronmoth.createentityaddon.rendering.copycats.entitymodel.MaterialEntry;

import java.util.Map;

public class SubLevelBlockEntity {

    @NBTName("x") public int x;
    @NBTName("y") public int y;
    @NBTName("z") public int z;
    @NBTName("id") public String id;

    @NBTName("Material") public MaterialData material;

    @NBTName("material_data") public Map<String, MaterialEntry> materialData;
}
