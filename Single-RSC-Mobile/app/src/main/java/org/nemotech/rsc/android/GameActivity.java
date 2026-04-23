package org.nemotech.rsc.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.Main;

/**
 * Main Android Activity that hosts the RSC game.
 * Replaces the desktop AWT Frame (Application.java).
 */
public class GameActivity extends Activity {

    private GameSurfaceView gameSurfaceView;
    private Thread gameThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        // Initialize platform bridge
        AndroidPlatform.init(this);

        // Create the game surface view
        gameSurfaceView = new GameSurfaceView(this);
        setContentView(gameSurfaceView);

        // Store reference for Shell/Surface to use
        GameBridge.surfaceView = gameSurfaceView;

        // Start game on a background thread
        gameThread = new Thread(() -> {
            try {
                Main.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "RSC-GameThread");
        gameThread.setPriority(Thread.NORM_PRIORITY);
        gameThread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The game will clean up via Shell.closeProgram()
        // Don't call System.exit() here as it kills the process
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
}
