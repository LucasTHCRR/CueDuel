package dev.paperstream.cueduel;

import java.util.Random;

public final class Sim {
    public static final double PL = 34, PT = 34, PR = 486, PB = 966, CX = 260, CY = 500, BR = 12;
    public static final double MAXV = 26;
    static final int STEPS = 6;
    static final double DRAG = 0.991, DECEL = 0.013, STOP_V = 0.04, CUSHION_E = 0.8, BALL_E = 0.95;
    static final double CORNER_GAP = 32, SIDE_GAP = 22, JAW = 18, POT_DEPTH = 6;
    public static final double HEAD_Y = 733, FOOT_X = CX, FOOT_Y = 330, CUE_X = CX, CUE_Y = 780;
    public static final int EV_BALL = 0, EV_RAIL = 1, EV_POT = 2;

    public static final double[][] POCKETS = {
        {PL - 6, PT - 6}, {PR + 6, PT - 6}, {PL - 14, CY}, {PR + 14, CY}, {PL - 6, PB + 6}, {PR + 6, PB + 6}};
    public static final double[][] AIM_POINTS = {
        {PL + 5, PT + 5}, {PR - 5, PT + 5}, {PL - 4, CY}, {PR + 4, CY}, {PL + 5, PB - 5}, {PR - 5, PB - 5}};
    // ax, ay, bx, by
    public static final double[][] SEGS = buildSegments();

    public int n;
    public final int[] num = new int[16];
    public final double[] px = new double[16], py = new double[16], vx = new double[16], vy = new double[16];
    public final boolean[] alive = new boolean[16];
    public int firstHit = -1;
    public boolean railAfter;
    public final int[] potted = new int[16];
    public int pottedCount;
    public final int[] pocketOf = new int[16];
    public final int[] evType = new int[64];
    public final float[] evVal = new float[64];
    public int evCount;
    public boolean recordEvents = true;

    private static double[][] buildSegments() {
        double cg = CORNER_GAP, sg = SIDE_GAP;
        double[][] s = new double[18][];
        int k = 0;
        s[k++] = new double[] {PL + cg, PT, PR - cg, PT};
        s[k++] = new double[] {PL + cg, PB, PR - cg, PB};
        for (double x0 : new double[] {PL, PR}) {
            s[k++] = new double[] {x0, PT + cg, x0, CY - sg};
            s[k++] = new double[] {x0, CY + sg, x0, PB - cg};
        }
        double[][] corners = {{PL, PT, 1, 1}, {PR, PT, -1, 1}, {PL, PB, 1, -1}, {PR, PB, -1, -1}};
        for (double[] c : corners) {
            double cx = c[0], cy = c[1], sx = c[2], sy = c[3];
            s[k++] = new double[] {cx + sx * cg, cy, cx + sx * (cg - JAW * 0.7), cy - sy * JAW};
            s[k++] = new double[] {cx, cy + sy * cg, cx - sx * JAW, cy + sy * (cg - JAW * 0.7)};
        }
        double[][] sides = {{PL, -1}, {PR, 1}};
        for (double[] e : sides) {
            double x1 = e[0], d = e[1];
            s[k++] = new double[] {x1, CY - sg, x1 + d * JAW, CY - sg + 3};
            s[k++] = new double[] {x1, CY + sg, x1 + d * JAW, CY + sg - 3};
        }
        return s;
    }

    // Dreieck mit der 8 in der Mitte, hinten je eine Volle und eine Halbe
    public static int[] rackOrder(Random rng) {
        int[] rest = {1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15};
        for (int i = rest.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = rest[i];
            rest[i] = rest[j];
            rest[j] = t;
        }
        int[] out = new int[15];
        for (int i = 0, r = 0; i < 15; i++) out[i] = i == 4 ? 8 : rest[r++];
        if ((out[10] < 8) == (out[14] < 8)) {
            for (int k = 0; k < 14; k++) {
                if (k == 4 || k == 10) continue;
                if ((out[k] < 8) != (out[14] < 8)) {
                    int t = out[10];
                    out[10] = out[k];
                    out[k] = t;
                    break;
                }
            }
        }
        return out;
    }

    public void setup(int[] nums) {
        n = 0;
        add(0, CUE_X, CUE_Y);
        int i = 0;
        double rowH = BR * Math.sqrt(3.0) + 0.6;
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r <= c; r++) {
                add(nums[i], FOOT_X + (r - c / 2.0) * (2 * BR + 0.6), FOOT_Y - c * rowH);
                i++;
            }
        }
        for (int k = 0; k < 16; k++) pocketOf[k] = 0;
        clearShot();
    }

    private void add(int number, double x, double y) {
        num[n] = number;
        px[n] = x;
        py[n] = y;
        vx[n] = 0;
        vy[n] = 0;
        alive[n] = true;
        n++;
    }

    public Sim copy() {
        Sim s = new Sim();
        s.n = n;
        System.arraycopy(num, 0, s.num, 0, 16);
        System.arraycopy(px, 0, s.px, 0, 16);
        System.arraycopy(py, 0, s.py, 0, 16);
        System.arraycopy(vx, 0, s.vx, 0, 16);
        System.arraycopy(vy, 0, s.vy, 0, 16);
        System.arraycopy(alive, 0, s.alive, 0, 16);
        System.arraycopy(pocketOf, 0, s.pocketOf, 0, 16);
        s.recordEvents = false;
        return s;
    }

    public int indexOf(int number) {
        for (int i = 0; i < n; i++) if (num[i] == number) return i;
        return -1;
    }

    public boolean isAlive(int number) {
        int i = indexOf(number);
        return i >= 0 && alive[i];
    }

    public int groupLeft(int group) {
        int c = 0;
        for (int i = 0; i < n; i++) if (alive[i] && Rules.groupOf(num[i]) == group) c++;
        return c;
    }

    public boolean freeSpot(double x, double y) {
        for (int i = 1; i < n; i++) {
            if (!alive[i]) continue;
            double dx = px[i] - x, dy = py[i] - y;
            if (Math.sqrt(dx * dx + dy * dy) <= 2 * BR + 1) return false;
        }
        return true;
    }

    public boolean validCuePos(double x, double y, boolean kitchen) {
        if (x < PL + BR || x > PR - BR || y < PT + BR || y > PB - BR) return false;
        if (kitchen && y < HEAD_Y) return false;
        return freeSpot(x, y);
    }

    public void placeCue(double x, double y) {
        px[0] = x;
        py[0] = y;
        vx[0] = 0;
        vy[0] = 0;
        alive[0] = true;
    }

    public void respot(int number) {
        int i = indexOf(number);
        double y = FOOT_Y;
        while (y > PT + BR && !freeSpot(FOOT_X, y)) y -= 1.0;
        px[i] = FOOT_X;
        py[i] = y;
        vx[i] = 0;
        vy[i] = 0;
        alive[i] = true;
    }

    private void clearShot() {
        firstHit = -1;
        railAfter = false;
        pottedCount = 0;
        evCount = 0;
    }

    public boolean pottedHas(int number) {
        for (int i = 0; i < pottedCount; i++) if (potted[i] == number) return true;
        return false;
    }

    // StrictMath, damit beide Handys im Online-Spiel gleich rechnen
    public void shoot(double angle, double power) {
        clearShot();
        vx[0] = StrictMath.cos(angle) * power * MAXV;
        vy[0] = StrictMath.sin(angle) * power * MAXV;
    }

    public boolean moving() {
        for (int i = 0; i < n; i++) if (alive[i] && (vx[i] != 0.0 || vy[i] != 0.0)) return true;
        return false;
    }

    private void event(int type, double v) {
        if (recordEvents && evCount < evType.length) {
            evType[evCount] = type;
            evVal[evCount] = (float) v;
            evCount++;
        }
    }

    public void step() {
        double r2 = 4.0 * BR * BR;
        for (int s = 0; s < STEPS; s++) {
            for (int i = 0; i < n; i++) {
                if (alive[i]) {
                    px[i] += vx[i] / STEPS;
                    py[i] += vy[i] / STEPS;
                }
            }
            for (int i = 0; i < n; i++) {
                if (!alive[i]) continue;
                double x = px[i], y = py[i];
                if (x < PL - POT_DEPTH || x > PR + POT_DEPTH || y < PT - POT_DEPTH || y > PB + POT_DEPTH) {
                    pot(i);
                    continue;
                }
                if (x > PL + BR + 1 && x < PR - BR - 1 && y > PT + BR + 1 && y < PB - BR - 1) continue;
                if (overPocket(x, y)) {
                    pot(i);
                    continue;
                }
                cushions(i);
            }
            for (int i = 0; i < n; i++) {
                if (!alive[i]) continue;
                for (int j = i + 1; j < n; j++) {
                    if (!alive[j]) continue;
                    double dx = px[j] - px[i];
                    if (dx >= 2 * BR || dx <= -2 * BR) continue;
                    double dy = py[j] - py[i];
                    double d2 = dx * dx + dy * dy;
                    if (d2 >= r2 || d2 == 0.0) continue;
                    double d = Math.sqrt(d2);
                    double nx = dx / d, ny = dy / d;
                    double push = (2 * BR - d) / 2;
                    px[i] -= nx * push;
                    py[i] -= ny * push;
                    px[j] += nx * push;
                    py[j] += ny * push;
                    double vn = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny;
                    if (vn > 0.0) {
                        double k = vn * (1.0 + BALL_E) / 2.0;
                        vx[i] -= nx * k;
                        vy[i] -= ny * k;
                        vx[j] += nx * k;
                        vy[j] += ny * k;
                        event(EV_BALL, vn);
                        if (firstHit < 0) {
                            if (num[i] == 0) firstHit = num[j];
                            else if (num[j] == 0) firstHit = num[i];
                        }
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (!alive[i]) continue;
            double sp = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
            if (sp == 0.0) continue;
            double ns = sp * DRAG - DECEL;
            if (ns < STOP_V) {
                vx[i] = 0;
                vy[i] = 0;
            } else {
                vx[i] *= ns / sp;
                vy[i] *= ns / sp;
            }
        }
    }

    private boolean overPocket(double x, double y) {
        for (int k = 0; k < 6; k++) {
            double r = (k == 2 || k == 3) ? 16.0 : 20.0;
            double dx = x - POCKETS[k][0], dy = y - POCKETS[k][1];
            if (dx * dx + dy * dy < r * r) return true;
        }
        return false;
    }

    private void cushions(int i) {
        double cx = px[i], cy = py[i];
        for (double[] s : SEGS) {
            double abx = s[2] - s[0], aby = s[3] - s[1];
            double t = ((cx - s[0]) * abx + (cy - s[1]) * aby) / (abx * abx + aby * aby);
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double qx = s[0] + abx * t, qy = s[1] + aby * t;
            double dx = cx - qx, dy = cy - qy;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d >= BR || d == 0.0) continue;
            double nx = dx / d, ny = dy / d;
            cx = qx + nx * BR;
            cy = qy + ny * BR;
            double vn = vx[i] * nx + vy[i] * ny;
            if (vn < 0.0) {
                vx[i] -= nx * vn * (1.0 + CUSHION_E);
                vy[i] -= ny * vn * (1.0 + CUSHION_E);
                if (firstHit >= 0) railAfter = true;
                event(EV_RAIL, -vn);
            }
        }
        px[i] = cx;
        py[i] = cy;
    }

    private void pot(int i) {
        alive[i] = false;
        vx[i] = 0;
        vy[i] = 0;
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            double dx = POCKETS[k][0] - px[i], dy = POCKETS[k][1] - py[i];
            double d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        pocketOf[num[i]] = best;
        potted[pottedCount++] = num[i];
        event(EV_POT, 1.0);
    }

    public int runToRest(int maxFrames) {
        int f = 0;
        while (moving() && f < maxFrames) {
            step();
            f++;
        }
        return f;
    }
}
