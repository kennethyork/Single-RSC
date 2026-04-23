package org.nemotech.rsc.android;

/**
 * Static bridge between the Android host and the game engine.
 * Provides the game thread access to the Android SurfaceView for rendering.
 */
public final class GameBridge {
    public static volatile GameSurfaceView surfaceView;
}
