package de.paperstream.cueduel;

public final class Rules {
    public static final int NONE = 0, SOLID = 1, STRIPE = 2, EIGHT = 3, CUE = 4;

    public static final class Judge {
        public boolean foul, eight;
        public String why = "";
        public int[] pots = new int[0];
        public int own;
    }

    public static int groupOf(int n) {
        if (n == 0) return CUE;
        if (n == 8) return EIGHT;
        return n < 8 ? SOLID : STRIPE;
    }

    public static int otherGroup(int g) {
        return g == SOLID ? STRIPE : SOLID;
    }

    public static Judge judge(Sim sim, int group, boolean onEight, boolean isBreak) {
        Judge j = new Judge();
        j.eight = sim.pottedHas(8);
        int c = 0;
        int[] tmp = new int[16];
        for (int i = 0; i < sim.pottedCount; i++) {
            int n = sim.potted[i];
            if (n != 0 && n != 8) tmp[c++] = n;
        }
        j.pots = new int[c];
        System.arraycopy(tmp, 0, j.pots, 0, c);
        if (sim.pottedHas(0)) {
            j.why = "Cue ball potted";
        } else if (sim.firstHit < 0) {
            j.why = "Hit nothing";
        } else if (group == NONE && sim.firstHit == 8 && !isBreak) {
            j.why = "Hit the 8 too early";
        } else if (group != NONE && !(groupOf(sim.firstHit) == group || (sim.firstHit == 8 && onEight))) {
            j.why = "Wrong ball first";
        } else if (c == 0 && !j.eight && !sim.railAfter && !isBreak) {
            j.why = "No rail after contact";
        }
        if (group != NONE) {
            for (int n : j.pots) if (groupOf(n) == group) j.own++;
        }
        j.foul = !j.why.isEmpty();
        return j;
    }
}
