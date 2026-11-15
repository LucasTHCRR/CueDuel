package de.paperstream.cueduel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

// Baut Meshes aus einfachen Formen: Position, Normale, Farbe, UV
final class Geo {
    static final int STRIDE = 12;
    final FloatArray v = new FloatArray();
    final ShortArray idx = new ShortArray();
    final Matrix4 tr = new Matrix4();
    private float r = 1, g = 1, b = 1, a = 1;
    private final Vector3 tp = new Vector3(), tn = new Vector3();
    private int count;

    Geo color(String hex) {
        Color c = Color.valueOf(hex);
        r = c.r;
        g = c.g;
        b = c.b;
        return this;
    }

    Geo color(float cr, float cg, float cb) {
        r = cr;
        g = cg;
        b = cb;
        return this;
    }

    // a = 0 heisst unbeleuchtet
    Geo lit(boolean on) {
        a = on ? 1 : 0;
        return this;
    }

    Geo at(float x, float y, float z) {
        tr.idt().translate(x, y, z);
        return this;
    }

    Geo rotY(float rad) {
        tr.rotateRad(Vector3.Y, rad);
        return this;
    }

    int vert(float x, float y, float z, float nx, float ny, float nz, float u, float w) {
        tp.set(x, y, z).mul(tr);
        tn.set(nx, ny, nz).rot(tr).nor();
        v.addAll(tp.x, tp.y, tp.z, tn.x, tn.y, tn.z);
        v.addAll(r, g, b, a);
        v.addAll(u, w);
        return count++;
    }

    void tri(int i0, int i1, int i2) {
        idx.add((short) i0);
        idx.add((short) i1);
        idx.add((short) i2);
    }

    void quadIdx(int i0, int i1, int i2, int i3) {
        tri(i0, i1, i2);
        tri(i0, i2, i3);
    }

    private void face(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float nx, float ny, float nz) {
        int i = vert(x0, y0, z0, nx, ny, nz, 0, 0);
        vert(x1, y1, z1, nx, ny, nz, 1, 0);
        vert(x2, y2, z2, nx, ny, nz, 1, 1);
        vert(x3, y3, z3, nx, ny, nz, 0, 1);
        quadIdx(i, i + 1, i + 2, i + 3);
    }

    void box(float sx, float sy, float sz) {
        float x = sx / 2, y = sy / 2, z = sz / 2;
        face(-x, y, -z, x, y, -z, x, y, z, -x, y, z, 0, 1, 0);
        face(-x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z, 0, -1, 0);
        face(x, -y, -z, x, y, -z, x, y, z, x, -y, z, 1, 0, 0);
        face(-x, -y, -z, -x, y, -z, -x, y, z, -x, -y, z, -1, 0, 0);
        face(-x, -y, z, x, -y, z, x, y, z, -x, y, z, 0, 0, 1);
        face(-x, -y, -z, x, -y, -z, x, y, -z, -x, y, -z, 0, 0, -1);
    }

    void quadY(float sx, float sz) {
        float x = sx / 2, z = sz / 2;
        face(-x, 0, -z, x, 0, -z, x, 0, z, -x, 0, z, 0, 1, 0);
    }

    void disc(float rad, int seg) {
        int c = vert(0, 0, 0, 0, 1, 0, 0.5f, 0.5f);
        for (int i = 0; i <= seg; i++) {
            float t = MathUtils.PI2 * i / seg;
            vert(MathUtils.cos(t) * rad, 0, MathUtils.sin(t) * rad, 0, 1, 0, 0, 0);
        }
        for (int i = 0; i < seg; i++) tri(c, c + 1 + i, c + 2 + i);
    }

    // Kegelstumpf entlang der X-Achse von x0 (Radius r0) bis x1 (Radius r1)
    void frustumX(float x0, float r0, float x1, float r1, int seg, boolean caps) {
        float slope = (r0 - r1) / (x1 - x0);
        int base = count;
        for (int i = 0; i <= seg; i++) {
            float t = MathUtils.PI2 * i / seg;
            float cy = MathUtils.cos(t), cz = MathUtils.sin(t);
            vert(x0, cy * r0, cz * r0, slope, cy, cz, 0, 0);
            vert(x1, cy * r1, cz * r1, slope, cy, cz, 0, 0);
        }
        for (int i = 0; i < seg; i++) {
            int k = base + i * 2;
            quadIdx(k, k + 1, k + 3, k + 2);
        }
        if (caps) {
            for (int e = 0; e < 2; e++) {
                float x = e == 0 ? x0 : x1, rad = e == 0 ? r0 : r1, nx = e == 0 ? -1 : 1;
                int c = vert(x, 0, 0, nx, 0, 0, 0, 0);
                for (int i = 0; i <= seg; i++) {
                    float t = MathUtils.PI2 * i / seg;
                    vert(x, MathUtils.cos(t) * rad, MathUtils.sin(t) * rad, nx, 0, 0, 0, 0);
                }
                for (int i = 0; i < seg; i++) tri(c, c + 1 + i, c + 2 + i);
            }
        }
    }

    // Zylinder entlang Y, Mitte im Ursprung, mit Deckel oben
    void cylinderY(float rad, float h, int seg) {
        int base = count;
        for (int i = 0; i <= seg; i++) {
            float t = MathUtils.PI2 * i / seg;
            float cx = MathUtils.cos(t), cz = MathUtils.sin(t);
            vert(cx * rad, -h / 2, cz * rad, cx, 0, cz, 0, 0);
            vert(cx * rad, h / 2, cz * rad, cx, 0, cz, 0, 0);
        }
        for (int i = 0; i < seg; i++) {
            int k = base + i * 2;
            quadIdx(k, k + 1, k + 3, k + 2);
        }
        float keep = tr.val[Matrix4.M13];
        tr.translate(0, h / 2, 0);
        disc(rad, seg);
        tr.translate(0, -h / 2, 0);
        tr.val[Matrix4.M13] = keep;
    }

    // Ring in der XZ-Ebene
    void torus(float big, float small, int seg, int tube) {
        int base = count;
        for (int i = 0; i <= seg; i++) {
            float t = MathUtils.PI2 * i / seg;
            float cx = MathUtils.cos(t), cz = MathUtils.sin(t);
            for (int k = 0; k <= tube; k++) {
                float p = MathUtils.PI2 * k / tube;
                float cr = MathUtils.cos(p), cyy = MathUtils.sin(p);
                vert(cx * (big + small * cr), small * cyy, cz * (big + small * cr), cx * cr, cyy, cz * cr, 0, 0);
            }
        }
        int row = tube + 1;
        for (int i = 0; i < seg; i++) {
            for (int k = 0; k < tube; k++) {
                int q = base + i * row + k;
                quadIdx(q, q + 1, q + row + 1, q + row);
            }
        }
    }

    void sphere(float rad, int su, int sv) {
        int base = count;
        for (int j = 0; j <= sv; j++) {
            float ph = MathUtils.PI * j / sv;
            float y = MathUtils.cos(ph), rr = MathUtils.sin(ph);
            for (int i = 0; i <= su; i++) {
                float th = MathUtils.PI2 * i / su;
                float x = MathUtils.cos(th) * rr, z = MathUtils.sin(th) * rr;
                vert(x * rad, y * rad, z * rad, x, y, z, (float) i / su, (float) j / sv);
            }
        }
        int row = su + 1;
        for (int j = 0; j < sv; j++) {
            for (int i = 0; i < su; i++) {
                int q = base + j * row + i;
                quadIdx(q, q + 1, q + row + 1, q + row);
            }
        }
    }

    Mesh build() {
        Mesh m = new Mesh(true, count, idx.size,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        m.setVertices(v.toArray());
        m.setIndices(idx.toArray());
        return m;
    }
}
