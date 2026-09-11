package eu.cronmoth.createentityaddon.rendering.chainconveyor.entitymodel;

import de.bluecolored.bluenbt.NBTReader;
import de.bluecolored.bluenbt.TagType;
import de.bluecolored.bluenbt.TypeDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code chain_conveyor}'s {@code Connections} list regardless of which shape the running
 * Minecraft version writes it in: MC 1.21 changed {@code NbtUtils.writeBlockPos} to emit a bare
 * {@code [I; x, y, z]} per entry; MC <=1.20.1 (Create 6.0.8 there) writes a {@code {X,Y,Z}}
 * compound. bluenbt has no way to try two field types for one NBT name - the field-name -> field
 * map only ever binds one, and the other stays empty - so this reads the raw tag type per list
 * element via {@link NBTReader#peek()} and normalizes both shapes to {@code int[]}. One class
 * works on every branch/MC-version instead of swapping {@code ChainConveyorEntity.connections}'
 * field type per branch.
 */
public class ConnectionsDeserializer implements TypeDeserializer<List<int[]>> {

    @Override
    public List<int[]> read(NBTReader reader) throws IOException {
        List<int[]> result = new ArrayList<>();
        int size = reader.beginList();
        for (int i = 0; i < size; i++) {
            TagType type = reader.peek();
            if (type == TagType.INT_ARRAY) {
                result.add(reader.nextIntArray());
            } else if (type == TagType.COMPOUND) {
                result.add(readCompoundEntry(reader));
            } else {
                reader.skip();
            }
        }
        reader.endList();
        return result;
    }

    private static int[] readCompoundEntry(NBTReader reader) throws IOException {
        int x = 0, y = 0, z = 0;
        reader.beginCompound();
        while (reader.peek() != TagType.END) {
            String name = reader.name();
            switch (name) {
                case "X" -> x = reader.nextInt();
                case "Y" -> y = reader.nextInt();
                case "Z" -> z = reader.nextInt();
                default -> reader.skip();
            }
        }
        reader.endCompound();
        return new int[]{x, y, z};
    }
}
