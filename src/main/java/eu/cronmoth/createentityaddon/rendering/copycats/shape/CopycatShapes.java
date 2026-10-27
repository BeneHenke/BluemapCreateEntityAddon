package eu.cronmoth.createentityaddon.rendering.copycats.shape;

import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.world.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CopycatShapes {

    private CopycatShapes() {}

    private static double[] o(double x, double y, double z) { return new double[]{x, y, z}; }
    private static double[] s(double x, double y, double z) { return new double[]{x, y, z}; }
    private static double[] m(double x, double y, double z) { return new double[]{x, y, z}; }

    private static Piece p(PieceTransform t, String partKey,
                           double[] off, double[] size, double[] move,
                           Direction... cull) {
        Set<Direction> mask = cull.length == 0
                ? EnumSet.noneOf(Direction.class)
                : EnumSet.copyOf(List.of(cull));
        return Piece.of(move,
                new double[]{move[0] + size[0], move[1] + size[1], move[2] + size[2]},
                off, mask, partKey, t);
    }

    private static int yRot(String facing) {
        return switch (facing) {
            case "west" -> 90;
            case "north" -> 180;
            case "east" -> 270;
            case "up" -> -1;   // handled by callers
            case "down" -> -2; // handled by callers
            default -> 0;      // south
        };
    }

    private static boolean bool(Map<String, String> props, String key) {
        return "true".equals(props.get(key));
    }

    private static int int_(Map<String, String> props, String key, int def) {
        String v = props.get(key);
        return v != null ? Integer.parseInt(v) : def;
    }

    public static List<Piece> pieces(BlockState state) {
        Map<String, String> props = state.getProperties();
        return switch (state.getFormatted()) {
            case "copycats:copycat_block", "copycats:copycat_ghost_block" -> fullBlock();
            case "copycats:copycat_slab" -> slab(props);
            case "copycats:copycat_slice" -> slice(props);
            case "copycats:copycat_layer" -> layer(props);
            case "copycats:copycat_beam" -> beam(props);
            case "copycats:copycat_corner_slice" -> cornerSlice(props);
            case "copycats:copycat_vertical_slice" -> verticalSlice(props);
            case "copycats:copycat_half_panel" -> halfPanel(props);
            case "copycats:copycat_vertical_step" -> verticalStep(props);
            case "copycats:copycat_stairs" -> stairs(props);
            case "copycats:copycat_vertical_stairs" -> verticalStairs(props);
            case "copycats:copycat_trapdoor", "copycats:copycat_iron_trapdoor" -> trapdoor(props);
            case "copycats:copycat_door", "copycats:copycat_iron_door" -> door(props);
            case "copycats:copycat_flat_pane" -> flatPane(props);
            case "copycats:copycat_fence" -> fence(props);
            case "copycats:copycat_fence_gate" -> fenceGate(props);
            case "copycats:copycat_ladder" -> ladder(props);
            case "copycats:copycat_stone_pressure_plate",
                 "copycats:copycat_light_weighted_pressure_plate",
                 "copycats:copycat_heavy_weighted_pressure_plate" -> pressurePlate(props);
            case "copycats:copycat_wooden_button", "copycats:copycat_stone_button" -> button(props);
            case "copycats:copycat_half_layer" -> halfLayer(props);
            case "copycats:copycat_stacked_half_layer" -> stackedHalfLayer(props);
            case "copycats:copycat_vertical_half_layer" -> verticalHalfLayer(props);
            case "copycats:copycat_board" -> board(props);
            case "copycats:copycat_byte" -> byteBlock(props);
            case "copycats:copycat_byte_panel" -> bytePanel(props);
            case "create:copycat_step" -> createStep(props);
            case "create:copycat_panel" -> createPanel(props);
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // full block (assembleAll)
    // ------------------------------------------------------------------

    private static List<Piece> fullBlock() {
        List<Piece> out = new ArrayList<>(1);
        out.add(p(new PieceTransform(), null, o(0, 0, 0), s(16, 16, 16), m(0, 0, 0)));
        return out;
    }

    // ------------------------------------------------------------------
    // Slab (multi-state; port of CopycatMultiSlabModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> slab(Map<String, String> props) {
        String type = props.getOrDefault("type", "bottom");
        String axis = props.getOrDefault("axis", "y");
        List<Piece> out = new ArrayList<>(2);
        if (!"top".equals(type)) addSlabHalf(out, "bottom", axis);
        if (!"bottom".equals(type)) addSlabHalf(out, "top", axis);
        return out;
    }

    private static void addSlabHalf(List<Piece> out, String partKey, String axis) {
        boolean positive = "bottom".equals(partKey);
        if (axis.equals("y")) {
            PieceTransform t = new PieceTransform().flipY(!positive);
            out.add(p(t, partKey, o(0, 0, 0), s(16, 4, 16), m(0, 0, 0), Direction.UP));
            out.add(p(t, partKey, o(0, 4, 0), s(16, 4, 16), m(0, 12, 0), Direction.DOWN));
        } else {
            int rot = axis.equals("x") ? (positive ? 270 : 90) : (positive ? 0 : 180);
            PieceTransform t = new PieceTransform().rotateY(rot);
            out.add(p(t, partKey, o(0, 0, 0), s(16, 16, 4), m(0, 0, 0), Direction.SOUTH));
            out.add(p(t, partKey, o(0, 0, 4), s(16, 16, 4), m(0, 0, 12), Direction.NORTH));
        }
    }

    // ------------------------------------------------------------------
    // Slice (single-state; port of CopycatSliceModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> slice(Map<String, String> props) {
        double l = int_(props, "layers", 1);
        boolean flipY = "top".equals(props.get("half"));
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "south"))).flipY(flipY);
        List<Piece> out = new ArrayList<>(4);
        out.add(p(t, null, o(0, 0, 16 - l), s(16, l, l), m(0, 0, 16 - l), Direction.UP, Direction.NORTH));
        out.add(p(t, null, o(0, l, 16 - l), s(16, l, l), m(0, 16 - l, 16 - l), Direction.DOWN, Direction.NORTH));
        out.add(p(t, null, o(0, 0, 16 - 2 * l), s(16, l, l), m(0, 0, 0), Direction.UP, Direction.SOUTH));
        out.add(p(t, null, o(0, l, 16 - 2 * l), s(16, l, l), m(0, 16 - l, 0), Direction.DOWN, Direction.SOUTH));
        return out;
    }

    // ------------------------------------------------------------------
    // Layer (port of CopycatLayerModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> layer(Map<String, String> props) {
        double l = int_(props, "layers", 1);
        String facing = props.getOrDefault("facing", "up");
        List<Piece> out = new ArrayList<>(2);
        if (facing.equals("up") || facing.equals("down")) {
            PieceTransform t = new PieceTransform().flipY(facing.equals("up"));
            out.add(p(t, null, o(0, 0, 0), s(16, l, 16), m(0, 0, 0), Direction.UP));
            out.add(p(t, null, o(0, l, 0), s(16, l, 16), m(0, 16 - l, 0), Direction.DOWN));
        } else {
            PieceTransform t = new PieceTransform().rotateY(yRot(facing));
            out.add(p(t, null, o(0, 0, 0), s(16, 16, l), m(0, 0, 0), Direction.SOUTH));
            out.add(p(t, null, o(0, 0, l), s(16, 16, l), m(0, 0, 16 - l), Direction.NORTH));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Beam (port of CopycatBeamModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> beam(Map<String, String> props) {
        String axis = props.getOrDefault("axis", "y");
        PieceTransform t = new PieceTransform();
        if (axis.equals("y")) t.rotateX(90);
        else if (axis.equals("x")) t.rotateY(90);
        List<Piece> out = new ArrayList<>(4);
        out.add(p(t, null, o(4, 4, 0), s(4, 4, 16), m(0, 0, 0), Direction.UP, Direction.EAST));
        out.add(p(t, null, o(8, 4, 0), s(4, 4, 16), m(12, 0, 0), Direction.UP, Direction.WEST));
        out.add(p(t, null, o(4, 8, 0), s(4, 4, 16), m(0, 12, 0), Direction.DOWN, Direction.EAST));
        out.add(p(t, null, o(8, 8, 0), s(4, 4, 16), m(12, 12, 0), Direction.DOWN, Direction.WEST));
        return out;
    }

    // ------------------------------------------------------------------
    // Corner slice (port of CopycatCornerSliceModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> cornerSlice(Map<String, String> props) {
        double l = int_(props, "layers", 1);
        boolean flipY = "top".equals(props.get("half"));
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "south"))).flipY(flipY);
        List<Piece> out = new ArrayList<>(8);
        out.add(p(t, null, o(16 - l, 0, 16 - l), s(l, l, l), m(16 - l, 0, 16 - l), Direction.UP, Direction.NORTH, Direction.WEST));
        out.add(p(t, null, o(16 - 2 * l, 0, 16 - l), s(l, l, l), m(0, 0, 16 - l), Direction.UP, Direction.NORTH, Direction.EAST));
        out.add(p(t, null, o(16 - l, 0, 16 - 2 * l), s(l, l, l), m(16 - l, 0, 0), Direction.UP, Direction.SOUTH, Direction.WEST));
        out.add(p(t, null, o(16 - 2 * l, 0, 16 - 2 * l), s(l, l, l), m(0, 0, 0), Direction.UP, Direction.SOUTH, Direction.EAST));
        out.add(p(t, null, o(16 - l, l, 16 - l), s(l, l, l), m(16 - l, 16 - l, 16 - l), Direction.DOWN, Direction.NORTH, Direction.WEST));
        out.add(p(t, null, o(16 - 2 * l, l, 16 - l), s(l, l, l), m(0, 16 - l, 16 - l), Direction.DOWN, Direction.NORTH, Direction.EAST));
        out.add(p(t, null, o(16 - l, l, 16 - 2 * l), s(l, l, l), m(16 - l, 16 - l, 0), Direction.DOWN, Direction.SOUTH, Direction.WEST));
        out.add(p(t, null, o(16 - 2 * l, l, 16 - 2 * l), s(l, l, l), m(0, 16 - l, 0), Direction.DOWN, Direction.SOUTH, Direction.EAST));
        return out;
    }

    // ------------------------------------------------------------------
    // Vertical slice (port of CopycatVerticalSliceModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> verticalSlice(Map<String, String> props) {
        double l = int_(props, "layers", 1);
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "south")));
        List<Piece> out = new ArrayList<>(4);
        out.add(p(t, null, o(16 - l, 0, 16 - l), s(l, 16, l), m(16 - l, 0, 16 - l), Direction.WEST, Direction.NORTH));
        out.add(p(t, null, o(16 - l, 0, 16 - 2 * l), s(l, 16, l), m(16 - l, 0, 0), Direction.WEST, Direction.SOUTH));
        out.add(p(t, null, o(16 - 2 * l, 0, 16 - l), s(l, 16, l), m(0, 0, 16 - l), Direction.EAST, Direction.NORTH));
        out.add(p(t, null, o(16 - 2 * l, 0, 16 - 2 * l), s(l, 16, l), m(0, 0, 0), Direction.EAST, Direction.SOUTH));
        return out;
    }

    // ------------------------------------------------------------------
    // Half panel (port of CopycatHalfPanelModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> halfPanel(Map<String, String> props) {
        String facing = props.getOrDefault("facing", "north");
        String offset = props.getOrDefault("offset", "north");
        List<Piece> out = new ArrayList<>(4);
        if (facing.equals("up") || facing.equals("down")) {
            PieceTransform t = new PieceTransform().rotateY(yRot(offset)).flipY(facing.equals("up"));
            out.add(p(t, null, o(0, 0, 12), s(16, 1, 4), m(0, 0, 12), Direction.UP, Direction.NORTH));
            out.add(p(t, null, o(0, 0, 8), s(16, 1, 4), m(0, 0, 0), Direction.UP, Direction.SOUTH));
            out.add(p(t, null, o(0, 1, 12), s(16, 2, 4), m(0, 14, 12), Direction.DOWN, Direction.NORTH));
            out.add(p(t, null, o(0, 1, 8), s(16, 2, 4), m(0, 14, 0), Direction.DOWN, Direction.SOUTH));
        } else if (axisOf(offset).equals(axisOf(facing))) {
            boolean flipY = positive(offset);
            PieceTransform t = new PieceTransform().rotateY(yRot(facing)).flipY(flipY);
            out.add(p(t, null, o(0, 0, 15), s(16, 4, 1), m(0, 0, 15), Direction.UP, Direction.NORTH));
            out.add(p(t, null, o(0, 4, 15), s(16, 4, 1), m(0, 12, 15), Direction.DOWN, Direction.NORTH));
            out.add(p(t, null, o(0, 0, 13), s(16, 4, 2), m(0, 0, 0), Direction.UP, Direction.SOUTH));
            out.add(p(t, null, o(0, 4, 13), s(16, 4, 2), m(0, 12, 0), Direction.DOWN, Direction.SOUTH));
        } else {
            boolean left = offset.equals(clockwiseCounterclockwise(facing, false));
            int leftOffset = left ? 8 : 0;
            PieceTransform t = new PieceTransform().rotateY(yRot(facing));
            out.add(p(t, null, o(leftOffset, 0, 15), s(4, 16, 1), m(0, 0, 15), Direction.EAST, Direction.NORTH));
            out.add(p(t, null, o(4 + leftOffset, 0, 15), s(4, 16, 1), m(12, 0, 15), Direction.WEST, Direction.NORTH));
            out.add(p(t, null, o(leftOffset, 0, 13), s(4, 16, 2), m(0, 0, 0), Direction.EAST, Direction.SOUTH));
            out.add(p(t, null, o(4 + leftOffset, 0, 13), s(4, 16, 2), m(12, 0, 0), Direction.WEST, Direction.SOUTH));
        }
        return out;
    }

    /** whether a direction string points to the positive axis side */
    private static boolean positive(String dir) {
        return dir.equals("up") || dir.equals("south") || dir.equals("east");
    }

    private static String axisOf(String dir) {
        return switch (dir) {
            case "up", "down" -> "y";
            case "north", "south" -> "z";
            default -> "x";
        };
    }

    /** the counter-clockwise (left) horizontal neighbor of a direction */
    private static String clockwiseCounterclockwise(String facing, boolean clockWise) {
        List<String> ring = List.of("north", "east", "south", "west");
        int idx = ring.indexOf(facing);
        if (idx < 0) return facing;
        return ring.get(Math.floorMod(idx + (clockWise ? 1 : 3), 4));
    }

    // ------------------------------------------------------------------
    // Vertical step (port of CopycatVerticalStepModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> verticalStep(Map<String, String> props) {
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "north")));
        List<Piece> out = new ArrayList<>(4);
        out.add(p(t, null, o(8, 0, 8), s(4, 16, 4), m(0, 0, 0), Direction.EAST, Direction.SOUTH));
        out.add(p(t, null, o(12, 0, 8), s(4, 16, 4), m(12, 0, 0), Direction.WEST, Direction.SOUTH));
        out.add(p(t, null, o(8, 0, 12), s(4, 16, 4), m(0, 0, 12), Direction.EAST, Direction.NORTH));
        out.add(p(t, null, o(12, 0, 12), s(4, 16, 4), m(12, 0, 12), Direction.WEST, Direction.NORTH));
        return out;
    }

    // ------------------------------------------------------------------
    // Stairs (simple variants; port of CopycatStairsModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> stairs(Map<String, String> props) {
        int rot = yRot(props.getOrDefault("facing", "north"));
        boolean top = "top".equals(props.get("half"));
        String shape = props.getOrDefault("shape", "straight");
        boolean flipX = shape.equals("inner_right") || shape.equals("outer_right");
        List<Piece> out = new ArrayList<>(10);
        switch (shape) {
            case "straight" -> assembleStairsStraight(out, new PieceTransform().rotateY(rot).flipY(top), null);
            case "inner_left", "inner_right" -> assembleStairsInnerLeft(out,
                    new PieceTransform().flipX(flipX).rotateY(rot).flipY(top), null);
            default -> assembleStairsOuterLeft(out,
                    new PieceTransform().flipX(flipX).rotateY(rot).flipY(top), null);
        }
        return out;
    }

    private static void assembleStairsStraight(List<Piece> out, PieceTransform t, String partKey) {
        out.add(p(t, partKey, o(0, 0, 0), s(16, 4, 8), m(0, 0, 0), Direction.UP, Direction.SOUTH));
        out.add(p(t, partKey, o(0, 4, 0), s(16, 4, 8), m(0, 12, 0), Direction.DOWN, Direction.SOUTH));
        out.add(p(t, partKey, o(0, 0, 8), s(16, 8, 8), m(0, 0, 8), Direction.UP, Direction.NORTH));
        out.add(p(t, partKey, o(0, 8, 8), s(16, 8, 4), m(0, 8, 0), Direction.DOWN, Direction.SOUTH));
        out.add(p(t, partKey, o(0, 8, 12), s(16, 8, 4), m(0, 8, 12), Direction.DOWN, Direction.NORTH));
    }

    private static void assembleStairsInnerLeft(List<Piece> out, PieceTransform t, String partKey) {
        out.add(p(t, partKey, o(0, 0, 0), s(8, 4, 8), m(0, 0, 0), Direction.UP, Direction.SOUTH, Direction.EAST));
        out.add(p(t, partKey, o(0, 4, 0), s(8, 4, 8), m(0, 12, 0), Direction.DOWN, Direction.SOUTH, Direction.EAST));
        out.add(p(t, partKey, o(0, 0, 8), s(16, 8, 8), m(0, 0, 8), Direction.UP, Direction.NORTH));
        out.add(p(t, partKey, o(8, 8, 8), s(8, 8, 8), m(8, 8, 8), Direction.DOWN, Direction.NORTH, Direction.WEST));
        out.add(p(t, partKey, o(0, 8, 12), s(8, 8, 4), m(0, 8, 12), Direction.DOWN, Direction.NORTH, Direction.EAST));
        out.add(p(t, partKey, o(0, 8, 8), s(8, 8, 4), m(0, 8, 0), Direction.DOWN, Direction.SOUTH, Direction.EAST));
        out.add(p(t, partKey, o(12, 8, 0), s(4, 8, 8), m(12, 8, 0), Direction.DOWN, Direction.SOUTH, Direction.WEST));
        out.add(p(t, partKey, o(8, 8, 0), s(4, 8, 8), m(0, 8, 0), Direction.DOWN, Direction.SOUTH, Direction.EAST));
        out.add(p(t, partKey, o(8, 0, 0), s(8, 8, 8), m(8, 0, 0), Direction.UP, Direction.SOUTH, Direction.WEST));
    }

    private static void assembleStairsOuterLeft(List<Piece> out, PieceTransform t, String partKey) {
        out.add(p(t, partKey, o(0, 0, 0), s(8, 4, 16), m(0, 0, 0), Direction.UP, Direction.EAST));
        out.add(p(t, partKey, o(0, 4, 0), s(8, 4, 16), m(0, 12, 0), Direction.DOWN, Direction.EAST));
        out.add(p(t, partKey, o(8, 0, 0), s(8, 4, 8), m(8, 0, 0), Direction.UP, Direction.SOUTH, Direction.WEST));
        out.add(p(t, partKey, o(8, 4, 0), s(8, 4, 8), m(8, 12, 0), Direction.DOWN, Direction.SOUTH, Direction.WEST));
        out.add(p(t, partKey, o(8, 0, 8), s(8, 8, 8), m(8, 0, 8), Direction.UP, Direction.NORTH, Direction.WEST));
        out.add(p(t, partKey, o(12, 8, 12), s(4, 8, 4), m(12, 8, 12), Direction.DOWN, Direction.NORTH, Direction.WEST));
        out.add(p(t, partKey, o(8, 8, 12), s(4, 8, 4), m(0, 8, 12), Direction.DOWN, Direction.NORTH, Direction.EAST));
        out.add(p(t, partKey, o(12, 8, 8), s(4, 8, 4), m(12, 8, 0), Direction.DOWN, Direction.SOUTH, Direction.WEST));
        out.add(p(t, partKey, o(8, 8, 8), s(4, 8, 4), m(0, 8, 0), Direction.DOWN, Direction.SOUTH, Direction.EAST));
    }

    // ------------------------------------------------------------------
    // Vertical stairs (port of CopycatVerticalStairsModelCore, simple variants)
    // ------------------------------------------------------------------

    private static List<Piece> verticalStairs(Map<String, String> props) {
        int rot = yRot(props.getOrDefault("facing", "north"));
        String shape = props.getOrDefault("shape", "straight");
        boolean right = "right".equals(props.getOrDefault("side", "left"));
        List<Piece> out = new ArrayList<>(10);
        switch (shape) {
            case "straight" -> assembleStairsStraight(out,
                    new PieceTransform().rotateX(90).rotateZ(90).flipX(right).rotateY(rot), null);
            case "inner_top", "inner_bottom" -> assembleStairsInnerLeft(out,
                    new PieceTransform().rotateX(90).rotateZ(90).flipX(right).flipY(shape.endsWith("top")).rotateY(rot), null);
            default -> assembleStairsOuterLeft(out,
                    new PieceTransform().rotateX(90).rotateZ(90).flipX(right).flipY(shape.endsWith("top")).rotateY(rot), null);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Trapdoor (port of CopycatTrapdoorModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> trapdoor(Map<String, String> props) {
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "north")))
                .flipY("top".equals(props.get("half")));
        boolean open = bool(props, "open");
        List<Piece> out = new ArrayList<>(2);
        if (!open) {
            out.add(p(t, null, o(0, 0, 0), s(16, 1, 16), m(0, 0, 0), Direction.UP));
            out.add(p(t, null, o(0, 1, 0), s(16, 2, 16), m(0, 14, 0), Direction.DOWN));
        } else {
            out.add(p(t, null, o(0, 0, 0), s(16, 16, 1), m(0, 0, 0), Direction.SOUTH));
            out.add(p(t, null, o(0, 0, 1), s(16, 16, 2), m(0, 0, 14), Direction.NORTH));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Door (WithoutCT variant of CopycatDoorModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> door(Map<String, String> props) {
        int rot = yRot(props.getOrDefault("facing", "north"));
        boolean rightHinge = "right".equals(props.get("hinge"));
        boolean open = bool(props, "open");
        PieceTransform t = new PieceTransform();
        if (open && !rightHinge) t.flipX(true);
        t.rotateY(rot);
        List<Piece> out = new ArrayList<>(2);
        if (!open) {
            out.add(p(t, null, o(0, 0, 0), s(16, 16, 2), m(0, 0, 0), Direction.SOUTH));
            out.add(p(t, null, o(0, 0, 2), s(16, 16, 1), m(0, 0, 15), Direction.NORTH));
        } else {
            out.add(p(t, null, o(0, 0, 0), s(2, 16, 16), m(0, 0, 0), Direction.EAST));
            out.add(p(t, null, o(2, 0, 0), s(1, 16, 16), m(15, 0, 0), Direction.WEST));
        }
        // the door spans the full block; the upper half uses the same geometry
        return out;
    }

    // ------------------------------------------------------------------
    // Flat pane (port of CopycatFlatPaneModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> flatPane(Map<String, String> props) {
        String axis = props.getOrDefault("axis", "y");
        PieceTransform t = new PieceTransform()
                .rotateX(axis.equals("z") ? 90 : 0)
                .rotateZ(axis.equals("x") ? 90 : 0);
        List<Piece> out = new ArrayList<>(2);
        out.add(p(t, null, o(0, 7, 0), s(16, 1, 16), m(0, 0, 0), Direction.UP));
        out.add(p(t, null, o(0, 8, 0), s(16, 1, 16), m(0, 0, 0), Direction.DOWN));
        return out;
    }

    // ------------------------------------------------------------------
    // Fence (port of CopycatFenceModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> fence(Map<String, String> props) {
        List<Piece> out = new ArrayList<>();
        // post: four 2x2 pillars forming the 4x4 center post
        for (String dir : List.of("north", "east", "south", "west")) {
            PieceTransform t = new PieceTransform().rotateY(yRot(dir));
            out.add(p(t, null, o(6, 0, 6), s(2, 16, 2), m(0, 0, 0), Direction.SOUTH, Direction.EAST));
        }
        // arms toward connected sides
        for (String dir : List.of("north", "east", "south", "west")) {
            if (!bool(props, dir)) continue;
            PieceTransform t = new PieceTransform().rotateY(yRot(dir));
            out.add(p(t, null, o(7, 6, 10), s(1, 1, 6), m(0, 0, 0), Direction.UP, Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(8, 6, 10), s(1, 1, 6), m(15, 0, 0), Direction.UP, Direction.NORTH, Direction.WEST));
            out.add(p(t, null, o(7, 7, 10), s(1, 2, 6), m(0, 14, 0), Direction.DOWN, Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(8, 7, 10), s(1, 2, 6), m(15, 14, 0), Direction.DOWN, Direction.NORTH, Direction.WEST));
            out.add(p(t, null, o(7, 12, 10), s(1, 1, 6), m(0, 0, 0), Direction.UP, Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(8, 12, 10), s(1, 1, 6), m(15, 0, 0), Direction.UP, Direction.NORTH, Direction.WEST));
            out.add(p(t, null, o(7, 13, 10), s(1, 2, 6), m(0, 14, 0), Direction.DOWN, Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(8, 13, 10), s(1, 2, 6), m(15, 14, 0), Direction.DOWN, Direction.NORTH, Direction.WEST));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Fence gate (port of CopycatFenceGateModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> fenceGate(Map<String, String> props) {
        int wallOffset = bool(props, "in_wall") ? -3 : 0;
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "north")));
        List<Piece> out = new ArrayList<>();
        for (boolean eastSide : List.of(false, true)) {
            int offsetX = eastSide ? 14 : 0;
            out.add(p(t, null, o(offsetX, 5 + wallOffset, 7), s(1, 6, 1), m(0, 0, 0), Direction.UP, Direction.SOUTH, Direction.EAST));
            out.add(p(t, null, o(offsetX + 1, 5 + wallOffset, 7), s(1, 6, 1), m(15, 0, 0), Direction.UP, Direction.SOUTH, Direction.WEST));
            out.add(p(t, null, o(offsetX, 5 + wallOffset, 8), s(1, 6, 1), m(0, 0, 15), Direction.UP, Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(offsetX + 1, 5 + wallOffset, 8), s(1, 6, 1), m(15, 0, 15), Direction.UP, Direction.NORTH, Direction.WEST));
            out.add(p(t, null, o(offsetX, 11 + wallOffset, 7), s(1, 5, 1), m(0, 11, 0), Direction.DOWN, Direction.SOUTH, Direction.EAST));
            out.add(p(t, null, o(offsetX + 1, 11 + wallOffset, 7), s(1, 5, 1), m(15, 11, 0), Direction.DOWN, Direction.SOUTH, Direction.WEST));
            out.add(p(t, null, o(offsetX, 11 + wallOffset, 8), s(1, 5, 1), m(0, 11, 15), Direction.DOWN, Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(offsetX + 1, 11 + wallOffset, 8), s(1, 5, 1), m(15, 11, 15), Direction.DOWN, Direction.NORTH, Direction.WEST));
        }
        if (bool(props, "open")) {
            for (boolean eastDoor : List.of(false, true)) {
                for (boolean eastSide : List.of(false, true)) {
                    int offsetX = (eastDoor ? 14 : 0) + (eastSide ? 1 : 0);
                    Direction sideCull = eastSide ? Direction.WEST : Direction.EAST;
                    out.add(p(t, null, o(offsetX, 12 + wallOffset, 9), s(1, 3, 6), m(eastSide ? 15 : 0, 13, 10), Direction.NORTH, sideCull));
                    out.add(p(t, null, o(offsetX, 9 + wallOffset, 13), s(1, 3, 2), m(eastSide ? 15 : 0, 7, 14), Direction.UP, Direction.DOWN, sideCull));
                    out.add(p(t, null, o(offsetX, 6 + wallOffset, 9), s(1, 3, 6), m(eastSide ? 15 : 0, 0, 10), Direction.NORTH, sideCull));
                }
            }
        } else {
            for (boolean southSide : List.of(false, true)) {
                PieceTransform t2 = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "north")) + (southSide ? 180 : 0));
                // right half of the closed door (material strips x[0,6] / x[0,2])
                out.add(p(t2, null, o(8, 12 + wallOffset, 7), s(6, 3, 1), m(0, 13, 0), Direction.SOUTH, Direction.EAST, Direction.WEST));
                out.add(p(t2, null, o(8, 9 + wallOffset, 7), s(2, 3, 1), m(0, 7, 0), Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.WEST));
                out.add(p(t2, null, o(8, 6 + wallOffset, 7), s(6, 3, 1), m(0, 0, 0), Direction.SOUTH, Direction.EAST, Direction.WEST));
                // left half of the closed door (material strips x[10,16] / x[14,16])
                out.add(p(t2, null, o(2, 12 + wallOffset, 7), s(6, 3, 1), m(10, 13, 0), Direction.SOUTH, Direction.EAST, Direction.WEST));
                out.add(p(t2, null, o(6, 9 + wallOffset, 7), s(2, 3, 1), m(14, 7, 0), Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.EAST));
                out.add(p(t2, null, o(2, 6 + wallOffset, 7), s(6, 3, 1), m(10, 0, 0), Direction.SOUTH, Direction.EAST, Direction.WEST));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Ladder (multi-state; port of CopycatMultiLadderModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> ladder(Map<String, String> props) {
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "north")));
        List<Piece> out = new ArrayList<>(6);
        if (bool(props, "rails")) {
            out.add(p(t, "rails", o(2, 0, 0), s(2, 16, 1), m(0, 0, 0)));
            out.add(p(t, "rails", o(12, 0, 0), s(2, 16, 1), m(14, 0, 0)));
        }
        if (bool(props, "steps")) {
            out.add(p(t, "steps", o(1, 1, 0.1), s(14, 2, 0.8), m(0, 0, 0)));
            out.add(p(t, "steps", o(1, 5, 0.1), s(14, 2, 0.8), m(0, 0, 0)));
            out.add(p(t, "steps", o(1, 9, 0.1), s(14, 2, 0.8), m(0, 0, 0)));
            out.add(p(t, "steps", o(1, 13, 0.1), s(14, 2, 0.8), m(0, 0, 0)));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Pressure plates (port of CopycatPressurePlateModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> pressurePlate(Map<String, String> props) {
        boolean powered = bool(props, "powered") || int_(props, "power", 0) > 0;
        double h = powered ? 0.5 : 1.0;
        List<Piece> out = new ArrayList<>(4);
        PieceTransform t = new PieceTransform();
        out.add(p(t, null, o(1, 0, 1), s(7, h, 7), m(0, 0, 0), Direction.SOUTH, Direction.EAST));
        out.add(p(t, null, o(8, 0, 8), s(7, h, 7), m(9, 0, 9), Direction.NORTH, Direction.WEST));
        out.add(p(t, null, o(8, 0, 1), s(7, h, 7), m(9, 0, 0), Direction.WEST, Direction.SOUTH));
        out.add(p(t, null, o(1, 0, 8), s(7, h, 7), m(0, 0, 9), Direction.NORTH, Direction.EAST));
        return out;
    }

    // ------------------------------------------------------------------
    // Buttons (port of CopycatButtonModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> button(Map<String, String> props) {
        String face = props.getOrDefault("face", "wall");
        int rot = yRot(props.getOrDefault("facing", "north"));
        boolean pressed = bool(props, "powered");
        List<Piece> out = new ArrayList<>(8);
        if (face.equals("wall")) {
            PieceTransform t = new PieceTransform().rotateY(rot);
            out.add(p(t, null, o(5, 6, pressed ? 0 : 1), s(3, 2, 1), m(1, 1, 1), Direction.UP, Direction.EAST));
            out.add(p(t, null, o(5, 8, pressed ? 0 : 1), s(3, 2, 1), m(1, 13, 1), Direction.DOWN, Direction.EAST));
            out.add(p(t, null, o(8, 8, pressed ? 0 : 1), s(3, 2, 1), m(12, 13, 1), Direction.DOWN, Direction.WEST));
            out.add(p(t, null, o(8, 6, pressed ? 0 : 1), s(3, 2, 1), m(12, 1, 1), Direction.UP, Direction.WEST));
            if (!pressed) {
                out.add(p(t, null, o(5, 6, 0), s(3, 2, 1), m(0, 0, 0), Direction.SOUTH, Direction.UP, Direction.EAST));
                out.add(p(t, null, o(5, 8, 0), s(3, 2, 1), m(0, 14, 0), Direction.SOUTH, Direction.DOWN, Direction.EAST));
                out.add(p(t, null, o(8, 8, 0), s(3, 2, 1), m(13, 14, 0), Direction.SOUTH, Direction.DOWN, Direction.WEST));
                out.add(p(t, null, o(8, 6, 0), s(3, 2, 1), m(13, 0, 0), Direction.SOUTH, Direction.UP, Direction.WEST));
            }
        } else {
            PieceTransform t = new PieceTransform().rotateY(rot).flipY(!face.equals("floor"));
            double y = pressed ? 0 : 1;
            out.add(p(t, null, o(5, y, 6), s(3, 1, 2), m(1, 0, 1), Direction.SOUTH, Direction.EAST));
            out.add(p(t, null, o(5, y, 8), s(3, 1, 2), m(1, 0, 13), Direction.NORTH, Direction.EAST));
            out.add(p(t, null, o(8, y, 6), s(3, 1, 2), m(12, 0, 1), Direction.SOUTH, Direction.WEST));
            out.add(p(t, null, o(8, y, 8), s(3, 1, 2), m(12, 0, 13), Direction.NORTH, Direction.WEST));
            if (!pressed) {
                out.add(p(t, null, o(5, 0, 6), s(3, 1, 2), m(0, 0, 0), Direction.UP, Direction.SOUTH, Direction.EAST));
                out.add(p(t, null, o(5, 0, 8), s(3, 1, 2), m(0, 0, 14), Direction.UP, Direction.NORTH, Direction.EAST));
                out.add(p(t, null, o(8, 0, 6), s(3, 1, 2), m(13, 0, 0), Direction.UP, Direction.SOUTH, Direction.WEST));
                out.add(p(t, null, o(8, 0, 8), s(3, 1, 2), m(13, 0, 14), Direction.UP, Direction.NORTH, Direction.WEST));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Half layer (multi-state; port of CopycatMultiHalfLayerModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> halfLayer(Map<String, String> props) {
        List<Piece> out = new ArrayList<>(8);
        boolean flipY = "top".equals(props.get("half"));
        int rot = props.getOrDefault("axis", "x").equals("x") ? 0 : 90;
        addHalfLayerPart(out, props, "negative_layers", rot, flipY);
        addHalfLayerPart(out, props, "positive_layers", rot, flipY);
        return out;
    }

    private static void addHalfLayerPart(List<Piece> out, Map<String, String> props,
                                         String partKey, int rot, boolean flipY) {
        int layer = int_(props, partKey, 0);
        if (layer == 0) return;
        boolean positive = partKey.equals("positive_layers");
        PieceTransform t = new PieceTransform().rotateY(rot + (positive ? 180 : 0)).flipY(flipY);
        double l = layer;
        out.add(p(t, partKey, o(0, 0, 0), s(4, l, 16), m(0, 0, 0), Direction.EAST, Direction.UP));
        out.add(p(t, partKey, o(0, l, 0), s(4, l, 16), m(0, 16 - l, 0), Direction.EAST, Direction.DOWN));
        out.add(p(t, partKey, o(4, 0, 0), s(4, l, 16), m(12, 0, 0), Direction.WEST, Direction.UP));
        out.add(p(t, partKey, o(4, l, 0), s(4, l, 16), m(12, 16 - l, 0), Direction.WEST, Direction.DOWN));
    }

    // ------------------------------------------------------------------
    // Stacked half layer (multi-state; port of CopycatStackedMultiHalfLayerModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> stackedHalfLayer(Map<String, String> props) {
        List<Piece> out = new ArrayList<>(8);
        int rot = yRot(props.getOrDefault("facing", "north"));
        addStackedHalfLayerPart(out, props, "negative_layers", rot);
        addStackedHalfLayerPart(out, props, "positive_layers", rot);
        return out;
    }

    private static void addStackedHalfLayerPart(List<Piece> out, Map<String, String> props,
                                                String partKey, int rot) {
        int layer = int_(props, partKey, 0);
        if (layer == 0) return;
        boolean positive = partKey.equals("positive_layers");
        PieceTransform t = new PieceTransform().flipY(positive).rotateY(rot + 180);
        double l = layer;
        out.add(p(t, partKey, o(0, 0, 0), s(16, 4, l), m(0, 0, 0), Direction.UP, Direction.SOUTH));
        out.add(p(t, partKey, o(0, 0, l), s(16, 4, l), m(0, 0, 16 - l), Direction.UP, Direction.NORTH));
        out.add(p(t, partKey, o(0, 4, 0), s(16, 4, l), m(0, 12, 0), Direction.DOWN, Direction.SOUTH));
        out.add(p(t, partKey, o(0, 4, l), s(16, 4, l), m(0, 12, 16 - l), Direction.DOWN, Direction.NORTH));
    }

    // ------------------------------------------------------------------
    // Vertical half layer (multi-state; port of CopycatVerticalMultiHalfLayerModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> verticalHalfLayer(Map<String, String> props) {
        List<Piece> out = new ArrayList<>(8);
        int rot = yRot(props.getOrDefault("facing", "north"));
        addVerticalHalfLayerPart(out, props, "negative_layers", rot);
        addVerticalHalfLayerPart(out, props, "positive_layers", rot);
        return out;
    }

    private static void addVerticalHalfLayerPart(List<Piece> out, Map<String, String> props,
                                                 String partKey, int rot) {
        int layer = int_(props, partKey, 0);
        if (layer == 0) return;
        boolean positive = partKey.equals("positive_layers");
        PieceTransform t = new PieceTransform().flipX(!positive).rotateY(rot + 180);
        double l = layer;
        out.add(p(t, partKey, o(0, 0, 0), s(4, 16, l), m(0, 0, 0), Direction.EAST, Direction.SOUTH));
        out.add(p(t, partKey, o(0, 0, l), s(4, 16, l), m(0, 0, 16 - l), Direction.EAST, Direction.NORTH));
        out.add(p(t, partKey, o(4, 0, 0), s(4, 16, l), m(12, 0, 0), Direction.WEST, Direction.SOUTH));
        out.add(p(t, partKey, o(4, 0, l), s(4, 16, l), m(12, 0, 16 - l), Direction.WEST, Direction.NORTH));
    }

    // ------------------------------------------------------------------
    // Board (multi-state; port of CopycatMultiBoardModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> board(Map<String, String> props) {
        List<Piece> out = new ArrayList<>(6);
        for (String dir : List.of("north", "south", "east", "west", "up", "down")) {
            if (!bool(props, dir)) continue;
            if (dir.equals("up") || dir.equals("down")) {
                PieceTransform t = new PieceTransform().flipY(dir.equals("up"));
                Set<Direction> cull = EnumSet.noneOf(Direction.class);
                if (bool(props, "north")) cull.add(Direction.NORTH);
                if (bool(props, "south")) cull.add(Direction.SOUTH);
                if (bool(props, "east")) cull.add(Direction.EAST);
                if (bool(props, "west")) cull.add(Direction.WEST);
                out.add(p(t, dir, o(0, 0, 0), s(16, 1, 16), m(0, 0, 0), cull.toArray(new Direction[0])));
            } else {
                PieceTransform t = new PieceTransform().rotateY(yRot(dir) + 180);
                String right = clockwiseCounterclockwise(dir, true);
                String left = clockwiseCounterclockwise(dir, false);
                Set<Direction> cull = EnumSet.noneOf(Direction.class);
                if (bool(props, "up")) cull.add(Direction.UP);
                if (bool(props, "down")) cull.add(Direction.DOWN);
                if (bool(props, right)) cull.add(Direction.EAST);
                if (bool(props, left)) cull.add(Direction.WEST);
                out.add(p(t, dir, o(0, 0, 0), s(16, 16, 1), m(0, 0, 0), cull.toArray(new Direction[0])));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Byte (multi-state; port of CopycatMultiByteModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> byteBlock(Map<String, String> props) {
        List<Piece> out = new ArrayList<>();
        // key -> [x, y, z] octant; x=east, y=top, z=south
        Map<String, double[]> octants = Map.of(
                "bottom_northwest", new double[]{0, 0, 0},
                "bottom_northeast", new double[]{1, 0, 0},
                "bottom_southwest", new double[]{0, 0, 1},
                "bottom_southeast", new double[]{1, 0, 1},
                "top_northwest", new double[]{0, 1, 0},
                "top_northeast", new double[]{1, 1, 0},
                "top_southwest", new double[]{0, 1, 1},
                "top_southeast", new double[]{1, 1, 1});
        for (var entry : octants.entrySet()) {
            if (!bool(props, entry.getKey())) continue;
            double ox = entry.getValue()[0] * 8;
            double oy = entry.getValue()[1] * 8;
            double oz = entry.getValue()[2] * 8;
            PieceTransform t = new PieceTransform();
            out.add(p(t, entry.getKey(), o(ox, oy, oz), s(4, 4, 4), m(0, 0, 0), Direction.UP, Direction.EAST, Direction.SOUTH));
            out.add(p(t, entry.getKey(), o(ox + 4, oy, oz), s(4, 4, 4), m(12, 0, 0), Direction.UP, Direction.WEST, Direction.SOUTH));
            out.add(p(t, entry.getKey(), o(ox, oy, oz + 4), s(4, 4, 4), m(0, 0, 12), Direction.UP, Direction.EAST, Direction.NORTH));
            out.add(p(t, entry.getKey(), o(ox + 4, oy, oz + 4), s(4, 4, 4), m(12, 0, 12), Direction.UP, Direction.WEST, Direction.NORTH));
            out.add(p(t, entry.getKey(), o(ox, oy + 4, oz), s(4, 4, 4), m(0, 12, 0), Direction.DOWN, Direction.EAST, Direction.SOUTH));
            out.add(p(t, entry.getKey(), o(ox + 4, oy + 4, oz), s(4, 4, 4), m(12, 12, 0), Direction.DOWN, Direction.WEST, Direction.SOUTH));
            out.add(p(t, entry.getKey(), o(ox, oy + 4, oz + 4), s(4, 4, 4), m(0, 12, 12), Direction.DOWN, Direction.EAST, Direction.NORTH));
            out.add(p(t, entry.getKey(), o(ox + 4, oy + 4, oz + 4), s(4, 4, 4), m(12, 12, 12), Direction.DOWN, Direction.WEST, Direction.NORTH));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Byte panel (multi-state; port of CopycatMultiBytePanelModelCore)
    // ------------------------------------------------------------------

    private static List<Piece> bytePanel(Map<String, String> props) {
        String facing = props.getOrDefault("facing", "down");
        List<Piece> out = new ArrayList<>();
        for (String key : List.of("bottom_left", "bottom_right", "top_left", "top_right")) {
            if (!bool(props, key)) continue;
            int i = key.equals("bottom_left") || key.equals("top_left") ? 1 : 0;
            int j = key.equals("top_left") || key.equals("top_right") ? 1 : 0;
            if (facing.equals("north") || facing.equals("east") || facing.equals("south") || facing.equals("west")) {
                PieceTransform t = new PieceTransform().rotateY(yRot(facing));
                out.add(p(t, key, o(i * 8, j * 8, 13), s(4, 4, 2), m(0, 0, 0), Direction.UP, Direction.EAST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8 + 4, j * 8, 13), s(4, 4, 2), m(12, 0, 0), Direction.UP, Direction.WEST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8, j * 8 + 4, 13), s(4, 4, 2), m(0, 12, 0), Direction.DOWN, Direction.EAST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8 + 4, j * 8 + 4, 13), s(4, 4, 2), m(12, 12, 0), Direction.DOWN, Direction.WEST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8, j * 8, 15), s(4, 4, 1), m(0, 0, 15), Direction.UP, Direction.EAST, Direction.NORTH));
                out.add(p(t, key, o(i * 8 + 4, j * 8, 15), s(4, 4, 1), m(12, 0, 15), Direction.UP, Direction.WEST, Direction.NORTH));
                out.add(p(t, key, o(i * 8, j * 8 + 4, 15), s(4, 4, 1), m(0, 12, 15), Direction.DOWN, Direction.EAST, Direction.NORTH));
                out.add(p(t, key, o(i * 8 + 4, j * 8 + 4, 15), s(4, 4, 1), m(12, 12, 15), Direction.DOWN, Direction.WEST, Direction.NORTH));
            } else if (facing.equals("down")) {
                PieceTransform t = new PieceTransform();
                out.add(p(t, key, o(i * 8, 0, j * 8), s(4, 2, 4), m(0, 0, 0), Direction.UP, Direction.EAST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8 + 4, 0, j * 8), s(4, 2, 4), m(12, 0, 0), Direction.UP, Direction.WEST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8, 0, j * 8 + 4), s(4, 2, 4), m(0, 0, 12), Direction.UP, Direction.EAST, Direction.NORTH));
                out.add(p(t, key, o(i * 8 + 4, 0, j * 8 + 4), s(4, 2, 4), m(12, 0, 12), Direction.UP, Direction.WEST, Direction.NORTH));
                out.add(p(t, key, o(i * 8, 2, j * 8), s(4, 1, 4), m(0, 0, 0), Direction.DOWN, Direction.EAST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8 + 4, 2, j * 8), s(4, 1, 4), m(12, 0, 0), Direction.DOWN, Direction.WEST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8, 2, j * 8 + 4), s(4, 1, 4), m(0, 0, 12), Direction.DOWN, Direction.EAST, Direction.NORTH));
                out.add(p(t, key, o(i * 8 + 4, 2, j * 8 + 4), s(4, 1, 4), m(12, 0, 12), Direction.DOWN, Direction.WEST, Direction.NORTH));
            } else { // up
                PieceTransform t = new PieceTransform();
                out.add(p(t, key, o(i * 8, 13, 8 - j * 8), s(4, 2, 4), m(0, 0, 0), Direction.UP, Direction.EAST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8 + 4, 13, 8 - j * 8), s(4, 2, 4), m(12, 0, 0), Direction.UP, Direction.WEST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8, 13, 8 - j * 8 + 4), s(4, 2, 4), m(0, 0, 12), Direction.UP, Direction.EAST, Direction.NORTH));
                out.add(p(t, key, o(i * 8 + 4, 13, 8 - j * 8 + 4), s(4, 2, 4), m(12, 0, 12), Direction.UP, Direction.WEST, Direction.NORTH));
                out.add(p(t, key, o(i * 8, 15, 8 - j * 8), s(4, 1, 4), m(0, 0, 0), Direction.DOWN, Direction.EAST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8 + 4, 15, 8 - j * 8), s(4, 1, 4), m(12, 0, 0), Direction.DOWN, Direction.WEST, Direction.SOUTH));
                out.add(p(t, key, o(i * 8, 15, 8 - j * 8 + 4), s(4, 1, 4), m(0, 0, 12), Direction.DOWN, Direction.EAST, Direction.NORTH));
                out.add(p(t, key, o(i * 8 + 4, 15, 8 - j * 8 + 4), s(4, 1, 4), m(12, 0, 12), Direction.DOWN, Direction.WEST, Direction.NORTH));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Create native copycat step (port of CopycatStepModel, facing=south/half=bottom canonical)
    // ------------------------------------------------------------------

    private static List<Piece> createStep(Map<String, String> props) {
        PieceTransform t = new PieceTransform().rotateY(yRot(props.getOrDefault("facing", "south")))
                .flipY("top".equals(props.get("half")));
        List<Piece> out = new ArrayList<>(4);
        out.add(p(t, null, o(0, 0, 12), s(16, 4, 4), m(0, 0, 12), Direction.NORTH, Direction.UP));
        // back, upper: select y[12,16] z[12,16], moved down to y[4,8]
        out.add(p(t, null, o(0, 4, 12), s(16, 4, 4), m(0, 12, 12), Direction.NORTH, Direction.DOWN));
        // front, lower: select y[0,4] z[0,4], moved forward to z[8,12]
        out.add(p(t, null, o(0, 0, 8), s(16, 4, 4), m(0, 0, 0), Direction.SOUTH, Direction.UP));
        // front, upper: select y[12,16] z[0,4], moved to y[4,8] z[8,12]
        out.add(p(t, null, o(0, 4, 8), s(16, 4, 4), m(0, 12, 0), Direction.SOUTH, Direction.DOWN));
        return out;
    }

    // ------------------------------------------------------------------
    // Create native copycat panel (port of CopycatPanelModel, facing=up canonical)
    // ------------------------------------------------------------------

    private static List<Piece> createPanel(Map<String, String> props) {
        String facing = props.getOrDefault("facing", "up");
        PieceTransform t = new PieceTransform();
        switch (facing) {
            case "down" -> t.flipY(true);
            case "north" -> t.rotateX(90);
            case "south" -> t.rotateX(270);
            case "east" -> t.rotateZ(90);
            case "west" -> t.rotateZ(270);
            default -> { } // up: identity
        }
        List<Piece> out = new ArrayList<>(2);
        // front: 1px plate at the facing-opposite side (material's bottom strip);
        // back: the material's top 2px strip moved down behind it (texture slice)
        out.add(p(t, null, o(0, 0, 0), s(16, 1, 16), m(0, 0, 0), Direction.UP));
        out.add(p(t, null, o(0, 1, 0), s(16, 2, 16), m(0, 14, 0), Direction.DOWN));
        return out;
    }
}
