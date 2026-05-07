package org.nemotech.rsc.plugins.default_;

import org.nemotech.rsc.model.GameObject;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.ObjectActionListener;
import org.nemotech.rsc.plugins.listeners.executive.ObjectActionExecutiveListener;

/**
 * World object interactions for RSC
 * Handles: ships, docks, fishing spots, doors, levers, traps, and other world objects
 */
public class WorldObjects extends Plugin implements ObjectActionListener, ObjectActionExecutiveListener {

    @Override
    public boolean blockObjectAction(GameObject obj, String command, Player player) {
        String name = obj.getGameObjectDef().getName().toLowerCase();
        int id = obj.getID();
        
        if (name.contains("ship") || name.contains("boat")) {
            return true;
        }
        if (name.contains("dock") || name.contains("pier") || name.contains("gangplank")) {
            return true;
        }
        if (name.contains("lever")) {
            return true;
        }
        if (name.contains("button")) {
            return true;
        }
        if (name.contains("trapdoor") || name.contains("hatch")) {
            return true;
        }
        if (name.contains("fishing spot")) {
            return true;
        }
        if (name.contains("net")) {
            return true;
        }
        if (name.contains("crate") || name.contains("barrel")) {
            return true;
        }
        if (name.contains("egg") || name.contains("nest")) {
            return true;
        }
        if (name.contains("track")) {
            return true;
        }
        
        switch (id) {
            case 55: case 158: case 234: case 235: case 242: case 321: case 322:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onObjectAction(GameObject obj, String command, Player player) {
        int id = obj.getID();
        String name = obj.getGameObjectDef().getName().toLowerCase();
        
        if (name.contains("ship") || name.contains("boat")) {
            player.message("The ship is berthed at the docks");
            return;
        }
        
        if (name.contains("dock") || name.contains("pier") || name.contains("gangplank")) {
            player.message("The dock is busy with fishermen");
            return;
        }
        
        if (name.contains("lever")) {
            player.message("You pull the lever");
            player.message("Nothing happens");
            return;
        }
        
        if (name.contains("button")) {
            player.message("You press the button");
            player.message("Nothing happens");
            return;
        }
        
        if (name.contains("trapdoor") || name.contains("hatch")) {
            player.message("The trapdoor is closed");
            return;
        }
        
        if (name.contains("fishing spot")) {
            player.message("You need to use a fishing rod on a fishing spot");
            return;
        }
        
        if (name.contains("net")) {
            player.message("The net is used for fishing");
            return;
        }
        
        if (name.contains("crate") || name.contains("barrel")) {
            player.message("The container is empty");
            return;
        }
        
        if (name.contains("egg") || name.contains("nest")) {
            player.message("You see eggs in the nest");
            return;
        }
        
        if (name.contains("track")) {
            player.message("Mine tracks lead to the dwarven mines");
            return;
        }
        
        switch (id) {
            case 55:
                player.message("The cart is full of coal");
                return;

            case 158: case 234: case 235: case 242: case 321: case 322:
                player.message("The ship is berthed at the docks");
                return;

            default:
                if (name.contains("dock") || name.contains("pier")) {
                    player.message("Wooden planks creak under your feet");
                    return;
                }
                if (name.contains("net") || name.contains("trap")) {
                    player.message("It's a " + name);
                    return;
                }
                if (name.contains("scaffolding") || name.contains("platform")) {
                    player.message("Construction is ongoing");
                    return;
                }
                if (name.contains("ladder") || name.contains("rope")) {
                    player.message("You could climb that");
                    return;
                }
                player.message("Nothing interesting happens");
        }
    }
}
