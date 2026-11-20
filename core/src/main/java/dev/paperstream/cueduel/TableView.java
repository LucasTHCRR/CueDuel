package dev.paperstream.cueduel;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.FloatArray;

// 3D-Raum, Tisch, Kugeln und Queue. Tischeinheiten mal S ergeben Welteinheiten.
final class TableView {
    static final float S = 0.01f;
    private static final float BR = (float) Sim.BR * S;
    private static final float RH = 0.2f, PR = 0.25f;

    private static final String VERT = "attribute vec3 a_position;\nattribute vec3 a_normal;\nattribute vec4 a_color;\n"
        + "attribute vec2 a_texCoord0;\nuniform mat4 u_projView;\nuniform mat4 u_world;\n"
        + "varying vec3 v_pos;\nvarying vec3 v_normal;\nvarying vec4 v_color;\nvarying vec2 v_uv;\n"
        + "void main() {\n  vec4 w = u_world * vec4(a_position, 1.0);\n  v_pos = w.xyz;\n"
        + "  v_normal = (u_world * vec4(a_normal, 0.0)).xyz;\n  v_color = a_color;\n  v_uv = a_texCoord0;\n"
        + "  gl_Position = u_projView * w;\n}\n";
    // q: 0 flaches Licht, 1 Lampenlicht mit Glanz, 2 zusätzlich Strukturen und Spiegelglanz
    private static String frag(int q) {
        String s = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "uniform sampler2D u_tex;\nuniform sampler2D u_noise;\nuniform float u_useTex;\nuniform vec3 u_cam;\n"
            + "uniform float u_spec;\nuniform float u_shine;\n"
            + "varying vec3 v_pos;\nvarying vec3 v_normal;\nvarying vec4 v_color;\nvarying vec2 v_uv;\n"
            + "void main() {\n  vec3 n = normalize(v_normal);\n  vec3 base = v_color.rgb;\n"
            + "  if (u_useTex > 0.5) base *= texture2D(u_tex, v_uv).rgb;\n";
        if (q == 2) {
            s += "  else {\n    float m = v_uv.x;\n"
                + "    if (m > 0.5 && m < 1.5) base *= 0.88 + 0.17 * texture2D(u_noise, v_pos.xz * 13.0).r"
                + " + 0.07 * texture2D(u_noise, v_pos.xz * 0.6).g;\n"
                + "    else if (m > 1.5 && m < 2.5) base *= 0.62 + 0.75 * texture2D(u_noise, vec2(v_pos.x * 2.6, v_pos.z * 0.16)).r;\n"
                + "    else if (m > 2.5 && m < 3.5) base *= 0.62 + 0.75 * texture2D(u_noise, vec2(v_pos.x * 0.16, v_pos.z * 2.6)).r;\n"
                + "    else if (m > 3.5) {\n      float p = fract(v_pos.x * 0.9);\n"
                + "      base *= (0.6 + 0.7 * texture2D(u_noise, vec2(v_pos.x * 1.8 + floor(v_pos.x * 0.9) * 0.37, v_pos.z * 0.08)).r)"
                + " * smoothstep(0.0, 0.035, p);\n    }\n  }\n";
        }
        if (q == 0) {
            s += "  vec3 col = base * (vec3(0.50, 0.47, 0.43) + vec3(0.55, 0.52, 0.47) * max(n.y, 0.0));\n";
        } else {
            s += "  vec3 l = vec3(0.0, 3.2, 0.0) - v_pos;\n  float d = length(l);\n  l /= d;\n"
                + "  float att = 1.0 / (1.0 + 0.05 * d * d);\n"
                + "  float lamp = max(dot(n, l), 0.0) * att * 1.75;\n"
                + "  float sun = max(dot(n, vec3(0.33, 0.83, 0.45)), 0.0) * 0.34;\n"
                + "  float hemi = 0.5 + 0.5 * n.y;\n"
                + "  vec3 light = mix(vec3(0.20, 0.17, 0.15), vec3(0.40, 0.36, 0.32), hemi)\n"
                + "    + vec3(1.0, 0.94, 0.82) * lamp + vec3(1.0, 0.96, 0.9) * sun;\n"
                + "  vec3 col = base * light;\n"
                + "  if (u_spec > 0.0) {\n    vec3 v = normalize(u_cam - v_pos);\n    vec3 h = normalize(l + v);\n"
                + "    float nh = max(dot(n, h), 0.0);\n"
                + "    col += vec3(1.0, 0.97, 0.9) * pow(nh, u_shine) * u_spec * min(att * 1.6, 1.0);\n";
            if (q == 2) {
                s += "    float fr = 1.0 - max(dot(n, v), 0.0);\n    fr = fr * fr * fr;\n"
                    + "    col += vec3(0.62, 0.56, 0.47) * fr * u_spec * 0.6;\n"
                    + "    col += vec3(1.0, 0.95, 0.85) * pow(nh, 9.0) * u_spec * 0.12 * att;\n";
            }
            s += "  }\n";
        }
        return s + "  col = mix(base, col, v_color.a);\n  gl_FragColor = vec4(col, 1.0);\n}\n";
    }

    final PerspectiveCamera cam = new PerspectiveCamera(50, 1, 1);
    float[] fixedCam;
    boolean headLine;

    private ShaderProgram shader;
    private final ShaderProgram[] shaders = new ShaderProgram[3];
    private Texture noise;
    private Mesh matte, gloss, lamp, sphere, stick;
    private final Texture[] ballTex = new Texture[16];
    private Texture white;
    private ImmediateModeRenderer20 imm;
    private final Matrix4 world = new Matrix4(), ident = new Matrix4();
    private final Quaternion[] rot = new Quaternion[16];
    private final Quaternion tq = new Quaternion();
    private final float[] lastX = new float[16], lastY = new float[16], fall = new float[16];
    private final float[] bx = new float[16], by = new float[16], bh = new float[16], bs = new float[16];
    private final boolean[] shown = new boolean[16];
    private final int[] nums = new int[16];
    private int count;
    private final Vector3 look = new Vector3(), tp = new Vector3(), tl = new Vector3();
    private boolean follow, stickOn;
    private float stickTipX, stickTipZ, stickDirX, stickDirZ;
    private final Matrix4 stickM = new Matrix4();
    private final FloatArray lines = new FloatArray();
    private boolean ghostOn;
    private float ghostX, ghostY;
    private int quality = -1;
    private com.badlogic.gdx.graphics.glutils.FrameBuffer fbo;
    private com.badlogic.gdx.graphics.g2d.SpriteBatch blit;

    static float wx(double x) {
        return (float) (x - Sim.CX) * S;
    }

    private static float holeR(int k) {
        return k == 2 || k == 3 ? 0.21f : 0.25f;
    }

    static float wz(double y) {
        return (float) (y - Sim.CY) * S;
    }

    void create() {
        ShaderProgram.pedantic = false;
        for (int q = 0; q < 3; q++) {
            shaders[q] = new ShaderProgram(VERT, frag(q));
            if (!shaders[q].isCompiled()) throw new IllegalStateException(shaders[q].getLog());
        }
        shader = shaders[1];
        imm = new ImmediateModeRenderer20(4000, false, true, 0);
        for (int i = 0; i < 16; i++) {
            rot[i] = new Quaternion();
            ballTex[i] = new Texture(Gdx.files.internal("balls/ball_" + i + ".png"), true);
            ballTex[i].setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            ballTex[i].setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
        }
        reloadWhite();
        buildTable();
        buildLamp();
        buildStick();
        applyQuality();
        // enger Tiefenbereich, sonst flimmert es auf Handys mit 16-Bit-Tiefenpuffer
        cam.near = 0.4f;
        cam.far = 48;
        cam.position.set(0, 13, 0.6f);
        look.set(0, 0, 0);
    }

    void reloadWhite() {
        if (white != null) white.dispose();
        com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(1, 1,
            com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        white = new Texture(pm);
        pm.dispose();
        // Rauschen für Tuch und Holz
        if (noise != null) noise.dispose();
        java.util.Random rnd = new java.util.Random(4);
        com.badlogic.gdx.graphics.Pixmap np = new com.badlogic.gdx.graphics.Pixmap(64, 64,
            com.badlogic.gdx.graphics.Pixmap.Format.RGB888);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                np.drawPixel(x, y, com.badlogic.gdx.graphics.Color.rgba8888(rnd.nextFloat(), rnd.nextFloat(), 0, 1));
            }
        }
        noise = new Texture(np, true);
        noise.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        noise.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        np.dispose();
    }

    void applyQuality() {
        if (quality == Store.quality) return;
        quality = Store.quality;
        shader = shaders[quality];
        if (sphere != null) sphere.dispose();
        Geo g = new Geo();
        if (quality == 2) g.sphere(BR, 28, 16);
        else if (quality == 1) g.sphere(BR, 20, 12);
        else g.sphere(BR, 16, 10);
        sphere = g.build();
    }

    // ---------- geometry ----------

    private void buildTable() {
        Geo m = new Geo();
        Geo g = new Geo();
        float tw = (float) (Sim.PR - Sim.PL) * S, td = (float) (Sim.PB - Sim.PT) * S;
        // Raum
        m.mat(4).color("6b4425").at(0, -3.2f, 0).quadY(30, 30);
        m.mat(0);
        float[][] walls = {{0, -13, 26, 0.2f}, {0, 13, 26, 0.2f}, {-13, 0, 0.2f, 26}, {13, 0, 0.2f, 26}};
        for (float[] w : walls) {
            m.color("2f5e4a").at(w[0], 1.5f, w[1]).box(w[2], 9.5f, w[3]);
            m.color("3b2010").at(w[0] * 0.995f, -2.4f, w[1] * 0.995f)
                .box(Math.max(w[2] - 0.05f, 0.3f), 1.6f, Math.max(w[3] - 0.05f, 0.3f));
        }
        // Tuch mit ausgeschnittenen Löchern
        m.mat(1).color("0f6a42").at(0, 0, 0);
        feltSheet(m);
        m.mat(0);
        // Schächte unter den Löchern
        m.lit(false);
        for (int k = 0; k < 6; k++) {
            double[] p = Sim.POCKETS[k];
            float r = holeR(k), px = wx(p[0]), pz = wz(p[1]);
            m.at(px, 0, pz);
            int base = -1;
            for (int a = 0; a <= 40; a++) {
                float t = MathUtils.PI2 * a / 40, c = MathUtils.cos(t), sn = MathUtils.sin(t);
                m.color(0.03f, 0.027f, 0.024f);
                int v0 = m.vert(c * r, 0, sn * r, -c, 0, -sn, 0, 0);
                m.color(0.008f, 0.008f, 0.008f);
                m.vert(c * r, -0.3f, sn * r, -c, 0, -sn, 0, 0);
                if (base >= 0) m.quadIdx(base, base + 1, v0 + 1, v0);
                base = v0;
            }
            m.color(0.008f, 0.008f, 0.008f).at(px, -0.298f, pz).disc(r, 40);
        }
        m.lit(true);
        // Banden
        m.mat(1).color("0d5e3a");
        for (int k = 0; k < 4; k++) cushionStrip(m, k < 2, k % 2 == 0 ? 1 : -1);
        m.mat(0);
        // Rahmen mit ausgeschnittenen Taschen
        g.color("6e3b1b");
        for (int k = 0; k < 4; k++) {
            g.mat(k < 2 ? 2 : 3);
            frameStrip(g, k < 2, k % 2 == 0 ? 1 : -1);
        }
        g.mat(2).color("5a2d12").at(0, -0.6f, 0).box(tw + 1.2f, 0.3f, td + 1.2f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                g.at(sx * (tw / 2 + 0.2f), -1.75f, sz * (td / 2 + 0.2f)).box(0.42f, 2.9f, 0.42f);
            }
        }
        g.mat(0);
        // Messingringe
        g.color("1b1a19");
        for (int k = 0; k < 6; k++) {
            double[] p = Sim.POCKETS[k];
            g.at(wx(p[0]), RH + 0.002f, wz(p[1])).torus(holeR(k) + 0.012f, 0.016f, 40, 8);
        }
        // Diamanten
        g.color("f3e9c9");
        for (int k = 1; k < 8; k++) {
            if (k == 4) continue;
            double z = Sim.PT + k * (Sim.PB - Sim.PT) / 8.0;
            for (double x : new double[] {Sim.PL - 31, Sim.PR + 31}) g.at(wx(x), RH + 0.005f, wz(z)).box(0.05f, 0.01f, 0.05f);
        }
        for (int k = 1; k < 4; k++) {
            double x2 = Sim.PL + k * (Sim.PR - Sim.PL) / 4.0;
            for (double z2 : new double[] {Sim.PT - 31, Sim.PB + 31}) g.at(wx(x2), RH + 0.005f, wz(z2)).box(0.05f, 0.01f, 0.05f);
        }
        // Kreide
        m.color("3b6fd4").at(wx(Sim.PR + 31), RH + 0.045f, wz(Sim.PT + 170)).rotY(0.6f).box(0.14f, 0.09f, 0.14f);
        matte = m.build();
        gloss = g.build();
    }

    // Tuchfläche zeilenweise, die Ränder weichen den Löchern aus
    private void feltSheet(Geo g) {
        double y0 = Sim.PT - 25, y1 = Sim.PB + 25;
        FloatArray rows = new FloatArray();
        rows.add((float) y0);
        rows.add((float) y1);
        for (int k = 0; k < 6; k += 2) {
            double cy = Sim.POCKETS[k][1], r = holeR(k) * 100.0;
            for (int i = 0; i <= 48; i++) {
                double y = cy - r + i * 2 * r / 48.0;
                if (y > y0 && y < y1) rows.add((float) y);
            }
        }
        rows.sort();
        int prev = -1;
        for (int i = 0; i < rows.size; i++) {
            double y = rows.get(i);
            double xl = Sim.PL - 25;
            for (int k = 0; k < 6; k += 2) {
                double[] p = Sim.POCKETS[k];
                double r = holeR(k) * 100.0, dy = y - p[1];
                if (Math.abs(dy) < r) xl = Math.max(xl, p[0] + Math.sqrt(r * r - dy * dy));
            }
            int v0 = g.vert(wx(xl), 0, wz(y), 0, 1, 0, 0, 0);
            g.vert(wx(2 * Sim.CX - xl), 0, wz(y), 0, 1, 0, 0, 0);
            if (prev >= 0) g.quadIdx(prev, prev + 1, v0 + 1, v0);
            prev = v0;
        }
    }

    private static double[][] pocketsOf(boolean vertical) {
        // Lage entlang der Kante, Abstand nach aussen, Lochradius
        return vertical
            ? new double[][] {{Sim.PT - 6, 6, 25}, {Sim.CY, 14, 21}, {Sim.PB + 6, 6, 25}}
            : new double[][] {{Sim.PL - 6, 6, 25}, {Sim.PR + 6, 6, 25}};
    }

    // Abstand der Bandennase nach aussen, -1 wo keine Bande ist
    private static double nose(boolean vertical, double a) {
        double c = vertical ? Sim.CY : Sim.CX, lo = vertical ? Sim.PT : Sim.PL;
        double mm = a <= c ? a : 2 * c - a;
        double j1 = lo + 32, j0 = j1 - 12.6;
        if (mm < j0) return -1;
        if (mm < j1) return 18 * (j1 - mm) / 12.6;
        if (!vertical || mm <= c - 22) return 0;
        if (mm <= c - 19) return 18 * (mm - (c - 22)) / 3;
        return -1;
    }

    // Hinterkante der Bande; am Loch folgt sie dem Kreis. -1 wo keine Bande ist
    private static double backOf(boolean vertical, double a, double[][] pk) {
        double n = nose(vertical, a);
        if (n < 0 || n >= 9.5) return -1;
        double b = 11;
        for (double[] p : pk) {
            double da = a - p[0], r = p[2] + 0.3;
            if (Math.abs(da) < r) {
                double sq = Math.sqrt(r * r - da * da);
                if (p[1] + sq > n && p[1] - sq < b) b = p[1] - sq;
            }
        }
        return b - n > 0.2 ? b : -1;
    }

    // Bande als durchgehendes Band von der Nase bis in den Rahmen, läuft rund um die Löcher
    private void cushionStrip(Geo g, boolean vertical, int sgn) {
        double[][] pk = pocketsOf(vertical);
        double a0 = vertical ? Sim.PT : Sim.PL, a1 = vertical ? Sim.PB : Sim.PR;
        FloatArray samples = new FloatArray();
        for (double[] p : pk) {
            for (double a = p[0] - 45; a <= p[0] + 45; a += 0.5) {
                if (a >= a0 && a <= a1) samples.add((float) a);
            }
        }
        samples.sort();
        final float top = 0.15f;
        g.at(0, 0, 0);
        for (int i = 0; i + 1 < samples.size; i++) {
            double sa = samples.get(i), sb = samples.get(i + 1);
            double ba = backOf(vertical, sa, pk), bb = backOf(vertical, sb, pk);
            if (ba < 0 || bb < 0) continue;
            double na = nose(vertical, sa), nb = nose(vertical, sb);
            float[] pa = map(vertical, sgn, sa, na), pb = map(vertical, sgn, sb, nb);
            float[] qa = map(vertical, sgn, sa, ba), qb = map(vertical, sgn, sb, bb);
            int t = g.vert(pa[0], top, pa[1], 0, 1, 0, 0, 0);
            g.vert(pb[0], top, pb[1], 0, 1, 0, 0, 0);
            g.vert(qb[0], top, qb[1], 0, 1, 0, 0, 0);
            g.vert(qa[0], top, qa[1], 0, 1, 0, 0, 0);
            g.quadIdx(t, t + 1, t + 2, t + 3);
            wall(g, vertical, sgn, pa, pb, sa, na, sb, nb, top, -1);
            if (ba < 10.9 || bb < 10.9) wall(g, vertical, sgn, qa, qb, sa, ba, sb, bb, top, 1);
            boolean first = i == 0 || backOf(vertical, samples.get(i - 1), pk) < 0;
            boolean last = i + 2 >= samples.size || backOf(vertical, samples.get(i + 2), pk) < 0;
            if (first) cap(g, vertical, sgn, pa, qa, top, -1);
            if (last) cap(g, vertical, sgn, pb, qb, top, 1);
        }
    }

    // senkrechte Fläche entlang einer Kante; side -1 zeigt zum Tisch, 1 zum Loch
    private void wall(Geo g, boolean vertical, int sgn, float[] p0, float[] p1, double a0, double c0, double a1,
            double c1, float top, int side) {
        double dl = Math.hypot(a1 - a0, c1 - c0);
        if (dl < 1e-6) return;
        double nAcross = side * (a1 - a0) / dl, nAlong = -side * (c1 - c0) / dl;
        float[] nn = dir(vertical, sgn, nAcross, nAlong);
        int w = g.vert(p0[0], top, p0[1], nn[0], 0, nn[1], 0, 0);
        g.vert(p1[0], top, p1[1], nn[0], 0, nn[1], 0, 0);
        g.vert(p1[0], 0, p1[1], nn[0], 0, nn[1], 0, 0);
        g.vert(p0[0], 0, p0[1], nn[0], 0, nn[1], 0, 0);
        g.quadIdx(w, w + 1, w + 2, w + 3);
    }

    private void cap(Geo g, boolean vertical, int sgn, float[] p, float[] q, float top, int along) {
        float[] nn = dir(vertical, sgn, 0, along);
        int c = g.vert(p[0], top, p[1], nn[0], 0, nn[1], 0, 0);
        g.vert(q[0], top, q[1], nn[0], 0, nn[1], 0, 0);
        g.vert(q[0], 0, q[1], nn[0], 0, nn[1], 0, 0);
        g.vert(p[0], 0, p[1], nn[0], 0, nn[1], 0, 0);
        g.quadIdx(c, c + 1, c + 2, c + 3);
    }

    // Ein Rahmenstück; die Innenkante weicht den Taschen kreisförmig aus
    private void frameStrip(Geo g, boolean vertical, int sgn) {
        final double inner = 10.0, outer = 52.0;
        double a0 = vertical ? Sim.PT - outer : Sim.PL - inner;
        double a1 = vertical ? Sim.PB + outer : Sim.PR + inner;
        double[][] pk = pocketsOf(vertical);
        FloatArray samples = new FloatArray();
        samples.add((float) a0);
        samples.add((float) a1);
        for (double[] p : pk) {
            for (int i = 0; i <= 44; i++) {
                double a = p[0] - p[2] - 2 + i * (2 * p[2] + 4) / 44.0;
                if (a > a0 && a < a1) samples.add((float) a);
            }
        }
        samples.sort();
        float top = RH, lowOut = -0.46f, lowIn = 0f;
        int prev = -1;
        for (int i = 0; i < samples.size; i++) {
            double a = samples.get(i);
            double off = inner;
            double nx = -1, ny = 0;
            boolean cut = false;
            for (double[] p : pk) {
                double da = a - p[0], r = p[2];
                // auch die Übergänge neben dem Loch dunkel färben
                if (Math.abs(da) < r + 4) cut = true;
                if (Math.abs(da) < r) {
                    double o = p[1] + Math.sqrt(r * r - da * da);
                    if (o > off) {
                        off = o;
                        cut = true;
                        // Normale zeigt zur Taschenmitte
                        double rx = p[1] - o, ry = -da, l = Math.hypot(rx, ry);
                        nx = rx / l;
                        ny = ry / l;
                    }
                }
            }
            // lokale Koordinaten: across = Abstand nach aussen, along = a
            float[] pi = map(vertical, sgn, a, off), po = map(vertical, sgn, a, outer);
            float[] nIn = dir(vertical, sgn, nx, ny), nOut = dir(vertical, sgn, 1, 0);
            g.at(0, 0, 0);
            int base = g.vert(po[0], top, po[1], 0, 1, 0, 0, 0);
            g.vert(pi[0], top, pi[1], 0, 1, 0, 0, 0);
            g.vert(po[0], top, po[1], nOut[0], 0, nOut[1], 0, 0);
            g.vert(po[0], lowOut, po[1], nOut[0], 0, nOut[1], 0, 0);
            if (cut) g.color(0.05f, 0.04f, 0.032f);
            g.vert(pi[0], top, pi[1], nIn[0], 0, nIn[1], 0, 0);
            if (cut) g.lit(false).color(0.012f, 0.012f, 0.012f);
            g.vert(pi[0], lowIn, pi[1], nIn[0], 0, nIn[1], 0, 0);
            g.lit(true).color("6e3b1b");
            if (prev >= 0) {
                g.quadIdx(prev, prev + 1, base + 1, base);
                g.quadIdx(prev + 2, prev + 3, base + 3, base + 2);
                g.quadIdx(prev + 4, prev + 5, base + 5, base + 4);
            }
            prev = base;
        }
    }

    private static float[] map(boolean vertical, int sgn, double along, double across) {
        if (vertical) return new float[] {wx(Sim.CX - sgn * (across + (Sim.CX - Sim.PL))), wz(along)};
        return new float[] {wx(along), wz(Sim.CY - sgn * (across + (Sim.CY - Sim.PT)))};
    }

    private static float[] dir(boolean vertical, int sgn, double across, double along) {
        // across zeigt in der Grundform nach links bzw. oben (weg vom Tisch)
        if (vertical) return new float[] {(float) (-sgn * across), (float) along};
        return new float[] {(float) along, (float) (-sgn * across)};
    }

    private void buildLamp() {
        Geo g = new Geo();
        g.color("0f4d34").at(0, 3.3f, 0).box(1.0f, 0.26f, 4.3f);
        g.lit(false).color("fff2c8").at(0, 3.16f, 0).box(0.88f, 0.02f, 4.1f);
        g.lit(true).color("222222");
        g.at(0, 4.9f, -1.5f).box(0.024f, 3.0f, 0.024f);
        g.at(0, 4.9f, 1.5f).box(0.024f, 3.0f, 0.024f);
        g.color("4a3a2c").at(0, 6.35f, 0).box(26.4f, 0.2f, 26.4f);
        lamp = g.build();
    }

    private void buildStick() {
        Geo g = new Geo();
        g.color("4a90d9").frustumX(0, 0.017f, 0.06f, 0.017f, 14, true);
        g.color("deb887").frustumX(0.06f, 0.017f, 2.06f, 0.034f, 14, false);
        g.color("2a160b").frustumX(2.06f, 0.034f, 3.26f, 0.043f, 14, true);
        stick = g.build();
    }

    // ---------- state ----------

    void resetBalls(Sim sim) {
        count = sim.n;
        for (int i = 0; i < 16; i++) {
            fall[i] = -1;
            shown[i] = i < sim.n;
            if (i < sim.n) {
                nums[i] = sim.num[i];
                lastX[i] = (float) sim.px[i];
                lastY[i] = (float) sim.py[i];
                rot[i].setEulerAnglesRad(MathUtils.random(MathUtils.PI2), MathUtils.random(MathUtils.PI2),
                    MathUtils.random(MathUtils.PI2));
            }
        }
    }

    void sync(Sim sim, float delta) {
        for (int i = 0; i < sim.n; i++) {
            float x = (float) sim.px[i], y = (float) sim.py[i];
            if (sim.alive[i]) {
                if (fall[i] >= 0) {
                    fall[i] = -1;
                    lastX[i] = x;
                    lastY[i] = y;
                }
                shown[i] = true;
                float dx = x - lastX[i], dy = y - lastY[i];
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l > 0.001f) {
                    tq.setFromAxisRad(dy / l, 0, -dx / l, l / (float) Sim.BR);
                    rot[i].mulLeft(tq).nor();
                }
                bx[i] = x;
                by[i] = y;
                bh[i] = BR;
                bs[i] = 1;
                lastX[i] = x;
                lastY[i] = y;
            } else if (shown[i]) {
                if (fall[i] < 0) fall[i] = 0;
                float f = Math.min(1f, fall[i] + delta * 2.6f);
                fall[i] = f;
                double[] pk = Sim.POCKETS[sim.pocketOf[sim.num[i]]];
                float k = Math.min(1f, f * 1.6f);
                bx[i] = lastX[i] + ((float) pk[0] - lastX[i]) * k;
                by[i] = lastY[i] + ((float) pk[1] - lastY[i]) * k;
                bh[i] = BR - f * 0.45f;
                bs[i] = 1f - f * 0.2f;
                if (f >= 1f) shown[i] = false;
            }
        }
    }

    void showStick(boolean on, double cx, double cy, double angle, double pull) {
        stickOn = on;
        if (!on) return;
        float px = wx(cx - Math.cos(angle) * pull), pz = wz(cy - Math.sin(angle) * pull);
        stickTipX = px * 1.03f;
        stickTipZ = pz * 1.02f;
        stickDirX = (float) -Math.cos(angle);
        stickDirZ = (float) -Math.sin(angle);
        stickM.idt().translate(px, BR + 0.012f, pz).rotateRad(Vector3.Y, MathUtils.PI - (float) angle)
            .rotateRad(Vector3.Z, 4 * MathUtils.degreesToRadians);
    }

    void hideGuides() {
        lines.clear();
        ghostOn = false;
    }

    private void seg(double ax, double ay, double bx2, double by2, float r, float g, float b, float a) {
        if (Math.hypot(bx2 - ax, by2 - ay) <= 1.0) return;
        lines.addAll((float) ax, (float) ay, (float) bx2, (float) by2);
        lines.addAll(r, g, b, a);
    }

    // Zielhilfe: Weg bis zur ersten Kugel oder Bande, Laufrichtung der Kugel und Abpraller
    void showGuides(Sim sim, double angle) {
        lines.clear();
        double cx = sim.px[0], cy = sim.py[0];
        double dx = Math.cos(angle), dy = Math.sin(angle);
        double tm = 3000.0, br = Sim.BR;
        int hit = -1;
        for (int i = 1; i < sim.n; i++) {
            if (!sim.alive[i]) continue;
            double fx = cx - sim.px[i], fy = cy - sim.py[i];
            double bq = fx * dx + fy * dy;
            double disc = bq * bq - (fx * fx + fy * fy - 4 * br * br);
            if (disc >= 0) {
                double t = -bq - Math.sqrt(disc);
                if (t > 0 && t < tm) {
                    tm = t;
                    hit = i;
                }
            }
        }
        double wt = 3000.0;
        if (dx > 1e-6) wt = Math.min(wt, (Sim.PR - br - cx) / dx);
        else if (dx < -1e-6) wt = Math.min(wt, (Sim.PL + br - cx) / dx);
        if (dy > 1e-6) wt = Math.min(wt, (Sim.PB - br - cy) / dy);
        else if (dy < -1e-6) wt = Math.min(wt, (Sim.PT + br - cy) / dy);
        double sx = cx + dx * br, sy = cy + dy * br;
        ghostOn = true;
        if (hit >= 0 && tm < wt) {
            double ex = cx + dx * tm, ey = cy + dy * tm;
            seg(sx, sy, ex, ey, 1, 1, 1, 0.85f);
            ghostX = (float) ex;
            ghostY = (float) ey;
            double hx = sim.px[hit], hy = sim.py[hit];
            double nl = Math.hypot(hx - ex, hy - ey);
            double nx = (hx - ex) / nl, ny = (hy - ey) / nl;
            seg(hx, hy, hx + nx * 100, hy + ny * 100, 1f, 0.9f, 0.47f, 1f);
            double dot = dx * nx + dy * ny;
            double tx = dx - nx * dot, ty = dy - ny * dot;
            double tlen = Math.hypot(tx, ty);
            if (tlen > 0.02) seg(ex, ey, ex + tx / tlen * 70 * tlen, ey + ty / tlen * 70 * tlen, 0.56f, 0.83f, 1f, 1f);
        } else {
            double ex = cx + dx * wt, ey = cy + dy * wt;
            seg(sx, sy, ex, ey, 1, 1, 1, 0.85f);
            ghostX = (float) ex;
            ghostY = (float) ey;
            double rx = dx, ry = dy;
            if (ex >= Sim.PR - br - 0.1 || ex <= Sim.PL + br + 0.1) rx = -rx;
            else ry = -ry;
            seg(ex, ey, ex + rx * 180, ey + ry * 180, 1, 1, 1, 0.5f);
        }
    }

    void updateCamera(boolean followCue, double cx, double cy, double angle, float delta) {
        follow = followCue;
        float aspect = Gdx.graphics.getWidth() / (float) Math.max(Gdx.graphics.getHeight(), 1);
        cam.viewportWidth = Gdx.graphics.getWidth();
        cam.viewportHeight = Gdx.graphics.getHeight();
        if (fixedCam != null) {
            cam.position.set(fixedCam[0], fixedCam[1], fixedCam[2]);
            look.set(fixedCam[3], fixedCam[4], fixedCam[5]);
        } else {
            if (followCue) {
                float dx = (float) Math.cos(angle), dz = (float) Math.sin(angle);
                float x = wx(cx), z = wz(cy);
                tp.set(x - dx * 2.5f, 1.75f, z - dz * 2.5f);
                tl.set(x + dx * 2.0f, 0, z + dz * 2.0f);
            } else {
                float h = Math.max(14f, 7.3f / (0.93f * aspect));
                tp.set(0, h, 0.7f);
                tl.set(0, 0, 0.15f);
            }
            cam.position.lerp(tp, 1f - (float) Math.exp(-delta * 6.0));
            look.lerp(tl, 1f - (float) Math.exp(-delta * 7.0));
        }
        cam.direction.set(look).sub(cam.position).nor();
        tp.set(cam.direction).crs(Vector3.Y).nor();
        cam.up.set(tp).crs(cam.direction).nor();
        cam.update();
    }

    // Bildschirmpunkt auf die Ebene der Kugelmitten, oder null
    double[] screenToTable(float sx, float sy) {
        Ray r = cam.getPickRay(sx, sy);
        if (r.direction.y >= 0) return null;
        float t = (BR - r.origin.y) / r.direction.y;
        return new double[] {(r.origin.x + r.direction.x * t) / S + Sim.CX, (r.origin.z + r.direction.z * t) / S + Sim.CY};
    }

    // ---------- drawing ----------

    void render(float dim) {
        GL20 gl = Gdx.gl;
        // Niedrig: Szene kleiner rendern und hochskalieren
        boolean low = quality == 0;
        if (low) {
            int fw = Math.max(1, Math.round(Gdx.graphics.getBackBufferWidth() * 0.7f));
            int fh = Math.max(1, Math.round(Gdx.graphics.getBackBufferHeight() * 0.7f));
            if (fbo == null || fbo.getWidth() != fw || fbo.getHeight() != fh) {
                if (fbo != null) fbo.dispose();
                fbo = new com.badlogic.gdx.graphics.glutils.FrameBuffer(
                    com.badlogic.gdx.graphics.Pixmap.Format.RGB888, fw, fh, true);
                fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
            fbo.begin();
        }
        gl.glClearColor(0.165f, 0.13f, 0.094f, 1);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL20.GL_DEPTH_TEST);
        gl.glDepthFunc(GL20.GL_LEQUAL);
        gl.glDepthMask(true);
        gl.glDisable(GL20.GL_CULL_FACE);
        gl.glDisable(GL20.GL_BLEND);
        shader.bind();
        shader.setUniformMatrix("u_projView", cam.combined);
        shader.setUniformMatrix("u_world", ident);
        shader.setUniformf("u_cam", cam.position);
        shader.setUniformi("u_tex", 0);
        shader.setUniformi("u_noise", 1);
        noise.bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformf("u_useTex", 0f);
        shader.setUniformf("u_spec", 0f);
        shader.setUniformf("u_shine", 24f);
        white.bind(0);
        matte.render(shader, GL20.GL_TRIANGLES);
        shader.setUniformf("u_spec", quality == 2 ? 0.45f : 0f);
        gloss.render(shader, GL20.GL_TRIANGLES);
        if (follow || fixedCam != null) {
            shader.setUniformf("u_spec", 0f);
            lamp.render(shader, GL20.GL_TRIANGLES);
        }

        // weiche Schatten und Linien auf dem Tuch
        gl.glEnable(GL20.GL_BLEND);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        // Schatten etwas zur Kamera ziehen, sonst flackern sie mit dem Tuch
        gl.glEnable(GL20.GL_POLYGON_OFFSET_FILL);
        gl.glPolygonOffset(-2f, -4f);
        imm.begin(cam.combined, GL20.GL_TRIANGLES);
        for (int i = 0; i < count && quality > 0; i++) {
            if (!shown[i] || bh[i] < BR * 0.5f) continue;
            float x = wx(bx[i]), z = wz(by[i]);
            if (quality == 2) {
                blob(x + x * 0.07f, z + z * 0.05f, BR * 2.3f, 0.42f);
                blob(x + x * 0.02f, z + z * 0.015f, BR * 1.15f, 0.75f);
            } else {
                blob(x + x * 0.05f, z + z * 0.035f, BR * 1.55f, 0.55f);
            }
        }
        if (stickOn && quality > 0) {
            // Schatten des Queues auf dem Tuch
            float a = quality == 2 ? 0.3f : 0.18f;
            quadLine(stickTipX, stickTipZ, stickTipX + stickDirX * 3.2f, stickTipZ + stickDirZ * 3.2f, 0.035f, 0.0035f, 0, 0, 0, a);
        }
        if (headLine) quadLine(wx(Sim.PL), wz(Sim.HEAD_Y), wx(Sim.PR), wz(Sim.HEAD_Y), 0.006f, 0.003f, 1, 1, 1, 0.35f);
        for (int i = 0; i + 7 < lines.size; i += 8) {
            quadLine(wx(lines.get(i)), wz(lines.get(i + 1)), wx(lines.get(i + 2)), wz(lines.get(i + 3)), 0.008f, 0.014f,
                lines.get(i + 4), lines.get(i + 5), lines.get(i + 6), lines.get(i + 7));
        }
        if (ghostOn) ring(wx(ghostX), wz(ghostY), BR - 0.012f, BR, 0.016f);
        imm.end();
        gl.glDisable(GL20.GL_POLYGON_OFFSET_FILL);
        gl.glDepthMask(true);
        gl.glDisable(GL20.GL_BLEND);

        shader.bind();
        shader.setUniformf("u_useTex", 1f);
        shader.setUniformf("u_spec", quality == 2 ? 1.0f : (quality == 1 ? 0.6f : 0f));
        shader.setUniformf("u_shine", quality == 2 ? 110f : 60f);
        for (int i = 0; i < count; i++) {
            if (!shown[i]) continue;
            world.idt().translate(wx(bx[i]), bh[i], wz(by[i])).rotate(rot[i]).scale(bs[i], bs[i], bs[i]);
            shader.setUniformMatrix("u_world", world);
            ballTex[nums[i]].bind(0);
            sphere.render(shader, GL20.GL_TRIANGLES);
        }
        if (stickOn) {
            white.bind(0);
            shader.setUniformf("u_useTex", 0f);
            shader.setUniformf("u_spec", quality == 0 ? 0f : 0.25f);
            shader.setUniformf("u_shine", 30f);
            shader.setUniformMatrix("u_world", stickM);
            stick.render(shader, GL20.GL_TRIANGLES);
        }
        gl.glDisable(GL20.GL_DEPTH_TEST);
        if (low) {
            fbo.end();
            if (blit == null) blit = new com.badlogic.gdx.graphics.g2d.SpriteBatch(4);
            blit.getProjectionMatrix().setToOrtho2D(0, 0, 1, 1);
            blit.disableBlending();
            blit.begin();
            blit.draw(fbo.getColorBufferTexture(), 0, 0, 1, 1, 0, 1, 1, 0);
            blit.end();
        }
    }

    private void blob(float x, float z, float rad, float alpha) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            float a0 = MathUtils.PI2 * i / seg, a1 = MathUtils.PI2 * (i + 1) / seg;
            imm.color(0, 0, 0, alpha);
            imm.vertex(x, 0.004f, z);
            imm.color(0, 0, 0, 0);
            imm.vertex(x + MathUtils.cos(a0) * rad, 0.004f, z + MathUtils.sin(a0) * rad);
            imm.color(0, 0, 0, 0);
            imm.vertex(x + MathUtils.cos(a1) * rad, 0.004f, z + MathUtils.sin(a1) * rad);
        }
    }

    private void quadLine(float x0, float z0, float x1, float z1, float half, float y, float r, float g, float b, float a) {
        float dx = x1 - x0, dz = z1 - z0;
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-5f) return;
        float nx = -dz / l * half, nz = dx / l * half;
        float[] p = {x0 + nx, z0 + nz, x1 + nx, z1 + nz, x1 - nx, z1 - nz, x0 - nx, z0 - nz};
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int k : order) {
            imm.color(r, g, b, a);
            imm.vertex(p[k * 2], y, p[k * 2 + 1]);
        }
    }

    private void ring(float x, float z, float r0, float r1, float y) {
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            float a0 = MathUtils.PI2 * i / seg, a1 = MathUtils.PI2 * (i + 1) / seg;
            float c0 = MathUtils.cos(a0), s0 = MathUtils.sin(a0), c1 = MathUtils.cos(a1), s1 = MathUtils.sin(a1);
            float[] p = {x + c0 * r0, z + s0 * r0, x + c0 * r1, z + s0 * r1, x + c1 * r1, z + s1 * r1, x + c1 * r0, z + s1 * r0};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int k : order) {
                imm.color(1, 1, 1, 0.9f);
                imm.vertex(p[k * 2], y, p[k * 2 + 1]);
            }
        }
    }

    void dispose() {
        for (ShaderProgram p : shaders) p.dispose();
        noise.dispose();
        matte.dispose();
        gloss.dispose();
        lamp.dispose();
        sphere.dispose();
        stick.dispose();
        white.dispose();
        imm.dispose();
        if (fbo != null) fbo.dispose();
        if (blit != null) blit.dispose();
        for (Texture t : ballTex) t.dispose();
    }
}
