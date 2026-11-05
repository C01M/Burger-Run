package com.c01m.burgerrun;

import android.os.Bundle;
import android.view.View;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.c01m.burgerrun.engine.SaveSystem;
import com.c01m.burgerrun.ui.BurgerRunGame;

/** Android entry point: hosts the libGDX game and owns the system-bars state. */
public class AndroidLauncher extends AndroidApplication {
    private volatile boolean fullscreen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useImmersiveMode = false; // applied manually so Options can toggle it
        config.numSamples = 2;
        initialize(new BurgerRunGame(new SaveSystem(this), this::setFullscreen), config);
    }

    private void setFullscreen(boolean on) {
        fullscreen = on;
        runOnUiThread(this::applyBars);
    }

    private void applyBars() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (fullscreen) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyBars();
    }
}
