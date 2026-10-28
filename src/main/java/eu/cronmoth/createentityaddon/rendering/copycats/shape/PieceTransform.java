package eu.cronmoth.createentityaddon.rendering.copycats.shape;

import de.bluecolored.bluemap.core.util.Direction;

import java.util.ArrayList;
import java.util.List;

public final class PieceTransform {

    public enum OpType { ROT_X, ROT_Y, ROT_Z, FLIP_X, FLIP_Y, FLIP_Z }

    private record Op(OpType type, int angle) {}

    private final List<Op> ops = new ArrayList<>();

    public PieceTransform rotateX(int angle) { ops.add(new Op(OpType.ROT_X, norm(angle))); return this; }
    public PieceTransform rotateY(int angle) { ops.add(new Op(OpType.ROT_Y, norm(angle))); return this; }
    public PieceTransform rotateZ(int angle) { ops.add(new Op(OpType.ROT_Z, norm(angle))); return this; }
    public PieceTransform flipX(boolean flip) { if (flip) ops.add(new Op(OpType.FLIP_X, 0)); return this; }
    public PieceTransform flipY(boolean flip) { if (flip) ops.add(new Op(OpType.FLIP_Y, 0)); return this; }
    public PieceTransform flipZ(boolean flip) { if (flip) ops.add(new Op(OpType.FLIP_Z, 0)); return this; }

    private static int norm(int angle) {
        return Math.floorMod(angle, 360) / 90 * 90; // multiples of 90 only
    }

    public double[] applyPoint(double x, double y, double z) {
        for (Op op : ops) {
            switch (op.type()) {
                case ROT_X -> {
                    switch (op.angle()) {
                        case 90 -> { double t = y; y = z; z = 1 - t; }
                        case 180 -> { y = 1 - y; z = 1 - z; }
                        case 270 -> { double t = y; y = 1 - z; z = t; }
                    }
                }
                case ROT_Y -> {
                    switch (op.angle()) {
                        case 90 -> { double t = x; x = 1 - z; z = t; }
                        case 180 -> { x = 1 - x; z = 1 - z; }
                        case 270 -> { double t = x; x = z; z = 1 - t; }
                    }
                }
                case ROT_Z -> {
                    switch (op.angle()) {
                        case 90 -> { double t = x; x = y; y = 1 - t; }
                        case 180 -> { x = 1 - x; y = 1 - y; }
                        case 270 -> { double t = x; x = 1 - y; y = t; }
                    }
                }
                case FLIP_X -> x = 1 - x;
                case FLIP_Y -> y = 1 - y;
                case FLIP_Z -> z = 1 - z;
            }
        }
        return new double[]{x, y, z};
    }

    public double[] applyBoxRaw(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (Op op : ops) {
            switch (op.type()) {
                case ROT_X -> {
                    switch (op.angle()) {
                        case 90 -> { double[] r = {minX, minZ, 1 - minY, maxX, maxZ, 1 - maxY}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                        case 180 -> { double[] r = {minX, 1 - minY, 1 - minZ, maxX, 1 - maxY, 1 - maxZ}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                        case 270 -> { double[] r = {minX, 1 - minZ, minY, maxX, 1 - maxZ, maxY}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                    }
                }
                case ROT_Y -> {
                    switch (op.angle()) {
                        case 90 -> { double[] r = {1 - minZ, minY, minX, 1 - maxZ, maxY, maxX}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                        case 180 -> { double[] r = {1 - minX, minY, 1 - minZ, 1 - maxX, maxY, 1 - maxZ}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                        case 270 -> { double[] r = {minZ, minY, 1 - minX, maxZ, maxY, 1 - maxX}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                    }
                }
                case ROT_Z -> {
                    switch (op.angle()) {
                        case 90 -> { double[] r = {minY, 1 - minX, minZ, maxY, 1 - maxX, maxZ}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                        case 180 -> { double[] r = {1 - minX, 1 - minY, minZ, 1 - maxX, 1 - maxY, maxZ}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                        case 270 -> { double[] r = {1 - minY, minX, minZ, 1 - maxY, maxX, maxZ}; minX=r[0]; minY=r[1]; minZ=r[2]; maxX=r[3]; maxY=r[4]; maxZ=r[5]; }
                    }
                }
                case FLIP_X -> { minX = 1 - minX; maxX = 1 - maxX; }
                case FLIP_Y -> { minY = 1 - minY; maxY = 1 - maxY; }
                case FLIP_Z -> { minZ = 1 - minZ; maxZ = 1 - maxZ; }
            }
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    public Direction applyDirection(Direction dir) {
        for (Op op : ops) {
            dir = switch (op.type()) {
                case ROT_X -> switch (op.angle()) {
                    case 90 -> switch (dir) { case UP -> Direction.NORTH; case NORTH -> Direction.DOWN; case DOWN -> Direction.SOUTH; case SOUTH -> Direction.UP; default -> dir; };
                    case 180 -> switch (dir) { case UP -> Direction.DOWN; case DOWN -> Direction.UP; case NORTH -> Direction.SOUTH; case SOUTH -> Direction.NORTH; default -> dir; };
                    case 270 -> switch (dir) { case UP -> Direction.SOUTH; case SOUTH -> Direction.DOWN; case DOWN -> Direction.NORTH; case NORTH -> Direction.UP; default -> dir; };
                    default -> dir;
                };
                case ROT_Y -> switch (op.angle()) {
                    case 90 -> switch (dir) { case NORTH -> Direction.EAST; case EAST -> Direction.SOUTH; case SOUTH -> Direction.WEST; case WEST -> Direction.NORTH; default -> dir; };
                    case 180 -> switch (dir) { case NORTH -> Direction.SOUTH; case SOUTH -> Direction.NORTH; case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> dir; };
                    case 270 -> switch (dir) { case NORTH -> Direction.WEST; case WEST -> Direction.SOUTH; case SOUTH -> Direction.EAST; case EAST -> Direction.NORTH; default -> dir; };
                    default -> dir;
                };
                case ROT_Z -> switch (op.angle()) {
                    case 90 -> switch (dir) { case UP -> Direction.EAST; case EAST -> Direction.DOWN; case DOWN -> Direction.WEST; case WEST -> Direction.UP; default -> dir; };
                    case 180 -> switch (dir) { case UP -> Direction.DOWN; case DOWN -> Direction.UP; case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> dir; };
                    case 270 -> switch (dir) { case UP -> Direction.WEST; case WEST -> Direction.DOWN; case DOWN -> Direction.EAST; case EAST -> Direction.UP; default -> dir; };
                    default -> dir;
                };
                case FLIP_X -> switch (dir) { case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> dir; };
                case FLIP_Y -> switch (dir) { case UP -> Direction.DOWN; case DOWN -> Direction.UP; default -> dir; };
                case FLIP_Z -> switch (dir) { case NORTH -> Direction.SOUTH; case SOUTH -> Direction.NORTH; default -> dir; };
            };
        }
        return dir;
    }
}
