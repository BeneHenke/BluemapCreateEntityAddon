package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.world.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class SubLevelData {

    private static final int OFFSET = 1 << 20;
    private static final long MASK = (1L << 21) - 1;

    final UUID uuid;
    final double[] position; // position in world coordinates
    final double[] orientation; // x, y, z, w
    final double[] pivot; // position of the sub-level's origin in world coordinates
    final int baseX, baseY, baseZ;

    final BlockState[] palette;
    final int[] xs, ys, zs, states;
    private final Map<Long, Integer> lookup;

    SubLevelData(UUID uuid, double[] position, double[] orientation, double[] pivot,
                 BlockState[] palette, int[] xs, int[] ys, int[] zs, int[] states) {
        this.uuid = uuid;
        this.position = position;
        this.orientation = orientation;
        this.pivot = pivot;
        this.baseX = (int) Math.floor(pivot[0]);
        this.baseY = (int) Math.floor(pivot[1]);
        this.baseZ = (int) Math.floor(pivot[2]);
        this.palette = palette;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.states = states;

        this.lookup = new HashMap<>(Math.max(16, xs.length * 2));
        for (int i = 0; i < xs.length; i++) lookup.put(key(xs[i], ys[i], zs[i]), states[i]);
    }

    int blockCount() {
        return xs.length;
    }

    int stateAt(int x, int y, int z) {
        Integer s = lookup.get(key(x, y, z));
        return s == null ? -1 : s;
    }

    private static long key(int x, int y, int z) {
        return (((long) (x + OFFSET) & MASK) << 42) | (((long) (y + OFFSET) & MASK) << 21) | ((long) (z + OFFSET) & MASK);
    }
}
