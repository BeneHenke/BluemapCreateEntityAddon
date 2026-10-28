package eu.cronmoth.createentityaddon.rendering.sublevel.nbt;

import de.bluecolored.bluenbt.NBTName;

import java.util.List;
import java.util.Map;

public class Chunk {

    /** Sections keyed by their index counted from the dimension's minimum height. */
    public Map<String, Section> sections;

    /** Block entities with absolute world coordinates. */
    @NBTName("block_entities") public List<SubLevelBlockEntity> blockEntities;
}
