package eu.cronmoth.createentityaddon.rendering.sublevel;

import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.BlockState;
import eu.cronmoth.createentityaddon.rendering.sublevel.nbt.SubLevelBlockEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

class SubLevelBuilder {

    private final List<BlockState> palette = new ArrayList<>();
    private final Map<String, Integer> paletteLookup = new HashMap<>();

    private final List<Integer> xs = new ArrayList<>();
    private final List<Integer> ys = new ArrayList<>();
    private final List<Integer> zs = new ArrayList<>();
    private final List<Integer> states = new ArrayList<>();

    private final List<Integer> beXs = new ArrayList<>();
    private final List<Integer> beYs = new ArrayList<>();
    private final List<Integer> beZs = new ArrayList<>();
    private final List<SubLevelBlockEntity> blockEntities = new ArrayList<>();

    int palette(String name, Map<String, String> properties) {
        String key = name + new TreeMap<>(properties);
        return paletteLookup.computeIfAbsent(key, k -> {
            palette.add(properties.isEmpty() ? new BlockState(new Key(name)) : new BlockState(new Key(name), properties));
            return palette.size() - 1;
        });
    }

    void add(int x, int y, int z, int state) {
        xs.add(x);
        ys.add(y);
        zs.add(z);
        states.add(state);
    }

    void addBlockEntity(int x, int y, int z, SubLevelBlockEntity be) {
        beXs.add(x);
        beYs.add(y);
        beZs.add(z);
        blockEntities.add(be);
    }

    SubLevelData build(UUID uuid, double[] position, double[] orientation, double[] pivot) {
        return new SubLevelData(uuid, position, orientation, pivot,
                palette.toArray(new BlockState[0]),
                toIntArray(xs), toIntArray(ys), toIntArray(zs), toIntArray(states),
                toIntArray(beXs), toIntArray(beYs), toIntArray(beZs),
                blockEntities.toArray(new SubLevelBlockEntity[0]));
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
