package eu.cronmoth.createentityaddon.rendering.tracks;

import com.flowpowered.math.vector.Vector3d;

/**
 * The curve math behind create's bezier track connections, kept apart from the rendering so it can
 * be read against create's own source: this is a port of {@code BezierConnection.Runtime} and
 * {@code Bezierator} (sampling, handle length) plus {@code TrackRenderer.getModelAngles}.
 */
final class BezierCurve {

    private BezierCurve() {}

    /** One sampled point of the curve: where it is, which way it runs, and which way is sideways. */
    record Sample(Vector3d position, Vector3d derivative, Vector3d normal) {}

    /** Port of {@code BezierConnection.Runtime} + {@code Bezierator}: arc-length-even samples of the curve. */
    static Sample[] sample(Vector3d end1, Vector3d end2, Vector3d axis1, Vector3d axis2,
                           Vector3d faceNormal1, Vector3d faceNormal2) {
        double handleLength = determineHandleLength(end1, end2, axis1, axis2);
        Vector3d finish1 = axis1.mul(handleLength).add(end1);
        Vector3d finish2 = axis2.mul(handleLength).add(end2);

        double length = 0;
        Vector3d prev = end1;
        for (int i = 1; i <= 16; i++) {
            Vector3d p = bezier(end1, finish1, finish2, end2, i / 16.0);
            length += p.distance(prev);
            prev = p;
        }
        int segments = (int) (length * 2);
        if (segments < 1) return null;

        double[] lut = new double[segments + 1];
        lut[0] = 1;
        double combined = 0;
        prev = end1;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vector3d p = bezier(end1, finish1, finish2, end2, t);
            if (i > 0) {
                combined += p.distance(prev) / length;
                lut[i] = t / combined;
            }
            prev = p;
        }

        boolean sameFace = faceNormal1.distance(faceNormal2) < 1e-6;
        Sample[] out = new Sample[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = (i == segments) ? 1.0 : (i * lut[i] / segments);
            Vector3d position = bezier(end1, finish1, finish2, end2, t);
            Vector3d derivative = bezierDerivative(end1, finish1, finish2, end2, t).normalize();
            Vector3d faceNormal = sameFace ? faceNormal1 : slerp(t, faceNormal1, faceNormal2);
            Vector3d normal = faceNormal.cross(derivative).normalize();
            out[i] = new Sample(position, derivative, normal);
        }
        return out;
    }

    static double[] getModelAngles(Vector3d normal, Vector3d diff) {
        double dx = diff.getX();
        double dy = diff.getY();
        double dz = diff.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.atan2(dx, dz);
        double pitch = Math.atan2(len, dy) - Math.PI * 0.5;

        Vector3d ref = rotate(rotate(new Vector3d(0, 1, 0), Math.toDegrees(pitch), 0), Math.toDegrees(yaw), 1);

        double signum = Math.signum(ref.dot(normal));
        if (Math.abs(signum) < 0.5f)
            signum = ref.sub(normal).lengthSquared() < 0.5f ? -1 : 1;
        double dot = diff.cross(normal).normalize().dot(ref);
        double roll = Math.acos(clamp(dot, -1, 1)) * signum;
        return new double[]{pitch, yaw, roll};
    }

    /** rotate a vector by {@code deg} degrees about axis 0=x / 1=y / 2=z (minecraft sense). */
    private static Vector3d rotate(Vector3d v, double deg, int axis) {
        double r = Math.toRadians(deg);
        double s = Math.sin(r);
        double c = Math.cos(r);
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        return switch (axis) {
            case 0 -> new Vector3d(x, y * c - z * s, y * s + z * c);
            case 1 -> new Vector3d(x * c + z * s, y, -x * s + z * c);
            default -> new Vector3d(x * c - y * s, x * s + y * c, z);
        };
    }

    private static Vector3d slerp(double t, Vector3d a, Vector3d b) {
        double dot = clamp(a.dot(b), -1, 1);
        double theta = Math.acos(dot) * t;
        Vector3d rel = b.sub(a.mul(dot));
        if (rel.lengthSquared() < 1e-12) return a;
        rel = rel.normalize();
        return a.mul(Math.cos(theta)).add(rel.mul(Math.sin(theta)));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static Vector3d bezier(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        double u = 1.0 - t;
        double tt = t * t;
        double uu = u * u;
        return p0.mul(uu * u)
                .add(p1.mul(3 * uu * t))
                .add(p2.mul(3 * u * tt))
                .add(p3.mul(tt * t));
    }

    private static Vector3d bezierDerivative(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        double u = 1.0 - t;
        return p1.sub(p0).mul(3 * u * u)
                .add(p2.sub(p1).mul(6 * u * t))
                .add(p3.sub(p2).mul(3 * t * t));
    }

    private static double determineHandleLength(Vector3d end1, Vector3d end2, Vector3d axis1, Vector3d axis2) {
        Vector3d cross1 = axis1.cross(new Vector3d(0, 1, 0));
        Vector3d cross2 = axis2.cross(new Vector3d(0, 1, 0));

        double a1 = Math.atan2(-axis2.getZ(), -axis2.getX());
        double a2 = Math.atan2(axis1.getZ(), axis1.getX());
        double angle = a1 - a2;

        float circle = 2 * (float) Math.PI;
        angle = (angle + circle) % circle;
        if (Math.abs(circle - angle) < Math.abs(angle))
            angle = circle - angle;

        if (Math.abs(angle) < 1e-6) {
            double[] intersect = intersect3d(end1, end2, axis1, cross2);
            if (intersect != null) {
                double t = Math.abs(intersect[0]);
                double u = Math.abs(intersect[1]);
                double min = Math.min(t, u);
                double max = Math.max(t, u);
                if (min > 1.2 && max / min > 1 && max / min < 3)
                    return max - min;
            }
            return end2.distance(end1) / 3.0;
        }

        double n = circle / angle;
        double factor = 4.0 / 3.0 * Math.tan(Math.PI / (2 * n));
        double[] intersect = intersect3d(end1, end2, cross1, cross2);
        if (intersect == null)
            return end2.distance(end1) / 3.0;

        double radius = Math.abs(intersect[1]);
        double handleLength = radius * factor;
        if (Math.abs(handleLength) < 1e-6)
            handleLength = 1;
        return handleLength;
    }

    private static double[] intersect3d(Vector3d p1, Vector3d p2, Vector3d d1, Vector3d d2) {
        double d1x = d1.getX();
        double d1z = d1.getZ();
        double d2x = d2.getX();
        double d2z = d2.getZ();

        double det = d1x * d2z - d1z * d2x;
        if (Math.abs(det) < 1e-6) return null;

        double dx = p2.getX() - p1.getX();
        double dz = p2.getZ() - p1.getZ();

        double t = (dx * d2z - dz * d2x) / det;
        double u = (dx * d1z - dz * d1x) / det;
        return new double[]{t, u};
    }
}
