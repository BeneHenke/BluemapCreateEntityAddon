package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluenbt.BlueNBT;
import eu.cronmoth.createentityaddon.rendering.sublevel.nbt.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads Sable {@code sublevels/*.slvls} files: an MCA-like container (1024 header entries of
 * 3-byte sector offset + 1-byte sector count, 4096-byte sectors) holding one gzip-NBT sub-level per entry.
 */
class SubLevelReader {

    private static final int SECTOR = 4096;
    /** Plot origin in blocks for plot (0,0); each plot spans {@code 1 << (log_size + 4)} blocks. */
    private static final int PLOT_BASE = 20_480_000;

    private static final BlueNBT BLUE_NBT = new BlueNBT();

    private final int minY;

    SubLevelReader(int minY) {
        this.minY = minY;
    }

    List<SubLevelData> readFile(Path file) throws IOException {
        byte[] b = Files.readAllBytes(file);
        List<SubLevelData> result = new ArrayList<>();
        for (int i = 0; i < 1024 && i * 4 + 4 <= b.length; i++) {
            int sector = readInt(b, i * 4) >>> 8;
            if (sector == 0) continue;
            int pos = sector * SECTOR;
            if (pos + 5 > b.length) continue;
            int len = readInt(b, pos);
            int compression = b[pos + 4];
            byte[] payload = Arrays.copyOfRange(b, pos + 5, Math.min(b.length, pos + 4 + len));
            SubLevelData data;
            try (InputStream in = new BufferedInputStream(open(payload, compression))) {
                data = parse(BLUE_NBT.read(in, SubLevelFile.class));
            }
            if (data.blockCount() > 0) result.add(data);
        }
        return result;
    }

    private static InputStream open(byte[] payload, int compression) throws IOException {
        boolean gzip = payload.length > 2 && (payload[0] & 0xff) == 0x1f && (payload[1] & 0xff) == 0x8b;
        InputStream raw = new ByteArrayInputStream(payload);
        return gzip ? new GZIPInputStream(raw) : compression == 2 ? new InflaterInputStream(raw) : raw;
    }

    private static int readInt(byte[] b, int o) {
        return ((b[o] & 0xff) << 24) | ((b[o + 1] & 0xff) << 16) | ((b[o + 2] & 0xff) << 8) | (b[o + 3] & 0xff);
    }

    private SubLevelData parse(SubLevelFile file) {
        Pose pose = file.pose;
        double[] position = {pose.position.x, pose.position.y, pose.position.z};
        double[] orientation = {pose.orientation.x, pose.orientation.y, pose.orientation.z, pose.orientation.w};
        double[] pivot = {pose.rotationPoint.x, pose.rotationPoint.y, pose.rotationPoint.z};
        int baseX = (int) Math.floor(pivot[0]);
        int baseY = (int) Math.floor(pivot[1]);
        int baseZ = (int) Math.floor(pivot[2]);

        Plot plot = file.plot;
        int plotSize = 1 << (plot.logSize + 4);
        int originX = PLOT_BASE + plot.plotX * plotSize;
        int originZ = PLOT_BASE + plot.plotZ * plotSize;

        SubLevelBuilder builder = new SubLevelBuilder();
        if (plot.chunks != null) {
            for (Map.Entry<String, Chunk> chunk : plot.chunks.entrySet()) {
                if (chunk.getValue().sections == null) continue;
                long key = Long.parseLong(chunk.getKey());
                int cx = (int) key;
                int cz = (int) (key >> 32);
                for (Map.Entry<String, Section> section : chunk.getValue().sections.entrySet()) {
                    int sectionY = Integer.parseInt(section.getKey());
                    readSection(section.getValue(),
                            originX + cx * 16 - baseX, minY + sectionY * 16 - baseY, originZ + cz * 16 - baseZ, builder);
                }
            }
        }
        return builder.build(toUuid(file.uuid), position, orientation, pivot);
    }

    private static void readSection(Section section, int relX, int relY, int relZ, SubLevelBuilder out) {
        BlockStates states = section.blockStates;
        if (states == null || states.palette == null || states.palette.isEmpty()) return;
        List<PaletteEntry> palette = states.palette;

        int[] paletteIndex = new int[palette.size()];
        for (int i = 0; i < paletteIndex.length; i++) {
            PaletteEntry entry = palette.get(i);
            String name = entry.name;
            if (name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")) {
                paletteIndex[i] = -1;
                continue;
            }
            paletteIndex[i] = out.palette(name, entry.properties == null ? Map.of() : entry.properties);
        }

        long[] data = states.data;
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        int valuesPerLong = 64 / bits;
        long mask = (1L << bits) - 1;

        for (int i = 0; i < 4096; i++) {
            int index = 0;
            if (data != null) {
                int longIndex = i / valuesPerLong;
                if (longIndex >= data.length) break;
                index = (int) ((data[longIndex] >>> ((i % valuesPerLong) * bits)) & mask);
            }
            if (index >= paletteIndex.length || paletteIndex[index] < 0) continue;
            out.add(relX + (i & 15), relY + (i >> 8), relZ + ((i >> 4) & 15), paletteIndex[index]);
        }
    }

    private static UUID toUuid(int[] a) {
        if (a == null || a.length != 4) return new UUID(0, 0);
        long hi = ((long) a[0] << 32) | (a[1] & 0xffffffffL);
        long lo = ((long) a[2] << 32) | (a[3] & 0xffffffffL);
        return new UUID(hi, lo);
    }
}
