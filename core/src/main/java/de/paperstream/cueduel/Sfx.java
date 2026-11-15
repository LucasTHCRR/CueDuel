package de.paperstream.cueduel;

import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;

public final class Sfx {
    private static final HashMap<String, Sound> SOUNDS = new HashMap<>();
    private static int budget = 2;
    private static long lastBall, lastRail;
    private static long lastVib;
    public static boolean quiet;
    // auf dem Handy laufen Ton und Vibration in einem eigenen Thread, damit das Bild nie wartet
    private static java.util.concurrent.ExecutorService worker;

    public static void async() {
        worker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sfx");
            t.setDaemon(true);
            return t;
        });
    }

    private static void run(Runnable r) {
        if (worker == null) {
            r.run();
            return;
        }
        try {
            worker.execute(r);
        } catch (RuntimeException ignored) {
        }
    }

    public static void load() {
        for (String k : new String[] {"ball", "rail", "pot", "cue", "win", "lose", "click"}) {
            try {
                SOUNDS.put(k, Gdx.audio.newSound(Gdx.files.internal("sfx/" + k + ".wav")));
            } catch (Exception e) {
                Gdx.app.error("Sfx", k, e);
            }
        }
    }

    public static void frame() {
        budget = 2;
    }

    public static void play(String kind, float strength) {
        Sound s = SOUNDS.get(kind);
        if (Store.mute || s == null) return;
        boolean hit = kind.equals("ball") || kind.equals("rail");
        if (hit) {
            // viele Treffer auf einmal bremsen sonst das Handy aus
            long now = System.currentTimeMillis();
            boolean ball = kind.equals("ball");
            if (budget <= 0 || now - (ball ? lastBall : lastRail) < 45) return;
            budget--;
            if (ball) lastBall = now;
            else lastRail = now;
        }
        final float vol = MathUtils.clamp(strength, 0.05f, 1f) * 0.65f;
        final float pitch = kind.equals("ball") ? 1f + MathUtils.random(-0.04f, 0.04f) : 1f;
        final Sound snd = s;
        run(() -> snd.play(vol, pitch, 0f));
    }

    public static void vibrate(int ms) {
        if (quiet || !Store.haptic) return;
        long now = System.currentTimeMillis();
        if (now - lastVib < 90) return;
        lastVib = now;
        final int len = ms;
        run(() -> {
            try {
                Gdx.input.vibrate(len);
            } catch (Exception ignored) {
            }
        });
    }

    public static void dispose() {
        for (Sound s : SOUNDS.values()) s.dispose();
        SOUNDS.clear();
    }
}
