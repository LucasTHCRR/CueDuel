package de.paperstream.cueduel;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

public final class Store {
    public static final int MAX_REPLAYS = 12, REPLAY_VERSION = 3;

    public static final class Shot {
        public int t;
        public boolean place;
        public double plx, ply, a, p;
    }

    public static final class Replay {
        public int[] nums;
        public int level;
        public boolean duo;
        public String date = "", res = "";
        public final List<Shot> shots = new ArrayList<>();
    }

    public static int level = 2, quality = -1;
    public static boolean haptic = true, mute = false, tutorialSeen = false;
    public static String lang = "";
    public static int games, wins, losses, streak, bestStreak, pots, shots, fouls;
    public static final int[][] levels = new int[4][2];
    public static final List<Replay> replays = new ArrayList<>();

    private static FileHandle file() {
        return Gdx.files.local("save.json");
    }

    public static void load() {
        try {
            FileHandle f = file();
            if (!f.exists()) return;
            JsonValue d = new JsonReader().parse(f.readString("UTF-8"));
            JsonValue c = d.get("cfg");
            if (c != null) {
                level = Math.max(1, Math.min(4, c.getInt("level", 2)));
                quality = Math.max(0, Math.min(2, c.getInt("quality", 1)));
                haptic = c.getBoolean("haptic", true);
                tutorialSeen = c.getBoolean("tutorial", false);
                mute = c.getBoolean("mute", false);
                lang = c.getString("lang", "");
            }
            JsonValue s = d.get("stats");
            if (s != null) {
                games = s.getInt("games", 0);
                wins = s.getInt("wins", 0);
                losses = s.getInt("losses", 0);
                streak = s.getInt("streak", 0);
                bestStreak = s.getInt("best_streak", 0);
                pots = s.getInt("pots", 0);
                shots = s.getInt("shots", 0);
                fouls = s.getInt("fouls", 0);
                JsonValue lv = s.get("levels");
                if (lv != null && lv.size == 8) {
                    int[] a = lv.asIntArray();
                    for (int i = 0; i < 4; i++) {
                        levels[i][0] = a[i * 2];
                        levels[i][1] = a[i * 2 + 1];
                    }
                }
            }
            JsonValue rp = d.get("replays");
            if (rp != null) {
                for (JsonValue r = rp.child; r != null; r = r.next) {
                    if (r.getInt("v", 0) != REPLAY_VERSION) continue;
                    Replay x = new Replay();
                    x.nums = r.get("nums").asIntArray();
                    x.level = r.getInt("level", 2);
                    x.duo = r.getBoolean("duo", false);
                    x.date = r.getString("date", "");
                    x.res = r.getString("res", "");
                    for (JsonValue q = r.get("shots").child; q != null; q = q.next) {
                        Shot sh = new Shot();
                        sh.t = q.getInt("t", 0);
                        sh.place = q.has("plx");
                        sh.plx = q.getDouble("plx", 0);
                        sh.ply = q.getDouble("ply", 0);
                        sh.a = q.getDouble("a", 0);
                        sh.p = q.getDouble("p", 0.5);
                        x.shots.add(sh);
                    }
                    if (x.nums.length == 15) replays.add(x);
                }
            }
        } catch (Exception e) {
            Gdx.app.error("Store", "load failed", e);
        }
    }

    public static void save() {
        StringBuilder b = new StringBuilder(4096);
        b.append("{\"cfg\":{\"level\":").append(level).append(",\"quality\":").append(quality)
            .append(",\"haptic\":").append(haptic).append(",\"tutorial\":").append(tutorialSeen).append(",\"mute\":").append(mute)
            .append(",\"lang\":\"").append(lang).append("\"},\"stats\":{\"games\":").append(games)
            .append(",\"wins\":").append(wins).append(",\"losses\":").append(losses)
            .append(",\"streak\":").append(streak).append(",\"best_streak\":").append(bestStreak)
            .append(",\"pots\":").append(pots).append(",\"shots\":").append(shots)
            .append(",\"fouls\":").append(fouls).append(",\"levels\":[");
        for (int i = 0; i < 4; i++) {
            if (i > 0) b.append(',');
            b.append(levels[i][0]).append(',').append(levels[i][1]);
        }
        b.append("]},\"replays\":[");
        for (int i = 0; i < replays.size(); i++) {
            Replay r = replays.get(i);
            if (i > 0) b.append(',');
            b.append("{\"v\":").append(REPLAY_VERSION).append(",\"nums\":[");
            for (int k = 0; k < r.nums.length; k++) {
                if (k > 0) b.append(',');
                b.append(r.nums[k]);
            }
            b.append("],\"level\":").append(r.level).append(",\"duo\":").append(r.duo)
                .append(",\"date\":\"").append(r.date).append("\",\"res\":\"").append(r.res).append("\",\"shots\":[");
            for (int k = 0; k < r.shots.size(); k++) {
                Shot s = r.shots.get(k);
                if (k > 0) b.append(',');
                b.append("{\"t\":").append(s.t);
                if (s.place) b.append(",\"plx\":").append(s.plx).append(",\"ply\":").append(s.ply);
                b.append(",\"a\":").append(s.a).append(",\"p\":").append(s.p).append('}');
            }
            b.append("]}");
        }
        b.append("]}");
        try {
            file().writeString(b.toString(), false, "UTF-8");
        } catch (Exception e) {
            Gdx.app.error("Store", "save failed", e);
        }
    }

    public static void addReplay(Replay r) {
        replays.add(0, r);
        while (replays.size() > MAX_REPLAYS) replays.remove(replays.size() - 1);
        save();
    }

    public static void resetStats() {
        games = wins = losses = streak = bestStreak = pots = shots = fouls = 0;
        for (int[] l : levels) l[0] = l[1] = 0;
        save();
    }
}
