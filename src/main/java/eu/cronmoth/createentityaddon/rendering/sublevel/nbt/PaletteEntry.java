package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

import de.bluecolored.bluenbt.NBTName;

import java.util.Map;

public class PaletteEntry {

    @NBTName("Name") public String name;
    @NBTName("Properties") public Map<String, String> properties;
}
