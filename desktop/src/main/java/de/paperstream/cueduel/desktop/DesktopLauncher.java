package de.paperstream.cueduel.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import de.paperstream.cueduel.Game;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Cue Duel");
        cfg.setWindowedMode(450, 900);
        cfg.setBackBufferConfig(8, 8, 8, 8, 16, 0, 2);
        cfg.useVsync(true);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new Game(args, false), cfg);
    }
}
