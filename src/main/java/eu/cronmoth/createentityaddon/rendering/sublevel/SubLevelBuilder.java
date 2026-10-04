package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.world.BlockState;

import java.util.*;

class SubLevelBuilder {

    private final List<BlockState> palette = new ArrayList<>();
    private final Map<String, Integer> paletteLookup = new HashMap<>();
    private int[] xs = new int[1024], ys = new int[1024], zs = new int[1024], states = new int[1024];
    private int size;

    int palette(String name, Map<String, String> properties) {
        String key = name + new TreeMap<>(properties);
        return paletteLookup.computeIfAbsent(key, k -> {
            palette.add(properties.isEmpty() ? new BlockState(name) : new BlockState(name, properties));
            return palette.size() - 1;
        });
    }

    void add(int x, int y, int z, int state) {
        if (size == xs.length) {
            int n = size * 2;
            xs = Arrays.copyOf(xs, n);
            ys = Arrays.copyOf(ys, n);
            zs = Arrays.copyOf(zs, n);
            states = Arrays.copyOf(states, n);
        }
        xs[size] = x;
        ys[size] = y;
        zs[size] = z;
        states[size++] = state;
    }

    SubLevelData build(UUID uuid, double[] position, double[] orientation, double[] pivot) {
        return new SubLevelData(uuid, position, orientation, pivot,
                palette.toArray(new BlockState[0]),
                Arrays.copyOf(xs, size), Arrays.copyOf(ys, size), Arrays.copyOf(zs, size), Arrays.copyOf(states, size));
    }
}
