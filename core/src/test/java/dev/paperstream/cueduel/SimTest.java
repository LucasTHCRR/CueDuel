package dev.paperstream.cueduel;

import java.util.Random;

// Läuft ohne Grafik: KI gegen KI auf allen Stufen, Regeln und Gleichlauf der Physik
public final class SimTest {
    private static int checks;

    public static void main(String[] args) {
        for (int level = 1; level <= 4; level++) {
            int games = level >= 3 ? 3 : 6;
            int shots = 0, fouls = 0, pots = 0;
            for (int g = 0; g < games; g++) {
                int[] r = playGame(level, 1000L * level + g);
                shots += r[0];
                fouls += r[1];
                pots += r[2];
            }
            System.out.println("level " + level + ": " + games + " games, " + shots + " shots, " + pots + " pots, " + fouls + " fouls");
            check(pots > 0, "level " + level + " never pots");
        }
        determinism();
        rack();
        System.out.println("OK, " + checks + " checks");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }

    // spielt eine Partie nach den Regeln des Spiels, liefert [Stöße, Fouls, Kugeln]
    private static int[] playGame(int level, long seed) {
        Random rng = new Random(seed);
        Sim sim = new Sim();
        sim.setup(Sim.rackOrder(rng));
        int[] groups = {Rules.NONE, Rules.NONE};
        int turn = 0, shots = 0, fouls = 0, pots = 0;
        boolean hand = false, isBreak = true;
        while (true) {
            check(shots < 400, "game does not finish (level " + level + ", seed " + seed + ")");
            int g = groups[turn];
            boolean onEight = g != Rules.NONE && sim.groupLeft(g) == 0;
            AI.Result r = AI.think(sim.copy(), g, onEight, hand, false, isBreak, level, rng.nextLong());
            if (r.place) {
                check(sim.validCuePos(r.placeX, r.placeY, false), "AI places the cue ball on an invalid spot");
                sim.placeCue(r.placeX, r.placeY);
            }
            check(sim.alive[0], "cue ball missing before a shot");
            hand = false;
            sim.shoot(r.a, r.p);
            int frames = sim.runToRest(4000);
            check(frames < 4000, "balls never come to rest");
            shots++;
            for (int i = 0; i < sim.n; i++) {
                if (!sim.alive[i]) continue;
                check(sim.px[i] > Sim.PL - 30 && sim.px[i] < Sim.PR + 30 && sim.py[i] > Sim.PT - 30
                    && sim.py[i] < Sim.PB + 30, "ball left the table");
                check(!Double.isNaN(sim.px[i]) && !Double.isNaN(sim.py[i]), "NaN position");
            }
            Rules.Judge j = Rules.judge(sim, g, onEight, isBreak);
            boolean wasBreak = isBreak;
            isBreak = false;
            pots += j.pots.length;
            if (j.foul) fouls++;
            if (j.eight && !wasBreak) return new int[] {shots, fouls, pots};
            if (j.eight) sim.respot(8);
            if (!j.foul && g == Rules.NONE && !wasBreak && j.pots.length > 0) {
                g = Rules.groupOf(j.pots[0]);
                groups[turn] = g;
                groups[1 - turn] = Rules.otherGroup(g);
                j = Rules.judge(sim, g, onEight, false);
            }
            boolean keep = !j.foul && ((g != Rules.NONE && j.own > 0) || (wasBreak && j.pots.length > 0));
            if (j.foul) {
                hand = true;
                turn = 1 - turn;
                if (!sim.alive[0]) sim.placeCue(Sim.CUE_X, Sim.CUE_Y);
            } else if (!keep) {
                turn = 1 - turn;
            }
        }
    }

    // gleicher Stoß, gleicher Endstand, Bit für Bit
    private static void determinism() {
        long[] sums = new long[2];
        for (int k = 0; k < 2; k++) {
            Sim sim = new Sim();
            sim.setup(Sim.rackOrder(new Random(7)));
            sim.shoot(-Math.PI / 2 + 0.013, 0.93);
            sim.runToRest(4000);
            sim.shoot(1.234, 0.6);
            sim.runToRest(4000);
            long h = 17;
            for (int i = 0; i < sim.n; i++) {
                h = h * 31 + Double.doubleToLongBits(sim.px[i]);
                h = h * 31 + Double.doubleToLongBits(sim.py[i]);
            }
            sums[k] = h;
        }
        check(sums[0] == sums[1], "simulation is not deterministic");
    }

    private static void rack() {
        for (int s = 0; s < 200; s++) {
            int[] r = Sim.rackOrder(new Random(s));
            check(r.length == 15 && r[4] == 8, "8 is not in the middle of the rack");
            check((r[10] < 8) != (r[14] < 8), "back corners are not one solid and one stripe");
            boolean[] seen = new boolean[16];
            for (int n : r) seen[n] = true;
            for (int n = 1; n <= 15; n++) check(seen[n], "ball " + n + " missing in the rack");
        }
    }
}
