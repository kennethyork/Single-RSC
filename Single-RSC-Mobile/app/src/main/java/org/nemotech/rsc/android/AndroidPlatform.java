package org.nemotech.rsc.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Platform bridge for Android. Provides file I/O that reads game data from
 * Android assets and player saves from internal storage.
 */
public final class AndroidPlatform {

    private static Context appContext;
    private static AssetManager assetManager;
    private static String internalDir;

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        assetManager = appContext.getAssets();
        internalDir = appContext.getFilesDir().getAbsolutePath() + "/";


        // Configure game constants for Android
        org.nemotech.rsc.Constants.IS_ANDROID = true;
        org.nemotech.rsc.Constants.CACHE_DIRECTORY = "";
        org.nemotech.rsc.Constants.SAVE_DIRECTORY = internalDir;
    }

    public static Context getContext() {
        return appContext;
    }

    public static AssetManager getAssets() {
        return assetManager;
    }

    /** Directory for writable files (player saves). */
    public static String getWritableDir() {
        return internalDir;
    }

    /**
     * Open a file from assets. The path should be relative to the cache root,
     * e.g. "jags/entity.jag" or "data/item_def.json".
     */
    public static InputStream openAsset(String relativePath) throws IOException {
        return new BufferedInputStream(assetManager.open(relativePath));
    }

    /**
     * Open a file – tries internal storage first (for writable files like saves),
     * then falls back to assets.
     */
    public static InputStream openFile(String path) throws IOException {
        // If the path contains the writable dir prefix, read from filesystem
        File f = new File(internalDir, path);
        if (f.exists()) {
            return new BufferedInputStream(new FileInputStream(f));
        }
        // Otherwise read from assets
        return openAsset(path);
    }

    /** Check if a file exists in internal storage. */
    public static boolean fileExists(String relativePath) {
        return new File(internalDir, relativePath).exists();
    }

    /** List files in an asset directory. */
    public static String[] listAssets(String dir) throws IOException {
        return assetManager.list(dir);
    }

    /** Decode a PNG/JPG image from assets into pixel array. Returns [width, height, ...pixels]. */
    public static int[] decodeImagePixels(String assetPath) throws IOException {
        InputStream is = openAsset(assetPath);
        Bitmap bmp = BitmapFactory.decodeStream(is);
        is.close();
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] result = new int[2 + w * h];
        result[0] = w;
        result[1] = h;
        bmp.getPixels(result, 2, w, 0, 0, w, h);
        bmp.recycle();
        return result;
    }

    /** Ensure a directory exists in the writable area. */
    public static void ensureDir(String relativePath) {
        new File(internalDir, relativePath).mkdirs();
    }

    /** Get a FileOutputStream for writing in internal storage. */
    public static FileOutputStream openWritableFile(String relativePath) throws IOException {
        File f = new File(internalDir, relativePath);
        f.getParentFile().mkdirs();
        return new FileOutputStream(f);
    }

    /** Check if a file exists in writable storage. */
    public static boolean writableFileExists(String relativePath) {
        return new File(internalDir, relativePath).exists();
    }

    /** Get the File object for a writable path. */
    public static File getWritableFile(String relativePath) {
        return new File(internalDir, relativePath);
    }
}
