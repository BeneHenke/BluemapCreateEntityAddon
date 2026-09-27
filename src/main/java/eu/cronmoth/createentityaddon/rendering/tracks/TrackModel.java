package eu.cronmoth.createentityaddon.rendering.tracks;

/**
 * The parts of a track model path the renderer dispatches on, worked out once instead of by
 * scattered substring tests. A path looks like {@code create:block/track/x_ortho} or
 * {@code railways:block/track/acacia/diag}.
 *
 * @param path  the full model path, as the variant reports it
 * @param base  everything up to the last {@code /} - the folder its siblings live in
 * @param shape the last segment: {@code x_ortho}, {@code diag_2}, {@code cross_d1_xo}, ...
 * @param gauge {@code standard} / {@code narrow} / {@code wide} / {@code monorail}, the key
 *              {@link TrackMeshExtension} stores its meshes under
 * @param material the track material id create writes into the connection nbt, e.g.
 *                 {@code create:standard} or {@code railways:acacia}
 */
record TrackModel(String path, String base, String shape, String gauge, String material) {

    static TrackModel of(String path) {
        int lastSlash = path.lastIndexOf('/');
        String base = lastSlash != -1 ? path.substring(0, lastSlash) : path;
        String shape = lastSlash != -1 ? path.substring(lastSlash + 1) : path;

        String gauge = path.contains("/monorail/") ? "monorail"
                : path.contains("_narrow") ? "narrow"
                : path.contains("_wide") ? "wide" : "standard";

        return new TrackModel(path, base, shape, gauge, material(path, lastSlash));
    }

    /** {@code create:block/track/diag} -&gt; {@code create:standard}; {@code railways:block/track/acacia/diag} -&gt; {@code railways:acacia}. */
    private static String material(String path, int lastSlash) {
        if (path.startsWith("create:")) return "create:standard";
        if (path.contains("/monorail/")) return "railways:monorail";
        int colon = path.indexOf(':');
        int prevSlash = lastSlash > 0 ? path.lastIndexOf('/', lastSlash - 1) : -1;
        if (colon < 0 || prevSlash <= colon) return "create:standard";
        return path.substring(0, colon) + ":" + path.substring(prevSlash + 1, lastSlash);
    }

    /** A real box model bluemap renders fine on its own. */
    boolean isStraight() {
        return shape.equals("x_ortho") || shape.equals("z_ortho") || shape.equals("cross_ortho");
    }

    /** A shape create ships as {@code .obj}, which bluemap cannot load. */
    boolean hasObjMesh() {
        return shape.equals("diag") || shape.equals("diag_2") || shape.equals("ascending");
    }

    /** One of the two diagonals - the shapes steam'n'rails only ships as gauge-wrong template json. */
    boolean isDiagonal() {
        return shape.equals("diag") || shape.equals("diag_2");
    }

    /** Steam'n'rails' two extra rail widths, which need their own meshes rather than create's. */
    boolean isNarrowOrWide() {
        return gauge.equals("narrow") || gauge.equals("wide");
    }

    /** The sibling model with the same base, e.g. the {@code x_ortho} half of a crossing. */
    TrackModel sibling(String otherShape) {
        return of(base + "/" + otherShape);
    }
}
