package org.nemotech.rsc.client;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.util.Util;
import org.nemotech.rsc.client.sound.MusicPlayer;
import org.nemotech.rsc.android.GameBridge;
import org.nemotech.rsc.android.GameSurfaceView;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Shell is the base class for the game client. On mobile it handles the game loop
 * and delegates rendering to the Android SurfaceView. Input is received from
 * GameSurfaceView touch events which update the mouse/key fields directly.
 */
public abstract class Shell implements Runnable {
    
    /* abstract methods */
    
    protected abstract void startGame();

    protected abstract void handleInputs();
    
    protected abstract void onClosing();

    protected abstract void draw();
    
    public abstract void handleKeyPress(int key);

    public abstract void handleMouseScroll(int rotation);
    
    /* variables */
    
    private Thread gameThread;
    
    protected MusicPlayer musicPlayer;
    
    private final String CHAR_MAP = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"\243$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";
    
    public String logoHeaderText, inputTextCurrent, inputTextFinal, loadingProgessText;
    public boolean keyLeft, keyRight, keyUp, keyDown, keySpace, interlace, closing;
    public int threadSleep, mouseX, mouseY, mouseButtonDown, lastMouseButtonDown, stopTimeout, interlaceTimer,
        loadingProgressPercent, panelWidth, panelHeight, targetFps, maxDrawTime, loadingStep;
    public volatile int cameraZoomOffset;  // pinch zoom offset from default
    public volatile int cameraRotationDelta; // touch drag rotation delta
    private long[] timings;
    
    /* resizable client code */
    
    protected int dimensionWidth, dimensionHeight;
    protected boolean resized = true;
    
    protected void doResize(int width, int height) {
        synchronized (this) {
            dimensionWidth = width;
            dimensionHeight = height;
            resized = false;
        }
    }
    
    /* constructor */

    public Shell() {
        panelWidth = Constants.APPLICATION_WIDTH;
        panelHeight = Constants.APPLICATION_HEIGHT;
        targetFps = 20;
        maxDrawTime = 1000;
        timings = new long[10];
        loadingStep = 1;
        loadingProgessText = "Loading";
        threadSleep = 10;
        inputTextCurrent = "";
        inputTextFinal = "";
    }

    public void start() {
        System.out.println("[Classic Client] Loading process started...");
        panelWidth = Constants.APPLICATION_WIDTH;
        panelHeight = Constants.APPLICATION_HEIGHT;
        doResize(panelWidth, panelHeight);

        // Connect to the Android SurfaceView
        GameSurfaceView sv = GameBridge.surfaceView;
        if (sv != null) {
            sv.setShell(this);
        }

        loadingStep = 1;
        gameThread = new Thread(this);
        gameThread.start();
        gameThread.setPriority(1);
    }

    protected void setTargetFps(int i) {
        targetFps = 1000 / i;
    }

    protected void resetTimings() {
        for(int i = 0; i < 10; i++) {
            timings[i] = 0L;
        }
    }

    protected void handleMouseDown(int i, int j, int k) {
    }

    protected void drawTextBox(String s, String s1) {
        // On mobile, loading text is handled by the loading screen logic
        System.out.println(s + " " + s1);
    }

    public void closeProgram() {
        closing = true;
        stopTimeout = -2;
        System.out.println("\nSaving player data and closing application...");
        if(musicPlayer != null && musicPlayer.isRunning()) {
            musicPlayer.stop();
        }
        if(musicPlayer != null) {
            musicPlayer.close();
        }
        onClosing();
        // On Android, don't call System.exit() - let the Activity handle lifecycle
    }

    @Override
    public void run() {
        if (loadingStep == 1) {
            loadingStep = 2;
            loadJagex();
            showLoadingProgress(0, "Loading...");
            startGame();
            loadingStep = 0;
        }
        int i = 0;
        int j = 256;
        int sleep = 1;
        int i1 = 0;
        for (int j1 = 0; j1 < 10; j1++)
            timings[j1] = System.currentTimeMillis();

        while (stopTimeout >= 0) {
            if (stopTimeout > 0) {
                stopTimeout--;
                if (stopTimeout == 0) {
                    closeProgram();
                    gameThread = null;
                    return;
                }
            }
            int k1 = j;
            int lastSleep = sleep;
            j = 300;
            sleep = 1;
            long time = System.currentTimeMillis();
            if (timings[i] == 0L) {
                j = k1;
                sleep = lastSleep;
            } else if (time > timings[i])
                j = (int) ((long) (2560 * targetFps) / (time - timings[i]));
            if (j < 25)
                j = 25;
            if (j > 256) {
                j = 256;
                sleep = (int) ((long) targetFps - (time - timings[i]) / 10L);
                if (sleep < threadSleep)
                    sleep = threadSleep;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                /* ignore */
            }
            timings[i] = time;
            i = (i + 1) % 10;
            if (sleep > 1) {
                for (int j2 = 0; j2 < 10; j2++)
                    if (timings[j2] != 0L)
                        timings[j2] += sleep;
            }
            int k2 = 0;
            while (i1 < 256) {
                handleInputs();
                i1 += j;
                if (++k2 > maxDrawTime) {
                    i1 = 0;
                    interlaceTimer += 6;
                    if (interlaceTimer > 25) {
                        interlaceTimer = 0;
                        interlace = true;
                    }
                    break;
                }
            }
            interlaceTimer--;
            i1 &= 0xff;
            if(!closing) {
                draw();
            }
        }
        if(stopTimeout == -1) {
            closeProgram();
        }
        gameThread = null;
    }

    private void loadJagex() {
        byte buff[] = readDataFile("jagex.jag", "Jagex library", 0);
        if (buff != null) {
            // Logo TGA decoding not needed on mobile - loading screen is handled by Surface
            // Just need the fonts
        }
        buff = readDataFile("fonts.jag", "Game fonts", 5);
        if (buff != null) {
            Surface.createFont(Util.unpackData("h11p.jf", 0, buff), 0);
            Surface.createFont(Util.unpackData("h12b.jf", 0, buff), 1);
            Surface.createFont(Util.unpackData("h12p.jf", 0, buff), 2);
            Surface.createFont(Util.unpackData("h13b.jf", 0, buff), 3);
            Surface.createFont(Util.unpackData("h14b.jf", 0, buff), 4);
            Surface.createFont(Util.unpackData("h16b.jf", 0, buff), 5);
            Surface.createFont(Util.unpackData("h20b.jf", 0, buff), 6);
            Surface.createFont(Util.unpackData("h24b.jf", 0, buff), 7);
        }
    }

    protected void showLoadingProgress(int i, String s) {
        loadingProgressPercent = i;
        loadingProgessText = s;
        System.out.println("[Loading] " + s + " (" + i + "%)");
    }

    /**
     * Decode a TGA image from raw bytes into an int[] ARGB pixel array.
     * Returns null if the data is invalid.
     */
    public int[] decodeTgaPixels(byte[] buff) {
        if (buff == null || buff.length < 786) return null;
        int w = (buff[13] & 0xFF) * 256 + (buff[12] & 0xFF);
        int h = (buff[15] & 0xFF) * 256 + (buff[14] & 0xFF);
        // Build palette
        int[] palette = new int[256];
        for (int k = 0; k < 256; k++) {
            int r = buff[20 + k * 3] & 0xFF;
            int g = buff[19 + k * 3] & 0xFF;
            int b = buff[18 + k * 3] & 0xFF;
            palette[k] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        int[] pixels = new int[w * h];
        int idx = 0;
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                pixels[idx++] = palette[buff[786 + x + y * w] & 0xFF];
            }
        }
        return pixels;
    }

    protected byte[] readDataFile(String file, String description, int percent) {
        file = Constants.CACHE_DIRECTORY + "jags/" + file;
        int archiveSize = 0;
        int archiveSizeCompressed = 0;
        byte archiveData[] = null;
        try {
            showLoadingProgress(percent, "Loading " + description + " - 0%");
            java.io.InputStream inputstream = Util.openFile(file);
            DataInputStream datainputstream = new DataInputStream(inputstream);
            byte header[] = new byte[6];
            datainputstream.readFully(header, 0, 6);
            archiveSize = ((header[0] & 0xff) << 16) + ((header[1] & 0xff) << 8) + (header[2] & 0xff);
            archiveSizeCompressed = ((header[3] & 0xff) << 16) + ((header[4] & 0xff) << 8) + (header[5] & 0xff);
            showLoadingProgress(percent, "Loading " + description + " - 5%");
            int read = 0;
            archiveData = new byte[archiveSizeCompressed];
            while (read < archiveSizeCompressed) {
                int length = archiveSizeCompressed - read;
                if (length > 1000)
                    length = 1000;
                datainputstream.readFully(archiveData, read, length);
                read += length;
                showLoadingProgress(percent, "Loading " + description + " - " + (5 + (read * 95) / archiveSizeCompressed) + "%");
            }
            datainputstream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        showLoadingProgress(percent, "Unpacking " + description);
        if (archiveSizeCompressed != archiveSize) {
            byte decompressed[] = new byte[archiveSize];
            BZLib.decompress(decompressed, archiveSize, archiveData, archiveSizeCompressed, 0);
            return decompressed;
        } else {
            return archiveData;
        }
    }

}
