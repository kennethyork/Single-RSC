package org.nemotech.rsc.plugins.commands;

import org.nemotech.rsc.client.mudclient;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.CommandListener;

public class Graphics extends Plugin implements CommandListener {

    @Override
    public void onCommand(String command, String[] args, Player player) {
        if(command.equals("toggleroofs")) {
            mudclient.getInstance().toggleRoofs();
            if (mudclient.getInstance().hideRoofs) {
                player.getSender().sendMessage("Roofs are now hidden.");
            } else {
                player.getSender().sendMessage("Roofs are now visible.");
            }
        }
    }
}
