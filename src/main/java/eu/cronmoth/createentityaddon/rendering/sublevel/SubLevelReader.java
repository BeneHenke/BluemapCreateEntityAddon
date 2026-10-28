package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.world.BlockEntity;
import de.bluecolored.bluemap.core.world.Entity;
import de.bluecolored.bluemap.core.world.mca.MCAUtil;
import de.bluecolored.bluemap.core.world.mca.entity.chunk.MCAEntityChunk;
import de.bluecolored.bluenbt.BlueNBT;
import eu.cronmoth.createentityaddon.AddonLog;
import eu.cronmoth.createentityaddon.rendering.contraptions.entitymodel.ContraptionEntity;
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
public class SubLevelReader {

    private static final int SECTOR = 4096;
    /** Plot origin in blocks for plot (0,0); each plot spans {@code 1 << (log_size + 4)} blocks. */
    private static final int PLOT_BASE = 20_480_000;

    private static final BlueNBT BLUE_NBT = MCAUtil.BLUENBT;

    private final int minY;

    public SubLevelReader(int minY) {
        this.minY = minY;
    }

    public List<SubLevelData> readFile(Path file) throws IOException {
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
            SubLevelFile slFile;
            try (InputStream in = new BufferedInputStream(open(payload, compression))) {
                slFile = BLUE_NBT.read(in, SubLevelFile.class);
            }
            SubLevelData data = parse(slFile, file);
            if (data.blockCount() > 0 || !data.contraptions.isEmpty()) result.add(data);
        }
        return result;
    }

    private void readContraptions(Path slvlsFile, SubLevelFile slFile, SubLevelBuilder builder,
                                  int baseX, int baseY, int baseZ,
                                  int plotMinX, int plotMaxX, int plotMinY, int plotMaxY,
                                  int plotMinZ, int plotMaxZ) {
        Plot plot = slFile.plot;
        int plotSize = 1 << (plot.logSize + 4);

        // entities dir: world/entities/  (slvlsFile = world/sublevels/r.X.Z.W.slvls)
        Path sublevelsDir = slvlsFile.getParent();
        if (sublevelsDir == null) return;
        Path worldDir = sublevelsDir.getParent();
        if (worldDir == null) return;
        Path entitiesDir = worldDir.resolve("entities");
        if (!Files.isDirectory(entitiesDir)) return;

        int minRegionX = plotMinX >> 9;
        int maxRegionX = plotMaxX >> 9;
        int minRegionZ = plotMinZ >> 9;
        int maxRegionZ = plotMaxZ >> 9;

        int count = 0;
        for (int rx = minRegionX; rx <= maxRegionX; rx++) {
            for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
                Path regionFile = entitiesDir.resolve("r." + rx + "." + rz + ".mca");
                if (!Files.isRegularFile(regionFile)) continue;
                try {
                    List<ContraptionEntity> entities = readContraptionsFromRegion(regionFile);
                    for (ContraptionEntity entity : entities) {
                        if (entity.getPos() == null) continue;
                        int ex = (int) Math.floor(entity.getPos().getX());
                        int ey = (int) Math.floor(entity.getPos().getY());
                        int ez = (int) Math.floor(entity.getPos().getZ());
                        if (ex < plotMinX || ex > plotMaxX) continue;
                        if (ey < plotMinY || ey > plotMaxY) continue;
                        if (ez < plotMinZ || ez > plotMaxZ) continue;

                        int localX = ex - baseX;
                        int localY = ey - baseY;
                        int localZ = ez - baseZ;
                        builder.addContraption(new SublevelContraption(entity, localX, localY, localZ));
                        count++;
                    }
                } catch (Exception e) {
                    AddonLog.warn("failed to read entity region " + regionFile + ": " + e);
                }
            }
        }
        if (count > 0) {
            AddonLog.info("loaded " + count + " contraption(s) for sub-level");
        }
    }

    private static List<ContraptionEntity> readContraptionsFromRegion(Path regionFile) throws IOException {
        List<ContraptionEntity> result = new ArrayList<>();
        byte[] b = Files.readAllBytes(regionFile);
        for (int i = 0; i < 1024 && i * 4 + 4 <= b.length; i++) {
            int sector = readInt(b, i * 4) >>> 8;
            if (sector == 0) continue;
            int pos = sector * SECTOR;
            if (pos + 5 > b.length) continue;
            int len = readInt(b, pos);
            int compression = b[pos + 4];
            byte[] payload = Arrays.copyOfRange(b, pos + 5, Math.min(b.length, pos + 4 + len));
            try (InputStream in = new BufferedInputStream(open(payload, compression))) {
                MCAEntityChunk chunk = BLUE_NBT.read(in, MCAEntityChunk.class);
                if (chunk != null && chunk.getEntities() != null) {
                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof ContraptionEntity contraption) {
                            result.add(contraption);
                        }
                    }
                }
            } catch (Exception e) {
                // skip corrupted chunks
            }
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

    private SubLevelData parse(SubLevelFile file, Path slvlsFile) {
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
                Chunk chunkData = chunk.getValue();
                long key = Long.parseLong(chunk.getKey());
                int cx = (int) key;
                int cz = (int) (key >> 32);
                if (chunkData.sections != null) {
                    for (Map.Entry<String, Section> section : chunkData.sections.entrySet()) {
                        int sectionY = Integer.parseInt(section.getKey());
                        readSection(section.getValue(),
                                originX + cx * 16 - baseX, minY + sectionY * 16 - baseY, originZ + cz * 16 - baseZ, builder);
                    }
                }
                if (chunkData.blockEntities != null) {
                    for (BlockEntity be : chunkData.blockEntities) {
                        builder.addBlockEntity(be.getX() - baseX, be.getY() - baseY, be.getZ() - baseZ, be);
                    }
                }
            }
        }

        // Read contraption entities from entity region files at plot coordinates
        int margin = 64;
        int plotMinX = originX - margin, plotMaxX = originX + plotSize + margin;
        int plotMinZ = originZ - margin, plotMaxZ = originZ + plotSize + margin;
        int plotMinY = baseY - margin, plotMaxY = baseY + 256 + margin;
        readContraptions(slvlsFile, file, builder, baseX, baseY, baseZ,
                plotMinX, plotMaxX, plotMinY, plotMaxY, plotMinZ, plotMaxZ);

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
