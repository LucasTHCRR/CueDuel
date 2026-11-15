package de.paperstream.cueduel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class AI {
    // n, pn, miss, pick, place, look
    private static final double[][] LEVELS = {
        {0.03, 0.3, 0.3, 99, 1, 0},
        {0.012, 0.15, 0.1, 3, 20, 0},
        {0.005, 0.06, 0.02, 1, 50, 1},
        {0.0018, 0.025, 0.0, 1, 100, 1}};

    public static final class Result {
        public boolean place;
        public double placeX, placeY, a, p;
    }

    public static int[] targets(Sim sim, int group) {
        int[] tmp = new int[16];
        int c = 0;
        for (int i = 0; i < sim.n; i++) {
            int n = sim.num[i];
            if (!sim.alive[i] || n == 0) continue;
            if (group == Rules.NONE && n != 8) tmp[c++] = i;
            else if (group != Rules.NONE && Rules.groupOf(n) == group) tmp[c++] = i;
        }
        if (c == 0) {
            int e = sim.indexOf(8);
            if (e >= 0 && sim.alive[e]) tmp[c++] = e;
        }
        int[] out = new int[c];
        System.arraycopy(tmp, 0, out, 0, c);
        return out;
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double abx = bx - ax, aby = by - ay;
        double l = abx * abx + aby * aby;
        if (l == 0.0) return Math.hypot(px - ax, py - ay);
        double t = ((px - ax) * abx + (py - ay) * aby) / l;
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return Math.hypot(px - (ax + abx * t), py - (ay + aby * t));
    }

    // Kandidaten [score, angle, power], bester zuerst
    public static List<double[]> plan(Sim sim, double cx, double cy, int[] tg) {
        List<double[]> out = new ArrayList<>();
        double br = Sim.BR;
        for (int ti : tg) {
            double tx = sim.px[ti], ty = sim.py[ti];
            for (double[] pk : Sim.AIM_POINTS) {
                double tpx = pk[0] - tx, tpy = pk[1] - ty;
                double d2 = Math.hypot(tpx, tpy);
                if (d2 < 1.0) continue;
                double dx = tpx / d2, dy = tpy / d2;
                double gx = tx - dx * 2 * br, gy = ty - dy * 2 * br;
                if (gx < Sim.PL + br || gx > Sim.PR - br || gy < Sim.PT + br || gy > Sim.PB - br) continue;
                double tgx = gx - cx, tgy = gy - cy;
                double d1 = Math.hypot(tgx, tgy);
                if (d1 < 0.001) continue;
                double cut = (tgx * dx + tgy * dy) / d1;
                if (cut < 0.25) continue;
                boolean blocked = false;
                for (int i = 1; i < sim.n; i++) {
                    if (i == ti || !sim.alive[i]) continue;
                    double ox = sim.px[i], oy = sim.py[i];
                    if (segDist(ox, oy, cx, cy, gx, gy) < 2 * br - 1 || segDist(ox, oy, tx, ty, pk[0], pk[1]) < 2 * br - 1) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) continue;
                double score = cut * 100.0 - (d1 + d2) * 0.05;
                double power = clamp(0.22 + (d1 + d2) / 1100.0 / Math.max(cut, 0.5), 0.3, 1.0);
                out.add(new double[] {score, StrictMath.atan2(tgy, tgx), power});
            }
        }
        out.sort((a, b) -> Double.compare(b[0], a[0]));
        return out;
    }

    static double lookAhead(Sim sim, int group, boolean onEight, double a, double p) {
        Sim s = sim.copy();
        s.shoot(a, p);
        s.runToRest(900);
        Rules.Judge j = Rules.judge(s, group, onEight, false);
        if (j.eight) return (!j.foul && onEight) ? 1000.0 : -1000.0;
        if (j.foul) return -300.0;
        int g = group;
        if (g == Rules.NONE && j.pots.length > 0) g = Rules.groupOf(j.pots[0]);
        boolean keeps = (group != Rules.NONE && j.own > 0) || (group == Rules.NONE && j.pots.length > 0);
        if (!keeps) return -100.0;
        List<double[]> nxt = plan(s, s.px[0], s.py[0], targets(s, g));
        return 200.0 + (nxt.isEmpty() ? -80.0 : nxt.get(0)[0]);
    }

    public static Result think(Sim sim, int group, boolean onEight, boolean hand, boolean kitchen, boolean isBreak,
            int level, long seed) {
        Random rng = new Random(seed);
        double[] P = LEVELS[Math.max(1, Math.min(4, level)) - 1];
        Result res = new Result();
        int[] tg = targets(sim, group);
        if (hand) {
            double best = -1e9, bx = 0, by = 0;
            boolean found = false;
            int tries = isBreak ? 1 : (int) P[4];
            int i = 0;
            while (i < 200 && (i < tries || !found)) {
                i++;
                double x = range(rng, Sim.PL + 20, Sim.PR - 20);
                double y = range(rng, kitchen ? Sim.HEAD_Y + 10 : Sim.PT + 20, Sim.PB - 20);
                if (isBreak) x = Sim.CX + range(rng, -60, 60);
                if (!sim.validCuePos(x, y, kitchen)) continue;
                List<double[]> l = plan(sim, x, y, tg);
                double sc = l.isEmpty() ? -500.0 : l.get(0)[0];
                if (!found || sc > best) {
                    best = sc;
                    bx = x;
                    by = y;
                    found = true;
                }
            }
            if (!found) {
                bx = Sim.CUE_X;
                by = Sim.CUE_Y;
            }
            sim.placeCue(bx, by);
            res.place = true;
            res.placeX = bx;
            res.placeY = by;
        }
        double cx = sim.px[0], cy = sim.py[0];
        double a = 0, pw = 0;
        if (isBreak) {
            a = StrictMath.atan2(Sim.FOOT_Y - cy, Sim.FOOT_X - cx) + rng.nextGaussian() * 0.01;
            pw = 1.0;
        } else {
            List<double[]> l = plan(sim, cx, cy, tg);
            if (!l.isEmpty() && rng.nextDouble() >= P[2]) {
                if (P[5] > 0) {
                    double bestV = -1e9;
                    for (int k = 0; k < Math.min(4, l.size()); k++) {
                        double[] c = l.get(k);
                        for (double f : new double[] {0.75, 1.0, 1.3}) {
                            double p2 = clamp(c[2] * f, 0.2, 1.0);
                            double v = lookAhead(sim, group, onEight, c[1], p2) + c[0] * 0.3;
                            if (v > bestV) {
                                bestV = v;
                                a = c[1];
                                pw = p2;
                            }
                        }
                    }
                } else {
                    double[] c2 = l.get(rng.nextInt(Math.min((int) P[3], l.size())));
                    a = c2[1];
                    pw = c2[2];
                }
            } else if (tg.length > 0) {
                int ti = tg[rng.nextInt(tg.length)];
                a = StrictMath.atan2(sim.py[ti] - cy, sim.px[ti] - cx) + range(rng, -0.15, 0.15);
                pw = range(rng, 0.3, 0.8);
            } else {
                pw = 0.5;
            }
        }
        res.a = a + rng.nextGaussian() * P[0];
        res.p = clamp(pw * (1.0 + rng.nextGaussian() * P[1]), 0.15, 1.0);
        return res;
    }

    private static double range(Random r, double a, double b) {
        return a + r.nextDouble() * (b - a);
    }

    static double clamp(double v, double a, double b) {
        return v < a ? a : (v > b ? b : v);
    }
}
