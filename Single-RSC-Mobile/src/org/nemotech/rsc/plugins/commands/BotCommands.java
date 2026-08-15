package org.nemotech.rsc.plugins.commands;

import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.CommandListener;

/**
 * Compatibility shell retained for plugin discovery.
 * Android exposes no player-automation bot commands.
 */
public class BotCommands extends Plugin implements CommandListener {

    public String[] getCommands() {
        return new String[0];
    }

    @Override
    public void onCommand(String command, String[] args, Player player) {
        // No bot commands are registered on Android.
    }
}
