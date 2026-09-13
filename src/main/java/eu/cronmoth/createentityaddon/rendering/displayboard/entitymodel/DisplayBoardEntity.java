package eu.cronmoth.createentityaddon.rendering.displayboard.entitymodel;

import de.bluecolored.bluemap.core.world.mca.blockentity.MCABlockEntity;
import de.bluecolored.bluenbt.NBTName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DisplayBoardEntity extends MCABlockEntity {
    private @NBTName("Controller") boolean controller;
    private @NBTName("XSize") int xSize = 1;
    private @NBTName("YSize") int ySize = 1;

    private @NBTName("Display0") DisplaySlot display0;
    private @NBTName("Display1") DisplaySlot display1;
    private @NBTName("Display2") DisplaySlot display2;
    private @NBTName("Display3") DisplaySlot display3;
    private @NBTName("Display4") DisplaySlot display4;
    private @NBTName("Display5") DisplaySlot display5;
    private @NBTName("Display6") DisplaySlot display6;
    private @NBTName("Display7") DisplaySlot display7;
    private @NBTName("Display8") DisplaySlot display8;
    private @NBTName("Display9") DisplaySlot display9;
    private @NBTName("Display10") DisplaySlot display10;
    private @NBTName("Display11") DisplaySlot display11;
    private @NBTName("Display12") DisplaySlot display12;
    private @NBTName("Display13") DisplaySlot display13;
    private @NBTName("Display14") DisplaySlot display14;
    private @NBTName("Display15") DisplaySlot display15;
    private @NBTName("Display16") DisplaySlot display16;
    private @NBTName("Display17") DisplaySlot display17;
    private @NBTName("Display18") DisplaySlot display18;
    private @NBTName("Display19") DisplaySlot display19;

    public DisplaySlot getDisplay(int index) {
        return switch (index) {
            case 0 -> display0;
            case 1 -> display1;
            case 2 -> display2;
            case 3 -> display3;
            case 4 -> display4;
            case 5 -> display5;
            case 6 -> display6;
            case 7 -> display7;
            case 8 -> display8;
            case 9 -> display9;
            case 10 -> display10;
            case 11 -> display11;
            case 12 -> display12;
            case 13 -> display13;
            case 14 -> display14;
            case 15 -> display15;
            case 16 -> display16;
            case 17 -> display17;
            case 18 -> display18;
            case 19 -> display19;
            default -> null;
        };
    }

    @Getter
    @Setter
    public static class DisplaySlot {
        private @NBTName("Sections") List<DisplaySection> sections = List.of();
        private @NBTName("Key") String key;
    }

    @Getter
    @Setter
    public static class DisplaySection {
        private @NBTName("Text") String text;
        private @NBTName("Width") float width;
        private @NBTName("Cycle") String cycle;
        private @NBTName("RightAligned") boolean rightAligned;
        private @NBTName("Wide") boolean wide;
        private @NBTName("Gap") boolean gap;
        private @NBTName("SingleFlap") boolean singleFlap;
    }
}
