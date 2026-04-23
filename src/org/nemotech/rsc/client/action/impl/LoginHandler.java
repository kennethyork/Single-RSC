package org.nemotech.rsc.client.action.impl;

import java.io.File;

import org.nemotech.rsc.Constants;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.model.World;
import org.nemotech.rsc.client.action.ActionHandler;
import org.nemotech.rsc.client.action.ActionManager;

public class LoginHandler implements ActionHandler {

    public int handleLogin(String username) {
        Player player = World.getWorld().getPlayer();
        byte loginCode = 22;
        try {
            int res = getLogin(username);
            switch (res) {
                case 3:
                    // Auto-create a fresh non-hardcore account when missing
                    boolean created = org.nemotech.rsc.client.action.ActionManager
                            .get(RegisterHandler.class)
                            .handleRegister(username.toLowerCase().trim(), true);
                    if (created) {
                        player.load(username);
                        return 0;
                    }
                    return 1; // fall back: couldn't create
                case 2:
                    return 2; // hardcore dead / locked
                default:
                    player.load(username);
                    if(res == 50) {
                        return 50;
                    }
                    return 0;
            }
        } catch(Exception e) {
            e.printStackTrace();
            //loginCode = 7;
        }
        if(loginCode != 22) {
            player.destroy(true);
        }
        return -1;
    }

    public int getLogin(String user) {
        try {
            user = user.replace("_", " ");
            File file = new File(Constants.CACHE_DIRECTORY + "players" + File.separator + user.trim() + "_data.dat");
            if(file.exists()) {
                // Check for hardcore lock state before allowing login
                try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(file))) {
                    org.nemotech.rsc.model.player.SaveFile data = (org.nemotech.rsc.model.player.SaveFile) ois.readObject();
                    if (data.hardcore && data.hardcoreDead) {
                        return 2;
                    }
                } catch (Exception ignore) {}
                return 1;
            } else {
                return 3;
            }
        } catch(Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

}
