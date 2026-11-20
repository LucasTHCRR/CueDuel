package dev.paperstream.cueduel.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import dev.paperstream.cueduel.Game;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration cfg = new AndroidApplicationConfiguration();
        cfg.useAccelerometer = false;
        cfg.useCompass = false;
        cfg.useImmersiveMode = true;
        cfg.numSamples = 2;
        cfg.depth = 24;
        cfg.useWakelock = true;
        initialize(new Game(new String[0], true), cfg);
    }
}
