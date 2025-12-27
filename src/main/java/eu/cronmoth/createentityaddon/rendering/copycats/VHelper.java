package eu.cronmoth.createentityaddon.rendering.copycats;

import de.bluecolored.bluemap.core.util.math.VectorM2f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;

public class VHelper {
    VectorM3f p; VectorM2f uv; float ao;
    VHelper(VectorM3f p, VectorM2f uv, float ao) {
        this.p = p; this.uv = uv; this.ao = ao;
    }
}
