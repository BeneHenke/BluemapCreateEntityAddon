package eu.cronmoth.createentityaddon.rendering.text;

import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import eu.cronmoth.createentityaddon.rendering.tracks.ObjMesh;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FontAtlas {

    public static final ResourcePath<Texture> PATH = new ResourcePath<>("createentityaddon", "font_atlas");

    private static final String RESOURCE = "/createentityaddon/font/ascii.png";
    private static final int COLS = 16;
    private static final int CELL = 8; // native px per glyph cell in ascii.png
    private static final char FIRST = 0x20; // ' ' - ascii.png's grid is blank before this (rows 0-1)
    private static final char LAST = 0x7E; // '~' - last printable-ASCII cell in the grid (row 7 ends here)
    private static final float UNITS_PER_CELL = 8f; // ascii.png's own native cell size
    public static final float MONOSPACE_PX = 7f;
    public static final float WIDE_MONOSPACE_PX = 9f;
    public static final float SECTION_GAP_PX = 1f;
    public static final float SECTION_GAP_WIDE_PX = 8f;

    private static final float SPACE_ADVANCE_PX = 4f;

    private static volatile FontAtlas instance;

    public static FontAtlas get() {
        FontAtlas i = instance;
        if (i == null) {
            synchronized (FontAtlas.class) {
                i = instance;
                if (i == null) instance = i = new FontAtlas();
            }
        }
        return i;
    }

    private static BufferedImage loadBitmap() throws IOException {
        try (InputStream in = FontAtlas.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing bundled resource " + RESOURCE);
            return ImageIO.read(in);
        }
    }

    /** Wraps the bundled vanilla font bitmap as a {@link Texture} at {@link #PATH} - see {@link FontAtlasExtension}. */
    public static Texture buildTexture() throws IOException {
        return Texture.from(PATH, loadBitmap());
    }

    /**
     * One already-formatted {@code FlapDisplaySection} (padded/truncated/aligned - see
     * {@code DisplayBoardTextRenderer.formatSectionText} - skipped entirely for {@code singleFlap},
     * matching {@code FlapDisplaySection.refresh}), ready to lay out.
     *
     * @param widthPx the section's own declared {@code Width} (NBT field, in px) - used for the
     *                row's total-width/centering math exactly like Create's {@code section.getSize()},
     *                independently of how long {@code text} actually turned out to be (matters for
     *                {@code singleFlap}, whose text isn't truncated/padded to match it)
     * @param singleFlap one whole-string flap instead of one flap per character - text renders with
     *                   the font's own natural proportional widths, not a fixed monospace cell
     */
    public record Section(String text, float widthPx, boolean wide, boolean singleFlap, boolean hasGap) {}

    /**
     * @param u0 left, v0 top, u1 right, v1 bottom (uv, tight around the glyph's actual pixels - not the whole cell, so glyphs aren't stretched to a common width)
     * @param width  the glyph's own undistorted pixel width - used to size its quad (and to center it inside a fixed monospace cell, see {@link #buildRow})
     * @param advance vanilla's real cursor step for this glyph - {@code (width/2) + 1}, the same
     *                formula {@code BitmapProvider} uses (verified against known real widths:
     *                {@code i}=1, {@code l}=2, most capitals=3) - narrower than {@code width} on
     *                purpose, so consecutive glyphs slightly overlap just like real Minecraft text.
     *                Only used for {@code singleFlap} sections; monospace-cell sections advance by
     *                a fixed {@link #MONOSPACE_PX}/{@link #WIDE_MONOSPACE_PX} instead.
     */
    private record Glyph(float u0, float v0, float u1, float v1, float width, float advance) {}

    private final Map<Character, Glyph> glyphs = new HashMap<>();

    private FontAtlas() {
        BufferedImage image;
        try {
            image = loadBitmap();
        } catch (IOException e) {
            throw new IllegalStateException("failed loading bundled font bitmap " + RESOURCE, e);
        }
        int atlasW = image.getWidth(), atlasH = image.getHeight();

        for (char c = FIRST; c <= LAST; c++) {
            int idx = c - FIRST;
            int row = 2 + idx / COLS; // rows 0-1 of the grid are blank/unused (control chars)
            int col = idx % COLS;
            int cellX = col * CELL, cellY = row * CELL;

            if (c == ' ') continue; // never drawn (see buildRow) - no glyph needed

            int left = -1, right = -1;
            for (int x = 0; x < CELL; x++) {
                boolean opaque = false;
                for (int y = 0; y < CELL && !opaque; y++) {
                    opaque = (image.getRGB(cellX + x, cellY + y) >>> 24) != 0;
                }
                if (opaque) {
                    if (left < 0) left = x;
                    right = x;
                }
            }
            if (left < 0) continue; // blank cell (unmapped character)

            int width = right - left + 1;
            glyphs.put(c, new Glyph(
                    (cellX + left) / (float) atlasW, cellY / (float) atlasH,
                    (cellX + right + 1) / (float) atlasW, (cellY + CELL) / (float) atlasH,
                    width, width / 2f + 1f));
        }
    }

    public ObjMesh buildRow(List<Section> sections, float cellSize) {
        List<Float> pos = new ArrayList<>();
        List<Float> uv = new ArrayList<>();
        int tris = 0;
        float worldPerPx = cellSize / UNITS_PER_CELL;

        float totalPx = 0f;
        for (Section s : sections) totalPx += s.widthPx() + (s.hasGap() ? SECTION_GAP_WIDE_PX : SECTION_GAP_PX);
        if (totalPx <= 0f) return null;

        float y0 = -cellSize / 2f, y1 = cellSize / 2f;
        float cursorPx = -totalPx / 2f; // whole row centered around local X=0

        for (Section s : sections) {
            float cellPx = s.wide() ? WIDE_MONOSPACE_PX : MONOSPACE_PX;
            for (int ci = 0; ci < s.text().length(); ci++) {
                char c = s.text().charAt(ci);
                Glyph gl = c == ' ' ? null : glyphs.get(c);

                if (gl != null) {
                    float glyphWorld = gl.width() * worldPerPx;
                    // singleFlap: draw flush at the natural glyph width, no fixed-cell centering
                    float x0 = s.singleFlap()
                            ? cursorPx * worldPerPx
                            : cursorPx * worldPerPx + (cellPx * worldPerPx - glyphWorld) / 2f;
                    float x1 = x0 + glyphWorld;
                    addQuad(pos, uv, x0, y1, x1, y1, x1, y0, x0, y0,
                            gl.u0(), gl.v0(), gl.u1(), gl.v0(), gl.u1(), gl.v1(), gl.u0(), gl.v1());
                    tris += 2;
                }
                // singleFlap steps by vanilla's real (tighter) advance, not the glyph's own width -
                // see Glyph#advance's doc; monospace-cell sections always step a fixed cellPx
                cursorPx += s.singleFlap() ? (gl != null ? gl.advance() : SPACE_ADVANCE_PX) : cellPx;
            }
            cursorPx += s.hasGap() ? SECTION_GAP_WIDE_PX : SECTION_GAP_PX;
        }

        if (tris == 0) return null;
        float[] posArr = new float[pos.size()];
        for (int i = 0; i < posArr.length; i++) posArr[i] = pos.get(i);
        float[] uvArr = new float[uv.size()];
        for (int i = 0; i < uvArr.length; i++) uvArr[i] = uv.get(i);
        return ObjMesh.of(posArr, uvArr, new int[tris]); // one texture slot: the atlas
    }

    private static void addQuad(List<Float> pos, List<Float> uv,
                                 float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                                 float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        // A,C,B / A,D,C winding - A->B->C gave a -Z normal (verified against the board's screen
        // going dark instead of showing text - see DisplayBoardTextRenderer.SCREEN_Z's doc), the
        // mesh is meant to face +Z
        addTri(pos, uv, x0, y0, x2, y2, x1, y1, u0, v0, u2, v2, u1, v1);
        addTri(pos, uv, x0, y0, x3, y3, x2, y2, u0, v0, u3, v3, u2, v2);
    }

    private static void addTri(List<Float> pos, List<Float> uv,
                                float ax, float ay, float bx, float by, float cx, float cy,
                                float au, float av, float bu, float bv, float cu, float cv) {
        pos.add(ax); pos.add(ay); pos.add(0f);
        pos.add(bx); pos.add(by); pos.add(0f);
        pos.add(cx); pos.add(cy); pos.add(0f);
        uv.add(au); uv.add(av);
        uv.add(bu); uv.add(bv);
        uv.add(cu); uv.add(cv);
    }
}
