package eu.cronmoth.createentityaddon.rendering.tracks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ObjMesh {

    /** 9 floats per triangle: x1,y1,z1, x2,y2,z2, x3,y3,z3. */
    final float[] pos;
    /** 6 floats per triangle: u1,v1, u2,v2, u3,v3. */
    final float[] uv;
    /** 1 per triangle: material slot ({@code map_Kd #N} from the sibling .mtl; 0=track, 1=_mip, 2=_crossing). */
    final int[] mat;
    final int triangles;

    private ObjMesh(float[] pos, float[] uv, int[] mat) {
        this.pos = pos;
        this.uv = uv;
        this.mat = mat;
        this.triangles = mat.length;
    }

    /**
     * Builds a mesh from ready triangle arrays (9 pos + 6 uv + 1 slot per triangle) - used when the
     * geometry is assembled in code rather than read from an {@code .obj}, e.g. a bluemap-parsed
     * box model whose UVs need a texture-size correction bluemap 5.7 doesn't apply.
     */
    public static ObjMesh of(float[] pos, float[] uv, int[] mat) {
        return new ObjMesh(pos, uv, mat);
    }

    /**
     * @param materialSlots {@code usemtl}-name -&gt; texture slot, parsed from the sibling {@code .mtl}
     *                      ({@code map_Kd #N}). Empty falls back to the trailing digit of the name
     *                      ({@code m_0}, {@code m_1}, ...).
     */
    static ObjMesh parse(InputStream in, Map<String, Integer> materialSlots) throws IOException {
        List<float[]> verts = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<Float> pos = new ArrayList<>();
        List<Float> uv = new ArrayList<>();
        List<Integer> mat = new ArrayList<>();
        int currentMat = 0;

        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v" -> verts.add(new float[]{parse(t[1]), parse(t[2]), parse(t[3])});
                    case "vt" -> uvs.add(new float[]{parse(t[1]), 1f - parse(t[2])}); // flip_v
                    case "usemtl" -> currentMat = materialSlots.getOrDefault(t[1], trailingDigit(t[1]));
                    case "f" -> {
                        int n = t.length - 1;
                        int[] vi = new int[n];
                        int[] ti = new int[n];
                        for (int i = 0; i < n; i++) {
                            String[] c = t[i + 1].split("/");
                            vi[i] = idx(c[0], verts.size());
                            ti[i] = c.length > 1 && !c[1].isEmpty() ? idx(c[1], uvs.size()) : -1;
                        }
                        for (int i = 1; i < n - 1; i++) {
                            addVertex(pos, uv, verts, uvs, vi[0], ti[0]);
                            addVertex(pos, uv, verts, uvs, vi[i], ti[i]);
                            addVertex(pos, uv, verts, uvs, vi[i + 1], ti[i + 1]);
                            mat.add(currentMat);
                        }
                    }
                    default -> { /* o, g, s, vn, mtllib, ... ignored */ }
                }
            }
        }

        float[] posArr = new float[pos.size()];
        for (int i = 0; i < posArr.length; i++) posArr[i] = pos.get(i);
        float[] uvArr = new float[uv.size()];
        for (int i = 0; i < uvArr.length; i++) uvArr[i] = uv.get(i);
        int[] matArr = new int[mat.size()];
        for (int i = 0; i < matArr.length; i++) matArr[i] = mat.get(i);
        return new ObjMesh(posArr, uvArr, matArr);
    }

    private static void addVertex(List<Float> pos, List<Float> uv, List<float[]> verts, List<float[]> uvs, int vi, int ti) {
        float[] v = verts.get(vi);
        pos.add(v[0]);
        pos.add(v[1]);
        pos.add(v[2]);
        float[] t = ti >= 0 && ti < uvs.size() ? uvs.get(ti) : new float[]{0f, 0f};
        uv.add(t[0]);
        uv.add(t[1]);
    }

    /**
     * {@code newmtl <name>} / {@code map_Kd #<key>} pairs -&gt; {@code {name: key}} with the {@code #}
     * stripped. {@code key} is a numeric slot for create's track meshes ({@code #0}) or a texture
     * name for other meshes ({@code #conveyor_port}). Insertion order preserved.
     */
    static Map<String, String> parseMtlTextureKeys(InputStream in) throws IOException {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            String current = null;
            while ((line = r.readLine()) != null) {
                String[] t = line.trim().split("\\s+");
                if (t.length < 2) continue;
                if (t[0].equals("newmtl")) current = t[1];
                else if (t[0].equals("map_Kd") && current != null && t[1].startsWith("#"))
                    map.put(current, t[1].substring(1));
            }
        }
        return map;
    }

    /** {@code map_Kd #N} lines in an .mtl -&gt; {materialName: N}. */
    static Map<String, Integer> parseMtl(InputStream in) throws IOException {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            String current = null;
            while ((line = r.readLine()) != null) {
                String[] t = line.trim().split("\\s+");
                if (t.length < 2) continue;
                if (t[0].equals("newmtl")) current = t[1];
                else if (t[0].equals("map_Kd") && current != null && t[1].startsWith("#")) {
                    try {
                        map.put(current, Integer.parseInt(t[1].substring(1)));
                    } catch (NumberFormatException ignored) { /* non-numeric ref */ }
                }
            }
        }
        return map;
    }

    private static int trailingDigit(String name) {
        int us = name.lastIndexOf('_');
        if (us >= 0) try {
            return Integer.parseInt(name.substring(us + 1));
        } catch (NumberFormatException ignored) { /* 'none' */ }
        return 0;
    }

    private static int idx(String s, int size) {
        int i = Integer.parseInt(s);
        return i < 0 ? size + i : i - 1;
    }

    private static float parse(String s) {
        return Float.parseFloat(s);
    }
}
