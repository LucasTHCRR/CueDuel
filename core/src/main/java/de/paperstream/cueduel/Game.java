package de.paperstream.cueduel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.JsonValue;

// Spielablauf: Züge, Eingabe, KI, Replays, Online und Statistik. Die Logik läuft fest mit 60 Hz.
public final class Game extends ApplicationAdapter implements Online.Listener {
    public static final String VERSION = "2.0.0-BETA";
    static final int PS = 0, AS = 1, PP = 2, AP = 3, PF = 4, AF = 5, RUN = 6, BEST = 7;

    enum St { IDLE, PLACE, AIM, AI, STRIKE, MOVING, RP, OVER, WAIT }

    final Sim sim = new Sim();
    final Online net = new Online();
    final boolean mobile;
    TableView view;
    Ui ui;
    St state = St.IDLE;
    int turn;
    final int[] groups = new int[2];
    boolean hand, kitchen, isBreak = true, onEight;
    int winner = -1;
    boolean duo, online, inGame;
    int mySide;
    double aim = -Math.PI / 2, power = 0.55, myPower = 0.55;
    boolean followCam = true;
    int spd = 1;
    String msg = "";
    Store.Replay replay;
    final int[] gs = new int[8];

    private final String[] args;
    private final Random rng = new Random();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai");
        t.setDaemon(true);
        return t;
    });
    private Store.Replay rec;
    private int replayI, rpT;
    private Store.Shot rpShot;
    private double[] placeRec;
    private long t0;
    private Future<AI.Result> aiTask;
    private AI.Result aiRes;
    private int aiT, aiGo;
    private float strikeT, strikeFrom, acc;
    private boolean dragging, grabbing;
    private float dragX;
    private double grabX, grabY;
    private int[] rackNums;
    private JsonValue pendingSnap;
    private boolean waitSnap, remoteShot;
    private int[] fin;
    private float resultDelay = -1;
    private String resTitle, resReason;
    private boolean resGood;
    private String[][] resTiles;
    private float clock, quitAt = -1, autoShoot = -1;
    private float[] shotTimes = new float[0];
    private int shotIdx;
    private boolean created, autoPlay;
    private float[] tap;
    private float resumeAt = -1;

    public Game(String[] args, boolean mobile) {
        this.args = args;
        this.mobile = mobile;
    }

    @Override
    public void create() {
        Store.load();
        if (Store.quality < 0) Store.quality = mobile ? 1 : 2;
        L.load();
        L.setLang(Store.lang.isEmpty() ? L.systemLang() : Store.lang);
        Sfx.load();
        if (mobile) Sfx.async();
        net.listener = this;
        view = new TableView();
        view.create();
        ui = new Ui(this);
        ui.create();
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new InputMultiplexer(ui.stage, new Touch()));
        newRack();
        state = St.IDLE;
        inGame = false;
        ui.openMenu();
        created = true;
        devArgs();
    }

    private void devArgs() {
        for (String a : args) {
            String v = a.contains("=") ? a.substring(a.indexOf('=') + 1) : "";
            if (a.startsWith("--level=")) {
                Store.level = Integer.parseInt(v);
            } else if (a.startsWith("--lang=")) {
                L.setLang(v);
                ui.rebuild();
            } else if (a.startsWith("--play")) {
                ui.closeAll();
                startGame(a.endsWith("duo"), false);
            } else if (a.equals("--aiwatch")) {
                ui.closeAll();
                startGame(false, false);
                turn = 1;
                begin();
            } else if (a.equals("--top")) {
                followCam = false;
            } else if (a.startsWith("--open=")) {
                if (v.equals("balls") || v.equals("result")) {
                    ui.closeAll();
                    startGame(false, false);
                }
                if (v.equals("result")) {
                    ui.showResult(L.t("VICTORY!"), true, L.t("You potted the 8 cleanly"), new String[][] {
                        {"6:42", "Duration"}, {"14 : 11", "Shots (you : AI)"}, {"8 : 5", "Potted"},
                        {"1 : 2", "Fouls"}, {"4", "Best run"}, {"57 %", "Pots per shot"}});
                } else {
                    ui.open(v);
                }
            } else if (a.startsWith("--cam=")) {
                String[] p = v.split(",");
                view.fixedCam = new float[6];
                for (int i = 0; i < 6; i++) view.fixedCam[i] = Float.parseFloat(p[i]);
            } else if (a.startsWith("--net-host")) {
                String[] p = v.split(",", -1);
                net.hostGame(p[0].isEmpty() ? "test" : p[0], p.length > 1 ? p[1] : "", a.contains("pub"));
            } else if (a.startsWith("--net-code=")) {
                String[] p = v.split(",", -1);
                net.join(p[0], p.length > 1 ? p[1] : "");
            } else if (a.equals("--net-shoot")) {
                autoShoot = 1;
            } else if (a.equals("--autoplay")) {
                autoShoot = 1;
                autoPlay = true;
            } else if (a.startsWith("--speed=")) {
                spd = Integer.parseInt(v);
            } else if (a.startsWith("--shots=")) {
                String[] p = v.split(",");
                shotTimes = new float[p.length];
                for (int i = 0; i < p.length; i++) shotTimes[i] = Float.parseFloat(p[i]);
            } else if (a.startsWith("--tap=")) {
                String[] p = v.split(",");
                tap = new float[] {Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])};
            } else if (a.startsWith("--resume=")) {
                resumeAt = Float.parseFloat(v);
            } else if (a.startsWith("--quit=")) {
                quitAt = Float.parseFloat(v);
            }
        }
    }

    // ---------- flow ----------

    void startGame(boolean twoPlayers, boolean viaNet) {
        if (!viaNet) leaveOnline();
        cancelAi();
        duo = twoPlayers;
        replay = null;
        Sfx.quiet = false;
        spd = 1;
        inGame = true;
        ui.setReplayMode(false);
        newRack();
    }

    void startReplay(Store.Replay log) {
        leaveOnline();
        cancelAi();
        saveLog("Aborted");
        inGame = false;
        duo = log.duo;
        replay = log;
        replayI = 0;
        Sfx.quiet = true;
        spd = 1;
        ui.setReplayMode(true);
        newRack();
    }

    void stopReplay() {
        replay = null;
        Sfx.quiet = false;
        spd = 1;
        ui.setReplayMode(false);
        state = St.IDLE;
        ui.openMenu();
    }

    void restart() {
        if (replay != null) return;
        if (online) {
            onlineAgain();
            return;
        }
        cancelAi();
        newRack();
    }

    private void newRack() {
        if (rec != null && replay == null) saveLog("Aborted");
        int[] nums;
        if (replay != null) nums = replay.nums.clone();
        else if (online && !net.host && rackNums != null) nums = rackNums.clone();
        else nums = Sim.rackOrder(rng);
        if (online && net.host && replay == null) {
            StringBuilder b = new StringBuilder("{\"t\":\"rack\",\"nums\":[");
            for (int i = 0; i < nums.length; i++) b.append(i > 0 ? "," : "").append(nums[i]);
            net.send(b.append("]}").toString());
        }
        fin = null;
        pendingSnap = null;
        waitSnap = false;
        resultDelay = -1;
        sim.setup(nums);
        view.resetBalls(sim);
        turn = 0;
        groups[0] = groups[1] = Rules.NONE;
        hand = false;
        kitchen = false;
        isBreak = true;
        winner = -1;
        placeRec = null;
        rec = null;
        if (replay == null) {
            rec = new Store.Replay();
            rec.nums = nums;
            rec.level = Store.level;
            rec.duo = duo;
            rec.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        }
        for (int i = 0; i < gs.length; i++) gs[i] = 0;
        t0 = System.currentTimeMillis();
        msg = L.t("Break!");
        begin();
    }

    private void begin() {
        if (replay != null) {
            nextRp();
            return;
        }
        if (online && turn != mySide) {
            state = St.WAIT;
            say(msg);
        } else if (turn == 0 || duo) {
            if (hand) {
                state = St.PLACE;
                if (!sim.alive[0] || !sim.validCuePos(sim.px[0], sim.py[0], kitchen)) defaultCue();
                say(msg + " – " + L.t("drag the cue ball, then OK"));
            } else {
                state = St.AIM;
                aimNear();
                usePower();
                say(msg);
            }
        } else {
            state = St.AI;
            aiT = 0;
            aiGo = 0;
            aiRes = null;
            say(msg);
        }
        ui.updateHud();
    }

    // die KI und Replays bewegen den Regler, beim eigenen Zug gilt wieder die eigene Stärke
    private void usePower() {
        power = myPower;
        ui.setPower(power);
    }

    private void defaultCue() {
        double x = Sim.CUE_X, y = Sim.CUE_Y;
        while (!sim.validCuePos(x, y, kitchen) && x < Sim.PR - Sim.BR) x += 4;
        sim.placeCue(x, y);
    }

    void confirmPlace() {
        if (state != St.PLACE || !sim.validCuePos(sim.px[0], sim.py[0], kitchen)) return;
        hand = false;
        placeRec = new double[] {sim.px[0], sim.py[0]};
        state = St.AIM;
        aimNear();
        usePower();
        say(L.t("Aim and shoot!"));
        ui.updateHud();
    }

    void shootPressed() {
        if (state == St.PLACE) confirmPlace();
        else if (state == St.AIM) strike();
    }

    private void strike() {
        if (online && !remoteShot) {
            String pl = placeRec == null ? "null" : "[" + placeRec[0] + "," + placeRec[1] + "]";
            net.send("{\"t\":\"shot\",\"a\":" + aim + ",\"p\":" + Math.max(0.05, power) + ",\"pl\":" + pl + "}");
        }
        remoteShot = false;
        strikeT = 0;
        strikeFrom = (float) (Sim.BR + 3 + power * 95);
        state = St.STRIKE;
        ui.updateHud();
    }

    private void shoot(double a, double p) {
        if (rec != null) {
            Store.Shot s = new Store.Shot();
            s.t = turn;
            s.place = placeRec != null;
            if (placeRec != null) {
                s.plx = placeRec[0];
                s.ply = placeRec[1];
            }
            s.a = a;
            s.p = p;
            rec.shots.add(s);
        }
        placeRec = null;
        int g = groups[turn];
        onEight = g != Rules.NONE && sim.groupLeft(g) == 0;
        sim.shoot(a, p);
        Sfx.play("cue", (float) (0.4 + p * 0.6));
        Sfx.vibrate((int) (20 + p * 50));
        state = St.MOVING;
    }

    private void resolve() {
        if (online && turn != mySide) {
            // Der Schütze schickt das Ergebnis, wir übernehmen es nur
            waitSnap = true;
            if (pendingSnap != null) applySnap(pendingSnap);
            return;
        }
        int shooter = turn;
        Object[] r = resolveLocal();
        if (online) {
            StringBuilder b = new StringBuilder("{\"t\":\"snap\",\"px\":[");
            for (int i = 0; i < 16; i++) b.append(i > 0 ? "," : "").append(sim.px[i]);
            b.append("],\"py\":[");
            for (int i = 0; i < 16; i++) b.append(i > 0 ? "," : "").append(sim.py[i]);
            b.append("],\"al\":[");
            for (int i = 0; i < 16; i++) b.append(i > 0 ? "," : "").append(sim.alive[i] ? 1 : 0);
            b.append("],\"turn\":").append(turn).append(",\"g0\":").append(groups[0]).append(",\"g1\":").append(groups[1])
                .append(",\"hand\":").append(hand).append(",\"kitchen\":").append(kitchen).append(",\"brk\":").append(isBreak)
                .append(",\"sh\":").append(shooter).append(",\"why\":").append(Online.quote((String) r[0]))
                .append(",\"keep\":").append(r[1]).append(",\"rs\":").append(r[2]).append(",\"fin\":");
            if (fin == null) b.append("null");
            else b.append('[').append(fin[0]).append(',').append(fin[1]).append(',').append(fin[2]).append(']');
            b.append(",\"gs\":[");
            for (int i = 0; i < gs.length; i++) b.append(i > 0 ? "," : "").append(gs[i]);
            net.send(b.append("]}").toString());
        }
    }

    // liefert [Foulgrund, darf weiterspielen, 8 zurückgelegt]
    private Object[] resolveLocal() {
        int sh = turn, o = 1 - sh;
        int g = groups[sh];
        Rules.Judge j = Rules.judge(sim, g, onEight, isBreak);
        boolean wasBreak = isBreak;
        isBreak = false;
        kitchen = false;
        int k = sh == 0 ? 0 : 1;
        int pn = j.pots.length + (j.eight ? 1 : 0);
        gs[PS + k]++;
        gs[PP + k] += pn;
        if (j.foul) gs[PF + k]++;
        if (k == 0) {
            if (!j.foul && pn > 0) {
                gs[RUN]++;
                gs[BEST] = Math.max(gs[BEST], gs[RUN]);
            } else {
                gs[RUN] = 0;
            }
        }
        if (j.eight && !wasBreak) {
            boolean win = !j.foul && onEight;
            finish(win ? sh : o, sh, win);
            return new Object[] {j.why, false, false};
        }
        boolean respot = false;
        if (j.eight) {
            sim.respot(8);
            respot = true;
        }
        if (!j.foul && g == Rules.NONE && !wasBreak && j.pots.length > 0) {
            g = Rules.groupOf(j.pots[0]);
            groups[sh] = g;
            groups[o] = Rules.otherGroup(g);
            j = Rules.judge(sim, g, onEight, false);
        }
        boolean keep = !j.foul && ((g != Rules.NONE && j.own > 0) || (wasBreak && j.pots.length > 0));
        if (j.foul) {
            hand = true;
            turn = o;
            Sfx.vibrate(180);
        } else if (!keep) {
            turn = o;
        }
        msg = compose(sh, j.why, keep, respot);
        begin();
        return new Object[] {j.why, keep, respot};
    }

    private String compose(int sh, String why, boolean keep, boolean respot) {
        int o = 1 - sh;
        String line;
        if (!why.isEmpty()) {
            line = L.t("Foul: {0}", L.t(why));
        } else if (keep) {
            if (online) line = sh == mySide ? L.t("Pot! Go again") : L.t("The opponent potted one");
            else if (duo) line = L.t("{0} goes again", name(sh));
            else line = sh == 0 ? L.t("Pot! Go again") : L.t("The AI potted one");
        } else {
            if (online) line = o == mySide ? L.t("Your turn") : L.t("Opponent's turn");
            else if (duo) line = L.t("{0}'s turn", name(o));
            else line = o == 0 ? L.t("Your turn") : L.t("AI's turn");
        }
        return respot ? L.t("The 8 goes back on the spot") + " · " + line : line;
    }

    private void finish(int winSide, int shooter, boolean clean) {
        fin = new int[] {winSide, shooter, clean ? 1 : 0};
        winner = winSide;
        state = St.OVER;
        boolean playerWon = winner == 0;
        boolean good = online ? winner == mySide : (playerWon || duo);
        Sfx.play(good ? "win" : "lose", 1f);
        Sfx.vibrate(300);
        saveLog(playerWon ? "Won" : "Lost");
        ui.updateHud();
        if (replay != null) {
            say(L.t("Replay finished"));
            return;
        }
        String k = clean ? "cleanly" : "too early or with a foul";
        String reason;
        if (duo) reason = L.t("{0} potted the 8 " + k, name(shooter));
        else if (shooter == 0) reason = L.t("You potted the 8 " + k);
        else reason = L.t("The AI potted the 8 " + k);
        if (online) say(winner == mySide ? L.t("You win!") : L.t("Opponent wins!"));
        else if (duo) say(L.t("{0} wins!", name(winner)));
        else say(playerWon ? L.t("You win!") : L.t("AI wins!"));
        inGame = false;
        if (!duo) {
            int lv = Store.level - 1;
            Store.games++;
            if (playerWon) {
                Store.wins++;
                Store.levels[lv][0]++;
                Store.streak = Math.max(1, Store.streak + 1);
            } else {
                Store.losses++;
                Store.levels[lv][1]++;
                Store.streak = Math.min(-1, Store.streak - 1);
            }
            Store.bestStreak = Math.max(Store.bestStreak, Store.streak);
            Store.pots += gs[PP];
            Store.shots += gs[PS];
            Store.fouls += gs[PF];
            Store.save();
        }
        long sec = (System.currentTimeMillis() - t0) / 1000;
        resTiles = new String[][] {
            {String.format(Locale.US, "%d:%02d", sec / 60, sec % 60), "Duration"},
            {gs[PS] + " : " + gs[AS], duo ? "Shots (P1 : P2)" : "Shots (you : AI)"},
            {gs[PP] + " : " + gs[AP], "Potted"},
            {gs[PF] + " : " + gs[AF], "Fouls"},
            {String.valueOf(gs[BEST]), "Best run"},
            {pct(gs[PP], gs[PS]), "Pots per shot"}};
        if (online) resTitle = good ? L.t("VICTORY!") : L.t("DEFEAT");
        else if (duo) resTitle = L.t("{0} WINS", name(winner).toUpperCase());
        else resTitle = playerWon ? L.t("VICTORY!") : L.t("DEFEAT");
        resGood = good;
        resReason = reason;
        resultDelay = 1.4f;
    }

    private void saveLog(String res) {
        if (rec == null || replay != null) return;
        if (rec.shots.size() >= 3) {
            rec.res = res;
            Store.addReplay(rec);
        }
        rec = null;
    }

    // ---------- online ----------

    void rematch() {
        if (online) onlineAgain();
        else startGame(duo, false);
    }

    void onlineAgain() {
        if (!online) return;
        if (net.host) {
            ui.closeAll();
            startGame(true, true);
        } else {
            net.send("{\"t\":\"again\"}");
            ui.toast(L.t("Waiting for the host…"));
        }
    }

    void leaveOnline() {
        if (online || net.active) {
            online = false;
            mySide = 0;
            net.stop();
        }
    }

    @Override
    public void onConnected() {
        online = true;
        mySide = 0;
        ui.closeAll();
        startGame(true, true);
    }

    @Override
    public void onLost() {
        if (!online) return;
        leaveOnline();
        cancelAi();
        inGame = false;
        state = St.IDLE;
        ui.openMenu();
        ui.toast(L.t("The opponent left the game"));
    }

    @Override
    public void onFailed(String why) {
        ui.netFailed(why);
    }

    @Override
    public void onDenied() {
        ui.netFailed("Wrong password");
    }

    @Override
    public void onStatus() {
        if (autoShoot > 0) System.out.println("lobby=" + net.lobbyId);
        ui.netStatus();
    }

    @Override
    public void onLobbies(List<Online.Lobby> list) {
        ui.lobbies(list);
    }

    @Override
    public void onMessage(JsonValue d) {
        switch (d.getString("t", "")) {
            case "rack":
                if (net.host) return;
                online = true;
                mySide = 1;
                rackNums = d.get("nums").asIntArray();
                if (rackNums.length != 15) return;
                ui.closeAll();
                startGame(true, true);
                break;
            case "again":
                if (online && net.host) onlineAgain();
                break;
            case "shot":
                if (state != St.WAIT) return;
                JsonValue pl = d.get("pl");
                if (pl != null && pl.isArray() && pl.size == 2) {
                    placeRec = pl.asDoubleArray();
                    sim.placeCue(placeRec[0], placeRec[1]);
                    hand = false;
                }
                aim = d.getDouble("a", 0);
                power = d.getDouble("p", 0.5);
                ui.setPower(power);
                remoteShot = true;
                strike();
                break;
            case "snap":
                if (waitSnap) applySnap(d);
                else pendingSnap = d;
                break;
            default:
                break;
        }
    }

    private void applySnap(JsonValue d) {
        pendingSnap = null;
        waitSnap = false;
        try {
            double[] px = d.get("px").asDoubleArray(), py = d.get("py").asDoubleArray();
            int[] al = d.get("al").asIntArray(), g = d.get("gs").asIntArray();
            for (int i = 0; i < 16; i++) {
                sim.px[i] = px[i];
                sim.py[i] = py[i];
                sim.alive[i] = al[i] == 1;
                sim.vx[i] = 0;
                sim.vy[i] = 0;
            }
            turn = d.getInt("turn", 0);
            groups[0] = d.getInt("g0", 0);
            groups[1] = d.getInt("g1", 0);
            hand = d.getBoolean("hand", false);
            kitchen = d.getBoolean("kitchen", false);
            isBreak = d.getBoolean("brk", false);
            System.arraycopy(g, 0, gs, 0, Math.min(g.length, gs.length));
            msg = compose(d.getInt("sh", 0), d.getString("why", ""), d.getBoolean("keep", false), d.getBoolean("rs", false));
            JsonValue f = d.get("fin");
            if (f != null && f.isArray() && f.size == 3) {
                int[] x = f.asIntArray();
                finish(x[0], x[1], x[2] == 1);
            } else {
                begin();
            }
        } catch (Exception e) {
            Gdx.app.error("Game", "bad snap", e);
            onLost();
        }
    }

    // ---------- replays ----------

    private void nextRp() {
        if (replayI >= replay.shots.size()) {
            state = St.OVER;
            say(L.t("Replay finished"));
            ui.updateHud();
            return;
        }
        rpT = 0;
        rpShot = replay.shots.get(replayI);
        replayI++;
        turn = rpShot.t;
        state = St.RP;
        ui.updateHud();
        ui.toast(L.t("Replay") + " " + replayI + "/" + replay.shots.size());
    }

    // ---------- AI ----------

    private void aiStart() {
        final Sim snap = sim.copy();
        final int g = groups[1];
        final boolean oe = g != Rules.NONE && sim.groupLeft(g) == 0;
        final boolean h = hand, kt = kitchen, br = isBreak;
        final int lv = Store.level;
        final long seed = rng.nextLong();
        aiTask = pool.submit(() -> AI.think(snap, g, oe, h, kt, br, lv, seed));
    }

    private void cancelAi() {
        if (aiTask != null) aiTask.cancel(false);
        aiTask = null;
        aiRes = null;
    }

    // ---------- tick ----------

    private void tick() {
        switch (state) {
            case AI:
                aiT++;
                if (aiT == 20) {
                    aiStart();
                } else if (aiT > 20) {
                    if (aiTask != null) {
                        if (!aiTask.isDone()) return;
                        try {
                            aiRes = aiTask.get();
                        } catch (Exception e) {
                            aiRes = new AI.Result();
                            aiRes.a = aim;
                            aiRes.p = 0.5;
                        }
                        aiTask = null;
                        if (aiRes.place) {
                            sim.placeCue(aiRes.placeX, aiRes.placeY);
                            placeRec = new double[] {aiRes.placeX, aiRes.placeY};
                            hand = false;
                        }
                    }
                    if (aiRes == null) return;
                    aiGo++;
                    aim += angDiff(aim, aiRes.a) * 0.07;
                    power += (aiRes.p - power) * 0.06;
                    ui.setPower(power);
                    if (aiGo > 70) {
                        aim = aiRes.a;
                        power = aiRes.p;
                        strike();
                    }
                }
                break;
            case RP:
                rpT++;
                if (rpT == 2 && rpShot.place) sim.placeCue(rpShot.plx, rpShot.ply);
                if (rpT > 2 && rpT <= 50) {
                    aim += angDiff(aim, rpShot.a) * 0.12;
                    power += (rpShot.p - power) * 0.1;
                    ui.setPower(power);
                } else if (rpT > 50) {
                    aim = rpShot.a;
                    power = rpShot.p;
                    strike();
                }
                break;
            case STRIKE:
                strikeT += 0.12f;
                if (strikeT >= 1f) shoot(aim, Math.max(0.05, power));
                break;
            case MOVING:
                sim.step();
                for (int i = 0; i < sim.evCount; i++) {
                    float v = sim.evVal[i] / 12f;
                    if (sim.evType[i] == Sim.EV_BALL) {
                        Sfx.play("ball", v);
                    } else if (sim.evType[i] == Sim.EV_RAIL) {
                        Sfx.play("rail", v);
                    } else {
                        Sfx.play("pot", 1f);
                        Sfx.vibrate(40);
                    }
                }
                sim.evCount = 0;
                if (!sim.moving()) resolve();
                break;
            default:
                break;
        }
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        clock += dt;
        net.update(dt);
        Sfx.frame();
        boolean paused = ui.hasOverlay() && !online;
        if (!paused && state != St.IDLE) {
            acc += dt;
            int n = 0;
            while (acc >= 1f / 60f && n < 4) {
                acc -= 1f / 60f;
                n++;
                for (int k = 0; k < spd; k++) tick();
            }
            if (n == 4) acc = 0;
        }
        if (resultDelay > 0) {
            resultDelay -= dt;
            if (resultDelay <= 0 && state == St.OVER && replay == null) ui.showResult(resTitle, resGood, resReason, resTiles);
        }
        if (autoShoot > 0 && clock > autoShoot) {
            autoShoot = clock + (autoPlay ? 0.3f : 1f);
            if (!autoPlay || state == St.OVER) System.out.println("net online=" + online + " state=" + state + " turn=" + turn + " me=" + mySide
                + " cue=" + sim.px[0] + "," + sim.py[0] + " sum=" + checksum() + " win=" + winner + " gs=" + java.util.Arrays.toString(gs) + " t=" + clock);
            if ((online && turn == mySide) || autoPlay) shootPressed();
            if (autoPlay && state == St.OVER && quitAt < 0) quitAt = clock + 2.5f;
        }
        if (state == St.AIM && ui.fine != 0) {
            ui.fineT += dt;
            if (ui.fineT > 0.3f) aim += ui.fine * dt * 0.12;
        }
        view.sync(sim, dt);
        boolean showStick = sim.alive[0] && (state == St.AIM || state == St.AI || state == St.STRIKE || state == St.RP);
        double pull = Sim.BR + 3 + power * 95;
        if (state == St.STRIKE) pull = strikeFrom + (Sim.BR + 1 - strikeFrom) * Math.min(1f, strikeT * strikeT);
        view.showStick(showStick, sim.px[0], sim.py[0], aim, pull);
        if (state == St.AIM) view.showGuides(sim, aim);
        else view.hideGuides();
        view.headLine = state == St.PLACE && kitchen;
        view.updateCamera(followCam && showStick, sim.px[0], sim.py[0], aim, dt);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        view.render(1f);
        ui.render(dt);

        if (shotIdx < shotTimes.length && clock >= shotTimes[shotIdx]) {
            shotIdx++;
            Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("user.dir") + "/shot_" + shotIdx + ".png"), pm, 6, true);
            pm.dispose();
            System.out.println("shot " + shotIdx + " fps=" + Gdx.graphics.getFramesPerSecond() + " state=" + state + " aim=" + aim + " cue=" + sim.px[0] + "," + sim.py[0] + " n=" + sim.n);
        }
        if (tap != null && clock >= tap[2]) {
            boolean d = ui.stage.touchDown((int) tap[0], (int) tap[1], 0, 0);
            boolean g = ui.stage.touchDragged((int) tap[0] + 60, (int) tap[1], 0);
            ui.stage.touchUp((int) tap[0] + 60, (int) tap[1], 0, 0);
            System.out.println("tap down=" + d + " drag=" + g + " power=" + power + " state=" + state);
            tap = null;
        }
        if (resumeAt > 0 && clock >= resumeAt) {
            resumeAt = -1;
            resume();
        }
        if (quitAt > 0 && clock >= quitAt) Gdx.app.exit();
    }

    private long checksum() {
        long h = 17;
        for (int i = 0; i < sim.n; i++) {
            h = h * 31 + Double.doubleToLongBits(sim.px[i]);
            h = h * 31 + Double.doubleToLongBits(sim.py[i]);
        }
        return h;
    }

    @Override
    public void resize(int width, int height) {
        if (created) ui.resize(width, height);
    }

    @Override
    public void pause() {
        Store.save();
    }

    @Override
    public void resume() {
        if (!created) return;
        view.reloadWhite();
        ui.reload();
    }

    @Override
    public void dispose() {
        Store.save();
        net.stop();
        pool.shutdownNow();
        view.dispose();
        ui.dispose();
        Sfx.dispose();
    }

    // ---------- input ----------

    private final class Touch extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                ui.back();
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int x, int y, int pointer, int button) {
            if (pointer != 0 || ui.hasOverlay()) return false;
            dragging = true;
            dragX = x;
            grabbing = false;
            if (state == St.PLACE) {
                // nur ein Griff auf (oder direkt neben) die Weiße nimmt sie auf
                double[] t = view.screenToTable(x, y);
                if (t != null && Math.hypot(t[0] - sim.px[0], t[1] - sim.py[0]) < Sim.BR * 4) {
                    grabbing = true;
                    grabX = sim.px[0] - t[0];
                    grabY = sim.py[0] - t[1];
                }
            }
            return true;
        }

        @Override
        public boolean touchDragged(int x, int y, int pointer) {
            if (pointer != 0 || !dragging) return false;
            if (state == St.AIM) {
                aim += (x - dragX) / Math.max(1, Gdx.graphics.getWidth()) * 2.4;
            } else if (state == St.PLACE && grabbing) {
                double[] t = view.screenToTable(x, y);
                if (t != null) {
                    double px = AI.clamp(t[0] + grabX, Sim.PL + Sim.BR, Sim.PR - Sim.BR);
                    double py = AI.clamp(t[1] + grabY, (kitchen ? Sim.HEAD_Y : Sim.PT) + Sim.BR, Sim.PB - Sim.BR);
                    if (sim.freeSpot(px, py)) sim.placeCue(px, py);
                }
            }
            dragX = x;
            return true;
        }

        @Override
        public boolean touchUp(int x, int y, int pointer, int button) {
            if (pointer != 0) return false;
            dragging = false;
            grabbing = false;
            return true;
        }
    }

    // ---------- helpers ----------

    private void aimNear() {
        int[] tg = AI.targets(sim, groups[turn]);
        if (tg.length == 0) return;
        int best = tg[0];
        double bd = Double.MAX_VALUE;
        for (int i : tg) {
            double d = Math.hypot(sim.px[i] - sim.px[0], sim.py[i] - sim.py[0]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        aim = Math.atan2(sim.py[best] - sim.py[0], sim.px[best] - sim.px[0]);
    }

    String name(int side) {
        if (online) return side == mySide ? L.t("You") : L.t("Opponent");
        if (duo) return side == 0 ? L.t("Player 1") : L.t("Player 2");
        return side == 0 ? L.t("You") : L.t("AI");
    }

    private void say(String t) {
        msg = t;
        if (!t.isEmpty()) ui.toast(t);
    }

    static String pct(int a, int b) {
        return b > 0 ? Math.round(100.0 * a / b) + " %" : "–";
    }

    static double angDiff(double from, double to) {
        double d = (to - from) % (Math.PI * 2);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return d;
    }

    void setLevel(int lv) {
        boolean changed = lv != Store.level;
        Store.level = lv;
        Store.save();
        if (changed && inGame && !duo) restart();
    }
}
