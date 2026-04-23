package org.nemotech.rsc.client.action.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.nemotech.rsc.client.action.ActionHandler;
import org.nemotech.rsc.client.mudclient;
import org.nemotech.rsc.Constants;
import org.nemotech.rsc.util.Util;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.World;

public class SleepHandler implements ActionHandler {
    
    private mudclient mc = mudclient.getInstance();
    
    /** Stored captcha pixel data: each entry is [width, height, ...pixels] */
    private int[][] imageData;
    
    private String[] wordNames;
    
    private int numImages;
    
    public void init() {
        try {
            String[] captchaFiles;
            if (Constants.IS_ANDROID) {
                captchaFiles = org.nemotech.rsc.android.AndroidPlatform.listAssets("captcha");
            } else {
                java.io.File dir = new java.io.File(Constants.CACHE_DIRECTORY + "captcha");
                captchaFiles = dir.list();
            }
            if (captchaFiles == null) captchaFiles = new String[0];
            List<String> pngNames = new ArrayList<>();
            for (String name : captchaFiles) {
                if (name.endsWith(".png")) {
                    pngNames.add(name);
                }
            }
            numImages = pngNames.size();
            imageData = new int[numImages][];
            wordNames = new String[numImages];
            for (int i = 0; i < numImages; i++) {
                String name = pngNames.get(i);
                wordNames[i] = name.substring(0, name.lastIndexOf("."));
                imageData[i] = decodePng("captcha/" + name);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private int[] decodePng(String path) throws IOException {
        return org.nemotech.rsc.android.AndroidPlatform.decodeImagePixels(path);
    }
    
    /** Returns captcha pixel data as [width, height, ...pixels]. */
    public int[] generateCaptcha(Player p) {
        int random = Util.random(numImages);
        p.setSleepword(wordNames[random]);
        return imageData[random];
    }
    
    public boolean handleGuess(String word) {
        Player player = World.getWorld().getPlayer();
        if(word.equalsIgnoreCase(player.getSleepword())) {
            player.getSender().sendWakeUp(true, false);
            return true;
        }
        return false;
    }
    
    public void handleSleep(int[] pixelData) {
        if (!mc.isSleeping) {
            mc.fatigueSleeping = mc.statFatigue;
        }
        mc.isSleeping = true;
        mc.inputTextCurrent = "";
        mc.inputTextFinal = "";
        try {
            mc.captchaWidth = pixelData[0];
            mc.captchaHeight = pixelData[1];
            mc.captchaPixels = new int[mc.captchaWidth][mc.captchaHeight];
            for(int x = 0; x < mc.captchaWidth; x++) {
                for(int y = 0; y < mc.captchaHeight; y++) {
                    mc.captchaPixels[x][y] = pixelData[2 + y * mc.captchaWidth + x];
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        mc.sleepingStatusText = null;
    }
    
}