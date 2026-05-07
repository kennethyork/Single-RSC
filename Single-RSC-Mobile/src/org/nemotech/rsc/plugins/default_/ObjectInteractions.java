package org.nemotech.rsc.plugins.default_;

import org.nemotech.rsc.model.GameObject;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.ObjectActionListener;
import org.nemotech.rsc.plugins.listeners.executive.ObjectActionExecutiveListener;

/**
 * Additional object interactions for RSC
 * Handles: altars, obelisks, coffins, gravestones, carts, wells, fountains
 */
public class ObjectInteractions extends Plugin implements ObjectActionListener, ObjectActionExecutiveListener {

    @Override
    public boolean blockObjectAction(GameObject obj, String command, Player player) {
        String name = obj.getGameObjectDef().getName().toLowerCase();
        int id = obj.getID();
        
        if (name.contains("altar")) {
            return true;
        }
        if (name.contains("obelisk")) {
            return true;
        }
        if (name.contains("coffin")) {
            return true;
        }
        if (name.contains("gravestone")) {
            return true;
        }
        if (name.contains("cart")) {
            return true;
        }
        if (name.contains("well")) {
            return true;
        }
        if (name.contains("fountain")) {
            return true;
        }
        if (name.contains("cauldron")) {
            return true;
        }
        if (name.contains("loom")) {
            return true;
        }
        if (name.contains("spinning wheel")) {
            return true;
        }
        if (name.contains("pottery wheel")) {
            return true;
        }
        if (name.contains("range")) {
            return true;
        }
        if (name.contains("furnace")) {
            return true;
        }
        if (name.contains("anvil")) {
            return true;
        }
        if (name.contains("forge")) {
            return true;
        }
        if (name.contains("bee")) {
            return true;
        }
        if (name.contains("hive")) {
            return true;
        }
        if (name.contains("trap")) {
            return true;
        }
        
        switch (id) {
            case 41: case 137: return true;
            case 145: return true;
            case 195: case 262: return true;
            case 208: return true;
            case 215: return true;
            case 221: return true;
            case 258: return true;
            case 285: return true;
            case 288: return true;
            case 300: return true;
            case 301: return true;
            default: return false;
        }
    }

    @Override
    public void onObjectAction(GameObject obj, String command, Player player) {
        int id = obj.getID();
        String name = obj.getGameObjectDef().getName().toLowerCase();
        
        if (name.contains("altar")) {
            player.message("You kneel at the altar");
            player.message("You feel a sense of tranquility");
            return;
        }
        
        if (name.contains("obelisk")) {
            player.message("The obelisk hums with ancient energy");
            player.message("Nothing interesting happens");
            return;
        }
        
        if (name.contains("coffin")) {
            player.message("The coffin is sealed shut");
            return;
        }
        
        if (name.contains("gravestone")) {
            player.message("You pray at the gravestone");
            return;
        }
        
        if (name.contains("cart")) {
            player.message("The cart is full of ore");
            return;
        }
        
        if (name.contains("well")) {
            player.message("You look down the well");
            player.message("The water is dark and deep");
            return;
        }
        
        if (name.contains("fountain")) {
            player.message("The water is cool and clear");
            return;
        }
        
        if (name.contains("cauldron")) {
            player.message("The cauldron bubbles ominously");
            return;
        }
        
        if (name.contains("loom") || name.contains("spinning wheel") || name.contains("pottery wheel")) {
            player.message("You need to use your inventory to do that");
            return;
        }
        
        if (name.contains("range") || name.contains("furnace") || name.contains("anvil") || name.contains("forge")) {
            player.message("You need to be close to it to use it");
            return;
        }
        
        if (name.contains("bee") || name.contains("hive")) {
            player.message("You'd better not disturb the bees");
            return;
        }
        
        if (name.contains("trap")) {
            player.message("You carefully avoid the trap");
            return;
        }
        
        switch (id) {
            case 41: case 137:
                player.message("The coffin is sealed shut");
                return;
                
            case 145:
                player.message("You kneel at the altar");
                player.message("You feel a sense of peace");
                return;
                
            case 195: case 262:
                player.message("The fish flops around");
                return;
                
            case 208:
                player.message("The pillar has collapsed");
                return;
                
            case 215:
                player.message("You examine the bone");
                return;
                
            case 221:
                player.message("The vine is too tough to break");
                return;
                
            case 258:
                player.message("The cauldron bubbles ominously");
                return;
                
            case 285:
                player.message("The hedge is overgrown");
                return;
                
            case 288:
                player.message("The giant crystal glows faintly");
                player.message("Ancient magic is at work here");
                return;
                
            case 300:
                player.message("The archway leads nowhere");
                return;
                
            case 301:
                player.message("The obelisk hums with energy");
                return;
                
            default:
                player.message("Nothing interesting happens");
        }
    }
}
