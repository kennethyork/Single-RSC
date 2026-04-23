package org.nemotech.rsc.client.action.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.model.player.Cache;
import org.nemotech.rsc.client.action.ActionHandler;
import org.nemotech.rsc.model.player.SaveFile;

public class RegisterHandler implements ActionHandler {
    
    public boolean handleRegister(String username, boolean hardcore) {
        username = username.replace("_", " ");
        String playersDir = Constants.SAVE_DIRECTORY + "players" + File.separator;
        new File(playersDir).mkdirs();
        File dataFile = new File(playersDir + username + "_data.dat");
        File cacheFile = new File(playersDir + username + "_cache.dat");
        if(!dataFile.exists() && !cacheFile.exists()) {
            try {
                // player data file
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dataFile));
                SaveFile playerData = new SaveFile(true);
                playerData.hardcore = hardcore;
                oos.writeObject(playerData);
                oos.close();
                
                // player cache file
                oos = new ObjectOutputStream(new FileOutputStream(cacheFile));
                Cache cache = new Cache();
                oos.writeObject(cache);
                oos.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    
}
