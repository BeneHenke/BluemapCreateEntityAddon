package eu.cronmoth.createentityaddon.rendering.copycats.shape;

import de.bluecolored.bluemap.core.util.Direction;

import java.util.EnumSet;
import java.util.Set;

public record Piece(
        double[] cropMin,
        double[] cropMax,
        double[] move,
        Set<Direction> culledMaterialFaces,
        String partKey,
        PieceTransform transform
) {

    /**
     * Builds the resolved piece from the local recipe values (pixels).
     *
     * @param selectMin crop region min in the local frame (px)
     * @param selectMax crop region max in the local frame (px)
     * @param offset    where the crop region min is placed in the local frame (px)
     * @param cull      material faces skipped by this piece (local frame)
     */
    public static Piece of(
            double[] selectMin, double[] selectMax, double[] offset,
            Set<Direction> cull, String partKey, PieceTransform transform
    ) {
        double[] raw = transform.applyBoxRaw(
                selectMin[0] / 16, selectMin[1] / 16, selectMin[2] / 16,
                selectMax[0] / 16, selectMax[1] / 16, selectMax[2] / 16);

        // the AABB handed to the crop step is normalized (Minecraft AABB constructor sorts min/max)
        double[] cropMin = new double[3];
        double[] cropMax = new double[3];
        for (int i = 0; i < 3; i++) {
            cropMin[i] = Math.min(raw[i], raw[i + 3]);
            cropMax[i] = Math.max(raw[i], raw[i + 3]);
        }

        double[] offsetPoint = transform.applyPoint(offset[0] / 16, offset[1] / 16, offset[2] / 16);
        double[] move = new double[3];
        for (int i = 0; i < 3; i++) {
            // the mod subtracts the RAW (possibly inverted) min - essential quirk, see PieceTransform
            move[i] = offsetPoint[i] - raw[i];
        }

        Set<Direction> culled = EnumSet.noneOf(Direction.class);
        for (Direction dir : cull) culled.add(transform.applyDirection(dir));

        return new Piece(cropMin, cropMax, move, culled, partKey, transform);
    }
}
