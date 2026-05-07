package org.nemotech.rsc.plugins.default_;

import org.nemotech.rsc.model.GameObject;
import org.nemotech.rsc.model.player.Player;
import org.nemotech.rsc.plugins.Plugin;
import org.nemotech.rsc.plugins.listeners.action.ObjectActionListener;
import org.nemotech.rsc.plugins.listeners.executive.ObjectActionExecutiveListener;

/**
 * Decorative object interactions for RSC
 * Handles: trees, rocks, plants, furniture, and other decorative objects
 */
public class DecorativeObjects extends Plugin implements ObjectActionListener, ObjectActionExecutiveListener {

    @Override
    public boolean blockObjectAction(GameObject obj, String command, Player player) {
        String name = obj.getGameObjectDef().getName().toLowerCase();
        
        if (name.contains("rock") && !name.contains("rock of ages")) {
            return true;
        }
        if (name.contains("tree") || name.contains("wood") || name.contains("pine")) {
            return true;
        }
        if (name.contains("grass") || name.contains("flower") || name.contains("bush")) {
            return true;
        }
        if (name.contains("fence") || name.contains("wall")) {
            return true;
        }
        if (name.contains("table") || name.contains("chair") || name.contains("bench")) {
            return true;
        }
        if (name.contains("cupboard") || name.contains("bookcase") || name.contains("shelf")) {
            return true;
        }
        if (name.contains("bed")) {
            return true;
        }
        if (name.contains("portrait") || name.contains("painting")) {
            return true;
        }
        if (name.contains("carpet") || name.contains("rug")) {
            return true;
        }
        if (name.contains("moss") || name.contains("lichen")) {
            return true;
        }
        if (name.contains("cobweb")) {
            return true;
        }
        if (name.contains("barrel") || name.contains("crate") || name.contains("bucket")) {
            return true;
        }
        if (name.contains("barrel") || name.contains("crate") || name.contains("bucket")) {
            return true;
        }
        if (name.contains("moss") || name.contains("lichen") || name.contains("fungus")) {
            return true;
        }
        if (name.contains("sand") || name.contains("dirt") || name.contains("mud")) {
            return true;
        }
        if (name.contains("vine") || name.contains("root")) {
            return true;
        }
        
        return false;
    }

    @Override
    public void onObjectAction(GameObject obj, String command, Player player) {
        String name = obj.getGameObjectDef().getName().toLowerCase();
        int id = obj.getID();
        
        if (name.contains("rock")) {
            player.message("You examine the rock");
            player.message("It's just a rock");
            return;
        }
        
        if (name.contains("tree") || name.contains("wood") || name.contains("pine")) {
            if (name.contains("dead")) {
                player.message("The tree is dead");
                player.message("It looks like it could be chopped down");
                return;
            }
            if (name.contains("palm")) {
                player.message("The palm tree sways in the breeze");
                return;
            }
            player.message("You examine the tree");
            player.message("It's a nice tree");
            return;
        }
        
        if (name.contains("grass") || name.contains("flower") || name.contains("bush")) {
            player.message("You examine the plants");
            return;
        }
        
        if (name.contains("fence") || name.contains("wall")) {
            player.message("You can't reach that");
            return;
        }
        
        if (name.contains("table") || name.contains("chair") || name.contains("bench")) {
            player.message("You sit down for a moment");
            player.message("It's comfortable");
            return;
        }
        
        if (name.contains("cupboard") || name.contains("bookcase") || name.contains("shelf")) {
            player.message("You look through the furniture");
            player.message("Nothing interesting");
            return;
        }
        
        if (name.contains("bed")) {
            player.message("You rest for a moment");
            player.message("You feel refreshed");
            return;
        }
        
        if (name.contains("portrait") || name.contains("painting")) {
            player.message("You examine the artwork");
            return;
        }
        
        if (name.contains("carpet") || name.contains("rug")) {
            player.message("The carpet is dusty");
            return;
        }
        
        if (name.contains("moss") || name.contains("lichen") || name.contains("fungus")) {
            player.message("You examine the growth");
            return;
        }
        
        if (name.contains("cobweb")) {
            player.message("The cobweb is covered in dust");
            return;
        }
        
        if (name.contains("barrel") || name.contains("crate") || name.contains("bucket")) {
            player.message("The container is empty");
            return;
        }
        
        if (name.contains("sand") || name.contains("dirt") || name.contains("mud")) {
            player.message("You get dirt on your hands");
            return;
        }
        
        if (name.contains("vine") || name.contains("root")) {
            player.message("The vegetation is thick here");
            return;
        }
        
        player.message("Nothing interesting happens");
    }
}
